package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.access.contract.McpAccountContext;
import com.pertegato.propertyflow.access.contract.McpScope;
import com.pertegato.propertyflow.access.contract.McpTenantAuthorization;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.caseprocessing.contract.CaseActionException;
import com.pertegato.propertyflow.caseprocessing.contract.CaseServiceException;
import com.pertegato.propertyflow.caseprocessing.contract.CaseStatus;
import com.pertegato.propertyflow.caseprocessing.contract.McpCaseReading;
import com.pertegato.propertyflow.caseprocessing.contract.McpMessageConfirmations;
import com.pertegato.propertyflow.caseprocessing.contract.StaffMessageSubmission.Capability;
import com.pertegato.propertyflow.caseprocessing.contract.TenantCaseView;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffMessageStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffMessageStore.Command;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantMessageStore.Message;
import com.pertegato.propertyflow.caseprocessing.service.McpCaseActions.Basis;
import com.pertegato.propertyflow.caseprocessing.service.McpCaseActions.Outcome;
import com.pertegato.propertyflow.intake.contract.CaseDescription;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("propertyflow-backend")
@ConditionalOnProperty(name = "propertyflow.mcp.backend.enabled", havingValue = "true")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class PersistentMcpMessageConfirmations implements McpMessageConfirmations {

    private static final String UNAVAILABLE = "MCP_CONFIRMATION_UNAVAILABLE";
    private static final String WORKFLOW_UNAVAILABLE = "WORKFLOW_UNAVAILABLE";
    private static final Pattern CASE_ID = Pattern.compile(McpCaseReading.CASE_ID_PATTERN);
    private static final int MAX_MESSAGE_LENGTH = 2000;
    private final JdbcOperations database;
    private final McpTenantAuthorization authorization;
    private final PersistentCaseService tenantCases;
    private final StaffCaseService staffCases;
    private final TenantMessageService tenantMessages;
    private final StaffMessageService staffMessages;
    private final StaffMessageStore staffCommands;
    private final McpCaseActions caseActions;
    private final int retentionDays;

    PersistentMcpMessageConfirmations(JdbcOperations database, McpTenantAuthorization authorization,
            PersistentCaseService tenantCases, StaffCaseService staffCases, TenantMessageService tenantMessages,
            StaffMessageService staffMessages, StaffMessageStore staffCommands, McpCaseActions caseActions,
            @Value("${propertyflow.mcp.audit-retention-days:90}") int retentionDays) {
        if (retentionDays < 1) {
            throw new IllegalArgumentException("Die MCP-Audit-Aufbewahrung muss mindestens einen Tag betragen.");
        }
        this.database = database;
        this.authorization = authorization;
        this.tenantCases = tenantCases;
        this.staffCases = staffCases;
        this.tenantMessages = tenantMessages;
        this.staffMessages = staffMessages;
        this.staffCommands = staffCommands;
        this.caseActions = caseActions;
        this.retentionDays = retentionDays;
    }

    @Override
    @Transactional(noRollbackFor = CaseActionException.class)
    public UUID prepare(McpAccountContext account, McpScope scope, String caseId, String rawText, boolean answerRequired) {
        try {
            requireWriteScope(scope);
            verify(account != null && authorization.lockDelegation(account, scope));
            String text = normalize(rawText);
            verify(caseId != null && CASE_ID.matcher(caseId).matches() && (!answerRequired || scope.staff()));
            TenantCaseView view = readCase(account, scope, caseId);
            verify(view.state().status() != CaseStatus.CLOSED);
            checkAvailability(account, scope, caseId, answerRequired);

            UUID operation = UUID.randomUUID();
            database.update("""
                    INSERT INTO pf_mcp_confirmation
                    (id, account_id, delegation_id, client_id, scope, case_id, case_version, case_title, message_text, answer_required, expires_at, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'WAITING')
                    """, operation, account.accountId(), account.delegationId(), account.clientId(), scope.name(), caseId, view.version(),
                    view.title(), text, answerRequired, OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10));
            audit(account, scope, caseId, operation, "PREPARED");
            return operation;
        } catch (CaseServiceException exception) {
            auditDenied(account, scope, "CASE_UNAVAILABLE");
            throw new CaseActionException(UNAVAILABLE);
        } catch (CaseActionException exception) {
            auditDenied(account, scope, "PREPARATION_DENIED");
            throw exception;
        }
    }

    @Override
    @Transactional(noRollbackFor = CaseActionException.class)
    public View continuation(McpAccountContext account, McpScope scope, UUID operation, String caseId, String rawText,
            boolean answerRequired, boolean cancel) {
        try {
            requireWriteScope(scope);
            Draft draft = load(operation);
            verify(draft.account().equals(account) && draft.scope() == scope && draft.caseId().equals(caseId)
                    && draft.text().equals(normalize(rawText)) && draft.answerRequired() == answerRequired);
            validate(draft);
            return continueDraft(draft, cancel);
        } catch (CaseActionException exception) {
            auditDenied(account, scope, "CONTINUATION_DENIED");
            throw exception;
        }
    }

    @Override
    @Transactional(noRollbackFor = CaseActionException.class)
    public UUID prepareCaseAction(McpAccountContext account, CaseRequest request) {
        McpScope scope = request.scope();
        try {
            verify(account != null && authorization.lockDelegation(account, scope));
            UUID operation = UUID.randomUUID();
            Basis basis = caseActions.prepare(account, operation, request);
            database.update("""
                    INSERT INTO pf_mcp_confirmation
                    (id, account_id, delegation_id, client_id, scope, case_id, case_version, case_title, message_text,
                     answer_required, expires_at, status, action_argument, task_key, review_token)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, FALSE, ?, 'WAITING', ?, ?, ?)
                    """, operation, account.accountId(), account.delegationId(), account.clientId(), scope.name(), basis.caseId(),
                    basis.version(), basis.title(), basis.text(), OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10),
                    basis.argument(), basis.taskKey(), basis.reviewToken());
            audit(account, auditAction(scope, basis.argument()), basis.caseId(), operation, "PREPARED");
            return operation;
        } catch (CaseServiceException | CaseActionException exception) {
            auditDenied(account, scope, "PREPARATION_DENIED");
            throw new CaseActionException(UNAVAILABLE);
        }
    }

    @Override
    @Transactional(noRollbackFor = CaseActionException.class)
    public View continueCaseAction(McpAccountContext account, UUID operation, CaseRequest request, boolean cancel) {
        McpScope scope = request.scope();
        try {
            Draft draft = load(operation);
            verify(draft.account().equals(account) && draft.scope() == scope);
            validate(draft);
            verify(caseActions.matches(account, operation, request, draft.basis()));
            return continueDraft(draft, cancel);
        } catch (CaseServiceException | CaseActionException exception) {
            auditDenied(account, scope, "CONTINUATION_DENIED");
            throw new CaseActionException(UNAVAILABLE);
        }
    }

    private View continueDraft(Draft draft, boolean cancel) {
        View recovered = recordedOutcome(draft);
        if (recovered != null) {
            return recovered;
        }
        if (cancel && draft.status() == Status.WAITING) {
            return transition(draft, Status.CANCELLED, null);
        }
        return result(draft);
    }

    @Override
    @Transactional
    public View view(UUID account, boolean staff, UUID operation) {
        Draft draft = load(operation);
        verifyOwner(draft, account, staff);
        validate(draft);
        View recovered = recordedOutcome(draft);
        return recovered == null ? result(draft) : recovered;
    }

    @Override
    @Transactional(noRollbackFor = CaseActionException.class)
    public View decide(UUID account, boolean staff, UUID operation, boolean confirm) {
        Draft draft = load(operation);
        verifyOwner(draft, account, staff);
        try {
            validate(draft);
            View recovered = recordedOutcome(draft);
            if (recovered != null) {
                return recovered;
            }
            if (draft.status() != Status.WAITING) {
                return result(draft);
            }
            if (!confirm) {
                return transition(draft, Status.CANCELLED, null);
            }
            audit(draft, "CONFIRMED");
            return execute(draft);
        } catch (CaseActionException exception) {
            audit(draft, "CONFIRMATION_DENIED");
            throw exception;
        }
    }

    private View execute(Draft draft) {
        if (draft.isCaseAction()) {
            return executeCaseAction(draft);
        }
        try {
            if (draft.scope().staff()) {
                staffMessages.send(new WebAccountContext(draft.account().accountId()), draft.caseId(), staffOperation(draft),
                        draft.text(), draft.answerRequired(), draft.version());
            } else {
                tenantMessages.sendForAccount(draft.account().accountId(), draft.caseId(), draft.id().toString(),
                        draft.text(), message -> audit(draft, "STORED"));
            }
            return result(draft.withStatus(Status.SUBMITTED, null));
        } catch (CaseActionException | IllegalStateException exception) {
            View stored = result(draft.withStatus(Status.SUBMITTED, null));
            return stored.status() == Status.SUBMITTED && stored.messageId() == null
                    ? transition(draft, Status.REJECTED, null)
                    : stored;
        }
    }

    private View executeCaseAction(Draft draft) {
        try {
            caseActions.execute(draft.account(), draft.id(), draft.scope() == McpScope.TENANT_INTAKE, draft.basis(),
                    caseId -> audit(draft.account(), draft.scope(), caseId, draft.id(), "STORED"));
        } catch (CaseActionException | CaseServiceException | IllegalStateException exception) {
            View current = caseActionOutcome(draft);
            return current == null ? transition(draft, Status.REJECTED, null) : current;
        }
        View current = caseActionOutcome(draft);
        return current == null ? transition(draft, Status.SUBMITTED, null) : current;
    }

    private View result(Draft draft) {
        if (draft.status() != Status.SUBMITTED) {
            return draft.view();
        }
        if (draft.isCaseAction()) {
            View current = caseActionOutcome(draft);
            return current == null ? draft.view() : current;
        }
        if (draft.scope().staff()) {
            Command command = staffCommands.latest(draft.caseId(), staffOperation(draft)).orElse(null);
            if (command != null) {
                return switch (command.state()) {
                    case "PUBLISHED" -> transition(draft, Status.SENT, command.id());
                    case "REJECTED" -> transition(draft, Status.REJECTED, null);
                    default -> pending(draft, command.id());
                };
            }
        } else {
            Message message = tenantMessages.findForAccount(draft.account().accountId(), draft.caseId(), draft.id().toString()).orElse(null);
            if (message != null) {
                return switch (message.state()) {
                    case "RECEIVED" -> transition(draft, Status.SENT, message.id());
                    case "SUPERSEDED" -> transition(draft, Status.REJECTED, null);
                    default -> pending(draft, message.id());
                };
            }
        }
        return draft.view();
    }

    private View recordedOutcome(Draft draft) {
        if (draft.status() != Status.WAITING) {
            return null;
        }
        if (draft.isCaseAction()) {
            return caseActionOutcome(draft);
        }
        View recovered = result(draft.withStatus(Status.SUBMITTED, null));
        return recovered.status() == Status.SUBMITTED && recovered.messageId() == null ? null : recovered;
    }

    private View caseActionOutcome(Draft draft) {
        Outcome result = caseActions.outcome(draft.account(), draft.id(), draft.scope() == McpScope.TENANT_INTAKE, draft.basis()).orElse(null);
        if (result == null) {
            return null;
        }
        if (result.status() == draft.status() && Objects.equals(result.caseId(), draft.caseId())) {
            return draft.view();
        }
        database.update("UPDATE pf_mcp_confirmation SET status = ?, case_id = ? WHERE id = ?",
                result.status().name(), result.caseId(), draft.id());
        if (result.status() != Status.SENT || draft.scope() == McpScope.TENANT_INTAKE) {
            audit(draft.account(), auditAction(draft.scope(), draft.actionArgument()), result.caseId(), draft.id(),
                    result.status() == Status.SENT ? "ACCEPTED" : result.status().name());
        }
        return draft.withCaseOutcome(result).view();
    }

    private View pending(Draft draft, UUID message) {
        if (draft.messageId() == null) {
            return transition(draft, Status.SUBMITTED, message);
        }
        return draft.view();
    }

    private void validate(Draft draft) {
        verify(draft.expires().isAfter(Instant.now()) && authorization.lockDelegation(draft.account(), draft.scope()));
        if (!draft.scope().staff() && draft.caseId() != null) {
            try {
                tenantCases.readStatusForAccount(draft.account().accountId(), draft.caseId());
            } catch (CaseServiceException exception) {
                throw new CaseActionException(UNAVAILABLE);
            }
        }
    }

    private static void verifyOwner(Draft draft, UUID account, boolean staff) {
        verify(account != null && draft.account().accountId().equals(account) && draft.scope().staff() == staff);
    }

    private TenantCaseView readCase(McpAccountContext account, McpScope scope, String caseId) {
        return scope.staff()
                ? staffCases.readPublicForStaff(new WebAccountContext(account.accountId()), caseId)
                : tenantCases.readForAccount(account.accountId(), caseId);
    }

    private void checkAvailability(McpAccountContext account, McpScope scope, String caseId, boolean answerRequired) {
        boolean available;
        if (scope.staff()) {
            Capability capability = staffMessages.available(new WebAccountContext(account.accountId()), caseId);
            available = answerRequired ? capability.question() : capability.information();
        } else {
            available = tenantMessages.availableForAccount(account.accountId(), caseId);
        }
        if (!available) {
            throw new CaseActionException(WORKFLOW_UNAVAILABLE);
        }
    }

    private Draft load(UUID id) {
        return database.query("SELECT * FROM pf_mcp_confirmation WHERE id = ? FOR UPDATE", this::draft, id)
                .stream()
                .findFirst()
                .orElseThrow(() -> new CaseActionException(UNAVAILABLE));
    }

    private Draft draft(ResultSet row, int rowNumber) throws SQLException {
        McpAccountContext account = new McpAccountContext(row.getObject("account_id", UUID.class),
                row.getObject("delegation_id", UUID.class), row.getString("client_id"));
        return new Draft(row.getObject("id", UUID.class), account, McpScope.valueOf(row.getString("scope")), row.getString("case_id"),
                row.getLong("case_version"), row.getString("case_title"), row.getString("message_text"), row.getBoolean("answer_required"),
                row.getObject("expires_at", OffsetDateTime.class).toInstant(), Status.valueOf(row.getString("status")),
                row.getObject("message_id", UUID.class), row.getString("action_argument"),
                row.getObject("task_key", Long.class), row.getString("review_token"));
    }

    private View transition(Draft draft, Status status, UUID message) {
        database.update("UPDATE pf_mcp_confirmation SET status = ?, message_id = ? WHERE id = ?", status.name(), message, draft.id());
        audit(draft, status.name());
        return draft.withStatus(status, message).view();
    }

    private void audit(Draft draft, String outcome) {
        audit(draft.account(), auditAction(draft.scope(), draft.actionArgument()), draft.caseId(), draft.id(), outcome);
    }

    @Override
    public void auditDenied(McpAccountContext account, McpScope scope, String outcome) {
        audit(account, scope, null, null, outcome);
    }

    private void audit(McpAccountContext account, McpScope scope, String caseId, UUID operation, String outcome) {
        audit(account, scope.name(), caseId, operation, outcome);
    }

    private void audit(McpAccountContext account, String action, String caseId, UUID operation, String outcome) {
        database.update("""
                INSERT INTO pf_mcp_audit (id, action, account_id, client_id, case_id, operation_id, outcome)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), action, account == null ? null : account.accountId(),
                account == null ? null : account.clientId(), caseId, operation, outcome);
    }

    private static String auditAction(McpScope scope, String argument) {
        return scope == McpScope.STAFF_TRANSITIONS ? "STATUS_" + argument : scope.name();
    }

    @Scheduled(cron = "0 23 3 * * *")
    @Transactional
    public void expireRecords() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        database.update("DELETE FROM pf_mcp_confirmation WHERE expires_at <= ?", now);
        database.update("DELETE FROM pf_mcp_audit WHERE occurred_at < ?", now.minus(retentionDays, ChronoUnit.DAYS));
    }

    private static String normalize(String rawText) {
        String text = CaseDescription.normalizeLineEndings(rawText);
        verify(text != null && !text.isBlank() && text.length() <= MAX_MESSAGE_LENGTH);
        return text;
    }

    private static void requireWriteScope(McpScope scope) {
        Objects.requireNonNull(scope);
        verify(scope == McpScope.TENANT_MESSAGES || scope == McpScope.STAFF_MESSAGES);
    }

    private static String staffOperation(Draft draft) {
        return "mcp:" + draft.id();
    }

    private static void verify(boolean condition) {
        if (!condition) {
            throw new CaseActionException(UNAVAILABLE);
        }
    }

    private record Draft(UUID id, McpAccountContext account, McpScope scope, String caseId, long version, String title, String text,
            boolean answerRequired, Instant expires, Status status, UUID messageId, String actionArgument, Long taskKey, String reviewToken) {

        boolean isCaseAction() {
            return scope == McpScope.TENANT_INTAKE || scope == McpScope.STAFF_TRANSITIONS;
        }

        Basis basis() {
            return new Basis(caseId, title, text, actionArgument, version, taskKey, reviewToken);
        }

        View view() {
            return new View(id, caseId, title, text, answerRequired, status, messageId, scope, actionArgument);
        }

        Draft withStatus(Status next, UUID message) {
            return new Draft(id, account, scope, caseId, version, title, text, answerRequired, expires, next, message,
                    actionArgument, taskKey, reviewToken);
        }

        Draft withCaseOutcome(Outcome outcome) {
            return new Draft(id, account, scope, outcome.caseId(), version, title, text, answerRequired, expires, outcome.status(), null,
                    actionArgument, taskKey, reviewToken);
        }

        @Override
        public String toString() {
            return "MCP-Bestätigung " + id + " (" + status + ")";
        }
    }
}
