package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.access.contract.McpAccountContext;
import com.pertegato.propertyflow.access.contract.McpScope;
import com.pertegato.propertyflow.access.contract.McpTenantAuthorization;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.caseprocessing.contract.CaseAccepted;
import com.pertegato.propertyflow.caseprocessing.contract.CaseActionException;
import com.pertegato.propertyflow.caseprocessing.contract.CaseStatus;
import com.pertegato.propertyflow.caseprocessing.contract.CaseStatusView;
import com.pertegato.propertyflow.caseprocessing.contract.CreateCaseCommand;
import com.pertegato.propertyflow.caseprocessing.contract.McpMessageConfirmations;
import com.pertegato.propertyflow.caseprocessing.contract.McpMessageConfirmations.Change;
import com.pertegato.propertyflow.caseprocessing.contract.McpMessageConfirmations.NewCase;
import com.pertegato.propertyflow.caseprocessing.contract.McpMessageConfirmations.Status;
import com.pertegato.propertyflow.caseprocessing.contract.McpMessageConfirmations.Transition;
import com.pertegato.propertyflow.caseprocessing.contract.StaffTaskDecisions;
import com.pertegato.propertyflow.caseprocessing.contract.StaffTaskDecisions.StaffTask;
import com.pertegato.propertyflow.caseprocessing.contract.TenantCaseView;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseReopenStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffDecisionStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffMessageStore;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantMessageStore.Message;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(PersistentMcpMessageConfirmationsTest.Store.class)
class PersistentMcpMessageConfirmationsTest {

    private static final String CASE_ID = "case-a";
    private static final String TEXT = "Privater Entwurf";
    private static final McpScope SCOPE = McpScope.TENANT_MESSAGES;
    private static final CaseStatusView CASE = new CaseStatusView(CASE_ID, CaseStatus.RECEIVED, Optional.empty());

    @Autowired
    private McpMessageConfirmations confirmations;

    @Autowired
    private McpTenantAuthorization authorization;

    @Autowired
    private PersistentCaseService cases;

    @Autowired
    private TenantMessageService messages;

    @Autowired
    private StaffCaseService staffCases;

    @Autowired
    private StaffTaskDecisions staffDecisions;

    @Autowired
    private JdbcTemplate database;

    private McpAccountContext account;

    @BeforeEach
    void prepareAccount() {
        database.update("DELETE FROM pf_mcp_confirmation");
        database.update("DELETE FROM pf_mcp_audit");
        reset(authorization, cases, messages, staffCases, staffDecisions);
        account = new McpAccountContext(UUID.randomUUID(), UUID.randomUUID(), "test-client");
        when(authorization.lockDelegation(account, SCOPE)).thenReturn(true);
        when(cases.readForAccount(account.accountId(), CASE_ID)).thenReturn(new TenantCaseView(CASE,
                "Fenster", "Beschreibung", "Einheit", Optional.empty(), "a***", Instant.EPOCH,
                Instant.EPOCH, 5, Optional.empty(), List.of(), List.of()));
        when(cases.readStatusForAccount(account.accountId(), CASE_ID)).thenReturn(CASE);
        when(messages.availableForAccount(account.accountId(), CASE_ID)).thenReturn(true);
    }

    @Test
    void requiresSameAccountAndBrowserDecisionAndReportsProcessingUntilReceived() {
        UUID operation = confirmations.prepare(account, SCOPE, CASE_ID, TEXT, false);
        UUID messageId = UUID.randomUUID();
        AtomicReference<Message> message = new AtomicReference<>();
        when(messages.findForAccount(account.accountId(), CASE_ID, operation.toString()))
                .thenAnswer(call -> Optional.ofNullable(message.get()));
        when(messages.sendForAccount(eq(account.accountId()), eq(CASE_ID), eq(operation.toString()), eq(TEXT), any()))
                .thenAnswer(call -> {
                    Consumer<UUID> onStored = call.getArgument(4);
                    onStored.accept(messageId);
                    message.set(message(messageId, "PENDING"));
                    return messageId;
                });

        assertEquals(Status.WAITING, confirmations.continuation(account, SCOPE, operation, CASE_ID, TEXT, false, false).status());
        assertThrows(CaseActionException.class,
                () -> confirmations.continuation(account, SCOPE, operation, CASE_ID, "Verändert", false, false));
        assertThrows(CaseActionException.class, () -> confirmations.decide(UUID.randomUUID(), false, operation, true));
        verify(messages, never()).sendForAccount(eq(account.accountId()), eq(CASE_ID), eq(operation.toString()), eq(TEXT), any());

        assertEquals(Status.SUBMITTED, confirmations.decide(account.accountId(), false, operation, true).status());
        message.set(message(messageId, "RECEIVED"));
        assertEquals(messageId, confirmations.continuation(account, SCOPE, operation, CASE_ID, TEXT, false, false).messageId());
        assertEquals(Status.SENT, confirmations.decide(account.accountId(), false, operation, true).status());
        verify(messages).sendForAccount(eq(account.accountId()), eq(CASE_ID), eq(operation.toString()), eq(TEXT), any());
        List<String> outcomes = database.queryForList("SELECT outcome FROM pf_mcp_audit WHERE operation_id = ?", String.class, operation);
        assertTrue(outcomes.containsAll(List.of("PREPARED", "CONFIRMED", "STORED", "SUBMITTED", "SENT")));
        assertFalse(database.queryForList("SELECT * FROM pf_mcp_audit").toString().contains(TEXT));
    }

    @Test
    void deniesRevokedAndExpiredDraftsAndHonoursCancellationAndAuditRetention() {
        UUID revoked = confirmations.prepare(account, SCOPE, CASE_ID, TEXT, false);
        when(authorization.lockDelegation(account, SCOPE)).thenReturn(false);
        assertThrows(CaseActionException.class, () -> confirmations.decide(account.accountId(), false, revoked, true));
        when(authorization.lockDelegation(account, SCOPE)).thenReturn(true);
        UUID cancelled = confirmations.prepare(account, SCOPE, CASE_ID, TEXT, false);
        assertEquals(Status.CANCELLED, confirmations.decide(account.accountId(), false, cancelled, false).status());
        assertEquals(Status.CANCELLED, confirmations.decide(account.accountId(), false, cancelled, true).status());
        database.update("UPDATE pf_mcp_confirmation SET expires_at = ? WHERE id = ?", OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1),
                revoked);
        assertThrows(CaseActionException.class, () -> confirmations.decide(account.accountId(), false, revoked, true));
        database.update("UPDATE pf_mcp_audit SET occurred_at = ? WHERE operation_id = ?",
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(91), revoked);
        ((PersistentMcpMessageConfirmations) confirmations).expireRecords();
        assertEquals(0, database.queryForObject("SELECT COUNT(*) FROM pf_mcp_audit WHERE operation_id = ?", Integer.class, revoked));
        assertTrue(database.queryForObject("SELECT COUNT(*) FROM pf_mcp_audit WHERE operation_id = ?", Integer.class, cancelled) > 0);
        verify(messages, never()).sendForAccount(any(), any(), any(), any(), any());
    }

    private static Message message(UUID id, String state) {
        return new Message(id, CASE_ID, UUID.randomUUID(), TEXT, 1, "default", state, true, null, null);
    }

    @Test
    void createsOnlyTheConfirmedIntakeOnceAndAuditsTheCaseReference() {
        NewCase request = new NewCase("Fenster", "Wohnung 7", TEXT);
        when(authorization.lockDelegation(account, McpScope.TENANT_INTAKE)).thenReturn(true);
        when(cases.validateForAccount(eq(account.accountId()), any())).thenAnswer(call -> call.getArgument(1));
        UUID operation = confirmations.prepareCaseAction(account, request);
        CreateCaseCommand command = new CreateCaseCommand(operation.toString(), request.title(), request.unit(), request.description(), null);
        AtomicReference<String> created = new AtomicReference<>();
        when(cases.findSubmission(account.accountId(), operation)).thenAnswer(call -> Optional.ofNullable(created.get()));
        when(cases.createForMcp(eq(account.accountId()), eq(command), any())).thenAnswer(call -> {
            Consumer<String> stored = call.getArgument(2);
            stored.accept(CASE_ID);
            created.set(CASE_ID);
            return new CaseAccepted(CASE_ID, "fake-personal-link-token", false);
        });
        assertEquals(Status.WAITING, confirmations.continueCaseAction(account, operation, request, false).status());
        assertThrows(CaseActionException.class, () -> confirmations.continueCaseAction(
                account, operation, new NewCase(request.title(), "Andere Wohnung", request.description()), false));
        assertThrows(CaseActionException.class, () -> confirmations.decide(UUID.randomUUID(), false, operation, true));
        verify(cases, never()).createForMcp(any(), any(), any());

        assertEquals(CASE_ID, confirmations.decide(account.accountId(), false, operation, true).caseId());
        assertEquals(Status.SENT, confirmations.decide(account.accountId(), false, operation, true).status());
        verify(cases).createForMcp(eq(account.accountId()), eq(command), any());
        assertEquals(CASE_ID, database.queryForObject(
                "SELECT case_id FROM pf_mcp_audit WHERE operation_id = ? AND outcome = 'STORED'", String.class, operation));
        assertFalse(database.queryForList("SELECT * FROM pf_mcp_audit").toString().contains("fake-personal-link-token"));
    }

    @Test
    void passesTheOriginallyReviewedVersionAndTaskToTheStaffService() {
        Transition transition = new Transition(CASE_ID, Change.ABSCHLIESSEN);
        WebAccountContext actor = new WebAccountContext(account.accountId());
        when(authorization.lockDelegation(account, McpScope.STAFF_TRANSITIONS)).thenReturn(true);
        when(staffCases.readPublicForStaff(actor, CASE_ID)).thenReturn(new TenantCaseView(CASE,
                "Fenster", "Beschreibung", "Einheit", Optional.empty(), "a***", Instant.EPOCH,
                Instant.EPOCH, 5, Optional.empty(), List.of(), List.of()));
        when(staffDecisions.current(actor, CASE_ID)).thenReturn(new StaffTask(true, "101", 5, "reviewed", true, true));
        UUID operation = confirmations.prepareCaseAction(account, transition);
        assertThrows(CaseActionException.class, () -> confirmations.continueCaseAction(
                account, operation, new Transition(CASE_ID, Change.FORTSETZEN), false));
        doThrow(new CaseActionException("STALE")).when(staffDecisions).closeCase(actor, CASE_ID, operation, 101, 5, "reviewed");
        assertEquals(Status.REJECTED, confirmations.decide(account.accountId(), true, operation, true).status());
        assertEquals(Status.REJECTED, confirmations.decide(account.accountId(), true, operation, true).status());
        verify(staffDecisions).closeCase(actor, CASE_ID, operation, 101, 5, "reviewed");
        assertEquals("STATUS_ABSCHLIESSEN", database.queryForObject(
                "SELECT action FROM pf_mcp_audit WHERE operation_id = ? AND outcome = 'PREPARED'", String.class, operation));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Store {

        @Bean
        SingleConnectionDataSource databaseSource() throws Exception {
            SingleConnectionDataSource source = new SingleConnectionDataSource(
                    "jdbc:h2:mem:mcp-confirmation-" + UUID.randomUUID() + ";MODE=PostgreSQL", "sa", "", true);
            String migration = new ClassPathResource("db/backend-migration/updates/V10__mcp_message_confirmation.sql")
                    .getContentAsString(StandardCharsets.UTF_8)
                    .replace("timestamptz", "timestamp with time zone");
            new ResourceDatabasePopulator(new ByteArrayResource(migration.getBytes(StandardCharsets.UTF_8)),
                    new ClassPathResource("db/backend-migration/updates/V11__mcp_case_actions.sql")).execute(source);
            return source;
        }

        @Bean
        JdbcTemplate database(SingleConnectionDataSource source) {
            return new JdbcTemplate(source);
        }

        @Bean
        PlatformTransactionManager transactions(JdbcTemplate database) {
            return new DataSourceTransactionManager(database.getDataSource());
        }

        @Bean
        McpTenantAuthorization authorization() {
            return mock(McpTenantAuthorization.class);
        }

        @Bean
        PersistentCaseService cases() {
            return mock(PersistentCaseService.class);
        }

        @Bean
        TenantMessageService messages() {
            return mock(TenantMessageService.class);
        }

        @Bean
        StaffCaseService staffCases() {
            return mock(StaffCaseService.class);
        }

        @Bean
        StaffTaskDecisions staffDecisions() {
            return mock(StaffTaskDecisions.class);
        }

        @Bean
        PersistentMcpMessageConfirmations confirmations(JdbcTemplate database, McpTenantAuthorization authorization,
                PersistentCaseService cases, TenantMessageService messages, StaffCaseService staffCases, StaffTaskDecisions staffDecisions) {
            McpCaseActions actions = new McpCaseActions(cases, staffCases, staffDecisions, (actor, caseId, operation, version) -> UUID.randomUUID(),
                    mock(StaffDecisionStore.class), mock(CaseReopenStore.class));
            return new PersistentMcpMessageConfirmations(database, authorization, cases, staffCases, messages,
                    mock(StaffMessageService.class), mock(StaffMessageStore.class), actions, 90);
        }
    }
}
