package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.access.contract.StaffAccessDecision;
import com.pertegato.propertyflow.access.contract.StaffAccountAccess;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.caseprocessing.contract.StaffDecisionAction;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseClosureStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseDataStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWaitStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWaitStore.Wait;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.CaseState;
import com.pertegato.propertyflow.caseprocessing.persistence.FollowUpCommandStore;
import com.pertegato.propertyflow.caseprocessing.persistence.FollowUpCommandStore.Command;
import com.pertegato.propertyflow.caseprocessing.persistence.ReplyClosureStore;
import com.pertegato.propertyflow.caseprocessing.persistence.ReplyClosureStore.Intent;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffDecisionStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffDecisionStore.Decision;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffReviewStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffReviewStore.Continuation;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantReplyStore;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantReplyStore.Call;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("propertyflow-backend & !frontend-demo")
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class CaseClosureService {

    private final StaffAccountAccess access;
    private final CaseWorkflowStore workflow;
    private final CaseDataStore caseData;
    private final StaffReviewStore reviews;
    private final StaffDecisionStore decisions;
    private final CaseClosureStore closures;
    private final FollowUpCommandStore followUps;
    private final CaseWaitStore waits;
    private final ReplyClosureStore replyClosures;
    private final TenantReplyStore replies;

    public CaseClosureService(StaffAccountAccess access, CaseWorkflowStore workflow, CaseDataStore caseData,
            StaffReviewStore reviews, StaffDecisionStore decisions, CaseClosureStore closures,
            FollowUpCommandStore followUps, CaseWaitStore waits, ReplyClosureStore replyClosures, TenantReplyStore replies) {
        this.access = access;
        this.workflow = workflow;
        this.caseData = caseData;
        this.reviews = reviews;
        this.decisions = decisions;
        this.closures = closures;
        this.followUps = followUps;
        this.waits = waits;
        this.replyClosures = replyClosures;
        this.replies = replies;
    }

    public ClosureContext context(String caseId, long leader, UUID decisionId) {
        lock(caseId, leader);
        Intent reply = replyClosures.find(decisionId).orElse(null);
        if (reply != null) {
            Call call = replies.get(reply.call());
            require(reply.accepted() && call.caseId().equals(caseId) && call.leader() == leader, "INVALID_REPLY_CLOSURE");
            return new ClosureContext(call.child(), call.parent(), call.tenant(), true);
        }
        Command followUp = followUps.find(decisionId).orElse(null);
        if (followUp != null) {
            Continuation continuation = followUpContinuation(caseId, leader, followUp);
            return new ClosureContext(continuation.childKey(), continuation.parentElementKey(), caseData.leader(caseId).orElseThrow().tenant());
        }
        Decision decision = decisions.get(decisionId);
        require(decision.caseId().equals(caseId) && decision.action() == StaffDecisionAction.CLOSE, "INVALID_CLOSURE_DECISION");
        Continuation continuation = reviews.continuation(decision.continuation(), caseId);
        require(continuation.leader() == leader && continuation.childKey() != null && continuation.parentElementKey() != null,
                "UNREGISTERED_CLOSURE_CHILD");
        return new ClosureContext(continuation.childKey(), continuation.parentElementKey(), caseData.leader(caseId).orElseThrow().tenant());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String clarificationFingerprint(String caseId, long leader) {
        return decisions.reviewFingerprint(caseId, leader);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean closeClarification(CaseState state, CaseDataStore.Command command) {
        if (!"CLOSE_CLARIFICATION".equals(command.type()) || !"UNDER_CLARIFICATION".equals(state.status())
                || !decisions.reviewFingerprint(state.id(), command.leader()).equals(command.text())
                || state.version() != command.expectedVersion()
                || access.checkStaffAccess(new WebAccountContext(command.actor())) != StaffAccessDecision.ALLOWED) {
            return false;
        }
        require(workflow.leadingInstance(state.id()).filter(key -> key == command.leader()).isPresent(), "OTHER_CLOSURE_GENERATION");
        closures.close(state, command);
        return true;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean replyClosureAvailable(String caseId, long leader) {
        return replies.active(caseId, leader).filter(call -> "WAITING".equals(call.state())).isPresent();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void prepareReply(CaseState state, CaseDataStore.Command command) {
        Call call = replies.active(state.id(), command.leader()).filter(current -> "WAITING".equals(current.state())).orElseThrow();
        replyClosures.prepare(command.id(), call.id());
    }

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public boolean replyAccepted(UUID command) {
        return replyClosures.find(command).filter(Intent::accepted).isPresent();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean acceptReply(CaseState state, CaseDataStore.Command command) {
        Intent intent = replyClosures.find(command.id()).orElseThrow();
        if (!"WAITING_FOR_TENANT".equals(state.status()) || !decisions.reviewFingerprint(state.id(), command.leader()).equals(command.text())
                || replies.active(state.id(), command.leader()).filter(call -> call.id().equals(intent.call()) && "WAITING".equals(call.state())).isEmpty()) {
            return false;
        }
        replyClosures.accept(intent);
        return true;
    }

    public ClosureResult close(String caseId, long leader, long completedChild, UUID decisionId) {
        CaseState state = lock(caseId, leader);
        ClosureContext context = context(caseId, leader, decisionId);
        require(context.child() == completedChild, "OTHER_CLOSURE_CHILD");
        if (context.reply()) {
            return closeReply(state, leader, decisionId);
        }
        Command followUp = followUps.find(decisionId).orElse(null);
        if (followUp != null) {
            return closeFollowUp(state, leader, followUp);
        }
        Decision decision = decisions.get(decisionId);
        if (closures.applied(decisionId)) {
            return new ClosureResult("CLOSED", null);
        }
        require(!"CLOSED".equals(state.status()), "CASE_ALREADY_CLOSED_BY_OTHER_DECISION");
        Continuation continuation = reviews.continuation(decision.continuation(), caseId);
        if (decision.conflictContinuation() != null) {
            return new ClosureResult("STALE", decision.conflictContinuation());
        }
        require(reviews.forCall(caseId, continuation.callRef()).filter(current -> current.id().equals(continuation.id())).isPresent(),
                "SUPERSEDED_CLOSURE_CONTEXT");
        boolean phaseMatches = "SReview".equals(decision.element()) ? "STAFF_REVIEW_REQUIRED".equals(state.status())
                : "SExternalWait".equals(decision.element()) ? "WAITING_FOR_EXTERNAL".equals(state.status())
                        : "SWork".equals(decision.element()) && "IN_PROGRESS".equals(state.status());
        if (!phaseMatches || state.version() != decision.version()
                || !decisions.reviewFingerprint(caseId, leader).equals(decision.reviewToken())
                || access.checkStaffAccess(new WebAccountContext(decision.actor())) != StaffAccessDecision.ALLOWED) {
            require("STAFF_REVIEW_REQUIRED".equals(state.status()) || "IN_PROGRESS".equals(state.status())
                    || "WAITING_FOR_EXTERNAL".equals(state.status()), "CLOSURE_PHASE_SUPERSEDED");
            return new ClosureResult("STALE", closures.conflict(state, decision, continuation));
        }
        closures.close(state, decision, leader);
        return new ClosureResult("CLOSED", null);
    }

    private ClosureResult closeReply(CaseState state, long leader, UUID id) {
        Intent intent = replyClosures.find(id).orElseThrow();
        CaseDataStore.Command command = caseData.get(id);
        require(command.leader() == leader && "CLOSE_REPLY".equals(command.type()), "INVALID_REPLY_CLOSURE");
        if (closures.applied(id)) {
            return new ClosureResult("CLOSED", null);
        }
        if (intent.conflict() != null) {
            return new ClosureResult("STALE", intent.conflict());
        }
        require("WAITING_FOR_TENANT".equals(state.status()) && "CLOSING".equals(replies.get(intent.call()).state()), "REPLY_CLOSURE_SUPERSEDED");
        if (state.version() != command.expectedVersion() || !decisions.reviewFingerprint(state.id(), leader).equals(command.text())
                || access.checkStaffAccess(new WebAccountContext(command.actor())) != StaffAccessDecision.ALLOWED) {
            UUID continuation = replyClosures.conflict(state, command);
            reviews.startReview(state, reviews.continuation(continuation, state.id()));
            return new ClosureResult("STALE", continuation);
        }
        closures.close(state, command);
        caseData.complete(id, state.version() + 1);
        replyClosures.completed(id);
        return new ClosureResult("CLOSED", null);
    }

    private Continuation followUpContinuation(String caseId, long leader, Command command) {
        require(command.caseId().equals(caseId) && "CLOSE".equals(command.action()), "INVALID_FOLLOW_UP_CLOSURE");
        Wait waiting = waits.get(command.waitId()).orElseThrow();
        Continuation continuation = reviews.forChild(caseId, waiting.staff()).orElseThrow();
        require(waiting.leader() == leader && continuation.leader() == leader
                && continuation.childKey() != null && continuation.parentElementKey() != null, "OTHER_FOLLOW_UP_CLOSURE_CHILD");
        return continuation;
    }

    private ClosureResult closeFollowUp(CaseState state, long leader, Command command) {
        if (closures.applied(command.id())) {
            return new ClosureResult("CLOSED", null);
        }
        require(!"CLOSED".equals(state.status()), "CASE_ALREADY_CLOSED_BY_OTHER_DECISION");
        if (command.conflictContinuation() != null) {
            return new ClosureResult("STALE", command.conflictContinuation());
        }
        Continuation previous = followUpContinuation(state.id(), leader, command);
        require("APPLYING".equals(command.state()), "UNCONFIRMED_FOLLOW_UP_CLOSURE");
        require(reviews.forCall(state.id(), previous.callRef()).filter(current -> current.id().equals(previous.id())).isPresent(),
                "SUPERSEDED_CLOSURE_CONTEXT");
        require("WAITING_FOR_FOLLOW_UP".equals(state.status()), "CLOSURE_PHASE_SUPERSEDED");
        if (state.version() != command.version() || !decisions.reviewFingerprint(state.id(), leader).equals(command.fingerprint())
                || waits.active(state.id()).filter(waiting -> waiting.id().equals(command.waitId())).isEmpty()
                || access.checkStaffAccess(new WebAccountContext(command.actor())) != StaffAccessDecision.ALLOWED) {
            UUID continuation = closures.conflict(state, command, previous);
            reviews.startReview(state, reviews.continuation(continuation, state.id()));
            return new ClosureResult("STALE", continuation);
        }
        closures.close(state, command, leader);
        return new ClosureResult("CLOSED", null);
    }

    private CaseState lock(String caseId, long leader) {
        CaseState state = workflow.lockCase(caseId).orElseThrow(() -> new WorkflowRejectedException("UNKNOWN_CASE"));
        require(workflow.leadingInstance(caseId).filter(key -> key == leader).isPresent(), "OTHER_CLOSURE_GENERATION");
        return state;
    }

    private void require(boolean condition, String code) {
        if (!condition) {
            throw new WorkflowRejectedException(code);
        }
    }

    public record ClosureContext(long child, long parentElement, String tenant, boolean reply) {
        public ClosureContext(long child, long parentElement, String tenant) {
            this(child, parentElement, tenant, false);
        }
    }

    public record ClosureResult(String state, UUID continuation) {
    }
}
