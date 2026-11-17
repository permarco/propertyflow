package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.access.contract.SubmissionContext;
import com.pertegato.propertyflow.caseprocessing.contract.CaseCreation;
import com.pertegato.propertyflow.caseprocessing.contract.CreateCaseCommand;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.Entry;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.StartOrder;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import com.pertegato.propertyflow.caseprocessing.persistence.ConversationEvaluationStore;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantMessageStore;
import com.pertegato.propertyflow.caseprocessing.persistence.UrgencyStore;
import com.pertegato.propertyflow.caseprocessing.service.ConversationEvaluationService.Result;
import com.pertegato.propertyflow.recommendation.service.ClosureAssessment;
import com.pertegato.propertyflow.recommendation.service.Recommendation;
import com.pertegato.propertyflow.triage.service.ConversationAnalysis.Answer;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static com.pertegato.propertyflow.recommendation.ClosureAssessmentFixtures.noClosure;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "propertyflow.workflow.enabled=false")
@ActiveProfiles("propertyflow-backend")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_DATABASE_URL", matches = ".+")
class ConversationEvaluationPostgresTest {

    @Autowired
    private CaseCreation creation;
    @Autowired
    private CaseWorkflowStore workflow;
    @Autowired
    private CaseWorkflowService initial;
    @Autowired
    private TenantMessageStore messages;
    @Autowired
    private ConversationEvaluationStore evaluations;
    @Autowired
    private UrgencyStore urgency;
    @Autowired
    private PlatformTransactionManager transactions;
    @Autowired
    private JdbcTemplate database;

    private String caseId;
    private UUID messageId;
    private UUID question;
    private UUID otherQuestion;

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
                new CreateCaseCommand("evaluation", "Heizung", "Wohnung", "Die Heizung bleibt kalt.", "secret@example.test")).caseId();
        StartOrder order = workflow.claim(100, "<default>", Duration.ofMinutes(2)).orElseThrow();
        initial.register(caseId, order.id(), 200, 100, "<default>");
        Entry entry = initial.prepareInitial(caseId, 200, 300);
        initial.registerAutomatic(caseId, entry.id(), entry.callRef(), 200, 400, 350);
        initial.startClarification(caseId, entry.id(), entry.callRef(), 200, 400);
        question = question("Seit wann?", 1);
        otherQuestion = question("Welcher Raum?", 2);
        database.update("UPDATE pf_case SET analysis_version = 2 WHERE id = ?", caseId);
        UUID access = database.queryForObject("SELECT id FROM pf_case_access WHERE case_id = ?", UUID.class, caseId);
        messageId = messages.save(new TenantMessageStore.Access(access, caseId), "answer", "Seit gestern.", 200, "<default>").id();
        messages.receive(messageId, 500, 450);
    }

    @Test
    void automaticAndMessagePathsShareOneEvaluationAndRejectOutdatedConsumption() {
        AtomicInteger calls = new AtomicInteger();
        ConversationEvaluationService service = service(input -> {
            calls.incrementAndGet();
            return success("HIGH");
        }, advice());
        Entry entry = workflow.entryForCase(caseId).orElseThrow();
        Result automatic = service.evaluateAutomatic(caseId, entry.id(), entry.callRef(), 200, 400);
        Result message = evaluate(service);
        assertEquals(automatic.evaluationId(), message.evaluationId());
        assertEquals("APPLIED", apply(service, message).state());
        assertEquals("APPLIED", service.applyAutomatic(caseId, entry.id(), entry.callRef(), 200, 400, automatic.evaluationId()).state());
        assertEquals("CURRENT", service.automaticResult(caseId, entry.id(), entry.callRef(), 200, 400, automatic.evaluationId()));
        assertEquals(1, calls.get());
        database.update("UPDATE pf_case SET version = version + 1, analysis_version = analysis_version + 1 WHERE id = ?", caseId);
        assertEquals("STALE", service.automaticResult(caseId, entry.id(), entry.callRef(), 200, 400, automatic.evaluationId()));
        database.update("UPDATE pf_automatic_entry SET effects_blocked = TRUE WHERE id = ?", entry.id());
        assertEquals("OBSOLETE", service.evaluateAutomatic(caseId, entry.id(), entry.callRef(), 200, 400).state());
        assertEquals(1, calls.get());
    }

    @Test
    void partialAnswerIsAtomicAuditableAndIdempotent() {
        AtomicInteger calls = new AtomicInteger();
        ConversationEvaluationService service = service(input -> {
            calls.incrementAndGet();
            assertEquals(4, input.messages().size());
            assertEquals(2, input.questions().size());
            assertFalse(input.messages().toString().contains("secret@example.test"));
            return success("HIGH");
        }, advice());
        Result result = evaluate(service);
        assertEquals("READY", result.state());
        assertEquals("OPEN", answerState(question));
        assertEquals("APPLIED", apply(service, result).state());
        assertEquals("ANSWERED", answerState(question));
        assertEquals("OPEN", answerState(otherQuestion));
        assertEquals(messageId, database.queryForObject("SELECT answered_message_id FROM pf_staff_answer_requirement WHERE message_id = ?",
                UUID.class, question));
        assertEquals("HIGH", value("urgency"));
        assertEquals(1, count("pf_urgency_mail_order"));
        assertEquals(1, count("pf_evaluation_delivery"));
        assertEquals("APPLIED", evaluate(service).state());
        assertEquals("APPLIED", apply(service, result).state());
        assertEquals(1, calls.get());
        assertEquals(1, count("pf_urgency_event"));
    }

    @Test
    void currentStaffReviewAndManualUrgencySurviveResolvedAnswer() {
        database.update("UPDATE pf_case SET status = 'STAFF_REVIEW_REQUIRED', urgency = 'LOW', urgency_source = 'STAFF' WHERE id = ?", caseId);
        ConversationEvaluationService service = service(input -> success("CRITICAL"), advice());
        apply(service, evaluate(service));
        assertEquals("STAFF_REVIEW_REQUIRED", value("status"));
        assertEquals("LOW", value("urgency"));
        assertEquals(0, count("pf_urgency_event"));
        assertEquals("CRITICAL_URGENCY", database.queryForObject("SELECT reason FROM pf_case_review_signal", String.class));
        assertEquals(0, count("pf_staff_continuation"));
    }

    @Test
    void modelAndKnowledgeFailuresPreserveAnswersAndUrgency() {
        database.update("UPDATE pf_case SET urgency = 'HIGH', urgency_source = 'SYSTEM' WHERE id = ?", caseId);
        ConversationEvaluationService service = service(input -> success("LOW"), context ->
                new Recommendation.Result(null, null, List.of(), List.of(), true, "APPROVED_KNOWLEDGE_MISSING", "test", "v1"));
        apply(service, evaluate(service));
        assertEquals("HIGH", value("urgency"));
        assertEquals("OPEN", answerState(question));
        assertEquals("ANALYSIS_FAILED", database.queryForObject("SELECT reason FROM pf_case_review_signal", String.class));
        assertEquals(0, count("pf_urgency_mail_order"));
    }

    @Test
    void staleSuccessAndFailureCauseNoEffectsAndUseCurrentSharedEvaluation() {
        ConversationEvaluationService service = service(input -> success("HIGH"), advice());
        Result old = evaluate(service);
        database.update("UPDATE pf_case SET version = version + 1, analysis_version = analysis_version + 1 WHERE id = ?", caseId);
        assertEquals("STALE", apply(service, old).state());
        assertEquals("OPEN", answerState(question));
        assertEquals(0, count("pf_case_review_signal"));
        Result current = evaluate(service);
        assertFalse(old.evaluationId().equals(current.evaluationId()));
        assertEquals("APPLIED", apply(service, current).state());
    }

    @Test
    void closureDuringModelCallPreventsLateEffects() {
        ConversationEvaluationService service = service(input -> {
            database.update("UPDATE pf_case SET status = 'CLOSED', version = version + 1 WHERE id = ?", caseId);
            return success("CRITICAL");
        }, advice());
        assertEquals("OBSOLETE", evaluate(service).state());
        assertEquals("CLOSED", value("status"));
        assertEquals("OPEN", answerState(question));
        assertEquals(0, count("pf_case_review_signal"));
        assertEquals(0, count("pf_urgency_event"));
    }

    @Test
    void concurrentRequestsShareOneExternalCallWithoutHoldingCaseLock() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        ConversationEvaluationService service = service(input -> {
            calls.incrementAndGet();
            entered.countDown();
            try {
                assertTrue(release.await(10, TimeUnit.SECONDS));
            } catch (InterruptedException exception) {
                throw new IllegalStateException(exception);
            }
            return success("NORMAL");
        }, advice());
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<Result> first = executor.submit(() -> evaluate(service));
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            try {
                assertEquals("BUSY", evaluate(service).state());
                database.update("UPDATE pf_case SET title = 'Neuer Titel' WHERE id = ?", caseId);
            } finally {
                release.countDown();
            }
            assertEquals("READY", first.get(10, TimeUnit.SECONDS).state());
        }
        assertEquals(1, calls.get());
    }

    @Test
    void closureProposalIsSharedPersistedAndNeverAuthorizesAnAutomaticEffect() {
        AtomicInteger calls = new AtomicInteger();
        ClosureAssessment.Result proposal = proposal();
        ConversationEvaluationService service = service(input -> success("NORMAL"), advice(), context -> {
            calls.incrementAndGet();
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(4, context.size());
            assertFalse(context.toString().contains("secret@example.test"));
            return proposal;
        });
        Result first = evaluate(service);
        Entry entry = workflow.entryForCase(caseId).orElseThrow();
        assertEquals(first.evaluationId(), service.evaluateAutomatic(caseId, entry.id(), entry.callRef(), 200, 400).evaluationId());
        assertEquals(proposal, evaluations.get(first.evaluationId()).outcome().closureAssessment());
        assertEquals("APPLIED", apply(service, first).state());
        assertEquals("APPLIED", apply(service, first).state());
        assertEquals(1, calls.get());
        assertEquals("UNDER_CLARIFICATION", value("status"));
        assertEquals("HUMAN_DECISION_REQUIRED", database.queryForObject("SELECT reason FROM pf_case_review_signal", String.class));
        assertEquals(1, count("pf_case_review_signal"));
        assertEquals(2, count("pf_staff_message"));
        assertEquals(0, count("pf_case_closure"));
        String projected = evaluations.recommendation(caseId, workflow.lockCase(caseId).orElseThrow().version())
                .orElseThrow().closureAssessment();
        assertTrue(projected.contains("Vorgeschlagener Abschlussgrund: Problem behoben."));
        assertTrue(projected.contains("Bitte anhand des gesamten Fallverlaufs prüfen."));
        assertTrue(projected.contains("Seit gestern."));
        assertFalse(projected.contains("fixture-validation-v1"));
    }

    @Test
    void closureFailurePreservesValidAnswerFindingsAndRequestsReview() {
        ConversationEvaluationService service = service(input -> success("HIGH"), advice(), context ->
                new ClosureAssessment.Result(ClosureAssessment.Outcome.REVIEW, null, null, List.of(), "",
                        "CLOSURE_ASSESSMENT_UNAVAILABLE", "test", "fixture-v1", "fixture-validation-v1"));
        Result result = evaluate(service);
        assertEquals("APPLIED", apply(service, result).state());
        assertEquals("ANSWERED", answerState(question));
        assertEquals("OPEN", answerState(otherQuestion));
        assertEquals("HIGH", value("urgency"));
        assertEquals("ANALYSIS_FAILED", database.queryForObject("SELECT reason FROM pf_case_review_signal", String.class));
        assertEquals("CLOSURE_ASSESSMENT_UNAVAILABLE", evaluations.get(result.evaluationId()).outcome().closureAssessment().failure());
        assertEquals("Die Abschlussbewertung ist nicht verfügbar. Bitte beurteilen Sie den Abschluss selbst.",
                evaluations.recommendation(caseId, workflow.lockCase(caseId).orElseThrow().version()).orElseThrow().closureAssessment());
    }

    @Test
    void newerCaseBasisDiscardsClosureProposalBeforePersistence() {
        ConversationEvaluationService service = service(input -> success("NORMAL"), advice(), context -> {
            database.update("UPDATE pf_case SET version = version + 1, analysis_version = analysis_version + 1 WHERE id = ?", caseId);
            return proposal();
        });
        Result result = evaluate(service);
        assertEquals("STALE", result.state());
        assertEquals("SUPERSEDED", evaluations.get(result.evaluationId()).state());
        assertNull(evaluations.get(result.evaluationId()).outcome());
        assertEquals(0, count("pf_case_review_signal"));
        assertEquals("OPEN", answerState(question));
    }

    @Test
    void closureDuringAssessmentDiscardsLateProposal() {
        ConversationEvaluationService service = service(input -> success("NORMAL"), advice(), context -> {
            database.update("UPDATE pf_case SET status = 'CLOSED', version = version + 1 WHERE id = ?", caseId);
            return proposal();
        });
        Result result = evaluate(service);
        assertEquals("OBSOLETE", result.state());
        assertNull(evaluations.get(result.evaluationId()).outcome());
        assertEquals("CLOSED", value("status"));
        assertEquals(0, count("pf_case_review_signal"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"STAFF_REVIEW_REQUIRED", "IN_PROGRESS", "WAITING_FOR_FOLLOW_UP", "WAITING_FOR_EXTERNAL"})
    void manualAndWaitingPhasesDoNotRequestAnAutomaticClosureAssessment(String phase) {
        database.update("UPDATE pf_case SET status = ? WHERE id = ?", phase, caseId);
        ConversationEvaluationService service = service(input -> success("NORMAL"), advice(), context -> {
            throw new AssertionError("In dieser Phase darf keine Abschlussprüfung gestartet werden.");
        });
        Result result = evaluate(service);
        assertEquals("READY", result.state());
        assertNull(evaluations.get(result.evaluationId()).outcome().closureAssessment());
    }

    @Test
    void criticalAnalysisDoesNotRequestClosureAssessment() {
        ConversationEvaluationService service = service(input -> success("CRITICAL"), advice(), context -> {
            throw new AssertionError("Zwingende Mitarbeiterprüfung hat Vorrang vor dem Abschlussvorschlag.");
        });
        Result result = evaluate(service);
        assertNull(evaluations.get(result.evaluationId()).outcome().closureAssessment());
        assertEquals("APPLIED", apply(service, result).state());
        assertEquals("CRITICAL_URGENCY", database.queryForObject("SELECT reason FROM pf_case_review_signal", String.class));
    }

    @Test
    void earlierStoredEvaluationWithoutClosureFieldRemainsReadableAndApplicable() {
        ConversationEvaluationService service = service(input -> success("NORMAL"), advice());
        Result result = evaluate(service);
        database.update("UPDATE pf_conversation_evaluation SET result_json = (result_json::jsonb - 'closureAssessment')::text WHERE id = ?",
                result.evaluationId());
        assertNull(evaluations.get(result.evaluationId()).outcome().closureAssessment());
        assertEquals("APPLIED", apply(service, result).state());
        assertEquals(0, count("pf_case_review_signal"));
    }

    private ClosureAssessment.Result proposal() {
        return new ClosureAssessment.Result(ClosureAssessment.Outcome.RESOLVED, null, null,
                List.of(new ClosureAssessment.Proof("WHOLE_CASE_COVERED", messageId, "Seit gestern.")),
                "Synthetischer Vorschlag für den Koordinationstest", null, "test", "fixture-v1", "fixture-validation-v1");
    }

    private ConversationAnalysis.Result success(String level) {
        return new ConversationAnalysis.Result(level, false, List.of(new Answer(question, messageId, "Seit gestern")), null, "test", "v1");
    }

    private Recommendation advice() {
        return context -> new Recommendation.Result("Verwaltung", "Prüfen", List.of(), List.of(), false, null, "test", "v1");
    }

    private ConversationEvaluationService service(ConversationAnalysis analyzer, Recommendation recommendation) {
        return service(analyzer, recommendation, noClosure());
    }

    private ConversationEvaluationService service(ConversationAnalysis analyzer, Recommendation recommendation, ClosureAssessment closure) {
        return new ConversationEvaluationService(workflow, messages, evaluations, urgency, analyzer, recommendation, closure, transactions,
                new AutomaticClosureRelease());
    }

    private Result evaluate(ConversationEvaluationService service) {
        return service.evaluate(caseId, messageId, 200, 500, "<default>");
    }

    private Result apply(ConversationEvaluationService service, Result result) {
        return service.apply(caseId, messageId, 200, 500, "<default>", result.evaluationId());
    }

    private String value(String column) {
        return database.queryForObject("SELECT " + column + " FROM pf_case WHERE id = ?", String.class, caseId);
    }

    private int count(String table) {
        return database.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private String answerState(UUID id) {
        return database.queryForObject("SELECT state FROM pf_staff_answer_requirement WHERE message_id = ?", String.class, id);
    }

    private UUID question(String text, long sequence) {
        UUID id = UUID.randomUUID();
        database.update("""
                INSERT INTO pf_staff_message_command (id, case_id, operation_id, actor_id, expected_version, leading_instance_key,
                    tenant_id, target_instance_key, body, answer_required, state)
                VALUES (?, ?, ?, ?, 1, 200, '<default>', 200, ?, TRUE, 'PUBLISHED')
                """, id, caseId, id.toString(), UUID.randomUUID(), text);
        database.update(
                "INSERT INTO pf_staff_message (id, staff_command_id, case_id, body, answer_required, analysis_version) VALUES (?, ?, ?, ?, TRUE, ?)",
                id, id, caseId, text, sequence);
        database.update("INSERT INTO pf_staff_answer_requirement (message_id, case_id) VALUES (?, ?)", id, caseId);
        return id;
    }
}
