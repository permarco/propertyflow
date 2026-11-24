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
import com.pertegato.propertyflow.caseprocessing.delivery.StaffTaskGateway;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseReopenStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.Entry;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.StartOrder;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffDecisionStore;
import java.time.Duration;
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
class CaseReopenPostgresTest {

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
    private StaffAccountAccess access;
    @Autowired
    private CaseReopenStore reopens;
    @Autowired
    private PlatformTransactionManager transactions;

    @Test
    void newGenerationPreservesCaseAndLinkAndRejectsOldAutomaticWork() {
        closeCase();
        long version = database.queryForObject("SELECT version FROM pf_case WHERE id = ?", Long.class, caseId);
        UUID oldEntry = workflow.entryForCase(caseId).orElseThrow().id();
        String tokenHash = database.queryForObject("SELECT token_hash FROM pf_case_access WHERE case_id = ?", String.class, caseId);
        CaseReopenService reopening = reopenService(true);
        UUID operation = UUID.randomUUID();
        UUID order = reopening.request(actor, caseId, operation, version);
        assertEquals("CLOSED", state());
        assertEquals(order, reopening.request(actor, caseId, operation, version));
        CaseReopenStore.Order activated = reopening.activate(caseId, order, 900, 100, "<default>", 901);
        assertEquals("IN_PROGRESS", state());
        assertEquals(900L, workflow.leadingInstance(caseId).orElseThrow());
        assertEquals("CONTINUE", review.read(caseId, 900, activated.continuation()).entryKind());
        review.claim(caseId, 900, activated.continuation(), 902, null);
        review.register(caseId, 900, activated.continuation(), 903, 904);
        assertEquals(activated, reopening.activate(caseId, order, 900, 100, "<default>", 901));
        assertEquals(null, reopening.activate(caseId, order, 910, 100, "<default>", 911));
        assertEquals(1, count("pf_case"));
        assertEquals(1, count("pf_acceptance_mail_order"));
        assertEquals(1, count("pf_case_closure"));
        assertEquals(2, count("pf_case_process_history"));
        assertEquals(2, count("pf_automatic_entry"));
        assertEquals(tokenHash, database.queryForObject("SELECT token_hash FROM pf_case_access WHERE case_id = ?", String.class, caseId));
        assertTrue(!oldEntry.equals(workflow.entryForCase(caseId).orElseThrow().id()));
        assertThrows(WorkflowRejectedException.class, () -> initial.prepareInitial(caseId, 200, 300));
    }

    @Test
    void currentCriticalUrgencyRequiresReviewAndManualPrioritySurvives() {
        database.update("UPDATE pf_case SET urgency = 'CRITICAL', urgency_source = 'STAFF' WHERE id = ?", caseId);
        closeCase();
        long version = database.queryForObject("SELECT version FROM pf_case WHERE id = ?", Long.class, caseId);
        CaseReopenService reopening = reopenService(true);
        UUID id = reopening.request(actor, caseId, UUID.randomUUID(), version);
        CaseReopenStore.Order activated = reopening.activate(caseId, id, 900, 100, "<default>", 901);
        assertEquals("STAFF_REVIEW_REQUIRED", state());
        assertEquals("REVIEW", review.read(caseId, 900, activated.continuation()).entryKind());
        assertEquals("STAFF", database.queryForObject("SELECT urgency_source FROM pf_case WHERE id = ?", String.class, caseId));
    }

    @Test
    void incompletePredecessorAndRevokedRoleNeverReopen() {
        closeCase();
        long version = database.queryForObject("SELECT version FROM pf_case WHERE id = ?", Long.class, caseId);
        assertThrows(CaseActionException.class, () -> reopenService(false).request(actor, caseId, UUID.randomUUID(), version));
        assertEquals(0, count("pf_case_reopen_order"));
        CaseReopenService reopening = reopenService(true);
        UUID id = reopening.request(actor, caseId, UUID.randomUUID(), version);
        database.update("UPDATE pf_staff_account SET staff_role = FALSE WHERE id = ?", actor.accountId());
        assertEquals(null, reopening.activate(caseId, id, 900, 100, "<default>", 901));
        assertEquals("CLOSED", state());
        assertEquals("REJECTED", reopens.get(id).orElseThrow().state());
        assertEquals(1, count("pf_case_process_history"));
    }

    private CaseReopenService reopenService(boolean completed) {
        return new CaseReopenService(access, workflow, reopens, (leader, tenant) -> completed, transactions);
    }

    private void closeCase() {
        StaffTask task = service.current(actor, caseId);
        service.closeCase(actor, caseId, UUID.randomUUID(), 700, task.version(), task.reviewToken());
        closure.close(caseId, 200, 600, engine.completed);
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
            assertTrue(UserTask.REVIEW_TASK.equals(binding.element()) || UserTask.WORK_TASK.equals(binding.element()));
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
