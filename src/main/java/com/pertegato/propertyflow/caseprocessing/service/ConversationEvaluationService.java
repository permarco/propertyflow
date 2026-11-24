package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.caseprocessing.contract.Urgency;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.CaseState;
import com.pertegato.propertyflow.caseprocessing.persistence.ConversationEvaluationStore;
import com.pertegato.propertyflow.caseprocessing.persistence.ConversationEvaluationStore.Evaluation;
import com.pertegato.propertyflow.caseprocessing.persistence.ConversationEvaluationStore.Outcome;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantMessageStore;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantMessageStore.Message;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantReplyStore;
import com.pertegato.propertyflow.caseprocessing.persistence.UrgencyStore;
import com.pertegato.propertyflow.recommendation.service.ClosureAssessment;
import com.pertegato.propertyflow.recommendation.service.Recommendation;
import com.pertegato.propertyflow.triage.service.ConversationAnalysis;
import com.pertegato.propertyflow.triage.service.ConversationAnalysis.Input;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@Service
@Profile("propertyflow-backend & !frontend-demo")
public class ConversationEvaluationService {

    private static final Duration ATTEMPT_LEASE = Duration.ofMinutes(4);
    private final CaseWorkflowStore workflow;
    private final TenantMessageStore messages;
    private final TenantReplyStore replies;
    private final ConversationEvaluationStore evaluations;
    private final UrgencyStore urgency;
    private final ConversationAnalysis analyzer;
    private final Recommendation recommendation;
    private final ClosureAssessment closureAssessment;
    private final AutomaticClosureRelease closureRelease;
    private final TransactionTemplate transaction;
    private final JsonMapper json = JsonMapper.builder().build();

    public ConversationEvaluationService(CaseWorkflowStore workflow, TenantMessageStore messages, ConversationEvaluationStore evaluations,
            UrgencyStore urgency, ConversationAnalysis analyzer, Recommendation recommendation, ClosureAssessment closureAssessment,
            PlatformTransactionManager transactions, AutomaticClosureRelease closureRelease, TenantReplyStore replies) {
        this.workflow = workflow;
        this.messages = messages;
        this.replies = replies;
        this.evaluations = evaluations;
        this.urgency = urgency;
        this.analyzer = analyzer;
        this.recommendation = recommendation;
        this.closureAssessment = closureAssessment;
        this.closureRelease = closureRelease;
        transaction = new TransactionTemplate(transactions);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Result applyReply(String caseId, UUID entry, long leader, long child, String tenant, UUID evaluation) {
        return apply(caseId, null, leader, evaluation, () -> verifyReply(caseId, entry, leader, child, tenant));
    }

    private CaseState verifyReply(String caseId, UUID entry, long leader, long child, String tenant) {
        CaseState state = workflow.lockCase(caseId).orElseThrow(() -> new WorkflowRejectedException("UNKNOWN_CASE"));
        return "WAITING_FOR_TENANT".equals(state.status()) && workflow.leadingInstance(caseId).filter(key -> key == leader).isPresent()
                && evaluations.replyCurrent(caseId, entry, leader, child, tenant) ? state : null;
    }

    private Work beginEvaluation(String caseId, long leader, UUID messageId, CaseState state, UUID attempt, boolean tenantReply) {
        if (state == null) {
            return new Work(null, null, new Result("OBSOLETE", null));
        }
        boolean reviewPending = tenantReply && replies.reviewPending(caseId, leader);
        if (!reviewPending && (messageId != null && evaluations.contactPending(messageId)
                || evaluations.decisionPending(caseId, state.version()))) {
            return new Work(null, null, new Result("BUSY", null));
        }
        long analysisVersion = evaluations.analysisVersion(caseId);
        Evaluation current = evaluations.current(caseId, leader, state.version(), analysisVersion).orElse(null);
        if (reviewPending) {
            if (current != null && "RUNNING".equals(current.state()) && current.attempt().equals(attempt)) {
                evaluations.supersede(current.id());
            }
            return new Work(null, null, new Result("REVIEW", null));
        }
        if (current != null && ("READY".equals(current.state()) || "APPLIED".equals(current.state()))) {
            return new Work(null, null, new Result(current.state(), current.id()));
        }
        if (current != null && "RUNNING".equals(current.state())) {
            if (current.attempt().equals(attempt)) {
                return new Work(current, evaluations.input(caseId), null);
            }
            if (current.startedAt().plus(ATTEMPT_LEASE).isAfter(Instant.now())) {
                return new Work(null, null, new Result("BUSY", current.id()));
            }
        }
        Evaluation evaluation = evaluations.begin(caseId, leader, state.version(), analysisVersion, state.status(), attempt);
        return new Work(evaluation, evaluations.input(caseId), null);
    }

    public StageResult evaluateAutomaticStage(AutomaticContext origin, EvaluationStep step, UUID attempt, UUID evaluationId) {
        StageContext context = new StageContext(origin.caseId(), origin.leader(), null,
                () -> verifyAutomatic(origin.caseId(), origin.entryId(), origin.callRef(), origin.leader(), origin.child()), false);
        return evaluateStage(context, step, attempt, evaluationId);
    }

    public StageResult evaluateMessageStage(MessageContext origin, EvaluationStep step, UUID attempt, UUID evaluationId) {
        StageContext context = new StageContext(origin.caseId(), origin.leader(), origin.messageId(),
                () -> verify(origin.caseId(), origin.messageId(), origin.leader(), origin.child(), origin.tenant()), false);
        return evaluateStage(context, step, attempt, evaluationId);
    }

    public StageResult evaluateReplyStage(ReplyContext origin, EvaluationStep step, UUID attempt, UUID evaluationId) {
        StageContext context = new StageContext(origin.caseId(), origin.leader(), null,
                () -> verifyReply(origin.caseId(), origin.entryId(), origin.leader(), origin.child(), origin.tenant()), true);
        return evaluateStage(context, step, attempt, evaluationId);
    }

    private StageResult evaluateStage(StageContext origin, EvaluationStep step, UUID attempt, UUID evaluationId) {
        Work work = transaction.execute(status -> {
            CaseState state = origin.lockCase().get();
            if (step == EvaluationStep.ANALYZE) {
                return beginEvaluation(origin.caseId(), origin.leader(), origin.messageId(), state, attempt, origin.tenantReply());
            }
            Evaluation current = evaluations.get(evaluationId);
            Result stopped = checkStage(origin, state, current, attempt);
            return stopped == null ? new Work(current, evaluations.input(origin.caseId()), null) : new Work(null, null, stopped);
        });
        if (work.result() != null) {
            return new StageResult(work.result(), null);
        }
        EvaluationStep next = nextStep(work.evaluation());
        require(step.compareTo(next) <= 0);
        if (step != next) {
            return pendingStep(work.evaluation().id(), next);
        }

        Outcome partial = work.evaluation().outcome();
        Outcome outcome = switch (step) {
            case ANALYZE -> new Outcome(analyzer.analyze(work.input()), null, null);
            case ADVISE -> new Outcome(partial.analysis(), recommend(work.input()), null);
            case ASSESS_CLOSURE -> new Outcome(partial.analysis(), partial.recommendation(), assessClosure(work.input()));
        };
        return transaction.execute(status -> saveStage(origin, work.evaluation(), step, outcome));
    }

    private StageResult saveStage(StageContext origin, Evaluation work, EvaluationStep step, Outcome outcome) {
        CaseState state = origin.lockCase().get();
        Evaluation current = evaluations.get(work.id());
        Result stopped = checkStage(origin, state, current, work.attempt());
        if (stopped != null) {
            return new StageResult(stopped, null);
        }
        EvaluationStep currentStep = nextStep(current);
        if (step != currentStep) {
            return pendingStep(current.id(), currentStep);
        }
        if (step == EvaluationStep.ANALYZE && outcome.analysis().failure() == null) {
            evaluations.checkpoint(current.id(), outcome);
            return pendingStep(current.id(), EvaluationStep.ADVISE);
        }
        if (step == EvaluationStep.ADVISE && closureRequired(current.phase(), outcome.analysis(), outcome.recommendation())) {
            evaluations.checkpoint(current.id(), outcome);
            return pendingStep(current.id(), EvaluationStep.ASSESS_CLOSURE);
        }
        evaluations.ready(current.id(), outcome);
        return new StageResult(new Result("READY", current.id()), null);
    }

    private Result checkStage(StageContext origin, CaseState state, Evaluation current, UUID attempt) {
        require(current.caseId().equals(origin.caseId()) && current.leader() == origin.leader());
        if (state == null) {
            if ("RUNNING".equals(current.state()) && current.attempt().equals(attempt)) {
                evaluations.supersede(current.id());
            }
            return new Result("OBSOLETE", current.id());
        }
        if (!current.attempt().equals(attempt) || "SUPERSEDED".equals(current.state())) {
            return new Result("STALE", current.id());
        }
        if ("APPLIED".equals(current.state())) {
            return new Result("APPLIED", current.id());
        }
        if (state.version() != current.version() || evaluations.analysisVersion(origin.caseId()) != current.analysisVersion()) {
            evaluations.supersede(current.id());
            return new Result("STALE", current.id());
        }
        if (evaluations.decisionPending(origin.caseId(), state.version())
                || origin.messageId() != null && evaluations.contactPending(origin.messageId())) {
            return new Result("BUSY", current.id());
        }
        return "READY".equals(current.state()) ? new Result("READY", current.id()) : null;
    }

    private EvaluationStep nextStep(Evaluation current) {
        if (current.outcome() == null) {
            return EvaluationStep.ANALYZE;
        }
        return current.outcome().recommendation() == null ? EvaluationStep.ADVISE : EvaluationStep.ASSESS_CLOSURE;
    }

    private StageResult pendingStep(UUID evaluationId, EvaluationStep next) {
        return new StageResult(new Result("RUNNING", evaluationId), next);
    }

    public Result apply(String caseId, UUID messageId, long leader, long child, String tenant, UUID evaluationId) {
        return apply(caseId, messageId, leader, evaluationId, () -> verify(caseId, messageId, leader, child, tenant));
    }

    public Result applyAutomatic(String caseId, UUID entryId, UUID callRef, long leader, long child, UUID evaluationId) {
        return apply(caseId, null, leader, evaluationId, () -> verifyAutomatic(caseId, entryId, callRef, leader, child));
    }

    private Result apply(String caseId, UUID messageId, long leader, UUID evaluationId, Supplier<CaseState> context) {
        return transaction.execute(status -> {
            CaseState state = context.get();
            Evaluation evaluation = evaluations.get(evaluationId);
            require(evaluation.caseId().equals(caseId) && evaluation.leader() == leader);
            if (state == null) {
                return new Result("OBSOLETE", evaluationId);
            }
            if ("APPLIED".equals(evaluation.state())) {
                return new Result("APPLIED", evaluationId);
            }
            if (evaluations.decisionPending(caseId, state.version()) || messageId != null && evaluations.contactPending(messageId)) {
                return new Result("BUSY", evaluationId);
            }
            if (!"READY".equals(evaluation.state()) || state.version() != evaluation.version()
                    || evaluations.analysisVersion(caseId) != evaluation.analysisVersion() || !state.status().equals(evaluation.phase())) {
                evaluations.supersede(evaluationId);
                return new Result("STALE", evaluationId);
            }
            Outcome outcome = evaluation.outcome();
            boolean approvedClosure = outcome.closureAssessment() != null && outcome.closureAssessment().proposed()
                    && closureRelease.approved(outcome.closureAssessment()) && closureAssessment.current(outcome.closureAssessment(),
                            evaluations.input(caseId).messages().stream().map(message -> new ClosureAssessment.Message(
                                    message.id(), message.author(), message.text(), message.sequence())).toList());
            if (outcome.failure() != null) {
                evaluations.signal(evaluation, "ANALYSIS_FAILED");
            } else {
                evaluations.applyAnswers(evaluation);
                urgency.conversation(caseId, evaluationId, Urgency.valueOf(outcome.analysis().urgency()));
                if (outcome.closureAssessment() != null) {
                    if (outcome.closureAssessment().failure() != null) {
                        evaluations.signal(evaluation, "ANALYSIS_FAILED");
                    } else if (outcome.closureAssessment().outcome() != ClosureAssessment.Outcome.NONE
                            && !approvedClosure) {
                        // Bis zum fachlichen Modellnachweis bleiben auch positive Vorschläge bei der Mitarbeiterprüfung.
                        evaluations.signal(evaluation, "HUMAN_DECISION_REQUIRED");
                    }
                }
                if ("CRITICAL".equals(outcome.analysis().urgency())) {
                    evaluations.signal(evaluation, "CRITICAL_URGENCY");
                }
                if (outcome.analysis().humanRequired() || outcome.recommendation().humanRequired()) {
                    evaluations.signal(evaluation, "HUMAN_DECISION_REQUIRED");
                }
                if (!outcome.analysis().answers().isEmpty() && evaluations.allAnswered(caseId)
                        && !approvedClosure
                        && ("WAITING_FOR_TENANT".equals(state.status()) || "UNDER_CLARIFICATION".equals(state.status()))) {
                    evaluations.signal(evaluation, "ALL_QUESTIONS_ANSWERED");
                }
            }
            evaluations.finish(evaluation);
            return new Result("APPLIED", evaluationId);
        });
    }

    public boolean failed(String caseId, UUID messageId, long leader, long child, String tenant, UUID evaluationId) {
        return Boolean.TRUE.equals(transaction.execute(status -> {
            CaseState state = verify(caseId, messageId, leader, child, tenant);
            Evaluation evaluation = evaluations.get(evaluationId);
            require(evaluation.caseId().equals(caseId) && evaluation.leader() == leader);
            return state != null && evaluation.outcome() != null && evaluation.outcome().failure() != null;
        }));
    }

    private Recommendation.Result recommend(Input input) {
        return recommendation.recommend(json.writeValueAsString(input));
    }

    private boolean closureRequired(String phase, ConversationAnalysis.Result analysis, Recommendation.Result advice) {
        return ("UNDER_CLARIFICATION".equals(phase) || "WAITING_FOR_TENANT".equals(phase)) && analysis.failure() == null
                && !analysis.humanRequired() && !"CRITICAL".equals(analysis.urgency()) && advice != null
                && advice.failure() == null && !advice.humanRequired();
    }

    private ClosureAssessment.Result assessClosure(Input input) {
        return closureAssessment.assess(input.messages().stream()
                .map(message -> new ClosureAssessment.Message(message.id(), message.author(), message.text(), message.sequence())).toList());
    }

    public boolean contextReady(String caseId, UUID messageId, long leader, long child, String tenant) {
        return Boolean.TRUE.equals(transaction.execute(status -> verify(caseId, messageId, leader, child, tenant) == null
                || !evaluations.contactPending(messageId)));
    }

    public String automaticResult(String caseId, UUID entryId, UUID callRef, long leader, long child, UUID evaluationId) {
        return transaction.execute(status -> {
            CaseState state = verifyAutomatic(caseId, entryId, callRef, leader, child);
            Evaluation evaluation = evaluations.get(evaluationId);
            require(evaluation.caseId().equals(caseId) && evaluation.leader() == leader);
            if (state == null) {
                return "OBSOLETE";
            }
            if (!"APPLIED".equals(evaluation.state()) || evaluation.appliedVersion() == null
                    || state.version() != evaluation.appliedVersion() || evaluations.analysisVersion(caseId) != evaluation.analysisVersion()) {
                return "STALE";
            }
            return evaluation.outcome().failure() == null ? "CURRENT" : "FAILED";
        });
    }

    private CaseState verifyAutomatic(String caseId, UUID entryId, UUID callRef, long leader, long child) {
        CaseState state = workflow.lockCase(caseId).orElseThrow(() -> new WorkflowRejectedException("UNKNOWN_CASE"));
        if (workflow.leadingInstance(caseId).filter(key -> key == leader).isEmpty() || "CLOSED".equals(state.status())) {
            return null;
        }
        CaseWorkflowStore.Entry entry = workflow.entryForCase(caseId).orElseThrow();
        require(entry.id().equals(entryId) && entry.callRef().equals(callRef) && entry.leadingKey() == leader
                && entry.childKey() != null && entry.childKey() == child);
        return ("UNDER_CLARIFICATION".equals(state.status()) || workflow.questionPhase(state))
                && !evaluations.automaticBlocked(callRef) ? state : null;
    }

    private CaseState verify(String caseId, UUID messageId, long leader, long child, String tenant) {
        CaseState state = workflow.lockCase(caseId).orElseThrow(() -> new WorkflowRejectedException("UNKNOWN_CASE"));
        Message message = messages.get(messageId);
        require(message.caseId().equals(caseId) && message.leader() == leader && message.child() != null && message.child() == child
                && message.tenant().equals(tenant));
        if ("CLOSED".equals(state.status()) || workflow.leadingInstance(caseId).filter(key -> key == leader).isEmpty()) {
            return null;
        }
        require("RECEIVED".equals(message.state()) && !"RECEIVED".equals(state.status()));
        return state;
    }

    private void require(boolean valid) {
        if (!valid) {
            throw new WorkflowRejectedException("INVALID_CONVERSATION_CONTEXT");
        }
    }

    public record Result(String state, UUID evaluationId) {
    }

    public record AutomaticContext(String caseId, UUID entryId, UUID callRef, long leader, long child) {
    }

    public record MessageContext(String caseId, UUID messageId, long leader, long child, String tenant) {
    }

    public record ReplyContext(String caseId, UUID entryId, long leader, long child, String tenant) {
    }

    public enum EvaluationStep {
        ANALYZE,
        ADVISE,
        ASSESS_CLOSURE
    }

    public record StageResult(Result result, EvaluationStep nextStep) {
    }

    private record StageContext(String caseId, long leader, UUID messageId, Supplier<CaseState> lockCase, boolean tenantReply) {
    }

    private record Work(Evaluation evaluation, Input input, Result result) {
    }
}
