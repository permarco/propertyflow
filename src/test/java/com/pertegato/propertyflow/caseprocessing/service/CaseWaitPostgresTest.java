package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.access.contract.StaffAccountAccess;
import com.pertegato.propertyflow.access.contract.SubmissionContext;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.caseprocessing.contract.CaseActionException;
import com.pertegato.propertyflow.caseprocessing.contract.CaseCreation;
import com.pertegato.propertyflow.caseprocessing.contract.CreateCaseCommand;
import com.pertegato.propertyflow.caseprocessing.contract.StaffDecisionAction;
import com.pertegato.propertyflow.caseprocessing.contract.StaffTaskDecisions.StaffTask;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants.UserTask;
import com.pertegato.propertyflow.caseprocessing.delivery.FollowUpGateway;
import com.pertegato.propertyflow.caseprocessing.delivery.StaffTaskGateway;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseDataStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWaitStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.Entry;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.StartOrder;
import com.pertegato.propertyflow.caseprocessing.persistence.FollowUpCommandStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffDecisionStore;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantMessageStore;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "propertyflow.workflow.enabled=false")
@ActiveProfiles("propertyflow-backend")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_DATABASE_URL", matches = ".+")
class CaseWaitPostgresTest {

    @Autowired
    private CaseCreation creation;

    @Autowired
    private CaseWorkflowStore workflow;

    @Autowired
    private CaseWorkflowService initial;

    @Autowired
    private StaffReviewService review;

    @Autowired
    private StaffDecisionService service;

    @Autowired
    private StaffDecisionStore store;

    @Autowired
    private StaffCaseService reading;

    @Autowired
    private CaseClosureService closure;

    @Autowired
    private JdbcTemplate database;

    @Autowired
    private TaskEngine engine;

    @Autowired
    private WebApplicationContext web;

    private String caseId;
    private WebAccountContext actor;
    private MockMvc browser;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("PROPERTYFLOW_TEST_DATABASE_URL"));
        properties.add("spring.datasource.username", () -> "propertyflow_test");
        properties.add("spring.datasource.password", () -> "propertyflow_test");
        properties.add("propertyflow.links.encryption-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
    }

    @BeforeEach
    void setup() {
        assertEquals("propertyflow_test", database.queryForObject("SELECT current_database()", String.class));
        database.execute("TRUNCATE pf_case CASCADE");
        actor = new WebAccountContext(UUID.randomUUID());
        database.update("""
                INSERT INTO pf_staff_account (id, name, email, active, staff_role, account_administration)
                VALUES (?, 'Mitarbeiter', ?, TRUE, TRUE, FALSE)
                """, actor.accountId(), actor.accountId() + "@example.test");
        caseId = creation.create(new SubmissionContext.Guest(UUID.randomUUID()),
                new CreateCaseCommand("decision", "Heizung", "Wohnung", "Kalt", "tenant@example.test")).caseId();
        StartOrder order = workflow.claim(100, "<default>", Duration.ofMinutes(2)).orElseThrow();
        initial.register(caseId, order.id(), 200, 100, "<default>");
        Entry entry = initial.prepareInitial(caseId, 200, 300);
        initial.registerAutomatic(caseId, entry.id(), entry.callRef(), 200, 400, 350);
        initial.startClarification(caseId, entry.id(), entry.callRef(), 200, 400);
        UUID continuation = review.check(caseId, 200, signal()).continuationId();
        review.claim(caseId, 200, continuation, 500, null);
        review.register(caseId, 200, continuation, 600, 550);
        review.startReview(caseId, 200, continuation, 600);
        engine.task = 700L;
        engine.completed = null;
        engine.loseResponse = false;
        engine.reject = false;
        browser = MockMvcBuilders.webAppContextSetup(web).build();
    }

    @Autowired
    private CaseWaitService waits;
    @Autowired
    private TenantMessageStore messages;

    @Autowired
    private StaffAccountAccess access;
    @Autowired
    private CaseDataStore cases;
    @Autowired
    private CaseWaitStore waitStore;
    @Autowired
    private FollowUpCommandStore changes;
    @Autowired
    private PlatformTransactionManager transactions;

    @Test
    void changingAndDeletingPreserveHistoryAndFenceOldTimer() {
        UUID original = begin();
        FollowUpCommandService commands = changes(false);
        long version = commands.current(actor, caseId).version();
        UUID operation = UUID.randomUUID();
        assertEquals("PENDING", commands.submit(actor, caseId, operation, original, version, false, 8, null, "Nur intern ändern"));
        UUID change = changes.find(caseId, actor.accountId(), operation).orElseThrow().id();
        FollowUpCommandService.Applied applied = commands.apply(change, 200, 800, 600, false);
        assertTrue(applied.accepted());
        assertEquals("APPLYING", changes.get(change).state());
        assertEquals("OBSOLETE", waits.timer(caseId, 200, 800, original));
        commands.confirmed(change);
        assertEquals("APPLIED", commands.submit(actor, caseId, operation, original, version, false, 8, null, "Nur intern ändern"));
        assertEquals(2, count("pf_case_wait"));
        assertEquals(change, waitStore.active(caseId).orElseThrow().id());
        assertEquals("WAITING_FOR_FOLLOW_UP", state());
        long updated = commands.current(actor, caseId).version();
        UUID deleteOperation = UUID.randomUUID();
        commands.submit(actor, caseId, deleteOperation, change, updated, true, null, null, "Nur intern löschen");
        UUID delete = changes.find(caseId, actor.accountId(), deleteOperation).orElseThrow().id();
        assertTrue(commands.apply(delete, 200, 800, 600, true).accepted());
        commands.confirmed(delete);
        assertEquals("IN_PROGRESS", state());
        assertTrue(waitStore.active(caseId).isEmpty());
        assertEquals("APPLIED", service.progress(caseId, 200, 600, delete));
        assertEquals("OBSOLETE", waits.timer(caseId, 200, 800, change));
        assertEquals(5, count("pf_case_status_event"));
        assertTrue(reading.readForStaff(actor, caseId).internalChanges().stream().anyMatch(changeEntry -> changeEntry.description().contains("Nur intern löschen")));
        assertEquals("APPLIED", commands.submit(actor, caseId, deleteOperation, change, updated, true, null, null, "Nur intern löschen"));
        assertEquals(5, count("pf_case_status_event"));
    }

    @Test
    void unchangedDateCreatesNeitherNewEpochNorNotification() {
        UUID original = begin();
        FollowUpCommandService commands = changes(false);
        long version = commands.current(actor, caseId).version();
        UUID operation = UUID.randomUUID();
        LocalDate date = waitStore.get(original).orElseThrow().dueAt().atZone(FollowUpDate.ZONE).toLocalDate();
        commands.submit(actor, caseId, operation, original, version, false, null, date, "Keine Änderung");
        UUID change = changes.find(caseId, actor.accountId(), operation).orElseThrow().id();
        assertEquals(original, commands.apply(change, 200, 800, 600, false).waitId());
        commands.confirmed(change);
        assertEquals(original, commands.apply(change, 200, 800, 600, false).waitId());
        assertEquals(1, count("pf_case_wait"));
        assertEquals(3, count("pf_case_status_event"));
        assertEquals(3, count("pf_case_mail_order"));
    }

    @Test
    void contactAfterConfirmationRejectsChangeAndUnblocksTheOriginalWait() {
        UUID original = begin();
        FollowUpCommandService commands = changes(false);
        long version = commands.current(actor, caseId).version();
        UUID operation = UUID.randomUUID();
        commands.submit(actor, caseId, operation, original, version, false, 8, null, "Nicht speichern");
        UUID accessId = database.queryForObject("SELECT id FROM pf_case_access WHERE case_id = ?", UUID.class, caseId);
        messages.save(new TenantMessageStore.Access(accessId, caseId),
                "during-change", "Neue Information", 200, "<default>");
        assertTrue(waitStore.claimContact().isPresent());
        UUID change = changes.find(caseId, actor.accountId(), operation).orElseThrow().id();
        assertTrue(!commands.apply(change, 200, 800, 600, false).accepted());
        assertEquals("REJECTED", changes.get(change).state());
        assertEquals(null, changes.get(change).reason());
        assertEquals(original, waitStore.active(caseId).orElseThrow().id());
        assertEquals(3, count("pf_case_status_event"));
    }

    @Test
    void unknownNativeResponseNeverCorrelatesAgainAndRoleLossPreventsCommit() {
        UUID original = begin();
        FollowUpCommandService commands = changes(true);
        long version = commands.current(actor, caseId).version();
        UUID operation = UUID.randomUUID();
        assertThrows(IllegalStateException.class,
                () -> commands.submit(actor, caseId, operation, original, version, true, null, null, "Nicht übernehmen"));
        assertEquals("PENDING", commands.submit(actor, caseId, operation, original, version, true, null, null, "Nicht übernehmen"));
        UUID command = changes.find(caseId, actor.accountId(), operation).orElseThrow().id();
        database.update("UPDATE pf_staff_account SET staff_role = FALSE WHERE id = ?", actor.accountId());
        assertTrue(!commands.apply(command, 200, 800, 600, true).accepted());
        assertEquals("WAITING_FOR_FOLLOW_UP", state());
        assertEquals("REJECTED", changes.get(command).state());
    }

    private FollowUpCommandService changes(boolean unknown) {
        return new FollowUpCommandService(access, workflow, cases, waitStore, store, changes,
                new FollowUpGateway() {
                    @Override
                    public boolean available(long child, long leader, long staff, String tenant, UUID waitId) {
                        return true;
                    }

                    @Override
                    public void correlate(UUID command, long child, String tenant, UUID waitId, boolean delete) {
                        if (unknown) {
                            throw new IllegalStateException("UNKNOWN_TEST_RESPONSE");
                        }
                    }
                }, transactions);
    }

    @Test
    void acceptedDateIsStableAcrossWorkerRetryAndLateProcessing() {
        StaffTask task = service.current(actor, caseId);
        UUID operation = UUID.randomUUID();
        service.followUp(actor, caseId, operation, 700, task.version(), task.reviewToken(), "Termin prüfen", 3, null);
        UUID id = engine.completed;
        Instant date = database.queryForObject("SELECT due_at FROM pf_staff_wait_request WHERE decision_id = ?",
                (row, index) -> row.getTimestamp(1).toInstant(), id);
        assertEquals("STAFF_REVIEW_REQUIRED", state());
        waits.enter(caseId, 200, 600, 800L, 750L, id, true);
        assertEquals("WAITING_FOR_FOLLOW_UP", state());
        assertEquals(date, waits.enter(caseId, 200, 600, 800L, 750L, id, true).dueAt());
        assertEquals(1, count("pf_case_wait"));
        assertEquals(date.atZone(FollowUpDate.ZONE).toLocalDate(), reading.readForStaff(actor, caseId).followUpDate().orElseThrow());
        assertEquals(3, count("pf_case_status_event"));
        assertEquals(3, count("pf_case_mail_order"));
    }

    @Test
    void contactAndTimerResolveOneWaitAndOldContactCannotEndNewWait() {
        UUID wait = begin();
        UUID access = database.queryForObject("SELECT id FROM pf_case_access WHERE case_id = ?", UUID.class, caseId);
        UUID message = messages.save(new TenantMessageStore.Access(access, caseId),
                "contact", "Neue Nachricht", 200, "<default>").id();
        assertEquals("REVIEW", waits.contact(caseId, 200, 600, wait, message));
        assertEquals("STAFF_REVIEW_REQUIRED", state());
        assertEquals("OBSOLETE", waits.timer(caseId, 200, 800, wait));
        assertEquals(1, count("pf_wait_resolution"));
        engine.task = 701L;
        StaffTask task = service.current(actor, caseId);
        service.followUp(actor, caseId, UUID.randomUUID(), 701, task.version(), task.reviewToken(), "Neuer Termin", 5, null);
        waits.enter(caseId, 200, 600, 801L, 751L, engine.completed, true);
        assertEquals("OBSOLETE", waits.contact(caseId, 200, 600, wait, message));
        assertEquals("WAITING_FOR_FOLLOW_UP", state());
        assertEquals(1, count("pf_wait_resolution"));
    }

    @Test
    void timerUsesStoredAbsoluteDateAndRejectsPrematureOrClosedExecution() {
        UUID wait = begin();
        assertThrows(WorkflowRejectedException.class, () -> waits.timer(caseId, 200, 800, wait));
        database.update("UPDATE pf_case_wait SET due_at = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE id = ?", wait);
        assertEquals("REVIEW", waits.timer(caseId, 200, 800, wait));
        assertEquals("REVIEW", waits.timer(caseId, 200, 800, wait));
        assertEquals(1, count("pf_wait_resolution"));
        assertTrue(reading.readForStaff(actor, caseId).followUpDate().isEmpty());
    }

    @Test
    void changedDecisionBasisNeverStartsTheWait() {
        StaffTask task = service.current(actor, caseId);
        service.followUp(actor, caseId, UUID.randomUUID(), 700, task.version(), task.reviewToken(), "", 2, null);
        database.update("UPDATE pf_case SET version = version + 1 WHERE id = ?", caseId);
        assertThrows(WorkflowRejectedException.class, () -> waits.enter(caseId, 200, 600, 800L, 750L, engine.completed, true));
        assertEquals(0, count("pf_case_wait"));
        assertEquals("STAFF_REVIEW_REQUIRED", state());
    }

    @Test
    void externalWaitCanBeExplicitlyResumedByCurrentNativeTask() {
        StaffTask reviewTask = service.current(actor, caseId);
        service.continueWork(actor, caseId, UUID.randomUUID(), 700, reviewTask.version(), reviewTask.reviewToken());
        service.progress(caseId, 200, 600, engine.completed);
        engine.task = 701L;
        StaffTask work = service.current(actor, caseId);
        service.waitExternal(actor, caseId, UUID.randomUUID(), 701, work.version(), work.reviewToken());
        waits.enter(caseId, 200, 600, null, null, engine.completed, false);
        assertEquals("WAITING_FOR_EXTERNAL", state());
        engine.task = 702L;
        StaffTask external = service.current(actor, caseId);
        assertTrue(external.canContinue());
        service.continueWork(actor, caseId, UUID.randomUUID(), 702, external.version(), external.reviewToken());
        service.progress(caseId, 200, 600, engine.completed);
        assertEquals("IN_PROGRESS", state());
        assertEquals("CANCELLED", database.queryForObject("SELECT state FROM pf_case_wait", String.class));
    }

    private UUID begin() {
        StaffTask task = service.current(actor, caseId);
        service.followUp(actor, caseId, UUID.randomUUID(), 700, task.version(), task.reviewToken(), "", 3, null);
        waits.enter(caseId, 200, 600, 800L, 750L, engine.completed, true);
        return engine.completed;
    }

    private UUID signal() {
        UUID id = UUID.randomUUID();
        database.update("""
                INSERT INTO pf_case_review_signal (id, case_id, source_type, source_ref, reason, leading_instance_key)
                VALUES (?, ?, 'TEST', ?, 'TEST_REVIEW', 200)
                """, id, caseId, UUID.randomUUID());
        return id;
    }

    private String state() {
        return database.queryForObject("SELECT status FROM pf_case WHERE id = ?", String.class, caseId);
    }

    private int count(String table) {
        return database.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    static class TaskEngine implements StaffTaskGateway {
        private Long task;
        private UUID completed;
        private boolean loseResponse;
        private boolean reject;

        @Override
        public Optional<Long> currentTask(Binding binding) {
            assertEquals(new Binding(200, 600, 550, "<default>", binding.element()), binding);
            assertTrue(UserTask.REVIEW_TASK.equals(binding.element()) || UserTask.WORK_TASK.equals(binding.element())
                    || UserTask.EXTERNAL_WAIT_TASK.equals(binding.element()));
            return Optional.ofNullable(task);
        }

        @Override
        public void completeTask(Binding binding, long taskKey, UUID decisionId, StaffDecisionAction action) {
            assertEquals(taskKey, task);
            if (reject) {
                throw new CaseActionException("INVALID_DECISION");
            }
            task = null;
            completed = decisionId;
            if (loseResponse) {
                throw new IllegalStateException("TEST_LOST_REPLY");
            }
        }
    }

    @TestConfiguration
    static class Configuration {
        @Bean
        @Primary
        TaskEngine taskEngine() {
            return new TaskEngine();
        }
    }
}
