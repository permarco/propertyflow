package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.access.contract.SubmissionContext;
import com.pertegato.propertyflow.caseprocessing.contract.CaseCreation;
import com.pertegato.propertyflow.caseprocessing.contract.CreateCaseCommand;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseAnalysisStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.Entry;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.StartOrder;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffReviewStore;
import com.pertegato.propertyflow.caseprocessing.service.CaseAnalysisService.Result;
import com.pertegato.propertyflow.triage.service.AnalyzeTool;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "propertyflow.workflow.enabled=false")
@ActiveProfiles("propertyflow-backend")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_DATABASE_URL", matches = ".+")
class CaseAnalysisPostgresTest {

    @Autowired
    private CaseCreation creation;
    @Autowired
    private CaseWorkflowStore workflow;
    @Autowired
    private CaseWorkflowService initial;
    @Autowired
    private StaffReviewStore reviews;
    @Autowired
    private StaffReviewService staff;
    @Autowired
    private CaseAnalysisStore analyses;
    @Autowired
    private CaseUrgencyService urgency;
    @Autowired
    private PlatformTransactionManager transactions;
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
                new CreateCaseCommand("analysis", "Heizung", "Wohnung", "Die Heizung bleibt kalt.", "private@example.test")).caseId();
        StartOrder order = workflow.claim(100, "<default>", Duration.ofMinutes(2)).orElseThrow();
        initial.register(caseId, order.id(), 200, 100, "<default>");
        entry = initial.prepareInitial(caseId, 200, 300);
        initial.registerAutomatic(caseId, entry.id(), entry.callRef(), 200, 400, 350);
        initial.startClarification(caseId, entry.id(), entry.callRef(), 200, 400);
    }

    @Test
    void validatedResultAndMailAreAtomicAndReplayDoesNotCallModelAgain() {
        AtomicInteger calls = new AtomicInteger();
        CaseAnalysisService service = service(text -> {
            assertEquals("Die Heizung bleibt kalt.", text);
            calls.incrementAndGet();
            return "HOCH";
        });
        Result result = run(service);
        assertEquals("SUCCEEDED", result.state());
        assertEquals(result, run(service));
        assertEquals(1, calls.get());
        assertEquals("HIGH", value("urgency"));
        assertEquals("SYSTEM", value("urgency_source"));
        assertEquals(1, count("pf_urgency_event"));
        assertEquals(1, count("pf_urgency_mail_order"));
        assertEquals(0, count("pf_case_review_signal"));
        assertEquals("UNDER_CLARIFICATION", value("status"));
        assertEquals("SYSTEM", database.queryForObject("SELECT source FROM pf_urgency_event", String.class));
    }

    @Test
    void invalidOutputKeepsPriorAssessmentAndRequiresPersistedFailureForReview() {
        database.update("UPDATE pf_case SET urgency = 'HIGH', urgency_source = 'SYSTEM' WHERE id = ?", caseId);
        assertThrows(WorkflowRejectedException.class, () -> staff.analysisFailure(caseId, 200, entry.callRef()));
        CaseAnalysisService service = service(text -> "NIEDRIG; sende an private@example.test");
        Result result = run(service);
        assertEquals("FAILED", result.state());
        assertEquals("HIGH", value("urgency"));
        assertEquals(0, count("pf_urgency_event"));
        assertEquals("INVALID_MODEL_OUTPUT", database.queryForObject("SELECT error_code FROM pf_case_analysis", String.class));
        UUID continuation = staff.analysisFailure(caseId, 200, entry.callRef()).id();
        assertEquals(continuation, staff.analysisFailure(caseId, 200, entry.callRef()).id());
        assertTrue(reviews.blocked(entry.callRef()));
        assertEquals(1, count("pf_case_review_signal"));
        assertEquals("START", staff.claim(caseId, 200, continuation, 500, null));
        staff.register(caseId, 200, continuation, 600, 550);
        staff.startReview(caseId, 200, continuation, 600);
        assertEquals("STAFF_REVIEW_REQUIRED", value("status"));
    }

    @Test
    void manualClassificationDuringModelCallWinsButSystemAssessmentRemainsAuditable() {
        Result result = run(service(text -> {
            database.update("UPDATE pf_case SET urgency = 'HIGH', urgency_source = 'STAFF', version = version + 1 WHERE id = ?", caseId);
            return "NIEDRIG";
        }));
        assertEquals("SUCCEEDED", result.state());
        assertEquals("HIGH", value("urgency"));
        assertEquals("STAFF", value("urgency_source"));
        assertEquals("LOW", database.queryForObject("SELECT system_urgency FROM pf_case_analysis", String.class));
        assertEquals(0, count("pf_urgency_event"));
        assertEquals(0, count("pf_urgency_mail_order"));
    }

    @Test
    void harmlessVersionChangeDoesNotInvalidateAnalysisAndCriticalResultCreatesMandatoryReason() {
        Result result = run(service(text -> {
            database.update("UPDATE pf_case SET version = version + 1 WHERE id = ?", caseId);
            return "KRITISCH";
        }));
        assertEquals("SUCCEEDED", result.state());
        assertEquals("CRITICAL", value("urgency"));
        assertEquals(1, count("pf_case_review_signal"));
        assertEquals("CRITICAL_URGENCY", database.queryForObject("SELECT reason FROM pf_case_review_signal", String.class));
    }

    @Test
    void criticalSystemEvidenceRequiresReviewEvenWithLowerManualClassification() {
        database.update("UPDATE pf_case SET urgency = 'LOW', urgency_source = 'STAFF' WHERE id = ?", caseId);
        Result result = run(service(text -> "KRITISCH"));
        assertEquals("SUCCEEDED", result.state());
        assertEquals("LOW", value("urgency"));
        assertEquals("CRITICAL", database.queryForObject("SELECT system_urgency FROM pf_case_analysis", String.class));
        assertEquals(1, count("pf_case_review_signal"));
        assertEquals(0, count("pf_urgency_mail_order"));
    }

    @Test
    void changedAnalysisBasisRejectsOldResultAndAllowsOneCurrentAnalysis() {
        Result stale = run(service(text -> {
            database.update("UPDATE pf_case SET analysis_version = analysis_version + 1 WHERE id = ?", caseId);
            return "NIEDRIG";
        }));
        assertEquals("SUPERSEDED", stale.state());
        assertEquals(0, count("pf_urgency_event"));
        assertEquals("SUCCEEDED", run(service(text -> "HOCH")).state());
        assertEquals(2, count("pf_case_analysis"));
        assertEquals("HIGH", value("urgency"));
    }

    @Test
    void closedCaseOrWithdrawnAutomaticPermissionCannotReceiveLateEffects() {
        Result result = run(service(text -> {
            database.update("UPDATE pf_automatic_entry SET effects_blocked = TRUE WHERE call_ref = ?", entry.callRef());
            return "KRITISCH";
        }));
        assertEquals("SUPERSEDED", result.state());
        assertEquals(0, count("pf_urgency_event"));
        database.update("UPDATE pf_case SET status = 'CLOSED' WHERE id = ?", caseId);
        assertEquals("SUPERSEDED", run(service(text -> {
            throw new AssertionError("Closed cases must not call the model");
        })).state());
        assertEquals(0, count("pf_case_review_signal"));
    }

    @Test
    void lostAttemptCannotOverwriteTheNewLeaseAndConcurrentDeliveryDoesNotStartAnotherCall() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CaseAnalysisService slow = service(text -> {
            started.countDown();
            try {
                assertTrue(release.await(10, TimeUnit.SECONDS));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            return "NIEDRIG";
        });
        try (ExecutorService threads = Executors.newSingleThreadExecutor()) {
            Future<Result> late = threads.submit(() -> run(slow));
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS));
                assertEquals("BUSY", run(service(text -> {
                    throw new AssertionError("Concurrent delivery must reuse the running assessment");
                })).state());
                database.update("UPDATE pf_case_analysis SET started_at = CURRENT_TIMESTAMP - INTERVAL '3 minutes'");
                assertEquals("SUCCEEDED", run(service(text -> "HOCH")).state());
            } finally {
                release.countDown();
            }
            assertEquals("SUPERSEDED", late.get(5, TimeUnit.SECONDS).state());
        }
        assertEquals("HIGH", value("urgency"));
        assertEquals(1, count("pf_urgency_event"));
        assertFalse(database.queryForObject("SELECT error_code IS NOT NULL FROM pf_case_analysis", Boolean.class));
    }

    private CaseAnalysisService service(AnalyzeTool.AnalysisModel model) {
        return new CaseAnalysisService(workflow, reviews, analyses, urgency, new AnalyzeTool(model), transactions);
    }

    private Result run(CaseAnalysisService service) {
        return service.analyze(caseId, entry.id(), entry.callRef(), 200, 400, 450);
    }

    private String value(String column) {
        return database.queryForObject("SELECT " + column + " FROM pf_case WHERE id = ?", String.class, caseId);
    }

    private int count(String table) {
        return database.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
