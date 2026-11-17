package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.access.contract.SubmissionContext;
import com.pertegato.propertyflow.caseprocessing.contract.CaseAccepted;
import com.pertegato.propertyflow.caseprocessing.contract.CaseCreation;
import com.pertegato.propertyflow.caseprocessing.contract.CaseReading;
import com.pertegato.propertyflow.caseprocessing.contract.CaseStatus;
import com.pertegato.propertyflow.caseprocessing.contract.CreateCaseCommand;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.Entry;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.StartOrder;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "propertyflow.workflow.enabled=false")
@ActiveProfiles("propertyflow-backend")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_DATABASE_URL", matches = ".+")
class CaseWorkflowPostgresTest {

    @Autowired
    private CaseCreation creation;

    @Autowired
    private CaseReading reading;

    @Autowired
    private CaseWorkflowStore store;

    @Autowired
    private CaseWorkflowService service;

    @Autowired
    private JdbcTemplate database;

    private CaseAccepted accepted;
    private StartOrder order;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("PROPERTYFLOW_TEST_DATABASE_URL"));
        properties.add("spring.datasource.username", () -> "propertyflow_test");
        properties.add("spring.datasource.password", () -> "propertyflow_test");
        properties.add("propertyflow.links.encryption-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
    }

    @BeforeEach
    void prepareCase() {
        assertEquals("propertyflow_test", database.queryForObject("select current_database()", String.class));
        database.execute("TRUNCATE pf_case CASCADE");
        accepted = creation.create(new SubmissionContext.Guest(UUID.randomUUID()),
                new CreateCaseCommand(UUID.randomUUID().toString(), "Heizung kalt", "Wohnung 4", "Seit gestern kalt.", "tenant@example.test"));
        order = store.claim(100, "<default>", Duration.ofMinutes(2)).orElseThrow();
    }

    @Test
    void registersBeforeChangingStatusAndPublishesOneStatusMailOrder() {
        service.verifyStart(accepted.caseId(), order.id(), 200, 100, "<default>");
        assertEquals(0, count("pf_case_process"));
        assertStatus(CaseStatus.RECEIVED);
        register();
        assertEquals("CONFIRMED", database.queryForObject("SELECT state FROM pf_process_start_order", String.class));
        assertStatus(CaseStatus.RECEIVED);
        Entry entry = initialEntry();
        clarify(entry);
        assertStatus(CaseStatus.UNDER_CLARIFICATION);
        assertEquals(1, count("pf_case_status_event"));
        assertEquals(1, count("pf_case_mail_order"));
        assertEquals(1, count("pf_acceptance_mail_order"));
    }

    @Test
    void retriesAfterEveryCommitAreIdempotentAndDoNotReopenClosedCase() {
        register();
        Entry entry = initialEntry();
        clarify(entry);
        database.update("UPDATE pf_case SET status = 'CLOSED', version = version + 1");
        long version = database.queryForObject("SELECT version FROM pf_case", Long.class);
        register();
        assertEquals(entry, service.prepareInitial(accepted.caseId(), 200, 300));
        service.registerAutomatic(accepted.caseId(), entry.id(), entry.callRef(), 200, 400, 350);
        clarify(entry);
        assertStatus(CaseStatus.CLOSED);
        assertEquals(version, database.queryForObject("SELECT version FROM pf_case", Long.class));
        assertEquals(1, count("pf_case_status_event"));
        assertEquals(1, count("pf_case_mail_order"));
    }

    @Test
    void refusesOtherDefinitionTenantOrderAndLeadingInstance() {
        assertThrows(WorkflowRejectedException.class, () -> service.register(accepted.caseId(), order.id(), 200, 101, "<default>"));
        assertThrows(WorkflowRejectedException.class, () -> service.register(accepted.caseId(), order.id(), 200, 100, "other"));
        assertThrows(WorkflowRejectedException.class, () -> service.register(accepted.caseId(), UUID.randomUUID(), 200, 100, "<default>"));
        register();
        assertThrows(WorkflowRejectedException.class, () -> service.register(accepted.caseId(), order.id(), 201, 100, "<default>"));
        assertEquals(200L, store.leadingInstance(accepted.caseId()).orElseThrow());
    }

    @Test
    void refusesUnregisteredOrStaleChildAndAnotherInitialEntry() {
        assertThrows(WorkflowRejectedException.class, () -> service.prepareInitial(accepted.caseId(), 200, 300));
        register();
        Entry entry = service.prepareInitial(accepted.caseId(), 200, 300);
        assertThrows(WorkflowRejectedException.class, () -> clarify(entry));
        assertThrows(WorkflowRejectedException.class, () -> service.prepareInitial(accepted.caseId(), 200, 301));
        assertThrows(WorkflowRejectedException.class,
                () -> service.registerAutomatic(accepted.caseId(), entry.id(), UUID.randomUUID(), 200, 400, 350));
        database.update("UPDATE pf_case SET version = version + 1");
        assertThrows(WorkflowRejectedException.class,
                () -> service.registerAutomatic(accepted.caseId(), entry.id(), entry.callRef(), 200, 400, 350));
        assertStatus(CaseStatus.RECEIVED);
    }

    @Test
    void statusCannotOverwriteAConcurrentBusinessChange() {
        register();
        Entry entry = initialEntry();
        database.update("UPDATE pf_case SET status = 'CLOSED', version = version + 1");
        assertThrows(WorkflowRejectedException.class, () -> clarify(entry));
        assertStatus(CaseStatus.CLOSED);
        assertEquals(0, count("pf_case_status_event"));
    }

    @Test
    void revokedLinkDoesNotLoseTheStatusChangeOrItsMailObligation() {
        register();
        Entry entry = initialEntry();
        database.update("UPDATE pf_case_access SET revoked_at = CURRENT_TIMESTAMP");
        clarify(entry);
        assertEquals("UNDER_CLARIFICATION", database.queryForObject("SELECT status FROM pf_case", String.class));
        assertEquals("PENDING", database.queryForObject("SELECT state FROM pf_case_mail_order", String.class));
        assertEquals(1, count("pf_case_status_event"));
    }

    @Test
    void missingMailPersistenceRollsBackStatusAndEvent() {
        register();
        Entry entry = initialEntry();
        database.execute("ALTER TABLE pf_case_mail_order ADD CONSTRAINT test_mail_failure CHECK (notification_type <> 'STATUS_CHANGED')");
        try {
            assertThrows(DataIntegrityViolationException.class, () -> clarify(entry));
            assertStatus(CaseStatus.RECEIVED);
            assertEquals(0, count("pf_case_status_event"));
        } finally {
            database.execute("ALTER TABLE pf_case_mail_order DROP CONSTRAINT test_mail_failure");
        }
        clarify(entry);
        assertStatus(CaseStatus.UNDER_CLARIFICATION);
    }

    @Test
    void leaseSurvivesRestartAndOldResponseCannotOverwriteNewOwnerOrConfirmation() {
        assertTrue(store.claim(101, "other", Duration.ofMinutes(2)).isEmpty());
        database.update("UPDATE pf_process_start_order SET lease_until = CURRENT_TIMESTAMP - INTERVAL '1 second'");
        StartOrder reclaimed = store.claim(101, "other", Duration.ofMinutes(2)).orElseThrow();
        assertEquals(order.id(), reclaimed.id());
        assertEquals(100, reclaimed.definitionKey());
        assertEquals("<default>", reclaimed.tenantId());
        store.finishAttempt(order, "REJECTED", true, Duration.ZERO);
        assertEquals(reclaimed.leaseId(), database.queryForObject("SELECT lease_id FROM pf_process_start_order", UUID.class));
        register();
        store.finishAttempt(reclaimed, "UNKNOWN", false, Duration.ZERO);
        assertEquals("CONFIRMED", database.queryForObject("SELECT state FROM pf_process_start_order", String.class));
        assertTrue(store.claim(100, "<default>", Duration.ofMinutes(2)).isEmpty());
    }

    @Test
    void twoConcurrentRegistrationsHaveExactlyOneWinner() throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Callable<Boolean>> tasks = List.of(() -> tryRegister(200), () -> tryRegister(201));
            List<Future<Boolean>> results = executor.invokeAll(tasks);
            assertFalse(results.get(0).get().equals(results.get(1).get()));
        }
        assertEquals(1, count("pf_case_process"));
    }

    @Test
    void concurrentStatusJobsProduceOneEventAndOneMailOrder() throws Exception {
        register();
        Entry entry = initialEntry();
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Callable<Void>> tasks = List.of(() -> {
                clarify(entry);
                return null;
            }, () -> {
                clarify(entry);
                return null;
            });
            for (Future<Void> result : executor.invokeAll(tasks)) {
                result.get();
            }
        }
        assertEquals(1, count("pf_case_status_event"));
        assertEquals(1, count("pf_case_mail_order"));
        assertEquals(1L, database.queryForObject("SELECT version FROM pf_case", Long.class));
    }

    private boolean tryRegister(long instanceKey) {
        try {
            service.register(accepted.caseId(), order.id(), instanceKey, 100, "<default>");
            return true;
        } catch (WorkflowRejectedException exception) {
            return false;
        }
    }

    private void register() {
        service.register(accepted.caseId(), order.id(), 200, 100, "<default>");
    }

    private Entry initialEntry() {
        Entry entry = service.prepareInitial(accepted.caseId(), 200, 300);
        service.registerAutomatic(accepted.caseId(), entry.id(), entry.callRef(), 200, 400, 350);
        return store.entryForCase(accepted.caseId()).orElseThrow();
    }

    private void clarify(Entry entry) {
        service.startClarification(accepted.caseId(), entry.id(), entry.callRef(), 200, 400);
    }

    private void assertStatus(CaseStatus expected) {
        assertEquals(expected, reading.readByToken(accepted.personalAccessToken()).state().status());
    }

    private int count(String table) {
        return database.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
