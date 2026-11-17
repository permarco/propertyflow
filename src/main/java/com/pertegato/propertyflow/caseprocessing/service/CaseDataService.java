package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.access.contract.StaffAccessDecision;
import com.pertegato.propertyflow.access.contract.StaffAccountAccess;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.caseprocessing.contract.CaseActionException;
import com.pertegato.propertyflow.caseprocessing.contract.StaffCaseDataCommands;
import com.pertegato.propertyflow.caseprocessing.contract.Urgency;
import com.pertegato.propertyflow.caseprocessing.delivery.CaseProcessGateway;
import com.pertegato.propertyflow.caseprocessing.persistence.AnswerRequirementStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseDataStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseDataStore.Command;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseDataStore.Leader;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.CaseState;
import com.pertegato.propertyflow.caseprocessing.persistence.DirectFollowUpStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffTakeoverStore;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HexFormat;
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
public class CaseDataService implements StaffCaseDataCommands {

    private static final int MAX_MEMO_LENGTH = 5000;
    private final StaffAccountAccess access;
    private final CaseWorkflowStore workflow;
    private final CaseDataStore store;
    private final CaseProcessGateway engine;
    private final CaseUrgencyService urgencies;
    private final AnswerRequirementStore answers;
    private final StaffTakeoverStore takeovers;
    private final CaseClosureService closures;
    private final DirectFollowUpStore directFollowUps;
    private final JsonMapper json = JsonMapper.builder().build();
    private final TransactionTemplate writes;

    public CaseDataService(StaffAccountAccess access, CaseWorkflowStore workflow, CaseDataStore store,
            CaseProcessGateway engine, CaseUrgencyService urgencies, AnswerRequirementStore answers, StaffTakeoverStore takeovers,
            CaseClosureService closures, DirectFollowUpStore directFollowUps, PlatformTransactionManager transactions) {
        this.access = access;
        this.workflow = workflow;
        this.store = store;
        this.engine = engine;
        this.urgencies = urgencies;
        this.answers = answers;
        this.takeovers = takeovers;
        this.closures = closures;
        this.directFollowUps = directFollowUps;
        writes = new TransactionTemplate(transactions);
        writes.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public boolean available(WebAccountContext context, String caseId) {
        authorize(context);
        Leader leader = writes.execute(transaction -> {
            CaseState state = lock(caseId);
            return active(state) ? store.leader(caseId).orElse(null) : null;
        });
        return leader != null && engine.active(leader.key(), leader.tenant());
    }

    @Override
    public CaseDataResult submit(WebAccountContext context, String caseId, String operationId, long expectedVersion, String text) {
        authorize(context);
        if (text == null || text.isBlank() || text.length() > MAX_MEMO_LENGTH) {
            throw new CaseActionException("INVALID_INPUT");
        }
        return submit(context, caseId, operationId, expectedVersion, "ADD_MEMO", text, "");
    }

    @Override
    public CaseDataResult submitMemo(WebAccountContext context, String caseId, String operation, String text) {
        authorize(context);
        Command existing = store.find(caseId, context.accountId(), operation).orElse(null);
        long version = existing == null ? writes.execute(transaction -> lock(caseId).version()) : existing.expectedVersion();
        return submit(context, caseId, operation, version, text);
    }

    @Override
    public CaseDataResult submitUrgency(WebAccountContext context, String caseId, String operationId, long expectedVersion,
            Urgency urgency, String reason) {
        authorize(context);
        if (urgency == null || reason == null || reason.length() > 1000) {
            throw new CaseActionException("INVALID_INPUT");
        }
        return submit(context, caseId, operationId, expectedVersion, "SET_MANUAL_URGENCY", urgency.name(), reason);
    }

    private CaseDataResult submit(WebAccountContext context, String caseId, String operationId, long expectedVersion,
            String type, String text, String reason) {
        if (operationId == null || operationId.isEmpty() || operationId.length() > 80 || expectedVersion < 0) {
            throw new CaseActionException("INVALID_INPUT");
        }
        String fingerprint = fingerprint(type, expectedVersion, text, reason);
        Command existing = store.find(caseId, context.accountId(), operationId).orElse(null);
        if (existing != null) {
            return confirm(replay(existing, fingerprint));
        }
        Leader candidate = store.leader(caseId).orElseThrow(() -> new CaseActionException("CASE_NOT_ACTIVE"));
        if (!engine.active(candidate.key(), candidate.tenant())) {
            throw new CaseActionException("WORKFLOW_UNAVAILABLE");
        }
        Command accepted = writes.execute(transaction -> {
            CaseState state = lock(caseId);
            authorize(context);
            if ("ADD_MEMO".equals(type) && store.externalOperationUsed(caseId, operationId)) {
                throw new CaseActionException("IDEMPOTENCY_CONFLICT");
            }
            Command concurrent = store.find(caseId, context.accountId(), operationId).orElse(null);
            if (concurrent != null) {
                return replay(concurrent, fingerprint);
            }
            if (!active(state) || !candidate.equals(store.leader(caseId).orElse(null))) {
                throw new CaseActionException("CASE_NOT_ACTIVE");
            }
            if (state.version() != expectedVersion) {
                throw new CaseActionException("CASE_CHANGED");
            }
            if ("CLOSE_CLARIFICATION".equals(type) && (!"UNDER_CLARIFICATION".equals(state.status())
                    || !closures.clarificationFingerprint(caseId, candidate.key()).equals(text))) {
                throw new CaseActionException("CASE_CHANGED");
            }
            if ("CLOSE_REPLY".equals(type) && (!"WAITING_FOR_TENANT".equals(state.status())
                    || !closures.replyClosureAvailable(caseId, candidate.key())
                    || !closures.clarificationFingerprint(caseId, candidate.key()).equals(text))) {
                throw new CaseActionException("CASE_CHANGED");
            }
            Command prepared = store.insert(caseId, context.accountId(), operationId, expectedVersion, type, text, reason, fingerprint, candidate);
            if ("DIRECT_FOLLOW_UP".equals(type)) {
                DirectFollowInput input = json.readValue(text, DirectFollowInput.class);
                if (!closures.clarificationFingerprint(caseId, candidate.key()).equals(input.reviewToken())) {
                    throw new CaseActionException("CASE_CHANGED");
                }
                directFollowUps.prepare(prepared, state, FollowUpDate.calculate(Clock.systemUTC(), input.days(),
                        input.date() == null ? null : LocalDate.parse(input.date())), input.reviewToken());
            } else if ("CLEAR_ANSWER".equals(type)) {
                answers.prepare(prepared, state);
            } else if ("TAKEOVER".equals(type)) {
                takeovers.prepare(prepared, state);
            } else if ("CLOSE_REPLY".equals(type)) {
                closures.prepareReply(state, prepared);
            }
            return prepared;
        });
        return confirm(accepted);
    }

    @Override
    public CaseDataResult submitAssignment(WebAccountContext context, String caseId, String operationId, long expectedVersion, String assignment) {
        authorize(context);
        String normalized = assignment == null ? "" : assignment.strip();
        if (normalized.isBlank() || normalized.length() > 200) {
            throw new CaseActionException("INVALID_INPUT");
        }
        return submit(context, caseId, operationId, expectedVersion, "SET_OBJECT_ASSIGNMENT", normalized, "");
    }

    @Override
    public CaseDataResult clearAnswer(WebAccountContext context, String caseId, String operation, long version, UUID message) {
        authorize(context);
        if (message == null) {
            throw new CaseActionException("INVALID_INPUT");
        }
        return submit(context, caseId, operation, version, "CLEAR_ANSWER", message.toString(), "");
    }

    @Override
    public boolean takeoverAvailable(WebAccountContext context, String caseId) {
        authorize(context);
        boolean clarification = Boolean.TRUE.equals(writes.execute(transaction -> "UNDER_CLARIFICATION".equals(lock(caseId).status())
                && workflow.entryForCase(caseId).filter(entry -> entry.childKey() != null).isPresent()));
        return clarification && available(context, caseId);
    }

    @Override
    public CaseDataResult takeOver(WebAccountContext context, String caseId, String operation, long version) {
        authorize(context);
        return submit(context, caseId, operation, version, "TAKEOVER", "", "");
    }

    public String takeoverState(UUID command, long leader, String tenant) {
        if (!"TAKEOVER".equals(store.get(command).type())) {
            throw new WorkflowRejectedException("WRONG_TAKEOVER_ACTION");
        }
        apply(command, leader, tenant);
        return store.get(command).state();
    }

    @Override
    public ClosureApproval clarificationClosure(WebAccountContext context, String caseId) {
        authorize(context);
        ClosureApproval approval = writes.execute(transaction -> {
            CaseState state = lock(caseId);
            Leader leader = store.leader(caseId).orElse(null);
            return "UNDER_CLARIFICATION".equals(state.status()) && leader != null
                    ? new ClosureApproval(true, state.version(), closures.clarificationFingerprint(caseId, leader.key()))
                    : new ClosureApproval(false, state.version(), "");
        });
        return approval.available() && available(context, caseId) ? approval : new ClosureApproval(false, approval.version(), "");
    }

    @Override
    public CaseDataResult closeClarification(WebAccountContext context, String caseId, String operation, long version, String reviewToken) {
        authorize(context);
        if (reviewToken == null || reviewToken.isBlank() || reviewToken.length() > 200) {
            throw new CaseActionException("INVALID_INPUT");
        }
        return submit(context, caseId, operation, version, "CLOSE_CLARIFICATION", reviewToken, "");
    }

    public String clarificationClosureState(UUID command, long leader, String tenant) {
        if (!"CLOSE_CLARIFICATION".equals(store.get(command).type())) {
            throw new WorkflowRejectedException("WRONG_CLOSURE_ACTION");
        }
        apply(command, leader, tenant);
        return store.get(command).state();
    }

    @Override
    public ClosureApproval replyClosure(WebAccountContext context, String caseId) {
        authorize(context);
        ClosureApproval approval = writes.execute(transaction -> {
            CaseState state = lock(caseId);
            Leader leader = store.leader(caseId).orElse(null);
            return "WAITING_FOR_TENANT".equals(state.status()) && leader != null && closures.replyClosureAvailable(caseId, leader.key())
                    ? new ClosureApproval(true, state.version(), closures.clarificationFingerprint(caseId, leader.key()))
                    : new ClosureApproval(false, state.version(), "");
        });
        return approval.available() && available(context, caseId) ? approval : new ClosureApproval(false, approval.version(), "");
    }

    @Override
    public CaseDataResult closeReply(WebAccountContext context, String caseId, String operation, long version, String reviewToken) {
        authorize(context);
        if (reviewToken == null || reviewToken.isBlank() || reviewToken.length() > 200) {
            throw new CaseActionException("INVALID_INPUT");
        }
        return submit(context, caseId, operation, version, "CLOSE_REPLY", reviewToken, "");
    }

    public String replyClosureState(UUID id, long leader, String tenant) {
        Command command = store.get(id);
        if (!"CLOSE_REPLY".equals(command.type()) || command.leader() != leader || !command.tenant().equals(tenant)) {
            throw new WorkflowRejectedException("WRONG_REPLY_CLOSURE_ACTION");
        }
        if (closures.replyAccepted(id)) {
            return "ACCEPTED";
        }
        apply(id, leader, tenant);
        return closures.replyAccepted(id) ? "ACCEPTED" : "REJECTED";
    }

    public String rejectReplyClosure(UUID id, long leader, String tenant) {
        Command reference = store.get(id);
        return writes.execute(transaction -> {
            lock(reference.caseId());
            Command command = store.get(id);
            if (!"CLOSE_REPLY".equals(command.type()) || command.leader() != leader || !command.tenant().equals(tenant)) {
                throw new WorkflowRejectedException("WRONG_REPLY_CLOSURE_ACTION");
            }
            if (closures.replyAccepted(id)) {
                return "ACCEPTED";
            }
            store.reject(id, "CASE_CHANGED");
            return "REJECTED";
        });
    }

    @Override
    public List<String> assignmentSuggestions(WebAccountContext context, String query) {
        authorize(context);
        if (query == null || query.length() > 200) {
            throw new CaseActionException("INVALID_INPUT");
        }
        return store.assignmentSuggestions(query.strip());
    }

    @Override
    public CaseDataResult read(WebAccountContext context, String caseId, String operationId) {
        authorize(context);
        return store.latest(caseId, operationId).map(this::result)
                .orElseThrow(() -> new CaseActionException("COMMAND_NOT_FOUND"));
    }

    public void apply(UUID commandId, long leader, String tenant) {
        Command reference = store.get(commandId);
        writes.executeWithoutResult(transaction -> {
            CaseState state = lock(reference.caseId());
            Command command = store.get(commandId);
            if (command.leader() != leader || !command.tenant().equals(tenant)) {
                throw new WorkflowRejectedException("WRONG_COMMAND_PROCESS");
            }
            if (!"PENDING".equals(command.state())) {
                return;
            }
            StaffAccessDecision decision = access.checkStaffAccess(new WebAccountContext(command.actor()));
            if (decision != StaffAccessDecision.ALLOWED) {
                store.reject(commandId, "FORBIDDEN");
            } else if (!active(state) || !store.leader(state.id()).filter(current -> current.key() == leader).isPresent()) {
                store.reject(commandId, "CASE_NOT_ACTIVE");
            } else if (state.version() != command.expectedVersion()) {
                store.reject(commandId, "CASE_CHANGED");
            } else {
                switch (command.type()) {
                    case "DIRECT_FOLLOW_UP" -> {
                        if (directFollowUps.apply(command, state, closures.clarificationFingerprint(state.id(), leader))) {
                            store.complete(command.id(), state.version() + 1);
                        } else {
                            store.reject(command.id(), "CASE_CHANGED");
                        }
                    }
                    case "CLOSE_REPLY" -> {
                        if (!closures.acceptReply(state, command)) {
                            store.reject(command.id(), "CASE_CHANGED");
                        }
                    }
                    case "CLOSE_CLARIFICATION" -> {
                        if (closures.closeClarification(state, command)) {
                            store.complete(command.id(), state.version() + 1);
                        } else {
                            store.reject(command.id(), "CASE_CHANGED");
                        }
                    }
                    case "ADD_MEMO" -> store.apply(command, state.version());
                    case "SET_OBJECT_ASSIGNMENT" -> store.assign(command, state.version());
                    case "CLEAR_ANSWER" -> {
                        if (answers.apply(command, state)) {
                            store.complete(command.id(), state.version() + 1);
                        } else {
                            store.reject(command.id(), "CASE_CHANGED");
                        }
                    }
                    case "TAKEOVER" -> {
                        if (takeovers.apply(command, state)) {
                            store.complete(command.id(), state.version() + 1);
                        } else {
                            store.reject(command.id(), "CASE_CHANGED");
                        }
                    }
                    case "SET_MANUAL_URGENCY" -> {
                        long version = urgencies.manual(command.caseId(), command.id(), command.actor(), Urgency.valueOf(command.text()), command.reason());
                        store.complete(command.id(), version);
                    }
                    default -> throw new WorkflowRejectedException("UNKNOWN_CASE_DATA_ACTION");
                }
            }
        });
    }

    @Override
    public ClosureApproval directFollowUp(WebAccountContext context, String caseId) {
        authorize(context);
        ClosureApproval approval = writes.execute(transaction -> {
            CaseState state = lock(caseId);
            Leader leader = store.leader(caseId).orElse(null);
            return leader != null && directFollowUps.available(state, leader.key())
                    ? new ClosureApproval(true, state.version(), closures.clarificationFingerprint(caseId, leader.key()))
                    : new ClosureApproval(false, state.version(), "");
        });
        return approval.available() && available(context, caseId) ? approval : new ClosureApproval(false, approval.version(), "");
    }

    @Override
    public CaseDataResult setDirectFollowUp(WebAccountContext context, String caseId, String operation, long version,
            String reviewToken, Integer days, LocalDate date, String reason) {
        authorize(context);
        if (reviewToken == null || reviewToken.isBlank() || reviewToken.length() > 64 || reason == null || reason.length() > 1000
                || (days == null) == (date == null) || days != null && days < 0) {
            throw new CaseActionException("INVALID_INPUT");
        }
        String input = json.writeValueAsString(new DirectFollowInput(reviewToken, days, date == null ? null : date.toString(), reason));
        return submit(context, caseId, operation, version, "DIRECT_FOLLOW_UP", input, "");
    }

    public String directFollowUpState(UUID id, long leader, String tenant) {
        if (!"DIRECT_FOLLOW_UP".equals(store.get(id).type())) {
            throw new WorkflowRejectedException("WRONG_FOLLOW_UP_ACTION");
        }
        apply(id, leader, tenant);
        return store.get(id).state();
    }

    private record DirectFollowInput(String reviewToken, Integer days, String date, String reason) {
    }

    private Command replay(Command existing, String fingerprint) {
        String stored = "ADD_MEMO".equals(existing.type()) || existing.fingerprint() == null
                ? fingerprint(existing.type(), existing.expectedVersion(), existing.text(), "") : existing.fingerprint();
        if (!stored.equals(fingerprint)) {
            throw new CaseActionException("IDEMPOTENCY_CONFLICT");
        }
        return existing;
    }

    private CaseDataResult confirm(Command command) {
        if ("PENDING".equals(command.state()) && !command.correlated()) {
            try {
                if ("DIRECT_FOLLOW_UP".equals(command.type())) {
                    engine.deliverDirectFollowUp(command.id(), command.leader(), command.tenant());
                } else if ("CLOSE_REPLY".equals(command.type())) {
                    engine.deliverReplyClosure(command.id(), command.leader(), command.tenant());
                } else if ("CLOSE_CLARIFICATION".equals(command.type())) {
                    engine.deliverClarificationClosure(command.id(), command.leader(), command.tenant());
                } else if ("CLEAR_ANSWER".equals(command.type())) {
                    engine.deliverClearAnswer(command.id(), command.leader(), command.tenant());
                } else if ("TAKEOVER".equals(command.type())) {
                    engine.deliverTakeover(command.id(), command.leader(), command.tenant());
                } else {
                    engine.deliverCaseData(command.id(), command.leader(), command.tenant());
                }
                store.correlated(command.id());
            } catch (CaseActionException rejected) {
                Command resolved = writes.execute(transaction -> {
                    lock(command.caseId());
                    store.rejectCorrelation(command.id(), rejected.code());
                    return store.get(command.id());
                });
                if ("APPLIED".equals(resolved.state())) {
                    return result(resolved);
                }
                throw rejected;
            } catch (RuntimeException exception) {
                // Correlation can have succeeded despite a lost response. Keep the same durable command.
                throw new CaseActionException("WORKFLOW_UNAVAILABLE");
            }
        }
        return result(store.get(command.id()));
    }

    private String fingerprint(String type, long version, String text, String reason) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String field : new String[] {type, "ADD_MEMO".equals(type) ? "" : Long.toString(version), text, reason}) {
                byte[] bytes = field.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    private CaseDataResult result(Command command) {
        return new CaseDataResult(command.operationId(), command.state(), command.code(), command.resultVersion());
    }

    private CaseState lock(String caseId) {
        return workflow.lockCase(caseId).orElseThrow(() -> new CaseActionException("CASE_NOT_AVAILABLE"));
    }

    private boolean active(CaseState state) {
        return !"RECEIVED".equals(state.status()) && !"CLOSED".equals(state.status());
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
}
