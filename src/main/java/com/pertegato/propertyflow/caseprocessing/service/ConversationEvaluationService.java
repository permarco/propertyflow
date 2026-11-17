package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.caseprocessing.contract.Urgency;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.CaseState;
import com.pertegato.propertyflow.caseprocessing.persistence.ConversationEvaluationStore;
import com.pertegato.propertyflow.caseprocessing.persistence.ConversationEvaluationStore.Evaluation;
import com.pertegato.propertyflow.caseprocessing.persistence.ConversationEvaluationStore.Outcome;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantMessageStore;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantMessageStore.Message;
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
            PlatformTransactionManager transactions, AutomaticClosureRelease closureRelease) {
        this.workflow = workflow;
        this.messages = messages;
        this.evaluations = evaluations;
        this.urgency = urgency;
        this.analyzer = analyzer;
        this.recommendation = recommendation;
        this.closureAssessment = closureAssessment;
        this.closureRelease = closureRelease;
        transaction = new TransactionTemplate(transactions);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Result evaluate(String caseId, UUID messageId, long leader, long child, String tenant) {
        return evaluate(caseId, leader, messageId, () -> verify(caseId, messageId, leader, child, tenant));
    }

    public Result evaluateAutomatic(String caseId, UUID entryId, UUID callRef, long leader, long child) {
        return evaluate(caseId, leader, null, () -> verifyAutomatic(caseId, entryId, callRef, leader, child));
    }

    public Result evaluateReply(String caseId, UUID entry, long leader, long child, String tenant) {
        return evaluate(caseId, leader, null, () -> verifyReply(caseId, entry, leader, child, tenant));
    }

    public Result applyReply(String caseId, UUID entry, long leader, long child, String tenant, UUID evaluation) {
        return apply(caseId, null, leader, evaluation, () -> verifyReply(caseId, entry, leader, child, tenant));
    }

    private CaseState verifyReply(String caseId, UUID entry, long leader, long child, String tenant) {
        CaseState state = workflow.lockCase(caseId).orElseThrow(() -> new WorkflowRejectedException("UNKNOWN_CASE"));
        return "WAITING_FOR_TENANT".equals(state.status()) && workflow.leadingInstance(caseId).filter(key -> key == leader).isPresent()
                && evaluations.replyCurrent(caseId, entry, leader, child, tenant) ? state : null;
    }

    private Result evaluate(String caseId, long leader, UUID messageId, Supplier<CaseState> context) {
        Work work = transaction.execute(status -> {
            CaseState state = context.get();
            if (state == null) {
                return new Work(null, null, new Result("OBSOLETE", null));
            }
            if (messageId != null && evaluations.contactPending(messageId) || evaluations.decisionPending(caseId, state.version())) {
                return new Work(null, null, new Result("BUSY", null));
            }
            long analysisVersion = evaluations.analysisVersion(caseId);
            Evaluation current = evaluations.current(caseId, leader, state.version(), analysisVersion).orElse(null);
            if (current != null && ("READY".equals(current.state()) || "APPLIED".equals(current.state()))) {
                return new Work(null, null, new Result(current.state(), current.id()));
            }
            if (current != null && "RUNNING".equals(current.state()) && current.startedAt().plus(ATTEMPT_LEASE).isAfter(Instant.now())) {
                return new Work(null, null, new Result("BUSY", current.id()));
            }
            return new Work(evaluations.begin(caseId, leader, state.version(), analysisVersion, state.status()), evaluations.input(caseId), null);
        });
        if (work.result() != null) {
            return work.result();
        }
        ConversationAnalysis.Result analysis = analyzer.analyze(work.input());
        Recommendation.Result advice = analysis.failure() == null ? recommendation.recommend(json.writeValueAsString(work.input())) : null;
        ClosureAssessment.Result closure = assessClosure(work, analysis, advice);
        Outcome outcome = new Outcome(analysis, advice, closure);
        return transaction.execute(status -> {
            CaseState state = context.get();
            Evaluation current = evaluations.get(work.evaluation().id());
            if (!current.attempt().equals(work.evaluation().attempt()) || !"RUNNING".equals(current.state())) {
                return new Result("STALE", current.id());
            }
            if (state == null || state.version() != current.version() || evaluations.analysisVersion(caseId) != current.analysisVersion()) {
                evaluations.supersede(current.id());
                return new Result(state == null ? "OBSOLETE" : "STALE", current.id());
            }
            evaluations.ready(current.id(), outcome);
            return new Result("READY", current.id());
        });
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

    private ClosureAssessment.Result assessClosure(Work work, ConversationAnalysis.Result analysis, Recommendation.Result advice) {
        String phase = work.evaluation().phase();
        if (!("UNDER_CLARIFICATION".equals(phase) || "WAITING_FOR_TENANT".equals(phase)) || analysis.failure() != null
                || analysis.humanRequired() || "CRITICAL".equals(analysis.urgency()) || advice == null
                || advice.failure() != null || advice.humanRequired()) {
            return null;
        }
        return closureAssessment.assess(work.input().messages().stream()
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

    private record Work(Evaluation evaluation, Input input, Result result) {
    }
}
