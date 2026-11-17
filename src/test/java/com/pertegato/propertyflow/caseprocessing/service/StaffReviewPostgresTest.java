package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.access.contract.SubmissionContext;
import com.pertegato.propertyflow.caseprocessing.contract.CaseCreation;
import com.pertegato.propertyflow.caseprocessing.contract.CreateCaseCommand;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.Entry;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.StartOrder;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffReviewStore;
import com.pertegato.propertyflow.caseprocessing.service.StaffReviewService.ReviewRoute;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "propertyflow.workflow.enabled=false")
@ActiveProfiles("propertyflow-backend")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_DATABASE_URL", matches = ".+")
class StaffReviewPostgresTest {

    @Autowired
    private CaseCreation creation;

    @Autowired
    private CaseWorkflowStore workflow;

    @Autowired
    private CaseWorkflowService initial;

    @Autowired
    private StaffReviewStore store;

    @Autowired
    private StaffReviewService service;

    @Autowired
    private JdbcTemplate database;

    private String caseId;
    private Entry entry;

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
        caseId = creation.create(new SubmissionContext.Guest(UUID.randomUUID()),
                new CreateCaseCommand("review", "Heizung", "Wohnung", "Kalt", "tenant@example.test")).caseId();
        StartOrder order = workflow.claim(100, "<default>", Duration.ofMinutes(2)).orElseThrow();
        initial.register(caseId, order.id(), 200, 100, "<default>");
        entry = initial.prepareInitial(caseId, 200, 300);
        initial.registerAutomatic(caseId, entry.id(), entry.callRef(), 200, 400, 350);
        initial.startClarification(caseId, entry.id(), entry.callRef(), 200, 400);
    }

    @Test
    void confirmationBlocksAutomaticEffectsBeforeStatusChangeAndRetryKeepsOneReview() {
        UUID signal = signal(200);
        ReviewRoute route = service.check(caseId, 200, signal);
        assertTrue(store.blocked(entry.callRef()));
        assertEquals("UNDER_CLARIFICATION", status());
        assertEquals(route, service.check(caseId, 200, signal));
        UUID id = route.continuationId();
        assertEquals(id, service.interruption(caseId, 200, entry.callRef()).id());
        assertEquals("START", service.claim(caseId, 200, id, 500, null));
        assertEquals("START", service.claim(caseId, 200, id, 500, null));
        service.register(caseId, 200, id, 600, 550);
        service.startReview(caseId, 200, id, 600);
        service.startReview(caseId, 200, id, 600);
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals(2, database.queryForObject("SELECT COUNT(*) FROM pf_case_status_event", Integer.class));
        assertEquals(2, database.queryForObject("SELECT COUNT(*) FROM pf_case_mail_order", Integer.class));
        assertEquals("NONE", service.check(caseId, 200, signal).route());
    }

    @Test
    void simultaneousReasonsShareContinuationAndReservationIsNotAnActiveTask() throws Exception {
        UUID first = signal(200);
        UUID second = signal(200);
        try (ExecutorService threads = Executors.newFixedThreadPool(2)) {
            Future<ReviewRoute> a = threads.submit(() -> service.check(caseId, 200, first));
            Future<ReviewRoute> b = threads.submit(() -> service.check(caseId, 200, second));
            UUID id = a.get().continuationId();
            assertEquals(id, b.get().continuationId());
            service.claim(caseId, 200, id, 500, null);
            assertThrows(IllegalStateException.class, () -> service.claim(caseId, 200, id, 501, null));
            service.register(caseId, 200, id, 600, 550);
            assertThrows(IllegalStateException.class, () -> service.claim(caseId, 200, id, 501, 600L));
            service.startReview(caseId, 200, id, 600);
            assertEquals("EXISTING", service.claim(caseId, 200, id, 501, 600L));
            assertEquals("START", service.claim(caseId, 200, id, 500, null));
        }
        assertEquals(1, database.queryForObject("SELECT COUNT(*) FROM pf_staff_continuation", Integer.class));
    }

    @Test
    void anotherReasonAugmentsSameReviewWithoutAnotherStatusMail() {
        UUID id = service.check(caseId, 200, signal(200)).continuationId();
        service.claim(caseId, 200, id, 500, null);
        service.register(caseId, 200, id, 600, 550);
        service.startReview(caseId, 200, id, 600);
        UUID later = signal(200);
        assertEquals(new ReviewRoute("EXISTING", id), service.check(caseId, 200, later));
        service.merge(caseId, 200, id, later, 600);
        service.merge(caseId, 200, id, later, 600);
        assertEquals(2, database.queryForObject("SELECT COUNT(*) FROM pf_case_mail_order", Integer.class));
        assertEquals("NONE", service.check(caseId, 200, later).route());
    }

    @Test
    void closedCaseAndOldGenerationNeverReopenOrClaim() {
        assertThrows(WorkflowRejectedException.class, () -> service.check(caseId, 200, signal(199)));
        UUID signal = signal(200);
        UUID id = service.check(caseId, 200, signal).continuationId();
        database.update("UPDATE pf_case SET status = 'CLOSED', version = version + 1");
        assertEquals("NONE", service.check(caseId, 200, signal).route());
        assertThrows(WorkflowRejectedException.class, () -> service.claim(caseId, 200, id, 500, null));
        assertEquals("CLOSED", status());
        assertEquals("NOTE", database.queryForObject("SELECT state FROM pf_case_review_signal WHERE id = ?", String.class, signal));
    }

    @Test
    void foreignChildIsRejectedButMandatoryReasonSurvivesNewCaseData() {
        UUID id = service.check(caseId, 200, signal(200)).continuationId();
        service.claim(caseId, 200, id, 500, null);
        service.register(caseId, 200, id, 600, 550);
        assertThrows(WorkflowRejectedException.class, () -> service.register(caseId, 200, id, 601, 551));
        assertThrows(WorkflowRejectedException.class, () -> service.startReview(caseId, 200, id, 601));
        database.update("UPDATE pf_case SET version = version + 1");
        service.startReview(caseId, 200, id, 600);
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals(3, database.queryForObject("SELECT version FROM pf_case", Long.class));
    }

    @Test
    void supersededOwnClaimCannotRestartAndReservationAloneCannotEndIt() {
        UUID previous = service.check(caseId, 200, signal(200)).continuationId();
        service.claim(caseId, 200, previous, 500, null);
        database.update("UPDATE pf_staff_continuation SET superseded = TRUE WHERE id = ?", previous);
        UUID current = service.check(caseId, 200, signal(200)).continuationId();
        service.claim(caseId, 200, current, 501, null);
        assertThrows(IllegalStateException.class, () -> service.claim(caseId, 200, previous, 500, null));
        assertThrows(WorkflowRejectedException.class, () -> service.register(caseId, 200, previous, 601, 551));
        service.register(caseId, 200, current, 600, 550);
        assertThrows(IllegalStateException.class, () -> service.claim(caseId, 200, previous, 500, 600L));
        service.startReview(caseId, 200, current, 600);
        assertEquals("SUPERSEDED", service.claim(caseId, 200, previous, 500, 600L));
        assertThrows(IllegalStateException.class, () -> service.reconcile(caseId, 200, previous, current, 601));
        service.reconcile(caseId, 200, previous, current, 600);
        service.reconcile(caseId, 200, previous, current, 600);
        assertEquals(0, database.queryForObject("SELECT count(*) FROM pf_case_review_signal WHERE continuation_id = ?", Integer.class, previous));
        assertEquals(2, database.queryForObject("SELECT count(*) FROM pf_case_review_signal WHERE continuation_id = ?", Integer.class, current));
        assertEquals(2, database.queryForObject("SELECT count(*) FROM pf_case_status_event", Integer.class));
    }

    @Test
    void registeredSupersededChildCannotReenterReview() {
        UUID previous = service.check(caseId, 200, signal(200)).continuationId();
        service.claim(caseId, 200, previous, 500, null);
        service.register(caseId, 200, previous, 600, 550);
        database.update("UPDATE pf_staff_continuation SET superseded = TRUE WHERE id = ?", previous);
        service.check(caseId, 200, signal(200));
        assertThrows(WorkflowRejectedException.class, () -> service.register(caseId, 200, previous, 600, 550));
        assertThrows(WorkflowRejectedException.class, () -> service.startReview(caseId, 200, previous, 600));
        assertEquals("UNDER_CLARIFICATION", status());
    }

    private UUID signal(long leader) {
        UUID id = UUID.randomUUID();
        database.update("""
                INSERT INTO pf_case_review_signal (id, case_id, source_type, source_ref, reason, leading_instance_key)
                VALUES (?, ?, 'MAIL', ?, 'MAIL_DELIVERY_REQUIRES_REVIEW', ?)
                """, id, caseId, UUID.randomUUID(), leader);
        return id;
    }

    private String status() {
        return database.queryForObject("SELECT status FROM pf_case", String.class);
    }
}
