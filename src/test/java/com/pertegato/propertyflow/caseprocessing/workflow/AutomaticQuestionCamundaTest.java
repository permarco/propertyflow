package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.access.contract.StaffAccountAccess;
import com.pertegato.propertyflow.access.contract.SubmissionContext;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.caseprocessing.contract.CaseAccepted;
import com.pertegato.propertyflow.caseprocessing.contract.CaseCreation;
import com.pertegato.propertyflow.caseprocessing.contract.CaseReading;
import com.pertegato.propertyflow.caseprocessing.contract.CreateCaseCommand;
import com.pertegato.propertyflow.caseprocessing.contract.PublicAuthor;
import com.pertegato.propertyflow.caseprocessing.contract.StaffCaseDataCommands.ClosureApproval;
import com.pertegato.propertyflow.caseprocessing.persistence.AnswerRequirementStore;
import com.pertegato.propertyflow.caseprocessing.persistence.AutomaticQuestionStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseDataStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWaitStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.StartOrder;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import com.pertegato.propertyflow.caseprocessing.persistence.ClarificationQuestionStore;
import com.pertegato.propertyflow.caseprocessing.persistence.ConversationEvaluationStore;
import com.pertegato.propertyflow.caseprocessing.persistence.DirectFollowUpStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffChangeStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffDecisionStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffMessageStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffReviewStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffTakeoverStore;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantMessageStore;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantReplyStore;
import com.pertegato.propertyflow.caseprocessing.persistence.UrgencyStore;
import com.pertegato.propertyflow.caseprocessing.service.AutomaticClosureRelease;
import com.pertegato.propertyflow.caseprocessing.service.AutomaticClosureService;
import com.pertegato.propertyflow.caseprocessing.service.AutomaticQuestionRoundService;
import com.pertegato.propertyflow.caseprocessing.service.AutomaticQuestionService;
import com.pertegato.propertyflow.caseprocessing.service.CaseClosureService;
import com.pertegato.propertyflow.caseprocessing.service.CaseDataService;
import com.pertegato.propertyflow.caseprocessing.service.CaseUrgencyService;
import com.pertegato.propertyflow.caseprocessing.service.CaseWaitService;
import com.pertegato.propertyflow.caseprocessing.service.CaseWorkflowService;
import com.pertegato.propertyflow.caseprocessing.service.ConversationEvaluationService;
import com.pertegato.propertyflow.caseprocessing.service.StaffMessageService;
import com.pertegato.propertyflow.caseprocessing.service.StaffReviewService;
import com.pertegato.propertyflow.caseprocessing.service.TenantReplyService;
import com.pertegato.propertyflow.caseprocessing.service.WorkflowRejectedException;
import com.pertegato.propertyflow.recommendation.service.QuestionCatalog;
import com.pertegato.propertyflow.recommendation.service.Recommendation;
import com.pertegato.propertyflow.triage.service.ConversationAnalysis;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.response.UserTask;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

import static com.pertegato.propertyflow.recommendation.ClosureAssessmentFixtures.noClosure;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "propertyflow.workflow.enabled=false")
@ActiveProfiles("propertyflow-backend")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_DATABASE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_CAMUNDA_URL", matches = ".+")
class AutomaticQuestionCamundaTest {

    @Autowired
    private CaseCreation creation;

    @Autowired
    private CaseWorkflowStore workflow;

    @Autowired
    private CaseWorkflowService initialService;

    @Autowired
    private JdbcTemplate database;

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private CaseClosureService clarificationClosures;

    @Autowired
    private CaseDataStore caseData;

    private CamundaClient engine;
    private InitialCaseWorkers initial;

    @Autowired
    private TenantMessageStore messages;

    @Autowired
    private StaffReviewService reviewService;

    @Autowired
    private CaseUrgencyService urgencyService;

    private CaseAccepted accepted;
    private String caseId;
    private Long leader;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("PROPERTYFLOW_TEST_DATABASE_URL"));
        properties.add("spring.datasource.username", () -> "propertyflow_test");
        properties.add("spring.datasource.password", () -> "propertyflow_test");
        properties.add("propertyflow.links.encryption-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
    }

    @BeforeEach
    void setup() throws Exception {
        assertEquals("propertyflow_test", database.queryForObject("SELECT current_database()", String.class));
        database.execute("TRUNCATE pf_case CASCADE");
        engine = CamundaClient.newClientBuilder().restAddress(URI.create(System.getenv("PROPERTYFLOW_TEST_CAMUNDA_URL")))
                .preferRestOverGrpc(true).defaultRequestTimeout(Duration.ofSeconds(5)).build();
        long definitionKey = engine.newDeployResourceCommand().addResourceFromClasspath("workflow/catalog-case.bpmn")
                .addResourceFromClasspath("workflow/follow-up.bpmn").addResourceFromClasspath("workflow/catalog-automatic.bpmn").addResourceFromClasspath("workflow/reply-staff.bpmn")
                .addResourceFromClasspath("workflow/conversation-processing.bpmn").addResourceFromClasspath("workflow/reply-processing.bpmn")
                .send().join().getProcesses().stream()
                .filter(process -> InitialCaseWorkers.CASE_PROCESS.equals(process.getBpmnProcessId()))
                .findFirst().orElseThrow().getProcessDefinitionKey();
        accepted = creation.create(new SubmissionContext.Guest(UUID.randomUUID()),
                new CreateCaseCommand("review", "Heizung", "Wohnung", "Kalt", "review@example.test"));
        caseId = accepted.caseId();
        StartOrder start = workflow.claim(definitionKey, "<default>", Duration.ofMinutes(2)).orElseThrow();
        assertEquals(ProcessStartGateway.StartResult.STARTED, new CamundaProcessStartGateway(engine).ensureStarted(start));
        initial = new InitialCaseWorkers(engine, initialService);
        ActivatedJob preparing = job(InitialCaseWorkers.PREPARE_KEY);
        leader = preparing.getProcessInstanceKey();
        initial.prepareKey(engine, preparing);
        initial.register(engine, job(InitialCaseWorkers.REGISTER));
        initial.prepareInitial(engine, job(InitialCaseWorkers.PREPARE_INITIAL));
        initial.registerAutomatic(engine, job(InitialCaseWorkers.REGISTER_AUTOMATIC));
        initial.clarification(engine, job(InitialCaseWorkers.CLARIFICATION));
        assertEquals("UNDER_CLARIFICATION", status());
        database.update("UPDATE pf_case SET assigned_unit = 'Wohnung A' WHERE id = ?", caseId);
    }

    @AfterEach
    void close() {
        if (engine != null) {
            if (leader != null && "ACTIVE".equals(engine.newProcessInstanceGetRequest(leader).send().join().getState().name())) {
                engine.newCancelInstanceCommand(leader).send().join();
            }
            engine.close();
        }
    }

    @Autowired
    private ConversationEvaluationStore evaluations;
    @Autowired
    private UrgencyStore urgencies;

    @Autowired
    private AutomaticQuestionService questions;
    @Autowired
    private AutomaticQuestionStore questionStore;
    @Autowired
    private StaffMessageStore staffMessages;
    @Autowired
    private TenantReplyStore replies;
    @Autowired
    private StaffAccountAccess access;
    @Autowired
    private CaseReading reading;
    private final AtomicInteger analysesCalled = new AtomicInteger();
    private final AtomicReference<List<String>> selected = new AtomicReference<>(List.of("RQ-001", "RQ-002"));

    @Test
    void catalogQuestionsPublishOnceAndEnterActualReplyWait() throws Exception {
        ConversationEvaluationService central = central();
        AutomaticQuestionWorkers worker = prepare(central);
        ActivatedJob publishing = job(AutomaticQuestionWorkers.PUBLISH);
        UUID batch = UUID.fromString(publishing.getVariablesAsMap().get("questionBatchRef").toString());
        worker.publish(engine, publishing);
        assertEquals("PUBLISHED", questions.publish(batch, leader, publishing.getProcessInstanceKey()).state());
        assertEquals(2, count("pf_staff_message"));
        assertEquals(2, count("pf_staff_message_mail_order"));
        assertEquals(2, count("pf_staff_answer_requirement"));
        assertEquals(2, reading.readByToken(accepted.personalAccessToken()).messages().stream()
                .filter(message -> message.author() == PublicAuthor.SYSTEM).count());
        assertEquals(QuestionCatalog.VERSION, questionStore.get(batch).catalog());
        assertTrue(staffMessages.history(caseId).stream().allMatch(message -> message.author() == PublicAuthor.SYSTEM));
        assertEquals(List.of("Seit wann besteht die Störung?", "In welchem Raum tritt sie auf?"),
                database.queryForList("SELECT body FROM pf_staff_message ORDER BY analysis_version", String.class));
        worker.finish(engine, job(AutomaticQuestionWorkers.FINISH));
        accept(worker);
        TenantReplyWorkers reply = new TenantReplyWorkers(engine,
                new TenantReplyService(workflow, replies, evaluations, central, questionStore, transactions,
                org.mockito.Mockito.mock(AutomaticClosureService.class)), replies);
        reply.register(engine, job(TenantReplyWorkers.REGISTER));
        assertEquals("WAITING_FOR_TENANT", status());
        assertEquals(1, count("pf_reply_entry"));
        assertEquals(1, count("pf_reply_call"));
        assertEquals(1, analysesCalled.get());
        assertTrue(tasks().isEmpty());
        assertEquals(0, database.queryForObject("SELECT count(*) FROM pf_case_review_signal WHERE state IN ('PENDING','CONFIRMED')", Integer.class));
    }

    @Test
    void missingObjectAssignmentRoutesToReviewWithoutPublishing() throws Exception {
        database.update("UPDATE pf_case SET assigned_unit = NULL WHERE id = ?", caseId);
        AutomaticQuestionWorkers worker = prepare(central());
        assertEquals("REVIEW", database.queryForObject("SELECT state FROM pf_automatic_question_batch", String.class));
        worker.finish(engine, job(AutomaticQuestionWorkers.FINISH));
        accept(worker);
        startReview();
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals(0, count("pf_staff_message"));
        assertEquals(0, count("pf_staff_answer_requirement"));
    }

    @Test
    void newMessageBeforePublicationRefreshesSharedEvaluationWithoutOldQuestion() throws Exception {
        ConversationEvaluationService central = central();
        AutomaticQuestionWorkers worker = prepare(central);
        ActivatedJob stale = job(AutomaticQuestionWorkers.PUBLISH);
        UUID access = database.queryForObject("SELECT id FROM pf_case_access WHERE case_id = ?", UUID.class, caseId);
        messages.save(new TenantMessageStore.Access(access, caseId), "new-context", "Seit gestern im Wohnzimmer.", leader, "<default>");
        selected.set(List.of("RQ-003"));
        worker.publish(engine, stale);
        assertEquals(0, count("pf_staff_message"));
        assertEquals("STALE", database.queryForObject("SELECT state FROM pf_automatic_question_batch", String.class));
        AutomaticEvaluationWorkers evaluation = new AutomaticEvaluationWorkers(engine, central);
        ActivatedJob refresh = job(AutomaticEvaluationWorkers.EVALUATE);
        assertEquals("CurrentAnalysis", refresh.getElementId());
        evaluation.evaluate(engine, refresh);
        worker.validate(engine, job(AutomaticQuestionWorkers.VALIDATE));
        worker.publish(engine, job(AutomaticQuestionWorkers.PUBLISH));
        assertEquals(List.of("Tritt die Störung ständig oder zeitweise auf?"), database.queryForList("SELECT body FROM pf_staff_message", String.class));
        assertEquals(2, analysesCalled.get());
        assertEquals(0, database.queryForObject("SELECT count(*) FROM pf_case_review_signal WHERE state IN ('PENDING','CONFIRMED')", Integer.class));
    }

    @Test
    void newMandatoryReasonAfterPublicationPreservesQuestionsAndSelectsOneReview() throws Exception {
        AutomaticQuestionWorkers worker = prepare(central());
        worker.publish(engine, job(AutomaticQuestionWorkers.PUBLISH));
        database.update("""
                INSERT INTO pf_case_review_signal (id, case_id, source_type, source_ref, reason, leading_instance_key)
                VALUES (?, ?, 'TEST', ?, 'HUMAN_DECISION_REQUIRED', ?)
                """, UUID.randomUUID(), caseId, UUID.randomUUID(), leader);
        worker.finish(engine, job(AutomaticQuestionWorkers.FINISH));
        accept(worker);
        startReview();
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals(2, count("pf_staff_message"));
        assertEquals(2, count("pf_staff_message_mail_order"));
        assertEquals(1, count("pf_staff_continuation"));
        assertEquals(0, count("pf_reply_call"));
    }

    @Test
    void knownAndManuallyCancelledCatalogQuestionIsNeverPublishedAgain() throws Exception {
        ConversationEvaluationService central = central();
        AutomaticQuestionWorkers worker = prepare(central);
        ActivatedJob publishing = job(AutomaticQuestionWorkers.PUBLISH);
        worker.publish(engine, publishing);
        database.update("UPDATE pf_staff_answer_requirement SET state = 'CANCELLED', resolution_type = 'MANUAL', resolved_at = CURRENT_TIMESTAMP");
        CaseWorkflowStore.Entry entry = workflow.entryForCase(caseId).orElseThrow();
        ConversationEvaluationService.Result evaluated = central.evaluateAutomatic(caseId, entry.id(), entry.callRef(), leader, entry.childKey());
        central.applyAutomatic(caseId, entry.id(), entry.callRef(), leader, entry.childKey(), evaluated.evaluationId());
        AutomaticQuestionService.Result decision = questions.validate(caseId, entry.id(), entry.callRef(), leader, entry.childKey(), evaluated.evaluationId());
        assertEquals("REVIEW", decision.state());
        assertEquals(2, count("pf_staff_message"));
        assertEquals(0, database.queryForObject("SELECT count(*) FROM pf_staff_answer_requirement WHERE state = 'OPEN'", Integer.class));
        assertTrue(questionStore.get(decision.reference()).questions().isEmpty());
    }

    @Test
    void unknownCatalogIdNeverBecomesPublicText() throws Exception {
        selected.set(List.of("RQ-999"));
        AutomaticQuestionWorkers worker = prepare(central());
        assertEquals("REVIEW", database.queryForObject("SELECT state FROM pf_automatic_question_batch", String.class));
        worker.finish(engine, job(AutomaticQuestionWorkers.FINISH));
        accept(worker);
        startReview();
        assertEquals(0, count("pf_staff_message"));
        assertEquals("STAFF_REVIEW_REQUIRED", status());
    }

    @Test
    void mandatoryReasonBeforePublicationDoesNotLoopOnAnUnchangedEvaluationVersion() throws Exception {
        AutomaticQuestionWorkers worker = prepare(central());
        ActivatedJob publishing = job(AutomaticQuestionWorkers.PUBLISH);
        database.update("""
                INSERT INTO pf_case_review_signal (id, case_id, source_type, source_ref, reason, leading_instance_key)
                VALUES (?, ?, 'TEST', ?, 'HUMAN_DECISION_REQUIRED', ?)
                """, UUID.randomUUID(), caseId, UUID.randomUUID(), leader);
        worker.publish(engine, publishing);
        assertEquals("REVIEW", database.queryForObject("SELECT state FROM pf_automatic_question_batch", String.class));
        worker.finish(engine, job(AutomaticQuestionWorkers.FINISH));
        accept(worker);
        startReview();
        assertEquals(0, count("pf_staff_message"));
        assertEquals("STAFF_REVIEW_REQUIRED", status());
    }

    @Test
    void partialAnswerToSystemQuestionKeepsRemainingQuestionWithoutDuplicatePublication() throws Exception {
        ConversationEvaluationService central = central();
        AutomaticQuestionWorkers worker = prepare(central);
        worker.publish(engine, job(AutomaticQuestionWorkers.PUBLISH));
        worker.finish(engine, job(AutomaticQuestionWorkers.FINISH));
        accept(worker);
        TenantReplyService replyService = new TenantReplyService(workflow, replies, evaluations, central, questionStore, transactions,
                org.mockito.Mockito.mock(AutomaticClosureService.class));
        TenantReplyWorkers reply = new TenantReplyWorkers(engine, replyService, replies);
        reply.register(engine, job(TenantReplyWorkers.REGISTER));
        UUID first = database.queryForObject("SELECT id FROM pf_staff_message WHERE catalog_question_id = 'RQ-001'", UUID.class);
        UUID access = database.queryForObject("SELECT id FROM pf_case_access WHERE case_id = ?", UUID.class, caseId);
        TenantMessageStore.Message answer = messages.save(new TenantMessageStore.Access(access, caseId), "partial", "Seit gestern.", leader, "<default>");
        ConversationEvaluationService answering = new ConversationEvaluationService(workflow, messages, evaluations, urgencies,
                input -> new ConversationAnalysis.Result("NORMAL", false,
                        List.of(new ConversationAnalysis.Answer(first, answer.id(), "Seit gestern.")), null, "test", "answer-v1"),
                input -> new Recommendation.Result("Verwaltung", "Weitere Antwort abwarten.", List.of(), selected.get(), false, null, "test", "catalog-v1"),
                noClosure(), transactions, new AutomaticClosureRelease());
        TenantReplyStore.Call waiting = replies.active(caseId, leader).orElseThrow();
        ConversationEvaluationService.Result evaluated = answering.evaluateReply(caseId, waiting.entry(), leader, waiting.child(), waiting.tenant());
        answering.applyReply(caseId, waiting.entry(), leader, waiting.child(), waiting.tenant(), evaluated.evaluationId());
        new TenantReplyDispatcher(replies, replyService, engine).dispatch();
        reply = new TenantReplyWorkers(engine, new TenantReplyService(workflow, replies, evaluations, answering, questionStore, transactions,
                org.mockito.Mockito.mock(AutomaticClosureService.class)), replies);
        reply.evaluate(engine, job(TenantReplyWorkers.EVALUATE));
        reply.validate(engine, job(TenantReplyWorkers.VALIDATE));
        reply.register(engine, job(TenantReplyWorkers.REGISTER));
        assertEquals("WAITING_FOR_TENANT", status());
        assertEquals(2, count("pf_staff_message"));
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_staff_answer_requirement WHERE state = 'OPEN'", Integer.class));
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_staff_answer_requirement WHERE state = 'ANSWERED'", Integer.class));
        assertEquals(0, count("pf_staff_continuation"));
    }

    @Test
    void answerCancellationAfterAutomaticCompletionContinuesToWorkWithoutLosingTheMainPath() throws Exception {
        cancelDuringHandover(false);
    }

    @Test
    void answerCancellationBeforeReplyRegistrationDoesNotRestoreTenantWait() throws Exception {
        cancelDuringHandover(true);
    }

    private void cancelDuringHandover(boolean replyStarted) throws Exception {
        selected.set(List.of("RQ-001"));
        AutomaticQuestionWorkers worker = prepare(central());
        worker.publish(engine, job(AutomaticQuestionWorkers.PUBLISH));
        worker.finish(engine, job(AutomaticQuestionWorkers.FINISH));
        if (replyStarted) {
            accept(worker);
            job(TenantReplyWorkers.REGISTER);
        } else {
            job(AutomaticQuestionWorkers.ACCEPT);
        }
        WebAccountContext actor = new WebAccountContext(UUID.randomUUID());
        database.update("""
                INSERT INTO pf_staff_account (id, name, email, active, staff_role, account_administration)
                VALUES (?, 'Mitarbeiter', ?, TRUE, TRUE, FALSE)
                """, actor.accountId(), actor.accountId() + "@example.test");
        CaseDataService data = new CaseDataService(access, workflow, caseData,
                new CamundaCaseGateway(new StaticListableBeanFactory(Map.of("engine", engine)).getBeanProvider(CamundaClient.class), true),
                urgencyService, new AnswerRequirementStore(database), new StaffTakeoverStore(database), clarificationClosures,
                new DirectFollowUpStore(database, new CaseWaitStore(database), new StaffTakeoverStore(database)), transactions);
        UUID message = database.queryForObject("SELECT id FROM pf_staff_message", UUID.class);
        data.clearAnswer(actor, caseId, "handover-clear", database.queryForObject("SELECT version FROM pf_case", Long.class), message);
        new CaseDataWorkers(engine, data).clearAnswer(engine, job("propertyflow.answer.clear"));
        assertEquals("IN_PROGRESS", status());
        AnswerCancellationWorkers cancellation = new AnswerCancellationWorkers(new AnswerRequirementStore(database), reviewService, engine);
        cancellation.dispatch();
        cancellation.continueWork(engine, job("propertyflow.answer.continue"));
        StaffReviewWorkers staff = new StaffReviewWorkers(engine, reviewService);
        staff.claim(engine, job("propertyflow.staff.claim"));
        staff.register(engine, job("propertyflow.staff.register"));
        await(() -> tasks().size() == 1 && "SWork".equals(tasks().getFirst().getElementId()));
        assertEquals("IN_PROGRESS", status());
        assertEquals(0, count("pf_reply_call"));
        assertEquals(1, count("pf_staff_continuation"));
        assertEquals(1, analysesCalled.get());
        assertEquals("CANCELLED", database.queryForObject("SELECT state FROM pf_staff_answer_requirement", String.class));
    }

    @Test
    void pendingMailProblemBlocksPublicationBeforeItsLeadingInstanceIsAssigned() throws Exception {
        UUID signal = UUID.randomUUID();
        database.update("""
                INSERT INTO pf_case_review_signal (id, case_id, source_type, source_ref, reason)
                VALUES (?, ?, 'TEST', ?, 'MAIL_DELIVERY_FAILED')
                """, signal, caseId, UUID.randomUUID());
        AutomaticQuestionWorkers worker = prepare(central());
        worker.finish(engine, job(AutomaticQuestionWorkers.FINISH));
        accept(worker);
        startReview();
        assertEquals(leader, database.queryForObject("SELECT leading_instance_key FROM pf_case_review_signal WHERE id = ?", Long.class, signal));
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals(0, count("pf_staff_message"));
    }

    @Autowired
    private AutomaticQuestionRoundService rounds;

    @Test
    void partialReplyStartsNewAutomaticCallAndPublishesOnlyNewCatalogQuestion() throws Exception {
        ConversationEvaluationService central = beginAdditionalRound();
        CaseWorkflowStore.Entry first = workflow.entryForCase(caseId).orElseThrow();
        ActivatedJob prepare = job(AutomaticQuestionRoundWorkers.PREPARE);
        prepareRound(prepare);
        CaseWorkflowStore.Entry second = workflow.entryForCase(caseId).orElseThrow();
        assertNotEquals(first.id(), second.id());
        assertNotEquals(first.callRef(), second.callRef());
        assertEquals(second.id(), rounds.prepare(UUID.fromString(prepare.getVariablesAsMap().get("replyDecisionRef").toString()),
                caseId, leader, prepare.getElementInstanceKey(),
                UUID.fromString(prepare.getVariablesAsMap().get("activeReplyCallRef").toString())).id());
        initial.registerAutomatic(engine, job(InitialCaseWorkers.REGISTER_AUTOMATIC));
        engine.newSetVariablesCommand(leader)
                .variables(Map.of("automaticInterruptionReady", true, "automaticInterruptionCallRef", first.callRef().toString()))
                .send()
                .join();
        AutomaticQuestionWorkers worker = prepare(central);
        worker.publish(engine, job(AutomaticQuestionWorkers.PUBLISH));
        worker.finish(engine, job(AutomaticQuestionWorkers.FINISH));
        engine.newSetVariablesCommand(leader)
                .variables(Map.of("clearedAnswerCallRef", prepare.getVariablesAsMap().get("activeReplyCallRef"),
                        "reviewNeedReplyCallRef", prepare.getVariablesAsMap().get("activeReplyCallRef")))
                .send()
                .join();
        accept(worker);
        TenantReplyWorkers reply = reply(central);
        reply.register(engine, job(TenantReplyWorkers.REGISTER));
        assertEquals("WAITING_FOR_TENANT", status());
        assertEquals(3, count("pf_staff_message"));
        assertEquals(3, count("pf_staff_message_mail_order"));
        assertEquals(2, count("pf_automatic_entry"));
        assertEquals(2, count("pf_reply_call"));
        assertEquals(2, count("pf_case_status_event"));
        assertEquals(2, database.queryForObject("SELECT count(*) FROM pf_staff_answer_requirement WHERE state = 'OPEN'", Integer.class));
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_staff_answer_requirement WHERE state = 'ANSWERED'", Integer.class));
        assertEquals(0, count("pf_staff_continuation"));
        assertEquals(1, analysesCalled.get());
        assertThrows(WorkflowRejectedException.class, () -> questions.finish(
                database.queryForObject("SELECT id FROM pf_automatic_question_batch WHERE automatic_entry_id = ?", UUID.class, first.id()),
                leader, first.childKey()));
    }

    @Test
    void changedRecommendationAfterReplyDecisionDoesNotPublishTheOldProposal() throws Exception {
        ConversationEvaluationService central = beginAdditionalRound();
        prepareRound(job(AutomaticQuestionRoundWorkers.PREPARE));
        initial.registerAutomatic(engine, job(InitialCaseWorkers.REGISTER_AUTOMATIC));
        UUID accessId = database.queryForObject("SELECT id FROM pf_case_access WHERE case_id = ?", UUID.class, caseId);
        messages.save(new TenantMessageStore.Access(accessId, caseId), "new-after-reply", "Der Zustand hat sich geändert.", leader, "<default>");
        selected.set(List.of("RQ-004"));
        AutomaticQuestionWorkers worker = prepare(central);
        worker.publish(engine, job(AutomaticQuestionWorkers.PUBLISH));
        assertEquals(0, database.queryForObject("SELECT count(*) FROM pf_staff_message WHERE catalog_question_id = 'RQ-003'", Integer.class));
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_staff_message WHERE catalog_question_id = 'RQ-004'", Integer.class));
        assertEquals("WAITING_FOR_TENANT", status());
    }

    @Test
    void mandatoryReasonDuringQuestionRoundLeadsToOneReviewWithoutNewQuestion() throws Exception {
        ConversationEvaluationService central = beginAdditionalRound();
        prepareRound(job(AutomaticQuestionRoundWorkers.PREPARE));
        initial.registerAutomatic(engine, job(InitialCaseWorkers.REGISTER_AUTOMATIC));
        database.update("""
                INSERT INTO pf_case_review_signal (id, case_id, source_type, source_ref, reason, leading_instance_key)
                VALUES (?, ?, 'TEST', ?, 'HUMAN_DECISION_REQUIRED', ?)
                """, UUID.randomUUID(), caseId, UUID.randomUUID(), leader);
        AutomaticQuestionWorkers worker = prepare(central);
        worker.finish(engine, job(AutomaticQuestionWorkers.FINISH));
        accept(worker);
        startReview();
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals(2, count("pf_staff_message"));
        assertEquals(1, count("pf_staff_continuation"));
    }

    @Test
    void mandatoryReasonInterruptsRegisteredQuestionRoundBeforeItsEvaluationCompletes() throws Exception {
        beginAdditionalRound();
        prepareRound(job(AutomaticQuestionRoundWorkers.PREPARE));
        initial.registerAutomatic(engine, job(InitialCaseWorkers.REGISTER_AUTOMATIC));
        ActivatedJob evaluation = job(AutomaticEvaluationWorkers.EVALUATE);
        database.update("""
                INSERT INTO pf_case_review_signal (id, case_id, source_type, source_ref, reason, leading_instance_key)
                VALUES (?, ?, 'TEST', ?, 'HUMAN_DECISION_REQUIRED', ?)
                """, UUID.randomUUID(), caseId, UUID.randomUUID(), leader);
        new ReviewSignalDispatcher(new StaffReviewStore(database), engine).dispatch();
        StaffReviewWorkers review = new StaffReviewWorkers(engine, reviewService);
        review.check(engine, job("propertyflow.review.check"));
        review.interruptAutomatic(engine, job("propertyflow.review.interrupt-automatic"));
        review.prepareStaff(engine, job("propertyflow.review.prepare-staff"));
        startReview();
        await(() -> "TERMINATED".equals(engine.newProcessInstanceGetRequest(evaluation.getProcessInstanceKey()).send().join().getState().name()));
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals(2, count("pf_staff_message"));
        assertEquals(1, count("pf_staff_continuation"));
    }

    @Test
    void cancellingRemainingQuestionDuringNewAutomaticRoundContinuesToOneWorkTask() throws Exception {
        beginAdditionalRound();
        prepareRound(job(AutomaticQuestionRoundWorkers.PREPARE));
        initial.registerAutomatic(engine, job(InitialCaseWorkers.REGISTER_AUTOMATIC));
        job(AutomaticEvaluationWorkers.EVALUATE);
        WebAccountContext actor = new WebAccountContext(UUID.randomUUID());
        database.update("""
                INSERT INTO pf_staff_account (id, name, email, active, staff_role, account_administration)
                VALUES (?, 'Mitarbeiter', ?, TRUE, TRUE, FALSE)
                """, actor.accountId(), actor.accountId() + "@example.test");
        CaseDataService data = new CaseDataService(access, workflow, caseData,
                new CamundaCaseGateway(new StaticListableBeanFactory(Map.of("engine", engine)).getBeanProvider(CamundaClient.class), true),
                urgencyService, new AnswerRequirementStore(database), new StaffTakeoverStore(database), clarificationClosures,
                new DirectFollowUpStore(database, new CaseWaitStore(database), new StaffTakeoverStore(database)), transactions);
        UUID message = database.queryForObject("SELECT message_id FROM pf_staff_answer_requirement WHERE state = 'OPEN'", UUID.class);
        data.clearAnswer(actor, caseId, "round-clear", database.queryForObject("SELECT version FROM pf_case", Long.class), message);
        new CaseDataWorkers(engine, data).clearAnswer(engine, job("propertyflow.answer.clear"));
        assertEquals("IN_PROGRESS", status());
        new AnswerCancellationWorkers(new AnswerRequirementStore(database), reviewService, engine).dispatch();
        StaffReviewWorkers staff = new StaffReviewWorkers(engine, reviewService);
        staff.prepareStaff(engine, job("propertyflow.review.prepare-staff"));
        staff.claim(engine, job("propertyflow.staff.claim"));
        staff.register(engine, job("propertyflow.staff.register"));
        await(() -> tasks().size() == 1 && "SWork".equals(tasks().getFirst().getElementId()));
        assertEquals(1, count("pf_staff_continuation"));
        assertEquals(2, count("pf_staff_message"));
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_staff_answer_requirement WHERE state = 'ANSWERED'", Integer.class));
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_staff_answer_requirement WHERE state = 'CANCELLED'", Integer.class));
    }

    @Test
    void fullyAnsweredQuestionsTakePriorityOverAdditionalCatalogProposal() throws Exception {
        ConversationEvaluationService central = central();
        AutomaticQuestionWorkers worker = prepare(central);
        worker.publish(engine, job(AutomaticQuestionWorkers.PUBLISH));
        worker.finish(engine, job(AutomaticQuestionWorkers.FINISH));
        accept(worker);
        reply(central).register(engine, job(TenantReplyWorkers.REGISTER));
        UUID accessId = database.queryForObject("SELECT id FROM pf_case_access WHERE case_id = ?", UUID.class, caseId);
        TenantMessageStore.Message answer = messages.save(new TenantMessageStore.Access(accessId, caseId), "complete-round",
                "Seit gestern im Wohnzimmer.", leader, "<default>");
        ConversationEvaluationService answering = new ConversationEvaluationService(workflow, messages, evaluations, urgencies,
                input -> new ConversationAnalysis.Result("NORMAL", false, input.questions().stream()
                        .map(question -> new ConversationAnalysis.Answer(question.id(), answer.id(), "Seit gestern im Wohnzimmer.")).toList(),
                        null, "test", "answer-v1"),
                input -> new Recommendation.Result("Verwaltung", "Zusätzliche Angabe vorgeschlagen.", List.of(), List.of("RQ-003"), false,
                        null, "test", "catalog-v1"), noClosure(), transactions, new AutomaticClosureRelease());
        TenantReplyStore.Call waiting = replies.active(caseId, leader).orElseThrow();
        ConversationEvaluationService.Result evaluated = answering.evaluateReply(caseId, waiting.entry(), leader, waiting.child(), waiting.tenant());
        answering.applyReply(caseId, waiting.entry(), leader, waiting.child(), waiting.tenant(), evaluated.evaluationId());
        TenantReplyService replyService = new TenantReplyService(workflow, replies, evaluations, answering, questionStore, transactions,
                org.mockito.Mockito.mock(AutomaticClosureService.class));
        new TenantReplyDispatcher(replies, replyService, engine).dispatch();
        reply(answering).prepareReview(engine, job(TenantReplyWorkers.PREPARE_REVIEW));
        startReview();
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals(1, count("pf_automatic_entry"));
        assertEquals(2, count("pf_staff_message"));
        assertEquals(2, database.queryForObject("SELECT count(*) FROM pf_staff_answer_requirement WHERE state = 'ANSWERED'", Integer.class));
    }

    @Test
    void reviewWakeFromReturnedReplyDoesNotRemainStuckDuringQuestionHandover() throws Exception {
        ConversationEvaluationService central = beginAdditionalRound();
        database.update("""
                INSERT INTO pf_case_review_signal (id, case_id, source_type, source_ref, reason, leading_instance_key)
                VALUES (?, ?, 'TEST', ?, 'HUMAN_DECISION_REQUIRED', ?)
                """, UUID.randomUUID(), caseId, UUID.randomUUID(), leader);
        new ReviewSignalDispatcher(new StaffReviewStore(database), engine).dispatch();
        new StaffReviewWorkers(engine, reviewService).check(engine, job("propertyflow.review.check"));
        ActivatedJob wake = job(TenantReplyWorkers.WAKE_REVIEW);
        reply(central).wakeReview(engine, wake);
        await(() -> {
            try {
                return "COMPLETED".equals(engine.newElementInstanceGetRequest(wake.getElementInstanceKey()).send().join().getState().name());
            } catch (RuntimeException exception) {
                return false;
            }
        });
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_case_review_signal WHERE state = 'PENDING'", Integer.class));
        prepareRound(job(AutomaticQuestionRoundWorkers.PREPARE));
        initial.registerAutomatic(engine, job(InitialCaseWorkers.REGISTER_AUTOMATIC));
        AutomaticQuestionWorkers worker = prepare(central);
        worker.finish(engine, job(AutomaticQuestionWorkers.FINISH));
        accept(worker);
        startReview();
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals(2, count("pf_staff_message"));
        assertEquals(1, count("pf_staff_continuation"));
    }

    @Autowired
    private DirectFollowUpStore directFollowUps;

    @Autowired
    private CaseWaitService waitService;

    private DirectFollowUpWorkers directWorker(CaseDataService data) {
        return new DirectFollowUpWorkers(engine, data, caseData, directFollowUps, replies, reviewService);
    }

    private UUID requestDirect(CaseDataService data) {
        ClosureApproval approval = data.directFollowUp(takeoverActor, caseId);
        assertTrue(approval.available());
        assertEquals("PENDING", data.setDirectFollowUp(takeoverActor, caseId, "direct-wait", approval.version(),
                approval.reviewToken(), 2, null, "Ersatzteil abwarten").state());
        return caseData.latest(caseId, "direct-wait").orElseThrow().id();
    }

    private void registerDirectStaff() throws Exception {
        StaffReviewWorkers staff = new StaffReviewWorkers(engine, reviewService);
        staff.prepareStaff(engine, job("propertyflow.review.prepare-staff"));
        staff.claim(engine, job("propertyflow.staff.claim"));
        staff.register(engine, job("propertyflow.staff.register"));
    }

    @Test
    void directFollowUpStopsAnalysisAndKeepsOneAbsoluteDateAndMailAcrossReplay() throws Exception {
        CaseDataService data = takeoverService();
        UUID id = requestDirect(data);
        Instant due = directFollowUps.get(id).due();
        int mails = count("pf_case_mail_order");
        DirectFollowUpWorkers worker = directWorker(data);
        ActivatedJob apply = job(DirectFollowUpWorkers.APPLY);
        assertEquals("APPLIED", data.directFollowUpState(id, leader, "<default>"));
        worker.apply(engine, apply);
        assertEquals("WAITING_FOR_FOLLOW_UP", status());
        assertEquals(mails + 1, count("pf_case_mail_order"));
        worker.signal(engine, job(DirectFollowUpWorkers.SIGNAL));
        registerDirectStaff();
        CaseWaitWorkers waits = new CaseWaitWorkers(engine, waitService);
        ActivatedJob save = job(CaseWaitWorkers.SAVE);
        waits.save(engine, save);
        CaseWaitStore.Wait wait = new CaseWaitStore(database).get(id).orElseThrow();
        assertEquals(due, wait.dueAt());
        assertEquals("ACTIVE", wait.state());
        assertTrue(wait.staff() > 0 && wait.child() != null);
        assertEquals(mails + 1, count("pf_case_mail_order"));
        assertEquals(1, count("pf_case_wait"));
        assertEquals(0, tasks().size());
    }

    @Test
    void contactBeforeDirectStaffRegistrationResolvesWaitAndCreatesOneReview() throws Exception {
        CaseDataService data = takeoverService();
        UUID id = requestDirect(data);
        DirectFollowUpWorkers worker = directWorker(data);
        worker.apply(engine, job(DirectFollowUpWorkers.APPLY));
        UUID contact = UUID.randomUUID();
        assertEquals("APPLIED", caseData.get(id).state());
        // Der reguläre Nachrichteneingang speichert Kontakt und Terminbezug in derselben Transaktion.
        database.update("""
                INSERT INTO pf_tenant_message (id, case_id, access_id, operation_id, body, leading_instance_key, tenant_id, analysis_version)
                SELECT ?, id, (SELECT access_id FROM pf_acceptance_mail_order WHERE case_id = ?), ?, 'Neuer Kontakt', ?, '<default>', analysis_version
                FROM pf_case WHERE id = ?
                """, contact, caseId, UUID.randomUUID(), leader, caseId);
        database.update("INSERT INTO pf_wait_contact (message_id, wait_id) VALUES (?, ?)", contact, id);
        worker.signal(engine, job(DirectFollowUpWorkers.SIGNAL));
        registerDirectStaff();
        new StaffReviewWorkers(engine, reviewService).startReview(engine, job("propertyflow.staff.start-review"));
        await(() -> tasks().size() == 1);
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals("CONTACT", new CaseWaitStore(database).get(id).orElseThrow().state());
        assertEquals(1, count("pf_wait_resolution"));
    }

    @Test
    void newMandatoryReasonDuringDirectHandoverRequiresReviewButKnownReasonIsConsidered() throws Exception {
        UUID known = UUID.randomUUID();
        database.update("INSERT INTO pf_case_review_signal (id, case_id, source_type, source_ref, reason) VALUES (?, ?, 'TEST', ?, 'ANALYSIS_FAILED')",
                known, caseId, UUID.randomUUID());
        CaseDataService data = takeoverService();
        UUID id = requestDirect(data);
        DirectFollowUpWorkers worker = directWorker(data);
        worker.apply(engine, job(DirectFollowUpWorkers.APPLY));
        assertEquals(id, database.queryForObject("SELECT considered_direct_command_id FROM pf_case_review_signal WHERE id = ?", UUID.class, known));
        UUID fresh = UUID.randomUUID();
        database.update("INSERT INTO pf_case_review_signal (id, case_id, source_type, source_ref, reason) VALUES (?, ?, 'TEST', ?, 'HUMAN_DECISION_REQUIRED')",
                fresh, caseId, UUID.randomUUID());
        worker.signal(engine, job(DirectFollowUpWorkers.SIGNAL));
        registerDirectStaff();
        new StaffReviewWorkers(engine, reviewService).startReview(engine, job("propertyflow.staff.start-review"));
        await(() -> tasks().size() == 1);
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals("REVIEW", new CaseWaitStore(database).get(id).orElseThrow().state());
        assertNull(database.queryForObject("SELECT considered_direct_command_id FROM pf_case_review_signal WHERE id = ?", UUID.class, fresh));
    }

    @Test
    void staleDirectFollowUpLeavesClarificationAndCreatesNoWait() throws Exception {
        CaseDataService data = takeoverService();
        UUID id = requestDirect(data);
        database.update("UPDATE pf_case SET version = version + 1 WHERE id = ?", caseId);
        directWorker(data).apply(engine, job(DirectFollowUpWorkers.APPLY));
        assertEquals("REJECTED", caseData.get(id).state());
        assertEquals("UNDER_CLARIFICATION", status());
        assertEquals(0, count("pf_case_wait"));
    }

    @Test
    void directFollowUpReusesAlreadyClaimedStaffEntryWithoutSecondTimer() throws Exception {
        selected.set(List.of());
        AutomaticQuestionWorkers automatic = prepare(central());
        automatic.finish(engine, job(AutomaticQuestionWorkers.FINISH));
        accept(automatic);
        StaffReviewWorkers staff = new StaffReviewWorkers(engine, reviewService);
        staff.claim(engine, job("propertyflow.staff.claim"));
        ActivatedJob registering = job("propertyflow.staff.register");
        CaseDataService data = takeoverService();
        UUID id = requestDirect(data);
        DirectFollowUpWorkers worker = directWorker(data);
        worker.apply(engine, job(DirectFollowUpWorkers.APPLY));
        assertEquals("APPLIED", caseData.get(id).state());
        worker.signal(engine, job(DirectFollowUpWorkers.SIGNAL));
        staff.register(engine, registering);
        new CaseWaitWorkers(engine, waitService).save(engine, job(CaseWaitWorkers.SAVE));
        assertEquals("WAITING_FOR_FOLLOW_UP", status());
        assertEquals(1, count("pf_staff_continuation"));
        assertEquals(1, count("pf_case_wait"));
    }

    @Test
    void revokedRoleRejectsDirectFollowUpBeforeEffects() throws Exception {
        CaseDataService data = takeoverService();
        UUID id = requestDirect(data);
        database.update("UPDATE pf_staff_account SET staff_role = FALSE WHERE id = ?", takeoverActor.accountId());
        directWorker(data).apply(engine, job(DirectFollowUpWorkers.APPLY));
        assertEquals("REJECTED", caseData.get(id).state());
        assertEquals("UNDER_CLARIFICATION", status());
        assertEquals(0, count("pf_case_wait"));
    }

    @Test
    void changedReviewBasisRejectsDirectFollowUpWithoutSilentlyConsideringNewReason() throws Exception {
        CaseDataService data = takeoverService();
        UUID id = requestDirect(data);
        database.update("INSERT INTO pf_case_review_signal (id, case_id, source_type, source_ref, reason) VALUES (?, ?, 'TEST', ?, 'ANALYSIS_FAILED')",
                UUID.randomUUID(), caseId, UUID.randomUUID());
        directWorker(data).apply(engine, job(DirectFollowUpWorkers.APPLY));
        assertEquals("REJECTED", caseData.get(id).state());
        assertEquals(0, count("pf_case_wait"));
        assertNull(database.queryForObject("SELECT considered_direct_command_id FROM pf_case_review_signal", UUID.class));
    }

    @Test
    void obsoleteStaffReservationHandsPendingReasonToActualNewAutomaticCall() throws Exception {
        beginAdditionalRound();
        CaseWorkflowStore.Entry previous = workflow.entryForCase(caseId).orElseThrow();
        UUID obsolete = UUID.randomUUID();
        database.update("""
                INSERT INTO pf_staff_continuation (id, case_id, leading_instance_key, automatic_call_ref, case_version, superseded)
                VALUES (?, ?, ?, ?, 0, TRUE)
                """, obsolete, caseId, leader, previous.callRef());
        prepareRound(job(AutomaticQuestionRoundWorkers.PREPARE));
        initial.registerAutomatic(engine, job(InitialCaseWorkers.REGISTER_AUTOMATIC));
        CaseWorkflowStore.Entry current = workflow.entryForCase(caseId).orElseThrow();
        UUID signal = UUID.randomUUID();
        database.update("""
                INSERT INTO pf_case_review_signal (id, case_id, source_type, source_ref, reason, leading_instance_key, continuation_id, state)
                VALUES (?, ?, 'TEST', ?, 'HUMAN_DECISION_REQUIRED', ?, ?, 'CONFIRMED')
                """, signal, caseId, UUID.randomUUID(), leader, obsolete);
        StaffReviewWorkers staff = new StaffReviewWorkers(engine, reviewService);
        engine.newModifyProcessInstanceCommand(leader).activateElement("StaffContinuationClaim")
                .withVariable("staffEntryContextRef", obsolete.toString()).send().join();
        staff.claim(engine, job("propertyflow.staff.claim"));
        staff.reconcileContinuation(engine, job("propertyflow.staff.reconcile-continuation"));
        assertEquals("ACTIVE", engine.newProcessInstanceGetRequest(current.childKey()).send().join().getState().name());
        assertEquals("PENDING", database.queryForObject("SELECT state FROM pf_case_review_signal WHERE id = ?", String.class, signal));
        assertEquals(1, count("pf_staff_continuation"));
        new ReviewSignalDispatcher(new StaffReviewStore(database), engine).dispatch();
        staff.check(engine, job("propertyflow.review.check"));
        staff.interruptAutomatic(engine, job("propertyflow.review.interrupt-automatic"));
        finishTakeover(true);
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals(2, count("pf_staff_continuation"));
    }

    private WebAccountContext takeoverActor;

    private CaseDataService takeoverService() {
        takeoverActor = new WebAccountContext(UUID.randomUUID());
        database.update("""
                INSERT INTO pf_staff_account (id, name, email, active, staff_role, account_administration)
                VALUES (?, 'Mitarbeiter', ?, TRUE, TRUE, FALSE)
                """, takeoverActor.accountId(), takeoverActor.accountId() + "@example.test");
        return new CaseDataService(access, workflow, caseData,
                new CamundaCaseGateway(new StaticListableBeanFactory(Map.of("engine", engine)).getBeanProvider(CamundaClient.class), true),
                urgencyService, new AnswerRequirementStore(database), new StaffTakeoverStore(database), clarificationClosures,
                new DirectFollowUpStore(database, new CaseWaitStore(database), new StaffTakeoverStore(database)), transactions);
    }

    private StaffTakeoverWorkers takeover(CaseDataService data) {
        return new StaffTakeoverWorkers(engine, data, new StaffTakeoverStore(database));
    }

    private void finishTakeover(boolean review) throws Exception {
        StaffReviewWorkers staff = new StaffReviewWorkers(engine, reviewService);
        staff.prepareStaff(engine, job("propertyflow.review.prepare-staff"));
        staff.claim(engine, job("propertyflow.staff.claim"));
        staff.register(engine, job("propertyflow.staff.register"));
        if (review) {
            staff.startReview(engine, job("propertyflow.staff.start-review"));
        }
        await(() -> tasks().size() == 1 && (review ? "SReview" : "SWork").equals(tasks().getFirst().getElementId()));
    }

    @Test
    void staffTakeoverStopsAutomaticWorkAndCreatesOneWorkTaskWithAudit() throws Exception {
        ActivatedJob analysis = job(AutomaticEvaluationWorkers.EVALUATE);
        CaseDataService data = takeoverService();
        long version = database.queryForObject("SELECT version FROM pf_case", Long.class);
        assertEquals("PENDING", data.takeOver(takeoverActor, caseId, "takeover", version).state());
        StaffTakeoverWorkers worker = takeover(data);
        ActivatedJob applying = job(StaffTakeoverWorkers.APPLY);
        worker.apply(engine, applying);
        assertEquals("IN_PROGRESS", status());
        assertEquals("APPLIED", data.takeOver(takeoverActor, caseId, "takeover", version).state());
        worker.signal(engine, job(StaffTakeoverWorkers.SIGNAL));
        finishTakeover(false);
        await(() -> "TERMINATED".equals(engine.newProcessInstanceGetRequest(analysis.getProcessInstanceKey()).send().join().getState().name()));
        assertEquals(1, count("pf_staff_continuation"));
        assertEquals(1, count("pf_staff_takeover"));
        assertEquals(2, count("pf_case_status_event"));
        assertEquals(2, count("pf_case_mail_order"));
        assertEquals(takeoverActor.accountId(), new StaffChangeStore(database).history(caseId).getFirst().actorId());
    }

    @Test
    void mandatoryReasonAtTakeoverPreservesReviewPriority() throws Exception {
        CaseDataService data = takeoverService();
        database.update("""
                INSERT INTO pf_case_review_signal (id, case_id, source_type, source_ref, reason, leading_instance_key)
                VALUES (?, ?, 'TEST', ?, 'HUMAN_DECISION_REQUIRED', ?)
                """, UUID.randomUUID(), caseId, UUID.randomUUID(), leader);
        data.takeOver(takeoverActor, caseId, "takeover", database.queryForObject("SELECT version FROM pf_case", Long.class));
        StaffTakeoverWorkers worker = takeover(data);
        worker.apply(engine, job(StaffTakeoverWorkers.APPLY));
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        worker.signal(engine, job(StaffTakeoverWorkers.SIGNAL));
        finishTakeover(true);
        assertEquals(1, count("pf_staff_continuation"));
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_case_review_signal WHERE considered_decision_id IS NULL", Integer.class));
    }

    @Test
    void newMandatoryReasonBetweenTakeoverCommitAndStaffEntryUpgradesTheSameContinuation() throws Exception {
        CaseDataService data = takeoverService();
        data.takeOver(takeoverActor, caseId, "takeover", database.queryForObject("SELECT version FROM pf_case", Long.class));
        StaffTakeoverWorkers worker = takeover(data);
        worker.apply(engine, job(StaffTakeoverWorkers.APPLY));
        assertEquals("IN_PROGRESS", status());
        database.update("""
                INSERT INTO pf_case_review_signal (id, case_id, source_type, source_ref, reason)
                VALUES (?, ?, 'TEST', ?, 'HUMAN_DECISION_REQUIRED')
                """, UUID.randomUUID(), caseId, UUID.randomUUID());
        worker.signal(engine, job(StaffTakeoverWorkers.SIGNAL));
        finishTakeover(true);
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals(1, count("pf_staff_continuation"));
        assertEquals(3, count("pf_case_status_event"));
    }

    @Test
    void staleTakeoverLeavesAutomaticPathAndMessagesUnchanged() throws Exception {
        ActivatedJob analysis = job(AutomaticEvaluationWorkers.EVALUATE);
        CaseDataService data = takeoverService();
        data.takeOver(takeoverActor, caseId, "takeover", database.queryForObject("SELECT version FROM pf_case", Long.class));
        UUID accessId = database.queryForObject("SELECT id FROM pf_case_access WHERE case_id = ?", UUID.class, caseId);
        messages.save(new TenantMessageStore.Access(accessId, caseId), "takeover-race", "Neue Information.", leader, "<default>");
        takeover(data).apply(engine, job(StaffTakeoverWorkers.APPLY));
        assertEquals("REJECTED", data.read(takeoverActor, caseId, "takeover").state());
        assertEquals("UNDER_CLARIFICATION", status());
        assertEquals("ACTIVE", engine.newProcessInstanceGetRequest(analysis.getProcessInstanceKey()).send().join().getState().name());
        assertEquals(0, count("pf_staff_continuation"));
        assertEquals(1, count("pf_case_status_event"));
    }

    @Test
    void revokedRoleRejectsPreparedTakeoverWithoutInterruptingAutomaticWork() throws Exception {
        CaseDataService data = takeoverService();
        data.takeOver(takeoverActor, caseId, "takeover", database.queryForObject("SELECT version FROM pf_case", Long.class));
        database.update("UPDATE pf_staff_account SET staff_role = FALSE WHERE id = ?", takeoverActor.accountId());
        takeover(data).apply(engine, job(StaffTakeoverWorkers.APPLY));
        assertEquals("REJECTED", database.queryForObject("SELECT state FROM pf_case_data_command", String.class));
        assertEquals("UNDER_CLARIFICATION", status());
        assertEquals(0, count("pf_staff_continuation"));
    }

    @Test
    void takeoverAfterAutomaticCompletionPreservesPublishedOpenQuestions() throws Exception {
        takeoverDuringHandover(false);
    }

    @Test
    void takeoverBeforeReplyRegistrationCannotBeOverwrittenByWaitingStatus() throws Exception {
        takeoverDuringHandover(true);
    }

    private void takeoverDuringHandover(boolean replyStarted) throws Exception {
        AutomaticQuestionWorkers automatic = prepare(central());
        automatic.publish(engine, job(AutomaticQuestionWorkers.PUBLISH));
        automatic.finish(engine, job(AutomaticQuestionWorkers.FINISH));
        if (replyStarted) {
            accept(automatic);
            job(TenantReplyWorkers.REGISTER);
        } else {
            job(AutomaticQuestionWorkers.ACCEPT);
        }
        CaseDataService data = takeoverService();
        data.takeOver(takeoverActor, caseId, "takeover", database.queryForObject("SELECT version FROM pf_case", Long.class));
        StaffTakeoverWorkers worker = takeover(data);
        worker.apply(engine, job(StaffTakeoverWorkers.APPLY));
        worker.signal(engine, job(StaffTakeoverWorkers.SIGNAL));
        finishTakeover(false);
        assertEquals("IN_PROGRESS", status());
        assertEquals(2, database.queryForObject("SELECT count(*) FROM pf_staff_answer_requirement WHERE state = 'OPEN'", Integer.class));
        assertEquals(1, count("pf_staff_continuation"));
        assertEquals(0, count("pf_reply_call"));
    }

    @Test
    void takeoverReusesReviewContinuationAlreadyClaimedByTheMainPath() throws Exception {
        selected.set(List.of());
        AutomaticQuestionWorkers automatic = prepare(central());
        automatic.finish(engine, job(AutomaticQuestionWorkers.FINISH));
        accept(automatic);
        StaffReviewWorkers staff = new StaffReviewWorkers(engine, reviewService);
        staff.claim(engine, job("propertyflow.staff.claim"));
        ActivatedJob registering = job("propertyflow.staff.register");
        UUID continuation = database.queryForObject("SELECT id FROM pf_staff_continuation", UUID.class);
        CaseDataService data = takeoverService();
        data.takeOver(takeoverActor, caseId, "takeover", database.queryForObject("SELECT version FROM pf_case", Long.class));
        StaffTakeoverWorkers worker = takeover(data);
        worker.apply(engine, job(StaffTakeoverWorkers.APPLY));
        assertEquals(continuation, database.queryForObject("SELECT continuation_id FROM pf_staff_takeover", UUID.class));
        worker.signal(engine, job(StaffTakeoverWorkers.SIGNAL));
        staff.register(engine, registering);
        staff.startReview(engine, job("propertyflow.staff.start-review"));
        await(() -> tasks().size() == 1 && "SReview".equals(tasks().getFirst().getElementId()));
        assertEquals(1, count("pf_staff_continuation"));
        assertEquals("STAFF_REVIEW_REQUIRED", status());
    }

    private StaffMessageService manualQuestions() {
        StaticListableBeanFactory clients = new StaticListableBeanFactory(Map.of("engine", engine));
        return new StaffMessageService(access, workflow, caseData, new StaffReviewStore(database), staffMessages,
                new CamundaCaseGateway(clients.getBeanProvider(CamundaClient.class), true),
                new CamundaStaffTaskGateway(clients.getBeanProvider(CamundaClient.class), true),
                new CamundaStaffMessageGateway(clients.getBeanProvider(CamundaClient.class), true),
                new CaseWaitStore(database), new StaffDecisionStore(database), replies, new ClarificationQuestionStore(database), transactions);
    }

    private void publishManualQuestion(StaffMessageService service) throws Exception {
        assertTrue(service.available(takeoverActor, caseId).question());
        assertEquals("PENDING", service.send(takeoverActor, caseId, "manual-question", "Welcher Termin passt Ihnen?", true));
        new StaffMessageWorkers(engine, service).publish(engine, job(StaffMessageWorkers.PUBLISH));
    }

    private void signalManualQuestion() throws Exception {
        new ClarificationQuestionWorkers(engine, new ClarificationQuestionStore(database))
                .signal(engine, job(ClarificationQuestionWorkers.SIGNAL));
        new StaffReviewWorkers(engine, reviewService).prepareStaff(engine, job("propertyflow.review.prepare-staff"));
    }

    private void registerManualReply() throws Exception {
        long source = workflow.entryForCase(caseId).orElseThrow().childKey();
        await(() -> List.of("COMPLETED", "TERMINATED").contains(engine.newProcessInstanceGetRequest(source).send().join().getState().name()));
        reply(central()).register(engine, job(TenantReplyWorkers.REGISTER));
    }

    @Test
    void manualQuestionDuringClarificationAtomicallyPublishesAndInterruptsAutomaticCall() throws Exception {
        ActivatedJob staleAnalysis = job(AutomaticEvaluationWorkers.EVALUATE);
        takeoverService();
        StaffMessageService service = manualQuestions();
        publishManualQuestion(service);
        assertEquals("WAITING_FOR_TENANT", status());
        assertEquals(1, count("pf_staff_message"));
        assertEquals(1, count("pf_staff_answer_requirement"));
        assertEquals(2, count("pf_case_status_event"));
        assertEquals(2, count("pf_case_mail_order"));
        assertEquals(1, count("pf_staff_message_mail_order"));
        assertEquals("PUBLISHED", service.send(takeoverActor, caseId, "manual-question", "Welcher Termin passt Ihnen?", true));
        signalManualQuestion();
        registerManualReply();
        assertEquals(1, count("pf_reply_call"));
        assertEquals(0, count("pf_staff_continuation"));
        assertEquals(1, count("pf_staff_message"));
        assertEquals("TERMINATED", engine.newProcessInstanceGetRequest(staleAnalysis.getProcessInstanceKey()).send().join().getState().name());
        assertTrue(tasks().isEmpty());
    }

    @Test
    void mandatoryReasonBeforeManualPublicationRejectsTheQuestionWithoutChangingTheCase() throws Exception {
        takeoverService();
        StaffMessageService service = manualQuestions();
        service.send(takeoverActor, caseId, "manual-question", "Welcher Termin passt Ihnen?", true);
        database.update("""
                INSERT INTO pf_case_review_signal (id, case_id, source_type, source_ref, reason)
                VALUES (?, ?, 'TEST', ?, 'HUMAN_DECISION_REQUIRED')
                """, UUID.randomUUID(), caseId, UUID.randomUUID());
        new StaffMessageWorkers(engine, service).publish(engine, job(StaffMessageWorkers.PUBLISH));
        assertEquals("REJECTED", database.queryForObject("SELECT state FROM pf_staff_message_command", String.class));
        assertEquals("UNDER_CLARIFICATION", status());
        assertEquals(0, count("pf_staff_message"));
        assertEquals(0, count("pf_reply_entry"));
    }

    @Test
    void mandatoryReasonAfterManualPublicationRoutesTheSameMainPathToReview() throws Exception {
        takeoverService();
        publishManualQuestion(manualQuestions());
        database.update("""
                INSERT INTO pf_case_review_signal (id, case_id, source_type, source_ref, reason)
                VALUES (?, ?, 'TEST', ?, 'HUMAN_DECISION_REQUIRED')
                """, UUID.randomUUID(), caseId, UUID.randomUUID());
        signalManualQuestion();
        startReview();
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals(1, count("pf_staff_continuation"));
        assertEquals(1, count("pf_staff_message"));
        assertEquals("OPEN", database.queryForObject("SELECT state FROM pf_staff_answer_requirement", String.class));
    }

    @Test
    void manualQuestionAfterAutomaticCompletionReplacesItsPreparedReplyEntry() throws Exception {
        manualQuestionDuringHandover(false);
    }

    @Test
    void manualQuestionBeforeAutomaticReplyRegistrationUsesItsOwnReplyEntry() throws Exception {
        manualQuestionDuringHandover(true);
    }

    private void manualQuestionDuringHandover(boolean replyStarted) throws Exception {
        AutomaticQuestionWorkers automatic = prepare(central());
        automatic.publish(engine, job(AutomaticQuestionWorkers.PUBLISH));
        automatic.finish(engine, job(AutomaticQuestionWorkers.FINISH));
        if (replyStarted) {
            accept(automatic);
            job(TenantReplyWorkers.REGISTER);
        } else {
            job(AutomaticQuestionWorkers.ACCEPT);
        }
        takeoverService();
        StaffMessageService service = manualQuestions();
        publishManualQuestion(service);
        signalManualQuestion();
        registerManualReply();
        UUID command = staffMessages.find(caseId, "manual-question").orElseThrow().id();
        assertEquals(command, database.queryForObject("SELECT entry_id FROM pf_reply_call", UUID.class));
        assertEquals("WAITING_FOR_TENANT", status());
        assertEquals(3, count("pf_staff_message"));
        assertEquals(3, count("pf_staff_answer_requirement"));
        assertEquals(1, count("pf_reply_call"));
        assertEquals(2, count("pf_case_status_event"));
    }

    @Test
    void manuallyClearedQuestionDuringAutomaticInterruptionDoesNotCreateAnEmptyReplyWait() throws Exception {
        CaseDataService data = takeoverService();
        publishManualQuestion(manualQuestions());
        UUID message = database.queryForObject("SELECT id FROM pf_staff_message", UUID.class);
        data.clearAnswer(takeoverActor, caseId, "clear-manual", database.queryForObject("SELECT version FROM pf_case", Long.class), message);
        new CaseDataWorkers(engine, data).clearAnswer(engine, job("propertyflow.answer.clear"));
        assertEquals("IN_PROGRESS", status());
        new AnswerCancellationWorkers(new AnswerRequirementStore(database), reviewService, engine).dispatch();
        finishTakeover(false);
        assertEquals(1, count("pf_staff_continuation"));
        assertEquals(0, count("pf_reply_call"));
        assertEquals("CANCELLED", database.queryForObject("SELECT state FROM pf_staff_answer_requirement", String.class));
    }

    @Test
    void clearingTheManualQuestionBeforeReplyRegistrationInterruptsThePreparedEntry() throws Exception {
        CaseDataService data = takeoverService();
        publishManualQuestion(manualQuestions());
        signalManualQuestion();
        ActivatedJob registering = job(TenantReplyWorkers.REGISTER);
        clearManualQuestion(data);
        AnswerCancellationWorkers cancellation = new AnswerCancellationWorkers(new AnswerRequirementStore(database), reviewService, engine);
        cancellation.dispatch();
        cancellation.continueWork(engine, job("propertyflow.answer.continue"));
        StaffReviewWorkers staff = new StaffReviewWorkers(engine, reviewService);
        staff.claim(engine, job("propertyflow.staff.claim"));
        staff.register(engine, job("propertyflow.staff.register"));
        await(() -> tasks().size() == 1 && "SWork".equals(tasks().getFirst().getElementId()));
        assertEquals("IN_PROGRESS", status());
        assertEquals(0, count("pf_reply_call"));
        await(() -> "TERMINATED".equals(engine.newProcessInstanceGetRequest(registering.getProcessInstanceKey()).send().join().getState().name()));
    }

    @Test
    void clearingTheLastQuestionPreservesAnUndeliveredMandatoryReason() throws Exception {
        CaseDataService data = takeoverService();
        publishManualQuestion(manualQuestions());
        pendingMandatoryReason();
        clearManualQuestion(data);
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        new AnswerCancellationWorkers(new AnswerRequirementStore(database), reviewService, engine).dispatch();
        finishTakeover(true);
        assertEquals(1, count("pf_staff_continuation"));
        assertEquals(leader, database.queryForObject("SELECT leading_instance_key FROM pf_case_review_signal", Long.class));
    }

    @Test
    void clearingTheLastQuestionPreservesCriticalUrgencyWithoutADeliveryMarker() throws Exception {
        CaseDataService data = takeoverService();
        publishManualQuestion(manualQuestions());
        database.update("UPDATE pf_case SET urgency = 'CRITICAL', urgency_source = 'STAFF' WHERE id = ?", caseId);
        clearManualQuestion(data);
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        new AnswerCancellationWorkers(new AnswerRequirementStore(database), reviewService, engine).dispatch();
        finishTakeover(true);
        assertEquals(1, count("pf_staff_continuation"));
    }

    @Test
    void aMandatoryReasonAfterClearingTheLastQuestionUpgradesTheSameStaffEntry() throws Exception {
        CaseDataService data = takeoverService();
        publishManualQuestion(manualQuestions());
        clearManualQuestion(data);
        assertEquals("IN_PROGRESS", status());
        pendingMandatoryReason();
        new AnswerCancellationWorkers(new AnswerRequirementStore(database), reviewService, engine).dispatch();
        finishTakeover(true);
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals(1, count("pf_staff_continuation"));
        assertEquals("REVIEW", database.queryForObject("SELECT entry_kind FROM pf_staff_continuation", String.class));
    }

    private void clearManualQuestion(CaseDataService data) throws Exception {
        UUID message = database.queryForObject("SELECT id FROM pf_staff_message", UUID.class);
        data.clearAnswer(takeoverActor, caseId, "clear-manual", database.queryForObject("SELECT version FROM pf_case", Long.class), message);
        new CaseDataWorkers(engine, data).clearAnswer(engine, job("propertyflow.answer.clear"));
    }

    private void pendingMandatoryReason() {
        database.update("""
                INSERT INTO pf_case_review_signal (id, case_id, source_type, source_ref, reason)
                VALUES (?, ?, 'TEST', ?, 'HUMAN_DECISION_REQUIRED')
                """, UUID.randomUUID(), caseId, UUID.randomUUID());
    }

    @Test
    void directClosureDuringClarificationStopsTheRootAndRecordsOneHumanDecisionWithoutMail() throws Exception {
        ActivatedJob oldAnalysis = job(AutomaticEvaluationWorkers.EVALUATE);
        CaseDataService data = takeoverService();
        submitClarificationClosure(data);
        new ClarificationClosureWorkers(engine, data).close(engine, job(ClarificationClosureWorkers.APPLY));
        await(() -> "COMPLETED".equals(engine.newProcessInstanceGetRequest(leader).send().join().getState().name()));
        assertEquals("CLOSED", status());
        assertEquals(1, count("pf_case_closure"));
        assertEquals(takeoverActor.accountId(), database.queryForObject("SELECT actor_id FROM pf_case_closure", UUID.class));
        assertEquals(2, count("pf_case_status_event"));
        assertEquals(1, count("pf_case_mail_order"));
        assertEquals(0, count("pf_staff_message"));
        assertEquals("TERMINATED", engine.newProcessInstanceGetRequest(oldAnalysis.getProcessInstanceKey()).send().join().getState().name());
        assertTrue(tasks().isEmpty());
    }

    @Test
    void committedClosureCanBeRetriedBeforeEscalationWithoutRepeatingBusinessEffects() throws Exception {
        CaseDataService data = takeoverService();
        submitClarificationClosure(data);
        ActivatedJob applying = job(ClarificationClosureWorkers.APPLY);
        UUID command = UUID.fromString(applying.getVariablesAsMap().get("clarificationClosureCommandRef").toString());
        assertEquals("APPLIED", data.clarificationClosureState(command, leader, "<default>"));
        assertEquals("CLOSED", status());
        assertEquals("ACTIVE", engine.newProcessInstanceGetRequest(leader).send().join().getState().name());
        CaseWorkflowStore.Entry entry = workflow.entryForCase(caseId).orElseThrow();
        assertEquals("OBSOLETE", central().evaluateAutomatic(caseId, entry.id(), entry.callRef(), leader, entry.childKey()).state());
        new ClarificationClosureWorkers(engine, data).close(engine, applying);
        await(() -> "COMPLETED".equals(engine.newProcessInstanceGetRequest(leader).send().join().getState().name()));
        assertEquals(1, count("pf_case_closure"));
        assertEquals(2, count("pf_case_status_event"));
        assertEquals(1, count("pf_case_mail_order"));
        assertThrows(WorkflowRejectedException.class, () -> data.clarificationClosureState(command, leader + 1, "<default>"));
    }

    @Test
    void closureAfterAutomaticCompletionCancelsQuestionsAndPreservesPublishedMessagesAndMail() throws Exception {
        AutomaticQuestionWorkers automatic = prepare(central());
        automatic.publish(engine, job(AutomaticQuestionWorkers.PUBLISH));
        automatic.finish(engine, job(AutomaticQuestionWorkers.FINISH));
        job(AutomaticQuestionWorkers.ACCEPT);
        CaseDataService data = takeoverService();
        submitClarificationClosure(data);
        new ClarificationClosureWorkers(engine, data).close(engine, job(ClarificationClosureWorkers.APPLY));
        await(() -> "COMPLETED".equals(engine.newProcessInstanceGetRequest(leader).send().join().getState().name()));
        assertEquals(2, count("pf_staff_message"));
        assertEquals(2, count("pf_staff_message_mail_order"));
        assertEquals(2, database.queryForObject("SELECT count(*) FROM pf_staff_answer_requirement WHERE resolution_type = 'CASE_CLOSED'", Integer.class));
        assertEquals(0, count("pf_reply_call"));
    }

    @Test
    void newTenantMessageRejectsDirectClosureAndKeepsAutomaticWorkActive() throws Exception {
        CaseDataService data = takeoverService();
        submitClarificationClosure(data);
        UUID accessId = database.queryForObject("SELECT id FROM pf_case_access WHERE case_id = ?", UUID.class, caseId);
        messages.save(new TenantMessageStore.Access(accessId, caseId), "closure-race", "Neue Information.", leader, "<default>");
        rejectClarificationClosure(data);
    }

    @Test
    void undeliveredMandatoryReasonRejectsDirectClosureEvenWithoutChangingCaseVersion() throws Exception {
        CaseDataService data = takeoverService();
        submitClarificationClosure(data);
        pendingMandatoryReason();
        rejectClarificationClosure(data);
    }

    @Test
    void revokedStaffRoleRejectsDirectClosure() throws Exception {
        CaseDataService data = takeoverService();
        submitClarificationClosure(data);
        database.update("UPDATE pf_staff_account SET staff_role = FALSE WHERE id = ?", takeoverActor.accountId());
        rejectClarificationClosure(data);
    }

    private void submitClarificationClosure(CaseDataService data) {
        ClosureApproval approval = data.clarificationClosure(takeoverActor, caseId);
        assertTrue(approval.available());
        assertEquals("PENDING", data.closeClarification(takeoverActor, caseId, "close-clarification", approval.version(), approval.reviewToken()).state());
    }

    private void rejectClarificationClosure(CaseDataService data) throws Exception {
        new ClarificationClosureWorkers(engine, data).close(engine, job(ClarificationClosureWorkers.APPLY));
        assertEquals("REJECTED", database.queryForObject("SELECT state FROM pf_case_data_command", String.class));
        assertEquals("UNDER_CLARIFICATION", status());
        assertEquals("ACTIVE", engine.newProcessInstanceGetRequest(leader).send().join().getState().name());
        assertEquals(0, count("pf_case_closure"));
        assertEquals(1, count("pf_case_status_event"));
    }

    private ConversationEvaluationService beginAdditionalRound() throws Exception {
        ConversationEvaluationService central = central();
        AutomaticQuestionWorkers worker = prepare(central);
        worker.publish(engine, job(AutomaticQuestionWorkers.PUBLISH));
        worker.finish(engine, job(AutomaticQuestionWorkers.FINISH));
        accept(worker);
        TenantReplyWorkers reply = reply(central);
        reply.register(engine, job(TenantReplyWorkers.REGISTER));
        UUID first = database.queryForObject("SELECT id FROM pf_staff_message WHERE catalog_question_id = 'RQ-001'", UUID.class);
        UUID accessId = database.queryForObject("SELECT id FROM pf_case_access WHERE case_id = ?", UUID.class, caseId);
        TenantMessageStore.Message answer = messages.save(new TenantMessageStore.Access(accessId, caseId), "partial-round",
                "Seit gestern.", leader, "<default>");
        selected.set(List.of("RQ-001", "RQ-003"));
        ConversationEvaluationService answering = new ConversationEvaluationService(workflow, messages, evaluations, urgencies,
                input -> new ConversationAnalysis.Result("NORMAL", false,
                        List.of(new ConversationAnalysis.Answer(first, answer.id(), "Seit gestern.")), null, "test", "answer-v1"),
                input -> new Recommendation.Result("Verwaltung", "Zusätzliche Angabe erforderlich.", List.of(), selected.get(), false,
                        null, "test", "catalog-v1"), noClosure(), transactions, new AutomaticClosureRelease());
        TenantReplyStore.Call waiting = replies.active(caseId, leader).orElseThrow();
        ConversationEvaluationService.Result evaluated = answering.evaluateReply(caseId, waiting.entry(), leader, waiting.child(), waiting.tenant());
        answering.applyReply(caseId, waiting.entry(), leader, waiting.child(), waiting.tenant(), evaluated.evaluationId());
        new TenantReplyDispatcher(replies,
                new TenantReplyService(workflow, replies, evaluations, answering, questionStore, transactions,
                org.mockito.Mockito.mock(AutomaticClosureService.class)), engine).dispatch();
        reply = reply(answering);
        reply.evaluate(engine, job(TenantReplyWorkers.EVALUATE));
        reply.validate(engine, job(TenantReplyWorkers.VALIDATE));
        assertEquals("QUESTION", database.queryForObject("SELECT outcome FROM pf_reply_decision", String.class));
        return central;
    }

    private TenantReplyWorkers reply(ConversationEvaluationService central) {
        return new TenantReplyWorkers(engine, new TenantReplyService(workflow, replies, evaluations, central, questionStore, transactions,
                org.mockito.Mockito.mock(AutomaticClosureService.class)), replies);
    }

    private void prepareRound(ActivatedJob preparing) throws Exception {
        TenantReplyStore.Call source = rounds.source(UUID.fromString(preparing.getVariablesAsMap().get("replyDecisionRef").toString()), caseId, leader);
        await(() -> "COMPLETED".equals(engine.newProcessInstanceGetRequest(source.child()).send().join().getState().name())
                && "COMPLETED".equals(engine.newElementInstanceGetRequest(source.parent()).send().join().getState().name()));
        new AutomaticQuestionRoundWorkers(engine, rounds).prepare(engine, preparing);
    }

    private AutomaticQuestionWorkers prepare(ConversationEvaluationService central) throws Exception {
        AutomaticEvaluationWorkers evaluation = new AutomaticEvaluationWorkers(engine, central);
        evaluation.evaluate(engine, job(AutomaticEvaluationWorkers.EVALUATE));
        evaluation.recommend(engine, job(AutomaticEvaluationWorkers.RECOMMEND));
        AutomaticQuestionWorkers worker = new AutomaticQuestionWorkers(engine, questions, org.mockito.Mockito.mock(AutomaticClosureService.class));
        worker.validate(engine, job(AutomaticQuestionWorkers.VALIDATE));
        return worker;
    }

    private ConversationEvaluationService central() {
        return new ConversationEvaluationService(workflow, messages, evaluations, urgencies, input -> {
            analysesCalled.incrementAndGet();
            return new ConversationAnalysis.Result("NORMAL", false, List.of(), null, "test", "catalog-v1");
        }, input -> new Recommendation.Result("Verwaltung", "Fehlende Sachangaben klären.", List.of(), selected.get(), false, null, "test", "catalog-v1"),
                noClosure(), transactions, new AutomaticClosureRelease());
    }

    private void accept(AutomaticQuestionWorkers worker) throws Exception {
        ActivatedJob result = job(AutomaticQuestionWorkers.ACCEPT);
        CaseWorkflowStore.Entry entry = workflow.entryForCase(caseId).orElseThrow();
        await(() -> "COMPLETED".equals(engine.newProcessInstanceGetRequest(entry.childKey()).send().join().getState().name())
                && "COMPLETED".equals(engine.newElementInstanceGetRequest(entry.parentElementKey()).send().join().getState().name()));
        worker.accept(engine, result);
    }

    private void startReview() throws Exception {
        StaffReviewWorkers worker = new StaffReviewWorkers(engine, reviewService);
        worker.claim(engine, job("propertyflow.staff.claim"));
        worker.register(engine, job("propertyflow.staff.register"));
        worker.startReview(engine, job("propertyflow.staff.start-review"));
        await(() -> tasks().size() == 1);
    }

    private ActivatedJob job(String type) throws Exception {
        for (int attempt = 0; attempt < 40; attempt++) {
            List<ActivatedJob> jobs = engine.newActivateJobsCommand().jobType(type).maxJobsToActivate(1)
                    .timeout(Duration.ofMinutes(2)).requestTimeout(Duration.ofSeconds(1)).send().join().getJobs();
            if (!jobs.isEmpty()) {
                ActivatedJob job = jobs.getFirst();
                await(() -> {
                    try {
                        engine.newProcessInstanceGetRequest(job.getProcessInstanceKey()).send().join();
                        return true;
                    } catch (RuntimeException exception) {
                        return false;
                    }
                });
                return job;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Missing review job: " + type);
    }

    private void await(BooleanSupplier condition) throws Exception {
        for (int attempt = 0; attempt < 40; attempt++) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Engine state did not converge");
    }

    private List<UserTask> tasks() {
        return engine.newUserTaskSearchRequest().filter(filter -> filter.state(UserTaskState.CREATED))
                .send().join().items();
    }

    private String status() {
        return database.queryForObject("SELECT status FROM pf_case", String.class);
    }

    private int count(String table) {
        return database.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
