package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.access.contract.StaffAccessDecision;
import com.pertegato.propertyflow.access.contract.StaffAccountAccess;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.caseprocessing.contract.CaseActionException;
import com.pertegato.propertyflow.caseprocessing.contract.StaffDecisionAction;
import com.pertegato.propertyflow.caseprocessing.contract.StaffTaskDecisions;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.delivery.StaffTaskGateway;
import com.pertegato.propertyflow.caseprocessing.delivery.StaffTaskGateway.Binding;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseDataStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.CaseState;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffDecisionStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffDecisionStore.Decision;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffReviewStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffReviewStore.Continuation;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("propertyflow-backend")
public class StaffDecisionService implements StaffTaskDecisions {

    private final StaffAccountAccess access;
    private final CaseWorkflowStore workflow;
    private final CaseDataStore caseData;
    private final StaffReviewStore reviews;
    private final StaffDecisionStore decisions;
    private final StaffTaskGateway engine;
    private final TransactionTemplate transaction;

    public StaffDecisionService(StaffAccountAccess access, CaseWorkflowStore workflow, CaseDataStore caseData,
            StaffReviewStore reviews, StaffDecisionStore decisions, StaffTaskGateway engine, PlatformTransactionManager transactions) {
        this.access = access;
        this.workflow = workflow;
        this.caseData = caseData;
        this.reviews = reviews;
        this.decisions = decisions;
        this.engine = engine;
        transaction = new TransactionTemplate(transactions);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public StaffTask current(WebAccountContext context, String caseId) {
        authorize(context);
        Snapshot snapshot = transaction.execute(status -> snapshot(caseId));
        if (snapshot == null) {
            return new StaffTask(false, null, 0, null, false, false);
        }
        Long task = engine.currentTask(snapshot.binding()).orElse(null);
        return new StaffTask(task != null, task == null ? null : Long.toString(task), snapshot.version(), snapshot.reviewToken(),
                task != null && !StaffProcessingBpmnConstants.UserTask.WORK_TASK.equals(snapshot.binding().element()), task != null);
    }

    @Override
    public void continueWork(WebAccountContext context, String caseId, UUID operationId, long taskKey, long version, String reviewToken, String reason) {
        submit(context, caseId, operationId, taskKey, version, reviewToken, StaffDecisionAction.CONTINUE, reason);
    }

    @Override
    public void closeCase(WebAccountContext context, String caseId, UUID operationId, long taskKey, long version, String reviewToken) {
        submit(context, caseId, operationId, taskKey, version, reviewToken, StaffDecisionAction.CLOSE, "");
    }

    private void submit(WebAccountContext context, String caseId, UUID operationId, long taskKey, long version,
            String reviewToken, StaffDecisionAction action, String reason) {
        submit(context, caseId, operationId, taskKey, version, reviewToken, action, reason, null);
    }

    public void followUp(WebAccountContext context, String caseId, UUID operationId, long taskKey, long version,
            String reviewToken, String reason, Integer days, LocalDate date) {
        Instant dueAt = FollowUpDate.calculate(Clock.systemUTC(), days, date);
        submit(context, caseId, operationId, taskKey, version, reviewToken, StaffDecisionAction.FOLLOW_UP, reason, dueAt);
    }

    public void waitExternal(WebAccountContext context, String caseId, UUID operationId, long taskKey, long version,
            String reviewToken) {
        submit(context, caseId, operationId, taskKey, version, reviewToken, StaffDecisionAction.WAIT_EXTERNAL, "", null);
    }

    private void submit(WebAccountContext context, String caseId, UUID operationId, long taskKey, long version,
            String reviewToken, StaffDecisionAction action, String reason, Instant dueAt) {
        authorize(context);
        if (operationId == null || taskKey <= 0 || version < 0 || reviewToken == null || reason == null || reason.length() > 1000) {
            throw new CaseActionException("INVALID_DECISION");
        }
        Snapshot snapshot = transaction.execute(status -> snapshot(caseId));
        require(snapshot != null && snapshot.version() == version && snapshot.reviewToken().equals(reviewToken));
        require(action != StaffDecisionAction.CONTINUE
                || !StaffProcessingBpmnConstants.UserTask.WORK_TASK.equals(snapshot.binding().element()));
        require(action != StaffDecisionAction.WAIT_EXTERNAL
                || StaffProcessingBpmnConstants.UserTask.WORK_TASK.equals(snapshot.binding().element()));
        require(engine.currentTask(snapshot.binding()).filter(key -> key == taskKey).isPresent());
        Decision decision = transaction.execute(status -> {
            authorize(context);
            Snapshot current = snapshot(caseId);
            require(snapshot.equals(current));
            Decision existing = decisions.find(caseId, context.accountId(), operationId).orElse(null);
            if (existing != null) {
                // Even an eventually consistent task search must not send a second native completion for this intent.
                throw new CaseActionException("INVALID_DECISION");
            }
            Decision prepared = decisions.prepare(caseId, snapshot.continuation(), context.accountId(), operationId, taskKey, version, reviewToken,
                    action, snapshot.binding().element(), reason);
            if (dueAt != null) {
                decisions.followUpRequest(prepared.id(), dueAt);
            }
            return prepared;
        });
        // No automatic retry: an unknown response may already have completed exactly this task.
        try {
            engine.completeTask(snapshot.binding(), taskKey, decision.id(), action);
        } catch (CaseActionException rejected) {
            decisions.reject(decision.id(), "NATIVE_DECISION_REJECTED");
            throw rejected;
        }
        decisions.confirmed(decision.id());
    }

    @Override
    public String decisionState(WebAccountContext context, String caseId, UUID operationId) {
        authorize(context);
        return decisions.find(caseId, context.accountId(), operationId).map(Decision::state).orElse(null);
    }

    public String progress(String caseId, long leader, long child, UUID decisionId) {
        return transaction.execute(status -> {
            CaseState state = lock(caseId);
            if (decisions.followUpResume(decisionId, caseId, leader, child)) {
                return "IN_PROGRESS".equals(state.status()) && workflow.leadingInstance(caseId).filter(key -> key == leader).isPresent()
                        ? "APPLIED" : "SUPERSEDED";
            }
            Decision decision = decisions.get(decisionId);
            Continuation continuation = reviews.continuation(decision.continuation(), caseId);
            if (decision.action() != StaffDecisionAction.CONTINUE || StaffProcessingBpmnConstants.UserTask.WORK_TASK.equals(decision.element())
                    || !decision.caseId().equals(caseId) || continuation.leader() != leader || continuation.childKey() == null
                    || continuation.childKey() != child || workflow.leadingInstance(caseId).filter(key -> key == leader).isEmpty()) {
                throw new WorkflowRejectedException("OTHER_STAFF_DECISION_PROCESS");
            }
            if ("APPLIED".equals(decision.state())) {
                return "APPLIED";
            }
            if (reviews.forCall(caseId, continuation.callRef()).filter(current -> current.id().equals(continuation.id())).isEmpty()) {
                decisions.reject(decisionId, "STAFF_CALL_SUPERSEDED");
                return "SUPERSEDED";
            }
            String expectedStatus = StaffProcessingBpmnConstants.UserTask.EXTERNAL_WAIT_TASK.equals(decision.element())
                    ? "WAITING_FOR_EXTERNAL" : "STAFF_REVIEW_REQUIRED";
            if (!expectedStatus.equals(state.status())) {
                decisions.reject(decisionId, "CASE_PHASE_CHANGED");
                return "SUPERSEDED";
            }
            if ("REJECTED".equals(decision.state()) || state.version() != decision.version()
                    || !decisions.reviewFingerprint(caseId, leader).equals(decision.reviewToken())
                    || access.checkStaffAccess(new WebAccountContext(decision.actor())) != StaffAccessDecision.ALLOWED) {
                decisions.reject(decisionId, "DECISION_BASIS_CHANGED");
                return "REVIEW_AGAIN";
            }
            decisions.progress(state, decisionId);
            return "APPLIED";
        });
    }

    private Snapshot snapshot(String caseId) {
        CaseState state = lock(caseId);
        if (!"STAFF_REVIEW_REQUIRED".equals(state.status()) && !"IN_PROGRESS".equals(state.status())
                && !"WAITING_FOR_EXTERNAL".equals(state.status())) {
            return null;
        }
        CaseWorkflowStore.Entry entry = workflow.entryForCase(caseId).orElseThrow();
        Continuation continuation = reviews.forCall(caseId, entry.callRef()).orElseThrow();
        CaseDataStore.Leader leader = caseData.leader(caseId).orElseThrow();
        if (!continuation.reviewApplied() || continuation.childKey() == null || continuation.parentElementKey() == null
                || continuation.leader() != leader.key()) {
            return null;
        }
        return new Snapshot(continuation.id(), new Binding(leader.key(), continuation.childKey(), continuation.parentElementKey(), leader.tenant(),
                "STAFF_REVIEW_REQUIRED".equals(state.status()) ? StaffProcessingBpmnConstants.UserTask.REVIEW_TASK
                        : "WAITING_FOR_EXTERNAL".equals(state.status()) ? StaffProcessingBpmnConstants.UserTask.EXTERNAL_WAIT_TASK
                                : StaffProcessingBpmnConstants.UserTask.WORK_TASK),
                state.version(), decisions.reviewFingerprint(caseId, leader.key()));
    }

    private CaseState lock(String caseId) {
        return workflow.lockCase(caseId).orElseThrow(() -> new CaseActionException("CASE_NOT_AVAILABLE"));
    }

    private void authorize(WebAccountContext context) {
        if (context == null) {
            throw new CaseActionException("UNAUTHENTICATED");
        }
        StaffAccessDecision decision = access.checkStaffAccess(context);
        if (decision != StaffAccessDecision.ALLOWED) {
            throw new CaseActionException(decision == StaffAccessDecision.UNAUTHENTICATED ? "UNAUTHENTICATED" : "FORBIDDEN");
        }
    }

    private void require(boolean condition) {
        if (!condition) {
            throw new CaseActionException("INVALID_DECISION");
        }
    }

    private record Snapshot(UUID continuation, Binding binding, long version, String reviewToken) {
    }
}
