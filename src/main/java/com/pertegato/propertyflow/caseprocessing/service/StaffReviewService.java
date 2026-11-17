package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.caseprocessing.persistence.CaseAnalysisStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.CaseState;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.Entry;
import com.pertegato.propertyflow.caseprocessing.persistence.ClarificationQuestionStore;
import com.pertegato.propertyflow.caseprocessing.persistence.DirectFollowUpStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffReviewStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffReviewStore.Continuation;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffReviewStore.Signal;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffTakeoverStore;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantReplyStore;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("propertyflow-backend & !frontend-demo")
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class StaffReviewService {

    private final CaseWorkflowStore workflow;
    private final StaffReviewStore reviews;
    private final CaseAnalysisStore analyses;
    private final StaffTakeoverStore takeovers;
    private final ClarificationQuestionStore questions;
    private final DirectFollowUpStore directFollowUps;
    private final TenantReplyStore replies;

    public StaffReviewService(CaseWorkflowStore workflow, StaffReviewStore reviews, CaseAnalysisStore analyses, StaffTakeoverStore takeovers,
            ClarificationQuestionStore questions, DirectFollowUpStore directFollowUps, TenantReplyStore replies) {
        this.workflow = workflow;
        this.reviews = reviews;
        this.analyses = analyses;
        this.takeovers = takeovers;
        this.questions = questions;
        this.directFollowUps = directFollowUps;
        this.replies = replies;
    }

    public Continuation analysisFailure(String caseId, long leader, UUID callRef) {
        CaseState state = lock(caseId, leader);
        Entry entry = workflow.entryForCase(caseId).orElseThrow(() -> new WorkflowRejectedException("MISSING_AUTOMATIC_ENTRY"));
        require(entry.callRef().equals(callRef) && entry.leadingKey() == leader, "OTHER_ANALYSIS_GENERATION");
        Continuation continuation = reviews.forCall(caseId, callRef).orElse(null);
        if (continuation != null && continuation.reviewApplied()) {
            return continuation;
        }
        UUID signal = reviews.currentConversationFailure(caseId, leader).orElseGet(() -> {
            UUID analysis = analyses.failure(callRef).orElseThrow(() -> new WorkflowRejectedException("MISSING_ANALYSIS_FAILURE"));
            return reviews.analysisFailure(caseId, leader, analysis);
        });
        require(automaticPhase(state), "STALE_ANALYSIS_FAILURE");
        continuation = reviews.prepare(state, leader, callRef);
        reviews.confirm(signal, continuation.id(), callRef);
        return continuation;
    }

    public ReviewRoute check(String caseId, long leader, UUID signalId) {
        CaseState state = lock(caseId, leader);
        Signal signal = reviews.signal(signalId, caseId);
        require(signal.leader() != null && signal.leader() == leader, "REVIEW_FROM_OTHER_GENERATION");
        if ("CLOSED".equals(state.status())) {
            reviews.note(signalId);
            return new ReviewRoute("NONE", null);
        }
        if ("CONSUMED".equals(signal.state()) || "NOTE".equals(signal.state())) {
            return new ReviewRoute("NONE", null);
        }
        if ("WAITING_FOR_TENANT".equals(state.status())
                && (!(workflow.questionPhase(state) || workflow.manualQuestionPhase(state)) || workflow.replyWaiting(caseId, leader))) {
            return new ReviewRoute("REPLY", null);
        }
        Entry entry = workflow.entryForCase(caseId).orElseThrow(() -> new IllegalStateException("AUTOMATIC_ENTRY_NOT_READY"));
        Continuation existing = reviews.forCall(caseId, entry.callRef()).orElse(null);
        if (existing != null && existing.reviewApplied() && "STAFF_REVIEW_REQUIRED".equals(state.status())) {
            reviews.confirm(signalId, existing.id(), entry.callRef());
            return new ReviewRoute("EXISTING", existing.id());
        }
        if (existing != null && existing.reviewApplied() && existing.childKey() != null
                && ("IN_PROGRESS".equals(state.status()) || "WAITING_FOR_EXTERNAL".equals(state.status())
                        || "WAITING_FOR_FOLLOW_UP".equals(state.status()))) {
            reviews.confirm(signalId, existing.id(), entry.callRef());
            return new ReviewRoute("OTHER", existing.id());
        }
        // A received case must complete its initial status transition before this phase can be interrupted.
        // Other phases keep the signal pending until their own verified delivery path is available.
        if (!(automaticPhase(state)) || entry.childKey() == null) {
            throw new IllegalStateException("REVIEW_PHASE_NOT_READY");
        }
        Continuation continuation = reviews.prepare(state, leader, entry.callRef());
        reviews.confirm(signalId, continuation.id(), entry.callRef());
        return new ReviewRoute("OTHER", continuation.id());
    }

    public Interruption routeInterruption(String caseId, long leader, UUID callRef) {
        CaseState state = lock(caseId, leader);
        Entry current = workflow.entryForCase(caseId).orElseThrow();
        require(current.callRef().equals(callRef), "OTHER_AUTOMATIC_CALL");
        UUID question = questions.forCall(caseId, leader, callRef).orElse(null);
        if (question != null && "WAITING_FOR_TENANT".equals(state.status())) {
            if (!questions.pending(caseId, leader) && reviews.forCall(caseId, callRef).isEmpty()) {
                return new Interruption("WAIT_FOR_REPLY", question);
            }
            Continuation continuation = reviews.prepare(state, leader, callRef);
            questions.confirm(caseId, leader, continuation.id());
            return new Interruption("STAFF", continuation.id());
        }
        return new Interruption("STAFF", interruption(caseId, leader, callRef).id());
    }

    public record Interruption(String outcome, UUID reference) {
    }

    public Continuation interruption(String caseId, long leader, UUID callRef) {
        CaseState state = lock(caseId, leader);
        Continuation continuation = reviews.forCall(caseId, callRef)
                .orElseThrow(() -> new WorkflowRejectedException("MISSING_REVIEW_DECISION"));
        require(continuation.leader() == leader && reviews.blocked(callRef), "INVALID_REVIEW_DECISION");
        require(automaticPhase(state) || continuation.reviewApplied(), "STALE_REVIEW_DECISION");
        return continuation;
    }

    public Continuation read(String caseId, long leader, UUID id) {
        CaseState state = lock(caseId, leader);
        Continuation continuation = reviews.continuation(id, caseId);
        require(continuation.leader() == leader, "OTHER_STAFF_GENERATION");
        require(!"CLOSED".equals(state.status()), "CLOSED_STAFF_CONTINUATION");
        if (workflow.entryForCase(caseId).flatMap(entry -> reviews.forCall(caseId, entry.callRef()))
                .filter(current -> current.id().equals(id)).isPresent()) {
            takeovers.upgrade(state, continuation);
        }
        return reviews.continuation(id, caseId);
    }

    public Continuation current(String caseId, long leader) {
        CaseState state = lock(caseId, leader);
        require(!"CLOSED".equals(state.status()), "CLOSED_STAFF_CONTINUATION");
        Entry entry = workflow.entryForCase(caseId).orElseThrow(() -> new IllegalStateException("CURRENT_ENTRY_PENDING"));
        Continuation current = reviews.forCall(caseId, entry.callRef())
                .orElseThrow(() -> new IllegalStateException("CURRENT_STAFF_CONTINUATION_PENDING"));
        require(current.leader() == leader, "OTHER_STAFF_GENERATION");
        return current;
    }

    public ExternalOwner externalOwner(String caseId, long leader, UUID previous) {
        CaseState state = lock(caseId, leader);
        Continuation source = reviews.continuation(previous, caseId);
        require(source.leader() == leader, "OTHER_STAFF_GENERATION");
        if ("WAITING_FOR_TENANT".equals(state.status())) {
            TenantReplyStore.Call reply = replies.active(caseId, leader).filter(call -> "WAITING".equals(call.state())).orElse(null);
            if (reply != null) {
                return new ExternalOwner("REPLY", reply.id(), reply.child(), reply.parent());
            }
        }
        Entry entry = workflow.entryForCase(caseId).orElseThrow(() -> new IllegalStateException("CURRENT_ENTRY_PENDING"));
        if (automaticPhase(state) && !entry.callRef().equals(source.callRef()) && entry.childKey() != null) {
            return new ExternalOwner("AUTOMATIC", entry.callRef(), entry.childKey(), entry.parentElementKey());
        }
        return null;
    }

    public String claimExternal(String caseId, long leader, UUID previous, ExternalOwner verified) {
        if (verified == null || !verified.equals(externalOwner(caseId, leader, previous))) {
            throw new IllegalStateException("SUCCESSOR_CHANGED");
        }
        return "SUPERSEDED";
    }

    public void reconcileExternal(String caseId, long leader, UUID previous, ExternalOwner verified) {
        if (verified == null || !verified.equals(externalOwner(caseId, leader, previous))) {
            throw new IllegalStateException("SUCCESSOR_CHANGED");
        }
        // Der bestehende dauerhafte Eingang führt relevante Gründe zum jetzt aktiven Aufruf.
        reviews.transferPendingToProcess(previous, leader);
    }

    public record ExternalOwner(String kind, UUID reference, long child, long parent) {
    }

    public void reconcile(String caseId, long leader, UUID previous, UUID owner, long verifiedLiveChild) {
        lock(caseId, leader);
        Continuation source = reviews.continuation(previous, caseId);
        Continuation current = current(caseId, leader);
        require(source.leader() == leader, "OTHER_STAFF_GENERATION");
        if (!current.id().equals(owner) || current.childKey() == null || current.childKey() != verifiedLiveChild || !current.reviewApplied()) {
            throw new IllegalStateException("CURRENT_STAFF_OWNER_CHANGED");
        }
        reviews.transferPending(previous, current);
    }

    public String claim(String caseId, long leader, UUID id, long claimElementKey, Long verifiedLiveChild) {
        CaseState state = lock(caseId, leader);
        Continuation continuation = reviews.continuation(id, caseId);
        require(continuation.leader() == leader && !"CLOSED".equals(state.status()), "STALE_STAFF_CONTINUATION");
        Continuation owner = current(caseId, leader);
        if (!owner.id().equals(id)) {
            if (verifiedLiveChild != null && verifiedLiveChild.equals(owner.childKey()) && owner.reviewApplied()) {
                return "SUPERSEDED";
            }
            throw new IllegalStateException("SUCCESSOR_NOT_YET_CONFIRMED");
        }
        if (continuation.claimElementKey() != null) {
            if (continuation.claimElementKey() == claimElementKey) {
                return "START";
            }
            if (verifiedLiveChild != null && verifiedLiveChild.equals(continuation.childKey()) && continuation.reviewApplied()) {
                return "EXISTING";
            }
            throw new IllegalStateException("STAFF_CONTINUATION_NOT_YET_CONFIRMED");
        }
        requireCurrentAutomatic(state, continuation);
        reviews.claim(id, claimElementKey);
        return "START";
    }

    public void register(String caseId, long leader, UUID id, long childKey, long parentElementKey) {
        CaseState state = lock(caseId, leader);
        Continuation continuation = reviews.continuation(id, caseId);
        require(continuation.leader() == leader && continuation.claimElementKey() != null, "UNCLAIMED_STAFF_CONTINUATION");
        require(current(caseId, leader).id().equals(id), "SUPERSEDED_STAFF_REGISTRATION");
        if (continuation.childKey() != null) {
            require(continuation.childKey() == childKey && continuation.parentElementKey() == parentElementKey, "OTHER_STAFF_INSTANCE");
            return;
        }
        requireCurrentAutomatic(state, continuation);
        takeovers.upgrade(state, continuation);
        reviews.bind(id, childKey, parentElementKey);
        directFollowUps.register(state, continuation, childKey);
    }

    public void startReview(String caseId, long leader, UUID id, long childKey) {
        CaseState state = lock(caseId, leader);
        Continuation continuation = reviews.continuation(id, caseId);
        require(continuation.leader() == leader && continuation.childKey() != null && continuation.childKey() == childKey,
                "UNREGISTERED_STAFF_INSTANCE");
        require(current(caseId, leader).id().equals(id), "SUPERSEDED_STAFF_REVIEW");
        if (continuation.reviewApplied()) {
            if ("IN_PROGRESS".equals(state.status()) || "WAITING_FOR_EXTERNAL".equals(state.status())) {
                reviews.interruptReview(state, continuation);
            }
            return;
        }
        requireCurrentAutomatic(state, continuation);
        reviews.startReview(state, continuation);
    }

    public void merge(String caseId, long leader, UUID id, UUID signalId, long verifiedLiveChild) {
        CaseState state = lock(caseId, leader);
        Continuation continuation = reviews.continuation(id, caseId);
        require(continuation.childKey() != null && continuation.childKey() == verifiedLiveChild && continuation.reviewApplied()
                && "STAFF_REVIEW_REQUIRED".equals(state.status()), "REVIEW_TASK_CHANGED");
        Signal signal = reviews.signal(signalId, caseId);
        require(id.equals(signal.continuationId()) && signal.leader() != null && signal.leader() == leader, "OTHER_REVIEW_SIGNAL");
        reviews.consume(signalId);
    }

    public StaffInterruption staffInterruption(String caseId, long leader, UUID id, UUID signalId) {
        CaseState state = lock(caseId, leader);
        Continuation continuation = reviews.continuation(id, caseId);
        Signal signal = reviews.signal(signalId, caseId);
        require(continuation.leader() == leader && continuation.childKey() != null, "OTHER_STAFF_GENERATION");
        if (!"CONFIRMED".equals(signal.state()) || !id.equals(signal.continuationId())) {
            return null;
        }
        if (!"IN_PROGRESS".equals(state.status()) && !"WAITING_FOR_EXTERNAL".equals(state.status())
                && !"WAITING_FOR_FOLLOW_UP".equals(state.status())) {
            return null;
        }
        if (reviews.waitMutationPending(caseId)) {
            throw new IllegalStateException("WAIT_MUTATION_PENDING");
        }
        UUID reference = reviews.phaseReference(caseId, continuation.childKey(), state.status())
                .orElseThrow(() -> new IllegalStateException("STAFF_PHASE_NOT_READY"));
        return new StaffInterruption(continuation.childKey(), state.status(), reference);
    }

    public String directWaitReference(UUID continuation) {
        return directFollowUps.forContinuation(continuation).map(UUID::toString).orElse("");
    }

    public String workReference(String caseId, long leader, UUID id) {
        CaseState state = lock(caseId, leader);
        Continuation continuation = reviews.continuation(id, caseId);
        require(continuation.leader() == leader && continuation.childKey() != null, "OTHER_STAFF_GENERATION");
        return "IN_PROGRESS".equals(state.status()) ? reviews.phaseReference(caseId, continuation.childKey(), state.status())
                .orElseThrow(() -> new IllegalStateException("STAFF_PHASE_NOT_READY")).toString() : "";
    }

    private boolean automaticPhase(CaseState state) {
        return "UNDER_CLARIFICATION".equals(state.status()) || workflow.questionPhase(state) || workflow.manualQuestionPhase(state);
    }

    private CaseState lock(String caseId, long leader) {
        CaseState state = workflow.lockCase(caseId).orElseThrow(() -> new WorkflowRejectedException("UNKNOWN_CASE"));
        require(workflow.leadingInstance(caseId).filter(key -> key == leader).isPresent(), "UNREGISTERED_LEADING_INSTANCE");
        return state;
    }

    private void requireCurrentAutomatic(CaseState state, Continuation continuation) {
        boolean allowed = "DIRECT_FOLLOW_UP".equals(continuation.origin())
                ? "WAITING_FOR_FOLLOW_UP".equals(state.status()) || "STAFF_REVIEW_REQUIRED".equals(state.status())
                : "REPLY_REVIEW".equals(continuation.origin()) ? "WAITING_FOR_TENANT".equals(state.status())
                || "STAFF_REVIEW_REQUIRED".equals(state.status()) : "CLOSE_CONFLICT".equals(continuation.origin())
                        || "REOPEN".equals(continuation.origin()) || "ANSWER_CLEARED".equals(continuation.origin())
                        || "TAKEOVER".equals(continuation.origin())
                ? "STAFF_REVIEW_REQUIRED".equals(state.status()) || "IN_PROGRESS".equals(state.status())
                        || "WAITING_FOR_EXTERNAL".equals(state.status())
                : automaticPhase(state);
        require(allowed && workflow.entryForCase(state.id()).filter(entry -> entry.callRef().equals(continuation.callRef())).isPresent()
                && reviews.forCall(state.id(), continuation.callRef()).filter(current -> current.id().equals(continuation.id())).isPresent(),
                "STALE_STAFF_CONTINUATION");
        // A mandatory reason survives intervening data updates. Re-evaluate against the locked current
        // phase and call instead of losing the reason on a harmless version change (for example a memo).
    }

    private void require(boolean condition, String reason) {
        if (!condition) {
            throw new WorkflowRejectedException(reason);
        }
    }

    public record ReviewRoute(String route, UUID continuationId) {
    }

    public record StaffInterruption(long child, String phase, UUID reference) {
    }
}
