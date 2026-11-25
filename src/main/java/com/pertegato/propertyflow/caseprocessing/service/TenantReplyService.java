package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.caseprocessing.persistence.AutomaticQuestionStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.CaseState;
import com.pertegato.propertyflow.caseprocessing.persistence.ConversationEvaluationStore;
import com.pertegato.propertyflow.caseprocessing.persistence.ConversationEvaluationStore.Evaluation;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantReplyStore;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantReplyStore.Call;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantReplyStore.Decision;
import com.pertegato.propertyflow.caseprocessing.service.ConversationEvaluationService.Result;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("propertyflow-backend")
public class TenantReplyService {

    private final CaseWorkflowStore workflow;
    private final TenantReplyStore replies;
    private final ConversationEvaluationStore evaluations;
    private final ConversationEvaluationService central;
    private final AutomaticQuestionStore questions;
    private final AutomaticClosureService closures;
    private final TransactionTemplate writes;

    public TenantReplyService(CaseWorkflowStore workflow, TenantReplyStore replies, ConversationEvaluationStore evaluations,
            ConversationEvaluationService central, AutomaticQuestionStore questions, PlatformTransactionManager transactions, AutomaticClosureService closures) {
        this.workflow = workflow;
        this.replies = replies;
        this.evaluations = evaluations;
        this.central = central;
        this.questions = questions;
        this.closures = closures;
        writes = new TransactionTemplate(transactions);
        writes.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public TenantReplyStore.Source source(UUID entry, String caseId, long leader) {
        return replies.source(entry, caseId, leader);
    }

    public Call register(String caseId, UUID entry, long leader, long child, long parent, String tenant, long sourceStaff) {
        return writes.execute(status -> {
            CaseState state = lock(caseId, leader);
            TenantReplyStore.Source source = replies.source(entry, caseId, leader);
            require(source.instance() == sourceStaff);
            if (source.automatic()) {
                require(workflow.entryForCase(caseId).filter(current -> current.childKey() != null
                        && current.childKey() == source.instance()).isPresent());
            }
            Call existing = replies.child(child).orElse(null);
            if (existing != null) {
                require(existing.entry().equals(entry) && existing.leader() == leader && existing.parent() == parent
                        && existing.tenant().equals(tenant));
                return existing;
            }
            require(!replies.closing(caseId, leader));
            require("WAITING_FOR_TENANT".equals(state.status())
                    || (source.automatic() ? "UNDER_CLARIFICATION" : "IN_PROGRESS").equals(state.status()));
            return replies.register(state, entry, leader, child, parent, tenant);
        });
    }

    public void ready(UUID callId) {
        writes.executeWithoutResult(status -> {
            Call call = replies.get(callId);
            CaseState state = lock(call.caseId(), call.leader());
            require("WAITING_FOR_TENANT".equals(state.status()));
            replies.ready(callId);
        });
    }

    public Result applyEvaluation(String caseId, UUID entry, long leader, long child, String tenant, UUID evaluationId) {
        Result result = evaluationId == null ? new Result("STALE", null) : central.applyReply(caseId, entry, leader, child, tenant, evaluationId);
        if (evaluationId != null && !"BUSY".equals(result.state())) {
            return result;
        }
        // Auch bei einer blockierten Übernahme hat eine inzwischen angeforderte Mitarbeiterprüfung Vorrang.
        return writes.execute(status -> {
            CaseState state = lock(caseId, leader);
            Call call = verify(caseId, entry, leader, child, tenant);
            if (!"WAITING_FOR_TENANT".equals(state.status()) || !"WAITING".equals(call.state())) {
                return new Result("OBSOLETE", null);
            }
            return replies.reviewPending(caseId, leader) ? new Result("REVIEW", null) : result;
        });
    }

    public Outcome validate(String caseId, UUID entry, long leader, long child, String tenant, UUID evaluationId) {
        return writes.execute(status -> {
            CaseState state = lock(caseId, leader);
            Call call = verify(caseId, entry, leader, child, tenant);
            Decision existing = replies.decision(call.id()).orElse(null);
            if (existing != null) {
                return outcome(existing);
            }
            require("WAITING_FOR_TENANT".equals(state.status()) && "WAITING".equals(call.state()));
            if (replies.reviewPending(caseId, leader)) {
                return outcome(replies.decide(call, state, null, true));
            }
            if (evaluationId == null) {
                return new Outcome("STALE", null);
            }
            Evaluation evaluation = evaluations.get(evaluationId);
            require(evaluation.caseId().equals(caseId) && evaluation.leader() == leader);
            if (!"APPLIED".equals(evaluation.state()) || evaluation.appliedVersion() == null || evaluation.appliedVersion() != state.version()
                    || evaluation.analysisVersion() != evaluations.analysisVersion(caseId)) {
                return new Outcome("STALE", null);
            }
            if (questions.restriction(caseId, leader) == null && closures.eligible(evaluation)) {
                return outcome(replies.decide(call, state, evaluationId, "CLOSE"));
            }
            boolean review = evaluation.outcome().failure() != null || evaluations.allAnswered(caseId);
            boolean additional = !review && evaluation.outcome().recommendation().questions().stream()
                    .anyMatch(question -> !questions.known(caseId, question));
            if (additional && questions.restriction(caseId, leader) != null) {
                evaluations.signal(evaluation, questions.restriction(caseId, leader));
                review = true;
            }
            return outcome(replies.decide(call, state, evaluationId, review ? "REVIEW" : additional ? "QUESTION" : "WAIT"));
        });
    }

    public Outcome review(UUID callId) {
        return writes.execute(status -> {
            Call call = replies.get(callId);
            CaseState state = lock(call.caseId(), call.leader());
            Decision existing = replies.decision(callId).orElse(null);
            if (existing != null) {
                return outcome(existing);
            }
            if (!"WAITING_FOR_TENANT".equals(state.status()) || !"WAITING".equals(call.state())
                    || !replies.reviewPending(call.caseId(), call.leader())) {
                return new Outcome("OBSOLETE", null);
            }
            return outcome(replies.decide(call, state, null, true));
        });
    }

    private Outcome outcome(Decision decision) {
        return new Outcome(decision.outcome(), decision.continuation() == null ? decision.id() : decision.continuation());
    }

    private Call verify(String caseId, UUID entry, long leader, long child, String tenant) {
        Call call = replies.child(child).orElseThrow(() -> new WorkflowRejectedException("UNREGISTERED_REPLY"));
        require(call.caseId().equals(caseId) && call.entry().equals(entry) && call.leader() == leader && call.tenant().equals(tenant));
        return call;
    }

    private CaseState lock(String caseId, long leader) {
        CaseState state = workflow.lockCase(caseId).orElseThrow(() -> new WorkflowRejectedException("UNKNOWN_CASE"));
        require(workflow.leadingInstance(caseId).filter(key -> key == leader).isPresent() && !"CLOSED".equals(state.status()));
        return state;
    }

    private void require(boolean valid) {
        if (!valid) {
            throw new WorkflowRejectedException("INVALID_REPLY_CONTEXT");
        }
    }

    public record Outcome(String state, UUID decision) {
    }
}
