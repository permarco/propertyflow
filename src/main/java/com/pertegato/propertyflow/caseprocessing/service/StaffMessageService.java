package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.access.contract.StaffAccessDecision;
import com.pertegato.propertyflow.access.contract.StaffAccountAccess;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.caseprocessing.contract.CaseActionException;
import com.pertegato.propertyflow.caseprocessing.contract.StaffDecisionAction;
import com.pertegato.propertyflow.caseprocessing.contract.StaffMessageSubmission;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.delivery.CaseProcessGateway;
import com.pertegato.propertyflow.caseprocessing.delivery.StaffMessageGateway;
import com.pertegato.propertyflow.caseprocessing.delivery.StaffTaskGateway;
import com.pertegato.propertyflow.caseprocessing.delivery.StaffTaskGateway.Binding;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseDataStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseDataStore.Leader;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWaitStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWaitStore.Wait;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.CaseState;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.Entry;
import com.pertegato.propertyflow.caseprocessing.persistence.ClarificationQuestionStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffDecisionStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffMessageStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffMessageStore.Command;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffReviewStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffReviewStore.Continuation;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantReplyStore;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantReplyStore.Call;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("propertyflow-backend")
public class StaffMessageService implements StaffMessageSubmission {

    private final StaffAccountAccess access;
    private final CaseWorkflowStore workflow;
    private final CaseDataStore cases;
    private final StaffReviewStore reviews;
    private final StaffMessageStore messages;
    private final CaseProcessGateway availability;
    private final CaseWaitStore waits;
    private final StaffDecisionStore decisions;
    private final TenantReplyStore replies;
    private final ClarificationQuestionStore clarificationQuestions;
    private final StaffTaskGateway tasks;
    private final StaffMessageGateway gateway;
    private final TransactionTemplate writes;

    public StaffMessageService(StaffAccountAccess access, CaseWorkflowStore workflow, CaseDataStore cases,
            StaffReviewStore reviews, StaffMessageStore messages, CaseProcessGateway availability, StaffTaskGateway tasks,
            StaffMessageGateway gateway, CaseWaitStore waits, StaffDecisionStore decisions, TenantReplyStore replies,
            ClarificationQuestionStore clarificationQuestions,
            PlatformTransactionManager transactions) {
        this.access = access;
        this.workflow = workflow;
        this.cases = cases;
        this.reviews = reviews;
        this.messages = messages;
        this.availability = availability;
        this.tasks = tasks;
        this.gateway = gateway;
        this.waits = waits;
        this.decisions = decisions;
        this.replies = replies;
        this.clarificationQuestions = clarificationQuestions;
        writes = new TransactionTemplate(transactions);
        writes.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public Capability available(WebAccountContext context, String caseId) {
        authorize(context);
        Basis basis = writes.execute(transaction -> basis(caseId));
        boolean allowed = basis != null && live(basis);
        return new Capability(allowed, allowed && questionsAllowed(basis.status()));
    }

    @Override
    public String send(WebAccountContext context, String caseId, String operation, String text, boolean answerRequired) {
        if (operation != null && operation.startsWith("mcp:")) {
            throw new CaseActionException("INVALID_INPUT");
        }
        return send(context, caseId, operation, text, answerRequired, null);
    }

    String send(WebAccountContext context, String caseId, String operation, String text, boolean answerRequired, Long expectedVersion) {
        authorize(context);
        if (operation == null || operation.isEmpty() || operation.length() > 80 || text == null || text.isBlank()) {
            throw new CaseActionException("INVALID_INPUT");
        }
        Command previous = messages.find(caseId, operation).orElse(null);
        if (previous != null) {
            same(previous, text, answerRequired);
            if (!"PENDING".equals(previous.state()) || messages.decision(previous.id()).isPresent()) {
                return state(previous);
            }
        }
        Basis candidate = writes.execute(transaction -> basis(caseId));
        if (candidate == null || answerRequired && !questionsAllowed(candidate.status())) {
            throw new CaseActionException("CASE_NOT_ACTIVE");
        }
        if (expectedVersion != null && candidate.version() != expectedVersion) {
            throw new CaseActionException("CASE_CHANGED");
        }
        Long task = candidate.binding() == null ? null : tasks.currentTask(candidate.binding()).orElse(null);
        if (!availability.active(candidate.leader().key(), candidate.leader().tenant())
                || candidate.binding() != null && task == null) {
            throw new CaseActionException("WORKFLOW_UNAVAILABLE");
        }
        Command command = writes.execute(transaction -> {
            authorize(context);
            Basis current = basis(caseId);
            if (messages.memoOperationUsed(caseId, operation)) {
                throw new CaseActionException("IDEMPOTENCY_CONFLICT");
            }
            Command existing = messages.find(caseId, operation).orElse(null);
            if (existing != null) {
                same(existing, text, answerRequired);
                return existing;
            }
            if (!candidate.equals(current)) {
                throw new CaseActionException("CASE_CHANGED");
            }
            Binding binding = candidate.binding();
            Command prepared = messages.prepare(caseId, operation, context.accountId(), candidate.version(), candidate.leader().key(),
                    candidate.leader().tenant(), candidate.reply() != null ? candidate.reply().child() : candidate.waiting() != null ? candidate.waiting().child()
                            : binding == null ? candidate.leader().key() : binding.child(),
                    candidate.reply() != null ? Long.valueOf(candidate.reply().parent()) : candidate.waiting() != null ? candidate.waiting().parent()
                            : binding == null ? null : binding.parentElement(),
                    task, binding == null ? null : binding.element(), candidate.automatic(), text, answerRequired);
            if (candidate.waiting() != null) {
                messages.bindWait(prepared.id(), candidate.waiting().id());
            }
            if (candidate.reply() != null) {
                messages.bindReply(prepared.id(), candidate.reply().id());
            }
            if (answerRequired && "IN_PROGRESS".equals(candidate.status())) {
                Entry entry = workflow.entryForCase(caseId).orElseThrow();
                UUID continuation = reviews.forCall(caseId, entry.callRef()).orElseThrow().id();
                UUID decision = decisions.prepare(caseId, continuation, context.accountId(), UUID.randomUUID(), task, candidate.version(),
                        decisions.reviewFingerprint(caseId, candidate.leader().key()), StaffDecisionAction.QUESTION,
                                StaffProcessingBpmnConstants.UserTask.WORK_TASK, "").id();
                messages.bindDecision(prepared.id(), decision);
            }
            return prepared;
        });
        UUID nativeDecision = messages.decision(command.id()).orElse(null);
        if (nativeDecision != null) {
            if (messages.nativeAttempt(command.id())) {
                try {
                    tasks.completeTask(new Binding(command.leader(), command.target(), command.parent(), command.tenant(), command.element()),
                            command.task(), nativeDecision, StaffDecisionAction.QUESTION);
                    decisions.confirmed(nativeDecision);
                } catch (CaseActionException rejected) {
                    decisions.reject(nativeDecision, rejected.code());
                    messages.reject(command.id(), rejected.code(), true);
                    throw rejected;
                }
            }
            return state(messages.get(command.id()));
        }
        if ("PENDING".equals(command.state()) && !command.correlated()) {
            try {
                UUID waitId = messages.waitFor(command.id()).orElse(null);
                UUID replyId = messages.replyFor(command.id()).orElse(null);
                if (replyId != null) {
                    gateway.correlateReply(command.id(), command.target(), command.tenant(), replies.get(replyId).entry().toString());
                } else if (waitId == null) {
                    gateway.correlate(command.id(), command.target(), command.tenant(), command.automatic() != null, messages.correlationKey(command));
                } else {
                    gateway.correlateFollowUp(command.id(), command.target(), command.tenant(), waits.scopeKey(command.target()));
                }
                messages.correlated(command.id());
            } catch (CaseActionException rejected) {
                String resolved = writes.execute(transaction -> {
                    workflow.lockCase(command.caseId()).orElseThrow();
                    messages.reject(command.id(), rejected.code(), true);
                    return messages.get(command.id()).state();
                });
                if ("PUBLISHED".equals(resolved)) {
                    return resolved;
                }
                throw rejected;
            } catch (RuntimeException unknown) {
                if (!"PUBLISHED".equals(messages.get(command.id()).state())) {
                    throw new CaseActionException("WORKFLOW_UNAVAILABLE");
                }
            }
        }
        return state(messages.get(command.id()));
    }

    @Override
    public String result(WebAccountContext context, String caseId, String operation) {
        authorize(context);
        return state(messages.latest(caseId, operation).orElseThrow(() -> new CaseActionException("COMMAND_NOT_FOUND")));
    }

    public String publish(UUID id, long leader, long target, String tenant) {
        Command reference = messages.get(id);
        if (reference.leader() != leader || reference.target() != target || !reference.tenant().equals(tenant)) {
            throw new WorkflowRejectedException("OTHER_STAFF_MESSAGE_PROCESS");
        }
        if (!"PENDING".equals(reference.state())) {
            return reference.state();
        }
        boolean taskOpen = reference.task() == null || tasks.currentTask(new Binding(leader, target, reference.parent(), tenant,
                reference.element())).filter(reference.task()::equals).isPresent();
        return writes.execute(transaction -> {
            CaseState state = workflow.lockCase(reference.caseId()).orElseThrow();
            Command command = messages.get(id);
            if (!"PENDING".equals(command.state())) {
                return command.state();
            }
            Basis current = basis(state.id());
            boolean sameScope = current != null && current.version() == command.version() && current.leader().key() == leader
                    && Objects.equals(current.automatic(), command.automatic())
                    && Objects.equals(current.waiting() == null ? null : current.waiting().id(), messages.waitFor(command.id()).orElse(null))
                    && Objects.equals(current.reply() == null ? null : current.reply().id(), messages.replyFor(command.id()).orElse(null))
                    && (current.reply() == null || current.reply().child() == target && current.reply().parent() == command.parent())
                    && (current.waiting() == null || current.waiting().child() == target && current.waiting().parent().equals(command.parent()))
                    && (current.binding() == null ? command.task() == null
                    : current.binding().child() == target && current.binding().parentElement() == command.parent()
                            && current.binding().element().equals(command.element()));
            if (!sameScope || !taskOpen || command.task() != null && messages.decisionStarted(command)
                    || command.answerRequired() && (!questionsAllowed(state.status()) || command.automatic() != null
                            && clarificationQuestions.pending(state.id(), leader))
                    || access.checkStaffAccess(new WebAccountContext(command.actor())) != StaffAccessDecision.ALLOWED) {
                messages.reject(id, "CASE_CHANGED", true);
                return "REJECTED";
            }
            messages.publish(command);
            if (command.automatic() != null && command.answerRequired()) {
                clarificationQuestions.published(command, state);
            }
            return "PUBLISHED";
        });
    }

    public boolean interruptsAutomatic(UUID command) {
        return clarificationQuestions.published(command);
    }

    public UUID questionCommand(UUID decision) {
        return messages.questionCommand(decision);
    }

    public String publishQuestion(UUID id, long leader, long staff, String tenant) {
        return writes.execute(transaction -> {
            Command command = messages.get(id);
            CaseState state = workflow.lockCase(command.caseId()).orElseThrow();
            UUID decisionId = messages.decision(id).orElseThrow(() -> new WorkflowRejectedException("MISSING_QUESTION_DECISION"));
            StaffDecisionStore.Decision decision = decisions.get(decisionId);
            if (command.leader() != leader || command.target() != staff || !command.tenant().equals(tenant)
                    || !command.answerRequired() || decision.action() != StaffDecisionAction.QUESTION) {
                throw new WorkflowRejectedException("OTHER_QUESTION_PROCESS");
            }
            if ("PUBLISHED".equals(command.state())) {
                return "PUBLISHED";
            }
            if (!"PENDING".equals(command.state()) || "REJECTED".equals(decision.state()) || !"IN_PROGRESS".equals(state.status())
                    || state.version() != command.version() || workflow.leadingInstance(state.id()).filter(key -> key == leader).isEmpty()
                    || !decisions.reviewFingerprint(state.id(), leader).equals(decision.reviewToken())
                    || access.checkStaffAccess(new WebAccountContext(command.actor())) != StaffAccessDecision.ALLOWED) {
                messages.reject(id, "CASE_CHANGED", true);
                decisions.reject(decisionId, "CASE_CHANGED");
                reviews.questionConflict(state.id(), leader, decision.continuation(), id);
                return "REJECTED";
            }
            messages.publish(command);
            messages.replyEntry(command);
            decisions.questionApplied(decisionId);
            return "PUBLISHED";
        });
    }

    private Basis basis(String caseId) {
        CaseState state = workflow.lockCase(caseId).orElseThrow(() -> new CaseActionException("CASE_NOT_AVAILABLE"));
        Leader leader = cases.leader(caseId).orElse(null);
        Entry entry = workflow.entryForCase(caseId).orElse(null);
        if (leader == null || entry == null) {
            return null;
        }
        if ("UNDER_CLARIFICATION".equals(state.status()) && !reviews.blocked(entry.callRef())) {
            return new Basis(state.version(), state.status(), leader, entry.callRef(), null, null, null);
        }
        if ("WAITING_FOR_TENANT".equals(state.status())) {
            Call call = replies.active(caseId, leader.key()).orElse(null);
            return call != null && "WAITING".equals(call.state())
                    ? new Basis(state.version(), state.status(), leader, null, null, null, call) : null;
        }
        if ("WAITING_FOR_FOLLOW_UP".equals(state.status())) {
            Wait wait = waits.active(caseId).orElse(null);
            return wait != null && wait.leader() == leader.key() && wait.child() != null && wait.parent() != null
                    ? new Basis(state.version(), state.status(), leader, null, null, wait, null) : null;
        }
        if (!"STAFF_REVIEW_REQUIRED".equals(state.status()) && !"IN_PROGRESS".equals(state.status())
                && !"WAITING_FOR_EXTERNAL".equals(state.status())) {
            return null;
        }
        Continuation continuation = reviews.forCall(caseId, entry.callRef()).orElse(null);
        if (continuation == null || !continuation.reviewApplied() || continuation.childKey() == null
                || continuation.parentElementKey() == null || continuation.leader() != leader.key()) {
            return null;
        }
        return new Basis(state.version(), state.status(), leader, null, new Binding(leader.key(), continuation.childKey(),
                continuation.parentElementKey(), leader.tenant(),
                        "STAFF_REVIEW_REQUIRED".equals(state.status()) ? StaffProcessingBpmnConstants.UserTask.REVIEW_TASK
                        : "WAITING_FOR_EXTERNAL".equals(state.status()) ? StaffProcessingBpmnConstants.UserTask.EXTERNAL_WAIT_TASK
                                : StaffProcessingBpmnConstants.UserTask.WORK_TASK), null, null);
    }

    private boolean live(Basis basis) {
        return availability.active(basis.leader().key(), basis.leader().tenant())
                && (basis.binding() == null || tasks.currentTask(basis.binding()).isPresent());
    }

    private String state(Command command) {
        if ("REJECTED".equals(command.state())) {
            throw new CaseActionException(command.resultCode());
        }
        return command.state();
    }

    private void same(Command command, String text, boolean answer) {
        if (!command.text().equals(text) || command.answerRequired() != answer) {
            throw new CaseActionException("IDEMPOTENCY_CONFLICT");
        }
    }

    private void authorize(WebAccountContext context) {
        StaffAccessDecision decision = context == null ? StaffAccessDecision.UNAUTHENTICATED : access.checkStaffAccess(context);
        if (decision != StaffAccessDecision.ALLOWED) {
            throw new CaseActionException(decision == StaffAccessDecision.UNAUTHENTICATED ? "UNAUTHENTICATED" : "FORBIDDEN");
        }
    }

    private boolean questionsAllowed(String status) {
        return "UNDER_CLARIFICATION".equals(status) || "IN_PROGRESS".equals(status) || "STAFF_REVIEW_REQUIRED".equals(status) || "WAITING_FOR_EXTERNAL".equals(status)
                || "WAITING_FOR_FOLLOW_UP".equals(status) || "WAITING_FOR_TENANT".equals(status);
    }

    private record Basis(long version, String status, Leader leader, UUID automatic, Binding binding, Wait waiting, Call reply) {
    }
}
