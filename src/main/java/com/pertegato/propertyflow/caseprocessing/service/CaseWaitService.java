package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.access.contract.StaffAccessDecision;
import com.pertegato.propertyflow.access.contract.StaffAccountAccess;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.caseprocessing.contract.StaffDecisionAction;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWaitStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWaitStore.Wait;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.CaseState;
import com.pertegato.propertyflow.caseprocessing.persistence.DirectFollowUpStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffDecisionStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffDecisionStore.Decision;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffReviewStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffReviewStore.Continuation;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("propertyflow-backend & !frontend-demo")
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class CaseWaitService {

    private final CaseWorkflowStore workflow;
    private final StaffDecisionStore decisions;
    private final StaffReviewStore reviews;
    private final CaseWaitStore waits;
    private final StaffAccountAccess access;
    private final DirectFollowUpStore directFollowUps;

    public CaseWaitService(CaseWorkflowStore workflow, StaffDecisionStore decisions, StaffReviewStore reviews,
            CaseWaitStore waits, StaffAccountAccess access, DirectFollowUpStore directFollowUps) {
        this.workflow = workflow;
        this.decisions = decisions;
        this.reviews = reviews;
        this.waits = waits;
        this.access = access;
        this.directFollowUps = directFollowUps;
    }

    public Wait enter(String caseId, long leader, long staff, Long child, Long parent, UUID decisionId, boolean followUp) {
        CaseState state = lock(caseId);
        if (followUp && directFollowUps.direct(decisionId)) {
            require(child != null && parent != null && workflow.leadingInstance(caseId).filter(key -> key == leader).isPresent());
            return directFollowUps.bind(decisionId, caseId, leader, staff, child, parent);
        }
        Decision decision = decisions.get(decisionId);
        Continuation continuation = reviews.continuation(decision.continuation(), caseId);
        require(decision.caseId().equals(caseId) && continuation.leader() == leader && continuation.childKey() != null
                && continuation.childKey() == staff && workflow.leadingInstance(caseId).filter(key -> key == leader).isPresent()
                && decision.action() == (followUp ? StaffDecisionAction.FOLLOW_UP : StaffDecisionAction.WAIT_EXTERNAL));
        Wait existing = waits.get(decisionId).orElse(null);
        if (existing != null) {
            require(existing.staff() == staff && Objects.equals(existing.child(), child)
                    && Objects.equals(existing.parent(), parent));
            return existing;
        }
        require(!followUp || child != null && child > 0 && parent != null && parent > 0);
        String expectedPhase = "SReview".equals(decision.element()) ? "STAFF_REVIEW_REQUIRED"
                : "SExternalWait".equals(decision.element()) ? "WAITING_FOR_EXTERNAL" : "IN_PROGRESS";
        if (!expectedPhase.equals(state.status()) || state.version() != decision.version() || "REJECTED".equals(decision.state())
                || !decisions.reviewFingerprint(caseId, leader).equals(decision.reviewToken())
                || access.checkStaffAccess(new WebAccountContext(decision.actor())) != StaffAccessDecision.ALLOWED
                || reviews.forCall(caseId, continuation.callRef()).filter(current -> current.id().equals(continuation.id())).isEmpty()) {
            throw new WorkflowRejectedException("STAFF_WAIT_BASIS_STALE");
        }
        return waits.begin(state, decision, leader, staff, child, parent, followUp ? waits.requestedDate(decisionId) : null);
    }

    public String timer(String caseId, long leader, long child, UUID waitId) {
        CaseState state = lock(caseId);
        Wait wait = waits.get(waitId).orElseThrow(() -> new WorkflowRejectedException("UNKNOWN_WAIT"));
        require(wait.caseId().equals(caseId) && wait.leader() == leader && wait.child() != null && wait.child() == child);
        if (!current(state, wait) || !"FOLLOW_UP".equals(wait.kind())) {
            if ("TIMER".equals(wait.state()) && "STAFF_REVIEW_REQUIRED".equals(state.status())
                    && workflow.leadingInstance(caseId).filter(key -> key == leader).isPresent()) {
                return "REVIEW";
            }
            return "OBSOLETE";
        }
        require(!wait.dueAt().isAfter(Instant.now()));
        waits.resolve(state, wait, "TIMER", "STAFF_REVIEW_REQUIRED");
        return "REVIEW";
    }

    public String contact(String caseId, long leader, long staff, UUID waitId, UUID messageId) {
        CaseState state = lock(caseId);
        Wait wait = waits.get(waitId).orElseThrow(() -> new WorkflowRejectedException("UNKNOWN_WAIT"));
        require(wait.caseId().equals(caseId) && wait.leader() == leader && wait.staff() == staff
                && waits.contact(messageId).filter(waitId::equals).isPresent());
        if (!current(state, wait) || !"FOLLOW_UP".equals(wait.kind())) {
            if (("TIMER".equals(wait.state()) || "CONTACT".equals(wait.state())) && "STAFF_REVIEW_REQUIRED".equals(state.status())
                    && workflow.leadingInstance(caseId).filter(key -> key == leader).isPresent()) {
                return "REVIEW";
            }
            return "OBSOLETE";
        }
        waits.resolve(state, wait, "CONTACT", "STAFF_REVIEW_REQUIRED");
        return "REVIEW";
    }

    public String reviewNeeded(String caseId, long leader, long staff, UUID waitId, UUID signalId) {
        CaseState state = lock(caseId);
        Wait wait = waits.get(waitId).orElseThrow(() -> new WorkflowRejectedException("UNKNOWN_WAIT"));
        require(wait.caseId().equals(caseId) && wait.leader() == leader && wait.staff() == staff);
        StaffReviewStore.Signal signal = reviews.signal(signalId, caseId);
        require(signal.leader() != null && signal.leader() == leader);
        if ("REVIEW".equals(wait.state()) && "CONSUMED".equals(signal.state()) && "STAFF_REVIEW_REQUIRED".equals(state.status())
                && workflow.leadingInstance(caseId).filter(key -> key == leader).isPresent()) {
            return "REVIEW";
        }
        require("CONFIRMED".equals(signal.state()));
        if (!current(state, wait)) {
            return "STAFF_REVIEW_REQUIRED".equals(state.status()) && workflow.leadingInstance(caseId).filter(key -> key == leader).isPresent()
                    ? "REVIEW" : "OBSOLETE";
        }
        waits.resolve(state, wait, "REVIEW", "STAFF_REVIEW_REQUIRED");
        reviews.consume(signalId);
        return "REVIEW";
    }

    private boolean current(CaseState state, Wait wait) {
        return "ACTIVE".equals(wait.state()) && workflow.leadingInstance(state.id()).filter(key -> key == wait.leader()).isPresent()
                && ("FOLLOW_UP".equals(wait.kind()) ? "WAITING_FOR_FOLLOW_UP" : "WAITING_FOR_EXTERNAL").equals(state.status());
    }

    private CaseState lock(String caseId) {
        return workflow.lockCase(caseId).orElseThrow(() -> new WorkflowRejectedException("UNKNOWN_CASE"));
    }

    private void require(boolean condition) {
        if (!condition) {
            throw new WorkflowRejectedException("INVALID_WAIT_CONTEXT");
        }
    }
}
