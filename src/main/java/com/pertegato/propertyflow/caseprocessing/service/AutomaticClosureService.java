package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.caseprocessing.persistence.AutomaticClosureStore.Command;
import com.pertegato.propertyflow.caseprocessing.persistence.AutomaticClosureStore;
import com.pertegato.propertyflow.caseprocessing.persistence.AutomaticQuestionStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseClosureStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.CaseState;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.Entry;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import com.pertegato.propertyflow.caseprocessing.persistence.ConversationEvaluationStore.Evaluation;
import com.pertegato.propertyflow.caseprocessing.persistence.ConversationEvaluationStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffReviewStore;
import com.pertegato.propertyflow.caseprocessing.service.AutomaticQuestionService.Result;
import com.pertegato.propertyflow.recommendation.service.ClosureAssessment;
import com.pertegato.propertyflow.recommendation.service.ClosureMessage;
import com.pertegato.propertyflow.recommendation.service.ClosureMessageService;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@Service
@Profile("propertyflow-backend & !frontend-demo")
public class AutomaticClosureService {

    private static final Duration GENERATION_LEASE = Duration.ofMinutes(3);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final CaseWorkflowStore workflow;
    private final ConversationEvaluationStore evaluations;
    private final AutomaticQuestionStore questions;
    private final AutomaticClosureStore commands;
    private final CaseClosureStore closures;
    private final StaffReviewStore reviews;
    private final ClosureAssessment assessment;
    private final ClosureMessage communication;
    private final AutomaticClosureRelease release;
    private final TransactionTemplate transaction;

    public AutomaticClosureService(CaseWorkflowStore workflow, ConversationEvaluationStore evaluations, AutomaticQuestionStore questions,
            AutomaticClosureStore commands, CaseClosureStore closures, StaffReviewStore reviews, ClosureAssessment assessment,
            ClosureMessage communication, AutomaticClosureRelease release, PlatformTransactionManager transactions) {
        this.workflow = workflow;
        this.evaluations = evaluations;
        this.questions = questions;
        this.commands = commands;
        this.closures = closures;
        this.reviews = reviews;
        this.assessment = assessment;
        this.communication = communication;
        this.release = release;
        transaction = new TransactionTemplate(transactions);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public boolean eligible(Evaluation evaluation) {
        return evaluation.outcome() != null && evaluation.outcome().failure() == null
                && !evaluation.outcome().analysis().humanRequired() && !evaluation.outcome().recommendation().humanRequired()
                && !"CRITICAL".equals(evaluation.outcome().analysis().urgency())
                && evaluation.outcome().closureAssessment() != null && evaluation.outcome().closureAssessment().proposed()
                && release.approved(evaluation.outcome().closureAssessment())
                && assessment.current(evaluation.outcome().closureAssessment(), messages(evaluation.caseId()));
    }

    // Aufruf innerhalb der fallgesperrten Validierung; keine zweite Transaktion auf derselben Fallsperre.
    public Result prepare(CaseState state, Entry entry, Evaluation evaluation) {
        require(eligible(evaluation));
        Command existing = commands.find(entry.id(), evaluation.id()).orElse(null);
        if (existing != null) {
            return new Result("CLOSE", existing.id());
        }
        ClosureAssessment.Result ground = evaluation.outcome().closureAssessment();
        String hash = basisHash(ground);
        return new Result("CLOSE", commands.prepare(state, entry, evaluation, hash).id());
    }

    static String basisHash(ClosureAssessment.Result ground) {
        // Die Belegreihenfolge darf bei erneuter Auswertung keinen zweiten Versandauftrag erzeugen.
        List<ClosureAssessment.Proof> proofs = ground.proofs().stream()
                .sorted(Comparator.comparing(ClosureAssessment.Proof::criterion)
                        .thenComparing(ClosureAssessment.Proof::messageId)
                        .thenComparing(ClosureAssessment.Proof::quote))
                .toList();
        return ClosureMessageService.digest(JSON.writeValueAsString(new Basis(ground.outcome(), ground.ruleId(), ground.ruleVersion(), proofs)));
    }

    public boolean owns(UUID id) {
        return commands.find(id).isPresent();
    }

    public Entry source(UUID id, String caseId, long leader) {
        Command command = commands.get(id);
        require(command.caseId().equals(caseId) && command.leader() == leader);
        Entry entry = workflow.entryForCase(caseId).orElseThrow();
        require(entry.id().equals(command.entry()) && entry.leadingKey() == leader
                && entry.childKey() != null && entry.parentElementKey() != null);
        return entry;
    }

    public Result publish(UUID id, long leader, long child) {
        Work work = transaction.execute(status -> {
            Command command = commands.get(id);
            CaseState state = context(command, leader, child);
            command = commands.get(id);
            if ("PUBLISHED".equals(command.state()) || "CLOSED".equals(command.state())) {
                return new Work(null, null, new Result(command.state(), id));
            }
            Result blocked = check(command, state, false);
            if (blocked != null) {
                return new Work(null, null, blocked);
            }
            if ("RUNNING".equals(command.state()) && command.started().plus(GENERATION_LEASE).isAfter(Instant.now())) {
                return new Work(null, null, new Result("BUSY", id));
            }
            UUID publication = commands.published(command);
            if (publication != null) {
                commands.reuse(id, publication, state.version(), evaluations.analysisVersion(state.id()));
                return new Work(null, null, new Result("PUBLISHED", id));
            }
            return new Work(commands.begin(id), messages(state.id()), null);
        });
        if (work.result() != null) {
            return work.result();
        }
        ClosureMessage.Result message;
        try {
            message = communication.generate(work.messages(), work.command().ground());
        } catch (RuntimeException exception) {
            transaction.executeWithoutResult(status -> {
                Command latest = commands.get(id);
                workflow.lockCase(latest.caseId()).orElseThrow();
                latest = commands.get(id);
                if ("RUNNING".equals(latest.state()) && work.command().attempt().equals(latest.attempt())) {
                    commands.state(id, "PREPARED");
                }
            });
            throw exception;
        }
        return transaction.execute(status -> {
            Command command = commands.get(id);
            CaseState state = context(command, leader, child);
            command = commands.get(id);
            if (!"RUNNING".equals(command.state()) || !work.command().attempt().equals(command.attempt())) {
                return new Result("STALE", id);
            }
            Result blocked = check(command, state, false);
            if (blocked != null) {
                return blocked;
            }
            if (message == null || !message.valid() || message.text() == null || message.text().isBlank()) {
                commands.state(id, "REVIEW");
                evaluations.signal(evaluations.get(command.evaluation()), "INVALID_CLOSURE_MESSAGE");
                return new Result("REVIEW", id);
            }
            commands.publish(command, message);
            return new Result("PUBLISHED", id);
        });
    }

    public Result close(UUID id, long leader, long child) {
        return transaction.execute(status -> {
            Command command = commands.get(id);
            CaseState state = context(command, leader, child);
            command = commands.get(id);
            if ("CLOSED".equals(command.state()) && closures.applied(id)) {
                return new Result("CLOSED", id);
            }
            require("PUBLISHED".equals(command.state()));
            Result blocked = check(command, state, true);
            if (blocked != null) {
                if ("BUSY".equals(blocked.state())) {
                    return blocked;
                }
                // Der bestehende BPMN-Rückweg führt zuerst zur aktuellen Auswertung und ihrer Mitarbeiterpriorität.
                return new Result("STALE", id);
            }
            closures.closeAutomatically(state, command);
            return new Result("CLOSED", id);
        });
    }

    public Result finish(UUID id, long leader, long child) {
        return transaction.execute(status -> {
            Command command = commands.get(id);
            CaseState state = context(command, leader, child);
            command = commands.get(id);
            if ("CLOSED".equals(command.state()) && closures.applied(id)) {
                require("CLOSED".equals(state.status()));
                return new Result("CLOSED", id);
            }
            require("REVIEW".equals(command.state()) && !"CLOSED".equals(state.status()));
            Entry entry = source(id, command.caseId(), leader);
            UUID continuation = command.continuation() == null ? reviews.prepare(state, leader, entry.callRef()).id() : command.continuation();
            commands.review(command, continuation, entry.callRef());
            return new Result("REVIEW", continuation);
        });
    }

    private Result check(Command command, CaseState state, boolean published) {
        Entry entry = source(command.id(), command.caseId(), command.leader());
        if (!("UNDER_CLARIFICATION".equals(state.status()) || workflow.questionPhase(state)) || evaluations.automaticBlocked(entry.callRef())) {
            return new Result("OBSOLETE", command.id());
        }
        if (commands.staffActionPending(state.id(), command.leader()) || evaluations.decisionPending(state.id(), state.version())) {
            return new Result("BUSY", command.id());
        }
        long version = published ? command.publishedVersion() : command.version();
        long analysis = published ? command.publishedAnalysisVersion() : command.analysisVersion();
        if (state.version() != version || evaluations.analysisVersion(state.id()) != analysis) {
            commands.state(command.id(), "STALE");
            return new Result("STALE", command.id());
        }
        Evaluation evaluation = evaluations.get(command.evaluation());
        if ("REVIEW".equals(command.state()) || questions.restriction(state.id(), command.leader()) != null
                || !eligible(evaluation)) {
            commands.state(command.id(), "REVIEW");
            evaluations.signal(evaluation, "HUMAN_DECISION_REQUIRED");
            return new Result("REVIEW", command.id());
        }
        return null;
    }

    private CaseState context(Command command, long leader, long child) {
        CaseState state = workflow.lockCase(command.caseId()).orElseThrow();
        require(command.leader() == leader && workflow.leadingInstance(command.caseId()).filter(key -> key == leader).isPresent());
        Entry entry = source(command.id(), command.caseId(), leader);
        require(entry.childKey() == child);
        return state;
    }

    private List<ClosureAssessment.Message> messages(String caseId) {
        return evaluations.input(caseId).messages().stream()
                .map(message -> new ClosureAssessment.Message(message.id(), message.author(), message.text(), message.sequence())).toList();
    }

    private void require(boolean valid) {
        if (!valid) {
            throw new WorkflowRejectedException("INVALID_AUTOMATIC_CLOSURE_CONTEXT");
        }
    }

    private record Basis(ClosureAssessment.Outcome outcome, String ruleId, String ruleVersion, List<ClosureAssessment.Proof> proofs) {
    }

    private record Work(Command command, List<ClosureAssessment.Message> messages, Result result) {
    }
}
