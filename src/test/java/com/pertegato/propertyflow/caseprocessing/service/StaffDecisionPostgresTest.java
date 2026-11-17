package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.access.contract.SubmissionContext;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.access.web.StaffWebSession;
import com.pertegato.propertyflow.caseprocessing.contract.CaseActionException;
import com.pertegato.propertyflow.caseprocessing.contract.CaseCreation;
import com.pertegato.propertyflow.caseprocessing.contract.CreateCaseCommand;
import com.pertegato.propertyflow.caseprocessing.contract.StaffDecisionAction;
import com.pertegato.propertyflow.caseprocessing.contract.StaffTaskDecisions.StaffTask;
import com.pertegato.propertyflow.caseprocessing.delivery.StaffTaskGateway;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.Entry;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.StartOrder;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffDecisionStore;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "propertyflow.workflow.enabled=false")
@ActiveProfiles("propertyflow-backend")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_DATABASE_URL", matches = ".+")
class StaffDecisionPostgresTest {

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

    @Test
    void nativeCompletionIsSeparateFromAtomicStatusAndMailCommit() throws Exception {
        StaffTask task = service.current(actor, caseId);
        assertTrue(task.available());
        UUID operation = UUID.randomUUID();
        service.continueWork(actor, caseId, operation, 700, task.version(), task.reviewToken(), "Interne Abklärung dokumentiert");
        assertEquals("CONFIRMED", store.find(caseId, actor.accountId(), operation).orElseThrow().state());
        assertEquals("STAFF_REVIEW_REQUIRED", state());
        assertTrue(reading.readForStaff(actor, caseId).internalChanges().isEmpty());
        assertEquals("APPLIED", service.progress(caseId, 200, 600, engine.completed));
        assertEquals("APPLIED", service.progress(caseId, 200, 600, engine.completed));
        assertEquals("IN_PROGRESS", state());
        assertEquals("Interne Abklärung dokumentiert", database.queryForObject("SELECT internal_reason FROM pf_staff_decision", String.class));
        assertEquals(3, count("pf_case_status_event"));
        assertEquals(3, count("pf_case_mail_order"));
        assertEquals(1, count("pf_case_review_signal"));
        assertEquals(1, reading.readForStaff(actor, caseId).internalChanges().size());
        assertEquals(actor.accountId(), reading.readForStaff(actor, caseId).internalChanges().getFirst().actorId());
        assertTrue(reading.readForStaff(actor, caseId).internalChanges().getFirst().description().contains("Interne Abklärung dokumentiert"));
        assertTrue(!reading.readForStaff(actor, caseId).publicView().toString().contains("Interne Abklärung dokumentiert"));
        browser.perform(signed(get("/mitarbeiter/faelle/" + caseId)))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.content()
                        .string(Matchers.containsString("Interne Abklärung dokumentiert")));
        assertThrows(CaseActionException.class, () -> service.continueWork(actor, caseId, UUID.randomUUID(), 700, task.version(), task.reviewToken()));
    }

    @Test
    void changedCaseAfterCompletionRequiresNewReviewWithoutStatusOrMailEffect() {
        StaffTask task = service.current(actor, caseId);
        service.continueWork(actor, caseId, UUID.randomUUID(), 700, task.version(), task.reviewToken(), "Verworfener interner Grund");
        database.update("UPDATE pf_case SET version = version + 1 WHERE id = ?", caseId);
        assertEquals("REVIEW_AGAIN", service.progress(caseId, 200, 600, engine.completed));
        assertEquals("REVIEW_AGAIN", service.progress(caseId, 200, 600, engine.completed));
        assertEquals("STAFF_REVIEW_REQUIRED", state());
        assertEquals(2, count("pf_case_mail_order"));
        assertTrue(reading.readForStaff(actor, caseId).internalChanges().isEmpty());
        assertEquals(null, database.queryForObject("SELECT internal_reason FROM pf_staff_decision", String.class));
        engine.task = 701L;
        StaffTask current = service.current(actor, caseId);
        assertThrows(CaseActionException.class,
                () -> service.continueWork(actor, caseId, UUID.randomUUID(), 700, current.version(), current.reviewToken()));
        assertEquals(701L, engine.task);
    }

    @Test
    void newReviewReasonInvalidatesConfirmedContextEvenBeforeCaseVersionChanges() {
        StaffTask task = service.current(actor, caseId);
        signal();
        assertThrows(CaseActionException.class,
                () -> service.continueWork(actor, caseId, UUID.randomUUID(), 700, task.version(), task.reviewToken()));
        assertEquals(0, count("pf_staff_decision"));
        StaffTask current = service.current(actor, caseId);
        service.continueWork(actor, caseId, UUID.randomUUID(), 700, current.version(), current.reviewToken());
        signal();
        assertEquals("REVIEW_AGAIN", service.progress(caseId, 200, 600, engine.completed));
        assertEquals("STAFF_REVIEW_REQUIRED", state());
    }

    @Test
    void lostCompletionResponseIsNotAutomaticallyRepeatedAndWorkerCanConfirmPreparedDecision() throws Exception {
        StaffTask task = service.current(actor, caseId);
        UUID operation = UUID.randomUUID();
        engine.loseResponse = true;
        browser.perform(signed(command(operation, task))).andExpect(status().isInternalServerError());
        assertEquals("PREPARED", store.find(caseId, actor.accountId(), operation).orElseThrow().state());
        assertEquals("STAFF_REVIEW_REQUIRED", state());
        engine.task = 700L;
        engine.reject = true;
        browser.perform(signed(command(operation, task))).andExpect(status().isBadRequest());
        assertEquals("PREPARED", store.find(caseId, actor.accountId(), operation).orElseThrow().state());
        assertEquals("APPLIED", service.progress(caseId, 200, 600, engine.completed));
        assertEquals("IN_PROGRESS", state());
        browser.perform(signed(command(operation, task))).andExpect(status().isBadRequest());
        assertEquals(1, count("pf_staff_decision"));
    }

    @Test
    void httpChecksSessionCsrfAndCurrentRoleBeforeCompletingTask() throws Exception {
        StaffTask task = service.current(actor, caseId);
        browser.perform(get("/mitarbeiter/faelle/" + caseId + "/pruefung")).andExpect(status().isUnauthorized());
        browser.perform(signed(command(UUID.randomUUID(), task).param("csrf", "wrong"))).andExpect(status().isForbidden());
        database.update("UPDATE pf_staff_account SET staff_role = FALSE WHERE id = ?", actor.accountId());
        browser.perform(signed(command(UUID.randomUUID(), task))).andExpect(status().isForbidden());
        assertEquals(700L, engine.task);
        assertEquals(0, count("pf_staff_decision"));
    }

    private MockHttpServletRequestBuilder command(UUID operation, StaffTask task) {
        return post("/mitarbeiter/faelle/" + caseId + "/fortsetzen").param("operationId", operation.toString())
                .param("userTaskKey", task.taskKey()).param("version", Long.toString(task.version()))
                .param("reviewToken", task.reviewToken()).param("csrf", "csrf-test");
    }

    @Test
    void explicitClosureRecordsHumanEvidenceWithoutMessageOrMailAndIsIdempotent() {
        StaffTask task = service.current(actor, caseId);
        assertTrue(task.canClose());
        service.closeCase(actor, caseId, UUID.randomUUID(), 700, task.version(), task.reviewToken());
        assertEquals("STAFF_REVIEW_REQUIRED", state());
        UUID decision = engine.completed;
        assertEquals("CLOSED", closure.close(caseId, 200, 600, decision).state());
        assertEquals("CLOSED", closure.close(caseId, 200, 600, decision).state());
        assertEquals("CLOSED", state());
        assertEquals(1, count("pf_case_closure"));
        assertEquals(2, count("pf_case_mail_order"));
        assertEquals(0, count("pf_internal_memo"));
        assertEquals(actor.accountId(), database.queryForObject("SELECT actor_id FROM pf_case_closure", UUID.class));
        assertEquals("Vom Mitarbeiter geprüft: keine weitere Bearbeitung erforderlich.",
                database.queryForObject("SELECT reason FROM pf_case_closure", String.class));
        assertThrows(WorkflowRejectedException.class, () -> closure.close(caseId, 201, 600, decision));
    }

    @Test
    void staleClosureReplacesCompletedStaffCallExactlyOnceWithoutDuplicateReviewMail() {
        StaffTask task = service.current(actor, caseId);
        service.closeCase(actor, caseId, UUID.randomUUID(), 700, task.version(), task.reviewToken());
        UUID decision = engine.completed;
        database.update("UPDATE pf_case SET version = version + 1 WHERE id = ?", caseId);
        CaseClosureService.ClosureResult conflict = closure.close(caseId, 200, 600, decision);
        assertEquals("STALE", conflict.state());
        assertEquals(conflict, closure.close(caseId, 200, 600, decision));
        assertEquals(2, count("pf_staff_continuation"));
        assertEquals(0, count("pf_case_closure"));
        assertEquals("START", review.claim(caseId, 200, conflict.continuation(), 501, null));
        review.register(caseId, 200, conflict.continuation(), 601, 551);
        review.startReview(caseId, 200, conflict.continuation(), 601);
        assertEquals("STAFF_REVIEW_REQUIRED", state());
        assertEquals(2, count("pf_case_mail_order"));
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_staff_continuation WHERE NOT superseded", Integer.class));
    }

    @Test
    void closureFromWorkUsesWorkTaskAndARevokedRoleCannotCloseAfterNativeConfirmation() {
        StaffTask reviewTask = service.current(actor, caseId);
        service.continueWork(actor, caseId, UUID.randomUUID(), 700, reviewTask.version(), reviewTask.reviewToken());
        service.progress(caseId, 200, 600, engine.completed);
        engine.task = 701L;
        StaffTask work = service.current(actor, caseId);
        assertTrue(work.canClose());
        assertEquals(false, work.canContinue());
        service.closeCase(actor, caseId, UUID.randomUUID(), 701, work.version(), work.reviewToken());
        database.update("UPDATE pf_staff_account SET staff_role = FALSE WHERE id = ?", actor.accountId());
        CaseClosureService.ClosureResult conflict = closure.close(caseId, 200, 600, engine.completed);
        assertEquals("STALE", conflict.state());
        assertEquals("START", review.claim(caseId, 200, conflict.continuation(), 501, null));
        review.register(caseId, 200, conflict.continuation(), 601, 551);
        review.startReview(caseId, 200, conflict.continuation(), 601);
        assertEquals("STAFF_REVIEW_REQUIRED", state());
        assertEquals(0, count("pf_case_closure"));
        assertEquals(4, count("pf_case_mail_order"));
    }

    private MockHttpServletRequestBuilder signed(MockHttpServletRequestBuilder request) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(StaffWebSession.ACCOUNT, actor.accountId());
        session.setAttribute(StaffWebSession.CSRF, "csrf-test");
        return request.session(session).with(servlet -> {
            servlet.setRequestedSessionId(session.getId());
            servlet.setRequestedSessionIdFromCookie(true);
            return servlet;
        });
    }

    @Test
    void definitiveNativeRejectionDiscardsReasonAndCreatesNoConfirmedAudit() {
        StaffTask task = service.current(actor, caseId);
        engine.reject = true;
        UUID operation = UUID.randomUUID();
        assertThrows(CaseActionException.class,
                () -> service.continueWork(actor, caseId, operation, 700, task.version(), task.reviewToken(), "Nicht speichern"));
        assertEquals("REJECTED", store.find(caseId, actor.accountId(), operation).orElseThrow().state());
        assertEquals(null, store.find(caseId, actor.accountId(), operation).orElseThrow().reason());
        assertTrue(reading.readForStaff(actor, caseId).internalChanges().isEmpty());
        assertEquals("STAFF_REVIEW_REQUIRED", state());
        assertEquals(2, count("pf_case_mail_order"));
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
            assertTrue("SReview".equals(binding.element()) || "SWork".equals(binding.element()));
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
