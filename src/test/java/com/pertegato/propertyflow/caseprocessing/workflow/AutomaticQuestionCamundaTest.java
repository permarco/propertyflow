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
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.ConversationEvaluationBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.persistence.AnswerRequirementStore;
import com.pertegato.propertyflow.caseprocessing.persistence.AutomaticQuestionStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseDataStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWaitStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.StartOrder;
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
import com.pertegato.propertyflow.caseprocessing.service.ConversationEvaluationService.AutomaticContext;
import com.pertegato.propertyflow.caseprocessing.service.ConversationEvaluationService.EvaluationStep;
import com.pertegato.propertyflow.caseprocessing.service.ConversationEvaluationService.ReplyContext;
import com.pertegato.propertyflow.caseprocessing.service.ConversationEvaluationService.StageResult;
import com.pertegato.propertyflow.caseprocessing.service.StaffMessageService;
import com.pertegato.propertyflow.caseprocessing.service.StaffReviewService;
import com.pertegato.propertyflow.caseprocessing.service.TenantReplyService;
import com.pertegato.propertyflow.caseprocessing.service.WorkflowRejectedException;
import com.pertegato.propertyflow.recommendation.service.QuestionCatalog;
import com.pertegato.propertyflow.recommendation.service.Recommendation;
import com.pertegato.propertyflow.triage.service.ConversationAnalysis;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.client.api.search.enums.ProcessInstanceState;
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

import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants.JobType.AUTOMATIC_PREPARE_RESULT;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants.JobType.AUTOMATIC_PUBLISH_QUESTIONS;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants.JobType.AUTOMATIC_RECOMMENDATION;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants.JobType.AUTOMATIC_VALIDATE_QUESTIONS;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants.JobType.REGISTER_AUTOMATIC;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants.JobType.START_CLARIFICATION;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants.Variablen.QUESTION_BATCH_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.ANSWER_CLEAR;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.ANSWER_CONTINUE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.AUTOMATIC_ACCEPT_RESULT;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.AUTOMATIC_PREPARE_QUESTION_ROUND;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.CLARIFICATION_CLOSE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.CLARIFICATION_QUESTION_SIGNAL;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.DIRECT_FOLLOW_UP_APPLY;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.DIRECT_FOLLOW_UP_SIGNAL;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.PREPARE_INITIAL;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.PREPARE_PROCESS_KEY;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.REGISTER_CASE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.REPLY_PREPARE_REVIEW;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.REPLY_WAKE_REVIEW;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.REVIEW_CHECK;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.REVIEW_INTERRUPT_AUTOMATIC;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.REVIEW_PREPARE_STAFF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.STAFF_CLAIM;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.STAFF_RECONCILE_CONTINUATION;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.TAKEOVER_APPLY;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.TAKEOVER_SIGNAL;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.CLARIFICATION_CLOSURE_COMMAND_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.CLEARED_ANSWER_CALL_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.REPLY_DECISION_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.JobType.STAFF_MESSAGE_PUBLISH;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.ACTIVE_REPLY_CALL_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.AUTOMATIC_INTERRUPTION_CALL_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.AUTOMATIC_INTERRUPTION_READY;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.REVIEW_NEED_REPLY_CALL_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.STAFF_ENTRY_CONTEXT_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.ConversationEvaluationBpmnConstants.JobType.AUTOMATIC_ADVISE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.ConversationEvaluationBpmnConstants.JobType.AUTOMATIC_ANALYZE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.ConversationEvaluationBpmnConstants.JobType.AUTOMATIC_ASSESS_CLOSURE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.FollowUpBpmnConstants.JobType.FOLLOW_UP_SAVE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants.JobType.STAFF_REGISTER;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants.JobType.STAFF_START_REVIEW;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.TenantReplyBpmnConstants.JobType.REPLY_APPLY_EVALUATION;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.TenantReplyBpmnConstants.JobType.REPLY_REGISTER;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.TenantReplyBpmnConstants.JobType.REPLY_VALIDATE;
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
        long definitionKey = WorkflowTestModels.deploy(engine).getProcesses().stream()
                .filter(process -> CaseProcessBpmnConstants.PROCESS_ID.equals(process.getBpmnProcessId()))
                .findFirst().orElseThrow().getProcessDefinitionKey();
        accepted = creation.create(new SubmissionContext.Guest(UUID.randomUUID()),
                new CreateCaseCommand("review", "Heizung", "Wohnung", "Kalt", "review@example.test"));
        caseId = accepted.caseId();
        StartOrder start = workflow.claim(definitionKey, "<default>", Duration.ofMinutes(2)).orElseThrow();
        assertEquals(ProcessStartGateway.StartResult.STARTED, new CamundaProcessStartGateway(engine).ensureStarted(start));
        initial = new InitialCaseWorkers(engine, initialService);
        ActivatedJob preparing = job(PREPARE_PROCESS_KEY);
        leader = preparing.getProcessInstanceKey();
        initial.prepareKey(engine, preparing);
        initial.register(engine, job(REGISTER_CASE));
        initial.prepareInitial(engine, job(PREPARE_INITIAL));
        initial.registerAutomatic(engine, job(REGISTER_AUTOMATIC));
        initial.clarification(engine, job(START_CLARIFICATION));
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
        ActivatedJob publishing = job(AUTOMATIC_PUBLISH_QUESTIONS);
        UUID batch = UUID.fromString(publishing.getVariablesAsMap().get(QUESTION_BATCH_REF).toString());
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
        worker.finish(engine, job(AUTOMATIC_PREPARE_RESULT));
        accept(worker);
        TenantReplyWorkers reply = new TenantReplyWorkers(engine,
                new TenantReplyService(workflow, replies, evaluations, central, questionStore, transactions,
                org.mockito.Mockito.mock(AutomaticClosureService.class)), replies);
        reply.register(engine, job(REPLY_REGISTER));
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
        worker.finish(engine, job(AUTOMATIC_PREPARE_RESULT));
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
        ActivatedJob stale = job(AUTOMATIC_PUBLISH_QUESTIONS);
        UUID access = database.queryForObject("SELECT id FROM pf_case_access WHERE case_id = ?", UUID.class, caseId);
        messages.save(new TenantMessageStore.Access(access, caseId), "new-context", "Seit gestern im Wohnzimmer.", leader, "<default>");
        selected.set(List.of("RQ-003"));
        worker.publish(engine, stale);
        assertEquals(0, count("pf_staff_message"));
        assertEquals("STALE", database.queryForObject("SELECT state FROM pf_automatic_question_batch", String.class));
        AutomaticEvaluationWorkers evaluation = new AutomaticEvaluationWorkers(engine, central);
        ActivatedJob refresh = job(AUTOMATIC_ANALYZE);
        assertEquals(ConversationEvaluationBpmnConstants.ServiceTask.ANALYZE_CONVERSATION, refresh.getElementId());
        runAutomaticEvaluationStages(evaluation, refresh);
        worker.validate(engine, job(AUTOMATIC_VALIDATE_QUESTIONS));
        worker.publish(engine, job(AUTOMATIC_PUBLISH_QUESTIONS));
        assertEquals(List.of("Tritt die Störung ständig oder zeitweise auf?"), database.queryForList("SELECT body FROM pf_staff_message", String.class));
        assertEquals(2, analysesCalled.get());
        assertEquals(0, database.queryForObject("SELECT count(*) FROM pf_case_review_signal WHERE state IN ('PENDING','CONFIRMED')", Integer.class));
    }

    @Test
    void newMandatoryReasonAfterPublicationPreservesQuestionsAndSelectsOneReview() throws Exception {
        AutomaticQuestionWorkers worker = prepare(central());
        worker.publish(engine, job(AUTOMATIC_PUBLISH_QUESTIONS));
        database.update("""
                INSERT INTO pf_case_review_signal (id, case_id, source_type, source_ref, reason, leading_instance_key)
                VALUES (?, ?, 'TEST', ?, 'HUMAN_DECISION_REQUIRED', ?)
                """, UUID.randomUUID(), caseId, UUID.randomUUID(), leader);
        worker.finish(engine, job(AUTOMATIC_PREPARE_RESULT));
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
        ActivatedJob publishing = job(AUTOMATIC_PUBLISH_QUESTIONS);
        worker.publish(engine, publishing);
        database.update("UPDATE pf_staff_answer_requirement SET state = 'CANCELLED', resolution_type = 'MANUAL', resolved_at = CURRENT_TIMESTAMP");
        CaseWorkflowStore.Entry entry = workflow.entryForCase(caseId).orElseThrow();
        ConversationEvaluationService.Result evaluated = evaluateAutomaticStages(central, entry);
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
        worker.finish(engine, job(AUTOMATIC_PREPARE_RESULT));
        accept(worker);
        startReview();
        assertEquals(0, count("pf_staff_message"));
        assertEquals("STAFF_REVIEW_REQUIRED", status());
    }

    @Test
    void mandatoryReasonBeforePublicationDoesNotLoopOnAnUnchangedEvaluationVersion() throws Exception {
        AutomaticQuestionWorkers worker = prepare(central());
        ActivatedJob publishing = job(AUTOMATIC_PUBLISH_QUESTIONS);
        database.update("""
                INSERT INTO pf_case_review_signal (id, case_id, source_type, source_ref, reason, leading_instance_key)
                VALUES (?, ?, 'TEST', ?, 'HUMAN_DECISION_REQUIRED', ?)
                """, UUID.randomUUID(), caseId, UUID.randomUUID(), leader);
        worker.publish(engine, publishing);
        assertEquals("REVIEW", database.queryForObject("SELECT state FROM pf_automatic_question_batch", String.class));
        worker.finish(engine, job(AUTOMATIC_PREPARE_RESULT));
        accept(worker);
        startReview();
        assertEquals(0, count("pf_staff_message"));
        assertEquals("STAFF_REVIEW_REQUIRED", status());
    }

    @Test
    void partialAnswerToSystemQuestionKeepsRemainingQuestionWithoutDuplicatePublication() throws Exception {
        ConversationEvaluationService central = central();
        AutomaticQuestionWorkers worker = prepare(central);
        worker.publish(engine, job(AUTOMATIC_PUBLISH_QUESTIONS));
        worker.finish(engine, job(AUTOMATIC_PREPARE_RESULT));
        accept(worker);
        TenantReplyService replyService = new TenantReplyService(workflow, replies, evaluations, central, questionStore, transactions,
                org.mockito.Mockito.mock(AutomaticClosureService.class));
        TenantReplyWorkers reply = new TenantReplyWorkers(engine, replyService, replies);
        reply.register(engine, job(REPLY_REGISTER));
        UUID first = database.queryForObject("SELECT id FROM pf_staff_message WHERE catalog_question_id = 'RQ-001'", UUID.class);
        UUID access = database.queryForObject("SELECT id FROM pf_case_access WHERE case_id = ?", UUID.class, caseId);
        TenantMessageStore.Message answer = messages.save(new TenantMessageStore.Access(access, caseId), "partial", "Seit gestern.", leader, "<default>");
        ConversationEvaluationService answering = new ConversationEvaluationService(workflow, messages, evaluations, urgencies,
                input -> new ConversationAnalysis.Result("NORMAL", false,
                        List.of(new ConversationAnalysis.Answer(first, answer.id(), "Seit gestern.")), null, "test", "answer-v1"),
                input -> new Recommendation.Result("Verwaltung", "Weitere Antwort abwarten.", List.of(), selected.get(), false, null, "test", "catalog-v1"),
                noClosure(), transactions, new AutomaticClosureRelease(), replies);
        prepareReplyEvaluation(answering);
        new TenantReplyDispatcher(replies, replyService, engine).dispatch();
        reply = new TenantReplyWorkers(engine, new TenantReplyService(workflow, replies, evaluations, answering, questionStore, transactions,
                org.mockito.Mockito.mock(AutomaticClosureService.class)), replies);
        applyCachedReplyEvaluation(reply, answering);
        reply.validate(engine, job(REPLY_VALIDATE));
        reply.register(engine, job(REPLY_REGISTER));
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
        worker.publish(engine, job(AUTOMATIC_PUBLISH_QUESTIONS));
        worker.finish(engine, job(AUTOMATIC_PREPARE_RESULT));
        if (replyStarted) {
            accept(worker);
            job(REPLY_REGISTER);
        } else {
            job(AUTOMATIC_ACCEPT_RESULT);
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
        new CaseDataWorkers(engine, data).clearAnswer(engine, job(ANSWER_CLEAR));
        assertEquals("IN_PROGRESS", status());
        AnswerCancellationWorkers cancellation = new AnswerCancellationWorkers(new AnswerRequirementStore(database), reviewService, engine);
        cancellation.dispatch();
        cancellation.continueWork(engine, job(ANSWER_CONTINUE));
        StaffReviewWorkers staff = new StaffReviewWorkers(engine, reviewService);
        staff.claim(engine, job(STAFF_CLAIM));
        staff.register(engine, job(STAFF_REGISTER));
        await(() -> tasks().size() == 1 && "Naechste_Mitarbeiteraktion_abwarten_USERTASK".equals(tasks().getFirst().getElementId()));
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
        worker.finish(engine, job(AUTOMATIC_PREPARE_RESULT));
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
        ActivatedJob prepare = job(AUTOMATIC_PREPARE_QUESTION_ROUND);
        prepareRound(prepare);
        CaseWorkflowStore.Entry second = workflow.entryForCase(caseId).orElseThrow();
        assertNotEquals(first.id(), second.id());
        assertNotEquals(first.callRef(), second.callRef());
        assertEquals(second.id(), rounds.prepare(UUID.fromString(prepare.getVariablesAsMap().get(REPLY_DECISION_REF).toString()),
                caseId, leader, prepare.getElementInstanceKey(),
                UUID.fromString(prepare.getVariablesAsMap().get(ACTIVE_REPLY_CALL_REF).toString())).id());
        initial.registerAutomatic(engine, job(REGISTER_AUTOMATIC));
        engine.newSetVariablesCommand(leader)
                .variables(Map.of(AUTOMATIC_INTERRUPTION_READY, true, AUTOMATIC_INTERRUPTION_CALL_REF, first.callRef().toString()))
                .send()
                .join();
        AutomaticQuestionWorkers worker = prepare(central);
        worker.publish(engine, job(AUTOMATIC_PUBLISH_QUESTIONS));
        worker.finish(engine, job(AUTOMATIC_PREPARE_RESULT));
        engine.newSetVariablesCommand(leader)
                .variables(Map.of(CLEARED_ANSWER_CALL_REF, prepare.getVariablesAsMap().get(ACTIVE_REPLY_CALL_REF),
                        REVIEW_NEED_REPLY_CALL_REF, prepare.getVariablesAsMap().get(ACTIVE_REPLY_CALL_REF)))
                .send()
                .join();
        accept(worker);
        TenantReplyWorkers reply = reply(central);
        reply.register(engine, job(REPLY_REGISTER));
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
        prepareRound(job(AUTOMATIC_PREPARE_QUESTION_ROUND));
        initial.registerAutomatic(engine, job(REGISTER_AUTOMATIC));
        UUID accessId = database.queryForObject("SELECT id FROM pf_case_access WHERE case_id = ?", UUID.class, caseId);
        messages.save(new TenantMessageStore.Access(accessId, caseId), "new-after-reply", "Der Zustand hat sich geändert.", leader, "<default>");
        selected.set(List.of("RQ-004"));
        AutomaticQuestionWorkers worker = prepare(central);
        worker.publish(engine, job(AUTOMATIC_PUBLISH_QUESTIONS));
        assertEquals(0, database.queryForObject("SELECT count(*) FROM pf_staff_message WHERE catalog_question_id = 'RQ-003'", Integer.class));
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_staff_message WHERE catalog_question_id = 'RQ-004'", Integer.class));
        assertEquals("WAITING_FOR_TENANT", status());
    }

    @Test
    void mandatoryReasonDuringQuestionRoundLeadsToOneReviewWithoutNewQuestion() throws Exception {
        ConversationEvaluationService central = beginAdditionalRound();
        prepareRound(job(AUTOMATIC_PREPARE_QUESTION_ROUND));
        initial.registerAutomatic(engine, job(REGISTER_AUTOMATIC));
        database.update("""
                INSERT INTO pf_case_review_signal (id, case_id, source_type, source_ref, reason, leading_instance_key)
                VALUES (?, ?, 'TEST', ?, 'HUMAN_DECISION_REQUIRED', ?)
                """, UUID.randomUUID(), caseId, UUID.randomUUID(), leader);
        AutomaticQuestionWorkers worker = prepare(central);
        worker.finish(engine, job(AUTOMATIC_PREPARE_RESULT));
        accept(worker);
        startReview();
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals(2, count("pf_staff_message"));
        assertEquals(1, count("pf_staff_continuation"));
    }

    @Test
    void mandatoryReasonInterruptsRegisteredQuestionRoundBeforeItsEvaluationCompletes() throws Exception {
        beginAdditionalRound();
        prepareRound(job(AUTOMATIC_PREPARE_QUESTION_ROUND));
        initial.registerAutomatic(engine, job(REGISTER_AUTOMATIC));
        ActivatedJob evaluation = job(AUTOMATIC_ANALYZE);
        database.update("""
                INSERT INTO pf_case_review_signal (id, case_id, source_type, source_ref, reason, leading_instance_key)
                VALUES (?, ?, 'TEST', ?, 'HUMAN_DECISION_REQUIRED', ?)
                """, UUID.randomUUID(), caseId, UUID.randomUUID(), leader);
        new ReviewSignalDispatcher(new StaffReviewStore(database), engine).dispatch();
        StaffReviewWorkers review = new StaffReviewWorkers(engine, reviewService);
        review.check(engine, job(REVIEW_CHECK));
        review.interruptAutomatic(engine, job(REVIEW_INTERRUPT_AUTOMATIC));
        review.prepareStaff(engine, job(REVIEW_PREPARE_STAFF));
        startReview();
        await(() -> "TERMINATED".equals(engine.newProcessInstanceGetRequest(evaluation.getProcessInstanceKey()).send().join().getState().name()));
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals(2, count("pf_staff_message"));
        assertEquals(1, count("pf_staff_continuation"));
    }

    @Test
    void cancellingRemainingQuestionDuringNewAutomaticRoundContinuesToOneWorkTask() throws Exception {
        beginAdditionalRound();
        prepareRound(job(AUTOMATIC_PREPARE_QUESTION_ROUND));
        initial.registerAutomatic(engine, job(REGISTER_AUTOMATIC));
        job(AUTOMATIC_ANALYZE);
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
        new CaseDataWorkers(engine, data).clearAnswer(engine, job(ANSWER_CLEAR));
        assertEquals("IN_PROGRESS", status());
        new AnswerCancellationWorkers(new AnswerRequirementStore(database), reviewService, engine).dispatch();
        StaffReviewWorkers staff = new StaffReviewWorkers(engine, reviewService);
        staff.prepareStaff(engine, job(REVIEW_PREPARE_STAFF));
        staff.claim(engine, job(STAFF_CLAIM));
        staff.register(engine, job(STAFF_REGISTER));
        await(() -> tasks().size() == 1 && "Naechste_Mitarbeiteraktion_abwarten_USERTASK".equals(tasks().getFirst().getElementId()));
        assertEquals(1, count("pf_staff_continuation"));
        assertEquals(2, count("pf_staff_message"));
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_staff_answer_requirement WHERE state = 'ANSWERED'", Integer.class));
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_staff_answer_requirement WHERE state = 'CANCELLED'", Integer.class));
    }

    @Test
    void fullyAnsweredQuestionsTakePriorityOverAdditionalCatalogProposal() throws Exception {
        ConversationEvaluationService central = central();
        AutomaticQuestionWorkers worker = prepare(central);
        worker.publish(engine, job(AUTOMATIC_PUBLISH_QUESTIONS));
        worker.finish(engine, job(AUTOMATIC_PREPARE_RESULT));
        accept(worker);
        reply(central).register(engine, job(REPLY_REGISTER));
        UUID accessId = database.queryForObject("SELECT id FROM pf_case_access WHERE case_id = ?", UUID.class, caseId);
        TenantMessageStore.Message answer = messages.save(new TenantMessageStore.Access(accessId, caseId), "complete-round",
                "Seit gestern im Wohnzimmer.", leader, "<default>");
        ConversationEvaluationService answering = new ConversationEvaluationService(workflow, messages, evaluations, urgencies,
                input -> new ConversationAnalysis.Result("NORMAL", false, input.questions().stream()
                        .map(question -> new ConversationAnalysis.Answer(question.id(), answer.id(), "Seit gestern im Wohnzimmer.")).toList(),
                        null, "test", "answer-v1"),
                input -> new Recommendation.Result("Verwaltung", "Zusätzliche Angabe vorgeschlagen.", List.of(), List.of("RQ-003"), false,
                        null, "test", "catalog-v1"), noClosure(), transactions, new AutomaticClosureRelease(), replies);
        prepareReplyEvaluation(answering);
        TenantReplyService replyService = new TenantReplyService(workflow, replies, evaluations, answering, questionStore, transactions,
                org.mockito.Mockito.mock(AutomaticClosureService.class));
        new TenantReplyDispatcher(replies, replyService, engine).dispatch();
        reply(answering).prepareReview(engine, job(REPLY_PREPARE_REVIEW));
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
        new StaffReviewWorkers(engine, reviewService).check(engine, job(REVIEW_CHECK));
        ActivatedJob wake = job(REPLY_WAKE_REVIEW);
        reply(central).wakeReview(engine, wake);
        await(() -> {
            try {
                return "COMPLETED".equals(engine.newElementInstanceGetRequest(wake.getElementInstanceKey()).send().join().getState().name());
            } catch (RuntimeException exception) {
                return false;
            }
        });
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_case_review_signal WHERE state = 'PENDING'", Integer.class));
        prepareRound(job(AUTOMATIC_PREPARE_QUESTION_ROUND));
        initial.registerAutomatic(engine, job(REGISTER_AUTOMATIC));
        AutomaticQuestionWorkers worker = prepare(central);
        worker.finish(engine, job(AUTOMATIC_PREPARE_RESULT));
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
        staff.prepareStaff(engine, job(REVIEW_PREPARE_STAFF));
        staff.claim(engine, job(STAFF_CLAIM));
        staff.register(engine, job(STAFF_REGISTER));
    }

    @Test
    void directFollowUpStopsAnalysisAndKeepsOneAbsoluteDateAndMailAcrossReplay() throws Exception {
        CaseDataService data = takeoverService();
        UUID id = requestDirect(data);
        Instant due = directFollowUps.get(id).due();
        int mails = count("pf_case_mail_order");
        DirectFollowUpWorkers worker = directWorker(data);
        ActivatedJob apply = job(DIRECT_FOLLOW_UP_APPLY);
        assertEquals("APPLIED", data.directFollowUpState(id, leader, "<default>"));
        worker.apply(engine, apply);
        assertEquals("WAITING_FOR_FOLLOW_UP", status());
        assertEquals(mails + 1, count("pf_case_mail_order"));
        worker.signal(engine, job(DIRECT_FOLLOW_UP_SIGNAL));
        registerDirectStaff();
        CaseWaitWorkers waits = new CaseWaitWorkers(engine, waitService);
        ActivatedJob save = job(FOLLOW_UP_SAVE);
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
        worker.apply(engine, job(DIRECT_FOLLOW_UP_APPLY));
        UUID contact = UUID.randomUUID();
        assertEquals("APPLIED", caseData.get(id).state());
        // Der reguläre Nachrichteneingang speichert Kontakt und Terminbezug in derselben Transaktion.
        database.update("""
                INSERT INTO pf_tenant_message (id, case_id, access_id, operation_id, body, leading_instance_key, tenant_id, analysis_version)
                SELECT ?, id, (SELECT access_id FROM pf_acceptance_mail_order WHERE case_id = ?), ?, 'Neuer Kontakt', ?, '<default>', analysis_version
                FROM pf_case WHERE id = ?
                """, contact, caseId, UUID.randomUUID(), leader, caseId);
        database.update("INSERT INTO pf_wait_contact (message_id, wait_id) VALUES (?, ?)", contact, id);
        worker.signal(engine, job(DIRECT_FOLLOW_UP_SIGNAL));
        registerDirectStaff();
        new StaffReviewWorkers(engine, reviewService).startReview(engine, job(STAFF_START_REVIEW));
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
        worker.apply(engine, job(DIRECT_FOLLOW_UP_APPLY));
        assertEquals(id, database.queryForObject("SELECT considered_direct_command_id FROM pf_case_review_signal WHERE id = ?", UUID.class, known));
        UUID fresh = UUID.randomUUID();
        database.update("INSERT INTO pf_case_review_signal (id, case_id, source_type, source_ref, reason) VALUES (?, ?, 'TEST', ?, 'HUMAN_DECISION_REQUIRED')",
                fresh, caseId, UUID.randomUUID());
        worker.signal(engine, job(DIRECT_FOLLOW_UP_SIGNAL));
        registerDirectStaff();
        new StaffReviewWorkers(engine, reviewService).startReview(engine, job(STAFF_START_REVIEW));
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
        directWorker(data).apply(engine, job(DIRECT_FOLLOW_UP_APPLY));
        assertEquals("REJECTED", caseData.get(id).state());
        assertEquals("UNDER_CLARIFICATION", status());
        assertEquals(0, count("pf_case_wait"));
    }

    @Test
    void directFollowUpReusesAlreadyClaimedStaffEntryWithoutSecondTimer() throws Exception {
        selected.set(List.of());
        AutomaticQuestionWorkers automatic = prepare(central());
        automatic.finish(engine, job(AUTOMATIC_PREPARE_RESULT));
        accept(automatic);
        StaffReviewWorkers staff = new StaffReviewWorkers(engine, reviewService);
        staff.claim(engine, job(STAFF_CLAIM));
        ActivatedJob registering = job(STAFF_REGISTER);
        CaseDataService data = takeoverService();
        UUID id = requestDirect(data);
        DirectFollowUpWorkers worker = directWorker(data);
        worker.apply(engine, job(DIRECT_FOLLOW_UP_APPLY));
        assertEquals("APPLIED", caseData.get(id).state());
        worker.signal(engine, job(DIRECT_FOLLOW_UP_SIGNAL));
        staff.register(engine, registering);
        new CaseWaitWorkers(engine, waitService).save(engine, job(FOLLOW_UP_SAVE));
        assertEquals("WAITING_FOR_FOLLOW_UP", status());
        assertEquals(1, count("pf_staff_continuation"));
        assertEquals(1, count("pf_case_wait"));
    }

    @Test
    void revokedRoleRejectsDirectFollowUpBeforeEffects() throws Exception {
        CaseDataService data = takeoverService();
        UUID id = requestDirect(data);
        database.update("UPDATE pf_staff_account SET staff_role = FALSE WHERE id = ?", takeoverActor.accountId());
        directWorker(data).apply(engine, job(DIRECT_FOLLOW_UP_APPLY));
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
        directWorker(data).apply(engine, job(DIRECT_FOLLOW_UP_APPLY));
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
        prepareRound(job(AUTOMATIC_PREPARE_QUESTION_ROUND));
        initial.registerAutomatic(engine, job(REGISTER_AUTOMATIC));
        CaseWorkflowStore.Entry current = workflow.entryForCase(caseId).orElseThrow();
        UUID signal = UUID.randomUUID();
        database.update("""
                INSERT INTO pf_case_review_signal (id, case_id, source_type, source_ref, reason, leading_instance_key, continuation_id, state)
                VALUES (?, ?, 'TEST', ?, 'HUMAN_DECISION_REQUIRED', ?, ?, 'CONFIRMED')
                """, signal, caseId, UUID.randomUUID(), leader, obsolete);
        StaffReviewWorkers staff = new StaffReviewWorkers(engine, reviewService);
        engine.newModifyProcessInstanceCommand(leader).activateElement("Mitarbeiterfortsetzung_uebernehmen_SERVICE")
                .withVariable(STAFF_ENTRY_CONTEXT_REF, obsolete.toString()).send().join();
        staff.claim(engine, job(STAFF_CLAIM));
        staff.reconcileContinuation(engine, job(STAFF_RECONCILE_CONTINUATION));
        assertEquals("ACTIVE", engine.newProcessInstanceGetRequest(current.childKey()).send().join().getState().name());
        assertEquals("PENDING", database.queryForObject("SELECT state FROM pf_case_review_signal WHERE id = ?", String.class, signal));
        assertEquals(1, count("pf_staff_continuation"));
        new ReviewSignalDispatcher(new StaffReviewStore(database), engine).dispatch();
        staff.check(engine, job(REVIEW_CHECK));
        staff.interruptAutomatic(engine, job(REVIEW_INTERRUPT_AUTOMATIC));
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
        staff.prepareStaff(engine, job(REVIEW_PREPARE_STAFF));
        staff.claim(engine, job(STAFF_CLAIM));
        staff.register(engine, job(STAFF_REGISTER));
        if (review) {
            staff.startReview(engine, job(STAFF_START_REVIEW));
        }
        await(() -> tasks().size() == 1 && (review ? "Fall_pruefen_und_Vorgehen_entscheiden_USERTASK" : "Naechste_Mitarbeiteraktion_abwarten_USERTASK").equals(tasks().getFirst().getElementId()));
    }

    @Test
    void staffTakeoverStopsAutomaticWorkAndCreatesOneWorkTaskWithAudit() throws Exception {
        ActivatedJob analysis = job(AUTOMATIC_ANALYZE);
        CaseDataService data = takeoverService();
        long version = database.queryForObject("SELECT version FROM pf_case", Long.class);
        assertEquals("PENDING", data.takeOver(takeoverActor, caseId, "takeover", version).state());
        StaffTakeoverWorkers worker = takeover(data);
        ActivatedJob applying = job(TAKEOVER_APPLY);
        worker.apply(engine, applying);
        assertEquals("IN_PROGRESS", status());
        assertEquals("APPLIED", data.takeOver(takeoverActor, caseId, "takeover", version).state());
        worker.signal(engine, job(TAKEOVER_SIGNAL));
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
        worker.apply(engine, job(TAKEOVER_APPLY));
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        worker.signal(engine, job(TAKEOVER_SIGNAL));
        finishTakeover(true);
        assertEquals(1, count("pf_staff_continuation"));
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_case_review_signal WHERE considered_decision_id IS NULL", Integer.class));
    }

    @Test
    void newMandatoryReasonBetweenTakeoverCommitAndStaffEntryUpgradesTheSameContinuation() throws Exception {
        CaseDataService data = takeoverService();
        data.takeOver(takeoverActor, caseId, "takeover", database.queryForObject("SELECT version FROM pf_case", Long.class));
        StaffTakeoverWorkers worker = takeover(data);
        worker.apply(engine, job(TAKEOVER_APPLY));
        assertEquals("IN_PROGRESS", status());
        database.update("""
                INSERT INTO pf_case_review_signal (id, case_id, source_type, source_ref, reason)
                VALUES (?, ?, 'TEST', ?, 'HUMAN_DECISION_REQUIRED')
                """, UUID.randomUUID(), caseId, UUID.randomUUID());
        worker.signal(engine, job(TAKEOVER_SIGNAL));
        finishTakeover(true);
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals(1, count("pf_staff_continuation"));
        assertEquals(3, count("pf_case_status_event"));
    }

    @Test
    void staleTakeoverLeavesAutomaticPathAndMessagesUnchanged() throws Exception {
        ActivatedJob analysis = job(AUTOMATIC_ANALYZE);
        CaseDataService data = takeoverService();
        data.takeOver(takeoverActor, caseId, "takeover", database.queryForObject("SELECT version FROM pf_case", Long.class));
        UUID accessId = database.queryForObject("SELECT id FROM pf_case_access WHERE case_id = ?", UUID.class, caseId);
        messages.save(new TenantMessageStore.Access(accessId, caseId), "takeover-race", "Neue Information.", leader, "<default>");
        takeover(data).apply(engine, job(TAKEOVER_APPLY));
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
        takeover(data).apply(engine, job(TAKEOVER_APPLY));
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
        automatic.publish(engine, job(AUTOMATIC_PUBLISH_QUESTIONS));
        automatic.finish(engine, job(AUTOMATIC_PREPARE_RESULT));
        if (replyStarted) {
            accept(automatic);
            job(REPLY_REGISTER);
        } else {
            job(AUTOMATIC_ACCEPT_RESULT);
        }
        CaseDataService data = takeoverService();
        data.takeOver(takeoverActor, caseId, "takeover", database.queryForObject("SELECT version FROM pf_case", Long.class));
        StaffTakeoverWorkers worker = takeover(data);
        worker.apply(engine, job(TAKEOVER_APPLY));
        worker.signal(engine, job(TAKEOVER_SIGNAL));
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
        automatic.finish(engine, job(AUTOMATIC_PREPARE_RESULT));
        accept(automatic);
        StaffReviewWorkers staff = new StaffReviewWorkers(engine, reviewService);
        staff.claim(engine, job(STAFF_CLAIM));
        ActivatedJob registering = job(STAFF_REGISTER);
        UUID continuation = database.queryForObject("SELECT id FROM pf_staff_continuation", UUID.class);
        CaseDataService data = takeoverService();
        data.takeOver(takeoverActor, caseId, "takeover", database.queryForObject("SELECT version FROM pf_case", Long.class));
        StaffTakeoverWorkers worker = takeover(data);
        worker.apply(engine, job(TAKEOVER_APPLY));
        assertEquals(continuation, database.queryForObject("SELECT continuation_id FROM pf_staff_takeover", UUID.class));
        worker.signal(engine, job(TAKEOVER_SIGNAL));
        staff.register(engine, registering);
        staff.startReview(engine, job(STAFF_START_REVIEW));
        await(() -> tasks().size() == 1 && "Fall_pruefen_und_Vorgehen_entscheiden_USERTASK".equals(tasks().getFirst().getElementId()));
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
        new StaffMessageWorkers(engine, service).publish(engine, job(STAFF_MESSAGE_PUBLISH));
    }

    private void signalManualQuestion() throws Exception {
        new ClarificationQuestionWorkers(engine, new ClarificationQuestionStore(database))
                .signal(engine, job(CLARIFICATION_QUESTION_SIGNAL));
        new StaffReviewWorkers(engine, reviewService).prepareStaff(engine, job(REVIEW_PREPARE_STAFF));
    }

    private void registerManualReply() throws Exception {
        long source = workflow.entryForCase(caseId).orElseThrow().childKey();
        await(() -> List.of("COMPLETED", "TERMINATED").contains(engine.newProcessInstanceGetRequest(source).send().join().getState().name()));
        reply(central()).register(engine, job(REPLY_REGISTER));
    }

    @Test
    void manualQuestionDuringClarificationAtomicallyPublishesAndInterruptsAutomaticCall() throws Exception {
        ActivatedJob staleAnalysis = job(AUTOMATIC_ANALYZE);
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
        new StaffMessageWorkers(engine, service).publish(engine, job(STAFF_MESSAGE_PUBLISH));
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
        automatic.publish(engine, job(AUTOMATIC_PUBLISH_QUESTIONS));
        automatic.finish(engine, job(AUTOMATIC_PREPARE_RESULT));
        if (replyStarted) {
            accept(automatic);
            job(REPLY_REGISTER);
        } else {
            job(AUTOMATIC_ACCEPT_RESULT);
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
        new CaseDataWorkers(engine, data).clearAnswer(engine, job(ANSWER_CLEAR));
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
        ActivatedJob registering = job(REPLY_REGISTER);
        clearManualQuestion(data);
        AnswerCancellationWorkers cancellation = new AnswerCancellationWorkers(new AnswerRequirementStore(database), reviewService, engine);
        cancellation.dispatch();
        cancellation.continueWork(engine, job(ANSWER_CONTINUE));
        StaffReviewWorkers staff = new StaffReviewWorkers(engine, reviewService);
        staff.claim(engine, job(STAFF_CLAIM));
        staff.register(engine, job(STAFF_REGISTER));
        await(() -> tasks().size() == 1 && "Naechste_Mitarbeiteraktion_abwarten_USERTASK".equals(tasks().getFirst().getElementId()));
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
        new CaseDataWorkers(engine, data).clearAnswer(engine, job(ANSWER_CLEAR));
    }

    private void pendingMandatoryReason() {
        database.update("""
                INSERT INTO pf_case_review_signal (id, case_id, source_type, source_ref, reason)
                VALUES (?, ?, 'TEST', ?, 'HUMAN_DECISION_REQUIRED')
                """, UUID.randomUUID(), caseId, UUID.randomUUID());
    }

    @Test
    void directClosureDuringClarificationStopsTheRootAndRecordsOneHumanDecisionWithoutMail() throws Exception {
        ActivatedJob oldAnalysis = job(AUTOMATIC_ANALYZE);
        CaseDataService data = takeoverService();
        submitClarificationClosure(data);
        new ClarificationClosureWorkers(engine, data).close(engine, job(CLARIFICATION_CLOSE));
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
        ActivatedJob applying = job(CLARIFICATION_CLOSE);
        UUID command = UUID.fromString(applying.getVariablesAsMap().get(CLARIFICATION_CLOSURE_COMMAND_REF).toString());
        assertEquals("APPLIED", data.clarificationClosureState(command, leader, "<default>"));
        assertEquals("CLOSED", status());
        assertEquals("ACTIVE", engine.newProcessInstanceGetRequest(leader).send().join().getState().name());
        CaseWorkflowStore.Entry entry = workflow.entryForCase(caseId).orElseThrow();
        assertEquals("OBSOLETE", evaluateAutomaticStages(central(), entry).state());
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
        automatic.publish(engine, job(AUTOMATIC_PUBLISH_QUESTIONS));
        automatic.finish(engine, job(AUTOMATIC_PREPARE_RESULT));
        job(AUTOMATIC_ACCEPT_RESULT);
        CaseDataService data = takeoverService();
        submitClarificationClosure(data);
        new ClarificationClosureWorkers(engine, data).close(engine, job(CLARIFICATION_CLOSE));
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
        new ClarificationClosureWorkers(engine, data).close(engine, job(CLARIFICATION_CLOSE));
        assertEquals("REJECTED", database.queryForObject("SELECT state FROM pf_case_data_command", String.class));
        assertEquals("UNDER_CLARIFICATION", status());
        assertEquals("ACTIVE", engine.newProcessInstanceGetRequest(leader).send().join().getState().name());
        assertEquals(0, count("pf_case_closure"));
        assertEquals(1, count("pf_case_status_event"));
    }

    private ConversationEvaluationService beginAdditionalRound() throws Exception {
        ConversationEvaluationService central = central();
        AutomaticQuestionWorkers worker = prepare(central);
        worker.publish(engine, job(AUTOMATIC_PUBLISH_QUESTIONS));
        worker.finish(engine, job(AUTOMATIC_PREPARE_RESULT));
        accept(worker);
        TenantReplyWorkers reply = reply(central);
        reply.register(engine, job(REPLY_REGISTER));
        UUID first = database.queryForObject("SELECT id FROM pf_staff_message WHERE catalog_question_id = 'RQ-001'", UUID.class);
        UUID accessId = database.queryForObject("SELECT id FROM pf_case_access WHERE case_id = ?", UUID.class, caseId);
        TenantMessageStore.Message answer = messages.save(new TenantMessageStore.Access(accessId, caseId), "partial-round",
                "Seit gestern.", leader, "<default>");
        selected.set(List.of("RQ-001", "RQ-003"));
        ConversationEvaluationService answering = new ConversationEvaluationService(workflow, messages, evaluations, urgencies,
                input -> new ConversationAnalysis.Result("NORMAL", false,
                        List.of(new ConversationAnalysis.Answer(first, answer.id(), "Seit gestern.")), null, "test", "answer-v1"),
                input -> new Recommendation.Result("Verwaltung", "Zusätzliche Angabe erforderlich.", List.of(), selected.get(), false,
                        null, "test", "catalog-v1"), noClosure(), transactions, new AutomaticClosureRelease(), replies);
        prepareReplyEvaluation(answering);
        new TenantReplyDispatcher(replies,
                new TenantReplyService(workflow, replies, evaluations, answering, questionStore, transactions,
                org.mockito.Mockito.mock(AutomaticClosureService.class)), engine).dispatch();
        reply = reply(answering);
        applyCachedReplyEvaluation(reply, answering);
        reply.validate(engine, job(REPLY_VALIDATE));
        assertEquals("QUESTION", database.queryForObject("SELECT outcome FROM pf_reply_decision", String.class));
        return central;
    }

    private TenantReplyWorkers reply(ConversationEvaluationService central) {
        return new TenantReplyWorkers(engine, new TenantReplyService(workflow, replies, evaluations, central, questionStore, transactions,
                org.mockito.Mockito.mock(AutomaticClosureService.class)), replies);
    }

    private void prepareReplyEvaluation(ConversationEvaluationService central) {
        TenantReplyStore.Call waiting = replies.active(caseId, leader).orElseThrow();
        ReplyContext context = new ReplyContext(caseId, waiting.entry(), leader, waiting.child(), waiting.tenant());
        UUID attempt = UUID.randomUUID();
        StageResult stage = central.evaluateReplyStage(context, EvaluationStep.ANALYZE, attempt, null);
        while (stage.nextStep() != null) {
            stage = central.evaluateReplyStage(context, stage.nextStep(), attempt, stage.result().evaluationId());
        }
        assertEquals("READY", stage.result().state());
        assertEquals("APPLIED", central.applyReply(caseId, waiting.entry(), leader, waiting.child(), waiting.tenant(),
                stage.result().evaluationId()).state());
    }

    private void applyCachedReplyEvaluation(TenantReplyWorkers reply, ConversationEvaluationService central) throws Exception {
        new AutomaticEvaluationWorkers(engine, central).analyze(engine, job(AUTOMATIC_ANALYZE));
        reply.applyEvaluation(engine, job(REPLY_APPLY_EVALUATION));
    }

    private ConversationEvaluationService.Result evaluateAutomaticStages(ConversationEvaluationService central, CaseWorkflowStore.Entry entry) {
        AutomaticContext context = new AutomaticContext(caseId, entry.id(), entry.callRef(), leader, entry.childKey());
        UUID attempt = UUID.randomUUID();
        StageResult stage = central.evaluateAutomaticStage(context, EvaluationStep.ANALYZE, attempt, null);
        while (stage.nextStep() != null) {
            stage = central.evaluateAutomaticStage(context, stage.nextStep(), attempt, stage.result().evaluationId());
        }
        return stage.result();
    }

    private void prepareRound(ActivatedJob preparing) throws Exception {
        TenantReplyStore.Call source = rounds.source(
                UUID.fromString(preparing.getVariablesAsMap().get(REPLY_DECISION_REF).toString()), caseId, leader);
        await(() -> "COMPLETED".equals(engine.newProcessInstanceGetRequest(source.child()).send().join().getState().name())
                && "COMPLETED".equals(engine.newElementInstanceGetRequest(source.parent()).send().join().getState().name()));
        new AutomaticQuestionRoundWorkers(engine, rounds).prepare(engine, preparing);
    }

    private AutomaticQuestionWorkers prepare(ConversationEvaluationService central) throws Exception {
        AutomaticEvaluationWorkers evaluation = new AutomaticEvaluationWorkers(engine, central);
        runAutomaticEvaluationStages(evaluation, job(AUTOMATIC_ANALYZE));
        // Fortsetzungen gehen im Originalmodell direkt zur Aktionsprüfung.
        if ("INITIAL".equals(workflow.entryKind(workflow.entryForCase(caseId).orElseThrow().id()))) {
            evaluation.recommend(engine, job(AUTOMATIC_RECOMMENDATION));
        }
        AutomaticQuestionWorkers worker = new AutomaticQuestionWorkers(engine, questions, org.mockito.Mockito.mock(AutomaticClosureService.class));
        worker.validate(engine, job(AUTOMATIC_VALIDATE_QUESTIONS));
        return worker;
    }

    private void runAutomaticEvaluationStages(AutomaticEvaluationWorkers evaluation, ActivatedJob analysis) throws Exception {
        evaluation.analyze(engine, analysis);
        if (hasNextEvaluationStage(analysis.getProcessInstanceKey(), ConversationEvaluationBpmnConstants.ServiceTask.RECOMMEND_PROCEDURE)) {
            evaluation.advise(engine, job(AUTOMATIC_ADVISE));
        }
        if (hasNextEvaluationStage(analysis.getProcessInstanceKey(), ConversationEvaluationBpmnConstants.ServiceTask.ASSESS_CLOSURE)) {
            evaluation.assessClosure(engine, job(AUTOMATIC_ASSESS_CLOSURE));
        }
    }

    private boolean hasNextEvaluationStage(long process, String element) throws InterruptedException {
        for (int attempt = 0; attempt < 40; attempt++) {
            if (engine.newProcessInstanceGetRequest(process).send().join().getState() != ProcessInstanceState.ACTIVE) {
                return false;
            }
            if (!engine.newElementInstanceSearchRequest()
                    .filter(filter -> filter.processInstanceKey(process).elementId(element).state(ElementInstanceState.ACTIVE))
                    .send().join().items().isEmpty()) {
                return true;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Evaluation stage did not become active or complete: " + element);
    }

    private ConversationEvaluationService central() {
        return new ConversationEvaluationService(workflow, messages, evaluations, urgencies, input -> {
            analysesCalled.incrementAndGet();
            return new ConversationAnalysis.Result("NORMAL", false, List.of(), null, "test", "catalog-v1");
        }, input -> new Recommendation.Result("Verwaltung", "Fehlende Sachangaben klären.", List.of(), selected.get(), false, null, "test", "catalog-v1"),
                noClosure(), transactions, new AutomaticClosureRelease(), replies);
    }

    private void accept(AutomaticQuestionWorkers worker) throws Exception {
        ActivatedJob result = job(AUTOMATIC_ACCEPT_RESULT);
        CaseWorkflowStore.Entry entry = workflow.entryForCase(caseId).orElseThrow();
        await(() -> "COMPLETED".equals(engine.newProcessInstanceGetRequest(entry.childKey()).send().join().getState().name())
                && "COMPLETED".equals(engine.newElementInstanceGetRequest(entry.parentElementKey()).send().join().getState().name()));
        worker.accept(engine, result);
    }

    private void startReview() throws Exception {
        StaffReviewWorkers worker = new StaffReviewWorkers(engine, reviewService);
        worker.claim(engine, job(STAFF_CLAIM));
        worker.register(engine, job(STAFF_REGISTER));
        worker.startReview(engine, job(STAFF_START_REVIEW));
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
