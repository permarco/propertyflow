package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.access.contract.SubmissionContext;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.caseprocessing.contract.CaseCreation;
import com.pertegato.propertyflow.caseprocessing.contract.CreateCaseCommand;
import com.pertegato.propertyflow.caseprocessing.persistence.AutomaticClosureStore;
import com.pertegato.propertyflow.caseprocessing.persistence.AutomaticQuestionStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseClosureStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.Entry;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.StartOrder;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import com.pertegato.propertyflow.caseprocessing.persistence.ConversationEvaluationStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffChangeStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffReviewStore;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantMessageStore;
import com.pertegato.propertyflow.caseprocessing.persistence.UrgencyStore;
import com.pertegato.propertyflow.recommendation.AutomaticClosureFixtures;
import com.pertegato.propertyflow.recommendation.service.ClosureAssessment;
import com.pertegato.propertyflow.recommendation.service.ClosureMessage;
import com.pertegato.propertyflow.recommendation.service.Recommendation;
import com.pertegato.propertyflow.triage.service.ConversationAnalysis;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "propertyflow.workflow.enabled=false")
@ActiveProfiles("propertyflow-backend")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_DATABASE_URL", matches = ".+")
class AutomaticClosurePostgresTest {

    @Autowired
    private CaseCreation creation;
    @Autowired
    private CaseWorkflowStore workflow;
    @Autowired
    private CaseWorkflowService initial;
    @Autowired
    private ConversationEvaluationStore evaluations;
    @Autowired
    private TenantMessageStore messages;
    @Autowired
    private UrgencyStore urgency;
    @Autowired
    private AutomaticQuestionStore questions;
    @Autowired
    private AutomaticClosureStore commands;
    @Autowired
    private CaseClosureStore closures;
    @Autowired
    private StaffReviewStore reviews;
    @Autowired
    private PlatformTransactionManager transactions;
    @Autowired
    private JdbcTemplate database;
    @Autowired
    private StaffCaseService reading;

    private final AutomaticClosureFixtures.Release release = new AutomaticClosureFixtures.Release();
    private final ClosureAssessment assessment = AutomaticClosureFixtures.assessment();
    private final AtomicInteger generated = new AtomicInteger();
    private String caseId;
    private Entry entry;
    private AutomaticClosureService service;
    private ConversationEvaluationService central;

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
        caseId = creation.create(new SubmissionContext.Guest(UUID.randomUUID()), new CreateCaseCommand("closure", "Heizung", "Wohnung",
                "Die Heizungsstörung ist vollständig behoben. Ich benötige keine weitere Hilfe.", "closure@example.test")).caseId();
        StartOrder order = workflow.claim(100, "<default>", Duration.ofMinutes(2)).orElseThrow();
        initial.register(caseId, order.id(), 200, 100, "<default>");
        entry = initial.prepareInitial(caseId, 200, 300);
        initial.registerAutomatic(caseId, entry.id(), entry.callRef(), 200, 400, 350);
        initial.startClarification(caseId, entry.id(), entry.callRef(), 200, 400);
        database.update("UPDATE pf_case SET assigned_unit = 'Wohnung A' WHERE id = ?", caseId);
        central = new ConversationEvaluationService(workflow, messages, evaluations, urgency,
                input -> new ConversationAnalysis.Result("LOW", false, List.of(), null, "fixture", "v1"),
                input -> new Recommendation.Result("Verwaltung", "Prüfen", List.of(), List.of(), false, null, "fixture", "v1"),
                assessment, transactions, release);
        service = service((context, ground) -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertFalse(context.toString().contains("closure@example.test"));
            generated.incrementAndGet();
            return AutomaticClosureFixtures.message();
        }, release);
    }

    @Test
    void publishesExactlyOnceAndClosesWithSharedCleanupAndSystemAudit() {
        UUID command = prepare();
        assertEquals("PUBLISHED", service.publish(command, 200, 400).state());
        assertEquals("PUBLISHED", service.publish(command, 200, 400).state());
        assertEquals("CLOSED", service.close(command, 200, 400).state());
        release.enabled = false;
        assertEquals("CLOSED", service.close(command, 200, 400).state());
        assertEquals("CLOSED", service.finish(command, 200, 400).state());
        assertEquals(1, generated.get());
        assertEquals(1, count("pf_staff_message"));
        assertEquals(1, count("pf_staff_message_mail_order"));
        assertEquals(1, count("pf_case_closure"));
        assertEquals("SYSTEM", database.queryForObject("SELECT source FROM pf_case_closure", String.class));
        assertEquals("System", new StaffChangeStore(database).history(caseId).getFirst().actorName());
        assertTrue(new StaffChangeStore(database).history(caseId).getFirst().description().contains("Quelle: System"));
        assertTrue(reviews.reasons(caseId).contains("Systemabschluss mit gespeicherter Abschlussgrundlage."));
        UUID employee = UUID.randomUUID();
        database.update("""
                INSERT INTO pf_staff_account (id, name, email, active, staff_role, account_administration)
                VALUES (?, 'Prüfer', ?, TRUE, TRUE, FALSE)
                """, employee, employee + "@example.test");
        assertEquals("System", reading.readForStaff(new WebAccountContext(employee), caseId).internalChanges().getFirst().actorName());
        assertEquals(0, count("pf_staff_answer_requirement"));
        assertEquals(1, count("pf_case_mail_order"));
        assertEquals("CLOSED", workflow.lockCase(caseId).orElseThrow().status());
    }

    @Test
    void productionGateDeniesEvenAFormallyValidStoredProposal() {
        UUID command = prepare();
        AutomaticClosureService denied = service((context, ground) -> {
            throw new AssertionError("Die Produktionssperre muss vor dem Modellaufruf greifen.");
        }, new AutomaticClosureRelease());
        assertEquals("REVIEW", denied.publish(command, 200, 400).state());
        assertEquals("REVIEW", denied.finish(command, 200, 400).state());
        assertEquals("REVIEW", denied.finish(command, 200, 400).state());
        assertEquals(1, count("pf_staff_continuation"));
        assertEquals(0, count("pf_staff_message"));
        assertEquals(0, count("pf_case_closure"));
    }

    @Test
    void changedBasisBeforePublicationDoesNotCreateReviewOrEffects() {
        UUID command = prepare();
        change();
        assertEquals("STALE", service.publish(command, 200, 400).state());
        assertEquals(0, generated.get());
        assertEquals(0, count("pf_case_review_signal"));
        assertEquals(0, count("pf_staff_message"));
    }

    @Test
    void staleClosureKeepsPublicationAndReusesItAfterReevaluation() {
        UUID command = prepare();
        service.publish(command, 200, 400);
        change();
        assertEquals("STALE", service.close(command, 200, 400).state());
        UUID next = prepare();
        assertFalse(command.equals(next));
        assertEquals("PUBLISHED", service.publish(next, 200, 400).state());
        assertEquals("CLOSED", service.close(next, 200, 400).state());
        assertEquals(1, generated.get());
        assertEquals(1, count("pf_staff_message"));
        assertEquals(1, count("pf_staff_message_mail_order"));
        assertEquals(2, count("pf_automatic_closure"));
    }

    @Test
    void gateWithdrawalAfterPublicationKeepsMessageAndBlocksClosure() {
        UUID command = prepare();
        service.publish(command, 200, 400);
        release.enabled = false;
        assertEquals("STALE", service.close(command, 200, 400).state());
        assertEquals(0, count("pf_case_closure"));
        assertEquals(1, count("pf_staff_message_mail_order"));
        assertEquals(1, count("pf_case_review_signal"));
    }

    @Test
    void invalidContentRoutesToReviewWithoutSavingADraft() {
        UUID command = prepare();
        AutomaticClosureService invalid = service((context, ground) -> new ClosureMessage.Result(null, false, "fixture", "v1", "v1"), release);
        assertEquals("REVIEW", invalid.publish(command, 200, 400).state());
        assertEquals("REVIEW", invalid.finish(command, 200, 400).state());
        assertEquals(0, count("pf_closure_publication"));
        assertEquals(0, count("pf_staff_message_mail_order"));
        assertEquals(1, count("pf_staff_continuation"));
    }

    @Test
    void technicalFailureCanRetryImmediatelyWithoutDuplicatePublication() {
        UUID command = prepare();
        AutomaticClosureService failing = service((context, ground) -> {
            throw new IllegalStateException("MODEL_TIMEOUT");
        }, release);
        assertThrows(IllegalStateException.class, () -> failing.publish(command, 200, 400));
        assertEquals("PREPARED", commands.get(command).state());
        assertEquals("PUBLISHED", service.publish(command, 200, 400).state());
        assertEquals(0, count("pf_case_review_signal"));
        assertEquals(1, count("pf_staff_message_mail_order"));
    }

    @Test
    void concurrentGenerationIsLeasedAndNewMessageInvalidatesTheResult() throws Exception {
        UUID command = prepare();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch complete = new CountDownLatch(1);
        AutomaticClosureService slow = service((context, ground) -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            started.countDown();
            try {
                assertTrue(complete.await(10, TimeUnit.SECONDS));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            return AutomaticClosureFixtures.message();
        }, release);
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<AutomaticQuestionService.Result> pending = executor.submit(() -> slow.publish(command, 200, 400));
            try {
                assertTrue(started.await(10, TimeUnit.SECONDS));
                assertEquals("BUSY", service.publish(command, 200, 400).state());
                UUID access = database.queryForObject("SELECT id FROM pf_case_access WHERE case_id = ?", UUID.class, caseId);
                new TransactionTemplate(transactions).executeWithoutResult(status -> {
                    workflow.lockCase(caseId).orElseThrow();
                    messages.save(new TenantMessageStore.Access(access, caseId), "new", "Die Heizung ist wieder kalt.", 200, "<default>");
                });
            } finally {
                complete.countDown();
            }
            assertEquals("STALE", pending.get(10, TimeUnit.SECONDS).state());
        }
        assertEquals(0, count("pf_staff_message"));
        assertEquals(0, count("pf_case_closure"));
    }

    @Test
    void oldLeaderAndForeignChildCannotPublishOrConfirm() {
        UUID command = prepare();
        assertThrows(WorkflowRejectedException.class, () -> service.publish(command, 200, 999));
        assertThrows(WorkflowRejectedException.class, () -> service.publish(command, 999, 400));
        service.publish(command, 200, 400);
        service.close(command, 200, 400);
        database.update("UPDATE pf_case_process SET process_instance_key = 999 WHERE case_id = ?", caseId);
        assertThrows(WorkflowRejectedException.class, () -> service.finish(command, 200, 400));
        assertEquals(1, count("pf_staff_message"));
    }

    @Test
    void staffPriorityDuringGenerationPreventsPublication() {
        UUID command = prepare();
        AutomaticClosureService competing = service((context, ground) -> {
            database.update("UPDATE pf_case SET status = 'IN_PROGRESS', version = version + 1 WHERE id = ?", caseId);
            return AutomaticClosureFixtures.message();
        }, release);
        assertEquals("OBSOLETE", competing.publish(command, 200, 400).state());
        assertEquals(0, count("pf_staff_message"));
    }

    @Test
    void storageFailureRollsBackMessageMailAndPublicationTogether() {
        UUID command = prepare();
        AutomaticClosureStore failing = new AutomaticClosureStore(database) {
            @Override
            public void publish(Command pending, ClosureMessage.Result message) {
                super.publish(pending, message);
                throw new IllegalStateException("COMMIT_SIMULATION_FAILED");
            }
        };
        AutomaticClosureService broken = new AutomaticClosureService(workflow, evaluations, questions, failing, closures, reviews,
                assessment, (context, ground) -> AutomaticClosureFixtures.message(), release, transactions);
        assertThrows(IllegalStateException.class, () -> broken.publish(command, 200, 400));
        assertEquals(0, count("pf_staff_message"));
        assertEquals(0, count("pf_staff_message_mail_order"));
        assertEquals(0, count("pf_closure_publication"));
        database.update("UPDATE pf_automatic_closure SET started_at = CURRENT_TIMESTAMP - INTERVAL '4 minutes' WHERE id = ?", command);
        assertEquals("PUBLISHED", service.publish(command, 200, 400).state());
        assertEquals(1, count("pf_staff_message_mail_order"));
    }

    @Test
    void internalMemoAndPrivateActorDataAreExcludedFromTheGeneratedContext() {
        UUID memo = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        database.update("""
                INSERT INTO pf_case_data_command (id, operation_id, case_id, actor_id, action_type, payload,
                    expected_version, leading_instance_key, tenant_id, state)
                VALUES (?, ?, ?, ?, 'ADD_MEMO', 'MEMO_SECRET', 0, 200, '<default>', 'APPLIED')
                """, memo, memo, caseId, actor);
        database.update("INSERT INTO pf_internal_memo (id, case_id, actor_id, text) VALUES (?, ?, ?, 'MEMO_SECRET')", memo, caseId, actor);
        UUID command = prepare();
        AutomaticClosureService checked = service((context, ground) -> {
            assertFalse(context.toString().contains("MEMO_SECRET"));
            assertFalse(context.toString().contains(actor.toString()));
            assertFalse(ground.toString().contains("MEMO_SECRET"));
            return AutomaticClosureFixtures.message();
        }, release);
        assertEquals("PUBLISHED", checked.publish(command, 200, 400).state());
    }

    @Test
    void independentMandatoryReviewReasonPreventsAnOtherwiseCurrentPublication() {
        UUID command = prepare();
        database.update("UPDATE pf_case SET assigned_unit = NULL WHERE id = ?", caseId);
        assertEquals("REVIEW", service.publish(command, 200, 400).state());
        assertEquals(0, generated.get());
        assertEquals(0, count("pf_staff_message"));
    }

    @Test
    void acceptedStaffCommandHasPriorityBeforePublicationAndClosure() {
        UUID command = prepare();
        UUID staff = UUID.randomUUID();
        database.update("""
                INSERT INTO pf_case_data_command (id, operation_id, case_id, actor_id, action_type, payload,
                    expected_version, leading_instance_key, tenant_id)
                VALUES (?, ?, ?, ?, 'TAKEOVER', '', ?, 200, '<default>')
                """, staff, staff, caseId, UUID.randomUUID(), workflow.lockCase(caseId).orElseThrow().version());
        assertEquals("BUSY", service.publish(command, 200, 400).state());
        assertEquals(0, generated.get());
        database.update("UPDATE pf_case_data_command SET state = 'REJECTED' WHERE id = ?", staff);
        assertEquals("PUBLISHED", service.publish(command, 200, 400).state());
        database.update("UPDATE pf_case_data_command SET state = 'PENDING' WHERE id = ?", staff);
        assertEquals("BUSY", service.close(command, 200, 400).state());
        assertEquals(0, count("pf_case_closure"));
        assertEquals(0, count("pf_case_review_signal"));
    }

    private UUID prepare() {
        ConversationEvaluationService.Result evaluated = central.evaluateAutomatic(caseId, entry.id(), entry.callRef(), 200, 400);
        central.applyAutomatic(caseId, entry.id(), entry.callRef(), 200, 400, evaluated.evaluationId());
        AutomaticQuestionService validator = new AutomaticQuestionService(workflow, evaluations, questions, reviews, service);
        AutomaticQuestionService.Result result = new TransactionTemplate(transactions).execute(status ->
                validator.validate(caseId, entry.id(), entry.callRef(), 200, 400, evaluated.evaluationId()));
        assertEquals("CLOSE", result.state());
        return result.reference();
    }

    private AutomaticClosureService service(ClosureMessage communication, AutomaticClosureRelease gate) {
        return new AutomaticClosureService(workflow, evaluations, questions, commands, closures, reviews,
                assessment, communication, gate, transactions);
    }

    private void change() {
        database.update("UPDATE pf_case SET version = version + 1, analysis_version = analysis_version + 1 WHERE id = ?", caseId);
    }

    private int count(String table) {
        return database.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }
}
