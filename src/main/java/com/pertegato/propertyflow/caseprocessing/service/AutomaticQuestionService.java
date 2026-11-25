package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.caseprocessing.persistence.AutomaticQuestionStore.Batch;
import com.pertegato.propertyflow.caseprocessing.persistence.AutomaticQuestionStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.CaseState;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.Entry;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import com.pertegato.propertyflow.caseprocessing.persistence.ConversationEvaluationStore.Evaluation;
import com.pertegato.propertyflow.caseprocessing.persistence.ConversationEvaluationStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffReviewStore;
import com.pertegato.propertyflow.recommendation.service.QuestionCatalog;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("propertyflow-backend")
@Transactional(propagation = Propagation.REQUIRES_NEW)
@RequiredArgsConstructor
public class AutomaticQuestionService {

    private final CaseWorkflowStore workflow;
    private final ConversationEvaluationStore evaluations;
    private final AutomaticQuestionStore questions;
    private final StaffReviewStore reviews;
    private final AutomaticClosureService closures;

    public Result validate(String caseId, UUID entryId, UUID call, long leader, long child, UUID evaluationId) {
        CaseState state = lock(caseId, leader);
        Entry entry = entry(caseId, entryId, call, leader, child);
        Batch existing = questions.find(entryId, evaluationId).orElse(null);
        if (existing != null) {
            return result(existing);
        }
        if (!("UNDER_CLARIFICATION".equals(state.status()) || workflow.questionPhase(state))) {
            return new Result("OBSOLETE", null);
        }
        Evaluation evaluation = evaluations.get(evaluationId);
        require(evaluation.caseId().equals(caseId) && evaluation.leader() == leader);
        if (!current(state, evaluation)) {
            return new Result("STALE", null);
        }
        String restriction = questions.restriction(caseId, leader);
        if (restriction == null && !evaluations.automaticBlocked(call) && closures.eligible(evaluation)) {
            return closures.prepare(state, entry, evaluation);
        }
        if (evaluation.outcome().failure() != null) {
            restriction = "ANALYSIS_FAILED";
        } else if (evaluation.outcome().analysis().humanRequired() || evaluation.outcome().recommendation().humanRequired()) {
            restriction = "HUMAN_DECISION_REQUIRED";
        } else if (evaluations.allAnswered(caseId)) {
            restriction = "ALL_QUESTIONS_ANSWERED";
        }
        List<String> proposed = evaluation.outcome().failure() == null ? evaluation.outcome().recommendation().questions() : List.of();
        if (!QuestionCatalog.QUESTIONS.keySet().containsAll(proposed)) {
            restriction = "INVALID_QUESTION_CATALOG";
        }
        if (restriction != null) {
            evaluations.signal(evaluation, restriction);
            return result(questions.prepare(state, entry, evaluationId, List.of(), "REVIEW", restriction));
        }
        if (evaluations.automaticBlocked(call)) {
            return new Result("OBSOLETE", null);
        }
        List<String> selected = proposed.stream().distinct().filter(question -> !questions.known(caseId, question)).toList();
        if (selected.isEmpty()) {
            // Ohne belastbare nächste Aktion wird keine Abschlussfreigabe aus freiem Empfehlungstext abgeleitet.
            String reason = questions.open(caseId) ? "EXISTING_ANSWER_REQUIREMENT" : "AUTOMATIC_ACTION_UNCLEAR";
            if (!questions.open(caseId)) {
                evaluations.signal(evaluation, reason);
            }
            return result(questions.prepare(state, entry, evaluationId, List.of(), questions.open(caseId) ? "WAIT" : "REVIEW", reason));
        }
        return result(questions.prepare(state, entry, evaluationId, selected, "PREPARED", null));
    }

    public Result publish(UUID id, long leader, long child) {
        Batch batch = questions.get(id);
        CaseState state = lock(batch.caseId(), leader);
        Entry entry = workflow.entryForCase(batch.caseId()).orElseThrow();
        entry(batch.caseId(), batch.entry(), entry.callRef(), leader, child);
        if ("PUBLISHED".equals(batch.state())) {
            return result(batch);
        }
        if (!("UNDER_CLARIFICATION".equals(state.status()) || workflow.questionPhase(state)) || evaluations.automaticBlocked(entry.callRef())) {
            return new Result("OBSOLETE", id);
        }
        String restriction = questions.restriction(batch.caseId(), leader);
        if ("PREPARED".equals(batch.state()) && restriction != null) {
            questions.requireReview(id, restriction);
            evaluations.signal(evaluations.get(batch.evaluation()), restriction);
            return new Result("REVIEW", id);
        }
        if (!"PREPARED".equals(batch.state()) || state.version() != batch.version()
                || !current(state, evaluations.get(batch.evaluation()))
                || !QuestionCatalog.VERSION.equals(batch.catalog()) || batch.questions().stream().anyMatch(question -> questions.known(batch.caseId(), question))) {
            questions.stale(id);
            return new Result("STALE", id);
        }
        questions.publish(batch, child);
        return new Result("PUBLISHED", id);
    }

    public Result finish(UUID id, long leader, long child) {
        Batch batch = questions.get(id);
        CaseState state = lock(batch.caseId(), leader);
        Entry entry = workflow.entryForCase(batch.caseId()).orElseThrow();
        entry(batch.caseId(), batch.entry(), entry.callRef(), leader, child);
        if (!("UNDER_CLARIFICATION".equals(state.status()) || workflow.questionPhase(state))) {
            return new Result("OBSOLETE", id);
        }
        boolean review = "REVIEW".equals(batch.state()) || questions.restriction(batch.caseId(), leader) != null
                || evaluations.allAnswered(batch.caseId());
        if (review) {
            UUID continuation = batch.continuation() == null ? reviews.prepare(state, leader, entry.callRef()).id() : batch.continuation();
            questions.review(id, continuation, entry.callRef());
            return new Result("REVIEW", continuation);
        }
        require("PUBLISHED".equals(batch.state()) || "WAIT".equals(batch.state()));
        require(questions.open(batch.caseId()));
        questions.waitEntry(batch, child, state.version());
        return new Result("WAIT_FOR_REPLY", id);
    }

    public Entry source(UUID batchId, String caseId, long leader) {
        Batch batch = questions.get(batchId);
        require(batch.caseId().equals(caseId) && batch.leader() == leader);
        Entry entry = workflow.entryForCase(caseId).orElseThrow();
        require(entry.id().equals(batch.entry()) && entry.leadingKey() == leader && entry.childKey() != null && entry.parentElementKey() != null);
        return entry;
    }

    private Result result(Batch batch) {
        return new Result("PREPARED".equals(batch.state()) ? "QUESTION" : batch.state(), batch.id());
    }

    private CaseState lock(String caseId, long leader) {
        CaseState state = workflow.lockCase(caseId).orElseThrow();
        require(workflow.leadingInstance(caseId).filter(key -> key == leader).isPresent() && !"CLOSED".equals(state.status()));
        return state;
    }

    private Entry entry(String caseId, UUID id, UUID call, long leader, long child) {
        Entry entry = workflow.entryForCase(caseId).orElseThrow();
        require(entry.id().equals(id) && entry.callRef().equals(call) && entry.leadingKey() == leader
                && entry.childKey() != null && entry.childKey() == child);
        return entry;
    }

    private boolean current(CaseState state, Evaluation evaluation) {
        return "APPLIED".equals(evaluation.state()) && evaluation.appliedVersion() != null && evaluation.appliedVersion() == state.version()
                && evaluation.analysisVersion() == evaluations.analysisVersion(state.id());
    }

    private void require(boolean valid) {
        if (!valid) {
            throw new WorkflowRejectedException("INVALID_AUTOMATIC_QUESTION_CONTEXT");
        }
    }

    public record Result(String state, UUID reference) {
    }
}
