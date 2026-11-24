package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.access.contract.StaffAccountAccess;
import com.pertegato.propertyflow.access.contract.SubmissionContext;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.caseprocessing.contract.CaseCreation;
import com.pertegato.propertyflow.caseprocessing.contract.CreateCaseCommand;
import com.pertegato.propertyflow.caseprocessing.contract.FollowUpCommands;
import com.pertegato.propertyflow.caseprocessing.contract.StaffTaskDecisions.StaffTask;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.delivery.StaffTaskGateway;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseDataStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWaitStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.StartOrder;
import com.pertegato.propertyflow.caseprocessing.persistence.ClarificationQuestionStore;
import com.pertegato.propertyflow.caseprocessing.persistence.FollowUpCommandStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffChangeStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffDecisionStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffMessageStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffReviewStore;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantMessageStore;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantReplyStore;
import com.pertegato.propertyflow.caseprocessing.service.CaseClosureService;
import com.pertegato.propertyflow.caseprocessing.service.CaseWaitService;
import com.pertegato.propertyflow.caseprocessing.service.CaseWorkflowService;
import com.pertegato.propertyflow.caseprocessing.service.FollowUpCommandService;
import com.pertegato.propertyflow.caseprocessing.service.StaffDecisionService;
import com.pertegato.propertyflow.caseprocessing.service.StaffMessageService;
import com.pertegato.propertyflow.caseprocessing.service.StaffReviewService;
import com.pertegato.propertyflow.triage.service.AnalyzeTool.AnalysisModel;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.response.UserTask;
import java.net.URI;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants.JobType.AUTOMATIC_RECOMMENDATION;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.ConversationEvaluationBpmnConstants.JobType.AUTOMATIC_ANALYZE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants.JobType.REGISTER_AUTOMATIC;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants.JobType.START_CLARIFICATION;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.PREPARE_INITIAL;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.PREPARE_PROCESS_KEY;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.REGISTER_CASE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.REVIEW_ANALYSIS_FAILURE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.REVIEW_CHECK;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.REVIEW_INTERRUPT_AUTOMATIC;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.STAFF_CLAIM;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.STAFF_CLOSE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.JobType.STAFF_MESSAGE_PUBLISH;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.PENDING_REVIEW_FOLLOW_UP_VERSION;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.PHASE_REVIEW_SIGNAL_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.STAFF_DECISION_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.FollowUpBpmnConstants.JobType.FOLLOW_UP_CHANGE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.FollowUpBpmnConstants.JobType.FOLLOW_UP_CLOSE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.FollowUpBpmnConstants.JobType.FOLLOW_UP_SAVE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.FollowUpBpmnConstants.JobType.FOLLOW_UP_TIMER;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.FollowUpBpmnConstants.Variablen.FOLLOW_UP_COMMAND_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants.JobType.FOLLOW_UP_STOP_CONTACT;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants.JobType.STAFF_PROGRESS;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants.JobType.STAFF_REGISTER;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants.JobType.STAFF_START_REVIEW;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants.JobType.STAFF_WAIT_EXTERNAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "propertyflow.workflow.enabled=false")
@ActiveProfiles("propertyflow-backend")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_DATABASE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_CAMUNDA_URL", matches = ".+")
class CaseWaitCamundaTest {

    @Autowired
    private CaseCreation creation;

    @Autowired
    private CaseWorkflowStore workflow;

    @Autowired
    private CaseWorkflowService initialService;

    @Autowired
    private StaffReviewService reviewService;

    @Autowired
    private StaffReviewStore reviewStore;

    @Autowired
    private JdbcTemplate database;

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private StaffAccountAccess access;

    @Autowired
    private CaseDataStore caseData;

    @Autowired
    private StaffDecisionStore decisions;

    private WebAccountContext actor;
    private CamundaClient engine;
    private InitialCaseWorkers initial;
    private StaffReviewWorkers reviews;
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
        caseId = creation.create(new SubmissionContext.Guest(UUID.randomUUID()),
                new CreateCaseCommand("review", "Heizung", "Wohnung", "Kalt", "review@example.test")).caseId();
        StartOrder start = workflow.claim(definitionKey, "<default>", Duration.ofMinutes(2)).orElseThrow();
        assertEquals(ProcessStartGateway.StartResult.STARTED, new CamundaProcessStartGateway(engine).ensureStarted(start));
        initial = new InitialCaseWorkers(engine, initialService);
        reviews = new StaffReviewWorkers(engine, reviewService);
        ActivatedJob preparing = job(PREPARE_PROCESS_KEY);
        leader = preparing.getProcessInstanceKey();
        initial.prepareKey(engine, preparing);
        initial.register(engine, job(REGISTER_CASE));
        initial.prepareInitial(engine, job(PREPARE_INITIAL));
        initial.registerAutomatic(engine, job(REGISTER_AUTOMATIC));
        initial.clarification(engine, job(START_CLARIFICATION));
        assertEquals("UNDER_CLARIFICATION", status());
        AutomaticEvaluationWorkers analysis = InitialAnalysisTestFixture.failedEvaluation(engine, workflow, database, transactions);
        analysis.analyze(engine, job(AUTOMATIC_ANALYZE));
        analysis.recommend(engine, job(AUTOMATIC_RECOMMENDATION));
        reviews.analysisFailure(engine, job(REVIEW_ANALYSIS_FAILURE));
        reviews.claim(engine, job(STAFF_CLAIM));
        reviews.register(engine, job(STAFF_REGISTER));
        reviews.startReview(engine, job(STAFF_START_REVIEW));
        await(() -> tasks().size() == 1);
        actor = new WebAccountContext(UUID.randomUUID());
        database.update("""
                INSERT INTO pf_staff_account (id, name, email, active, staff_role, account_administration)
                VALUES (?, 'Mitarbeiter', ?, TRUE, TRUE, FALSE)
                """, actor.accountId(), actor.accountId() + "@example.test");
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
    private CaseWaitService waitService;

    @Test
    void nativeFollowUpStartsRealAbsoluteTimerAndReturnsExactlyOneReviewTask() throws Exception {
        StaffDecisionService service = service(new CamundaStaffTaskGateway(
                new StaticListableBeanFactory(Map.of("engine", engine)).getBeanProvider(CamundaClient.class), true));
        StaffTask task = service.current(actor, caseId);
        service.followUp(actor, caseId, UUID.randomUUID(), Long.parseLong(task.taskKey()), task.version(), task.reviewToken(), "", 3, null);
        ActivatedJob save = job(FOLLOW_UP_SAVE);
        UUID decision = UUID.fromString(save.getVariablesAsMap().get(STAFF_DECISION_REF).toString());
        database.update("UPDATE pf_staff_wait_request SET due_at = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE decision_id = ?", decision);
        CaseWaitWorkers worker = new CaseWaitWorkers(engine, waitService);
        worker.save(engine, save);
        assertEquals("WAITING_FOR_FOLLOW_UP", status());
        worker.timer(engine, job(FOLLOW_UP_TIMER));
        reviews.startReview(engine, job(STAFF_START_REVIEW));
        await(() -> tasks().size() == 1 && "Fall_pruefen_und_Vorgehen_entscheiden_USERTASK".equals(tasks().getFirst().getElementId()));
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals(1, count("pf_wait_resolution"));
        assertEquals(4, count("pf_case_mail_order"));
        assertTrue(!task.taskKey().equals(Long.toString(tasks().getFirst().getUserTaskKey())));
    }

    @Autowired
    private CaseWaitStore waitStore;
    @Autowired
    private TenantMessageStore messages;

    @Autowired
    private StaffMessageStore staffMessages;

    @Autowired
    private FollowUpCommandStore followUpChanges;

    @Test
    void changedTimerUsesNewEpochAndDeletionReturnsToNativeWorkTask() throws Exception {
        CamundaStaffTaskGateway taskGateway = new CamundaStaffTaskGateway(
                new StaticListableBeanFactory(Map.of("engine", engine)).getBeanProvider(CamundaClient.class), true);
        StaffDecisionService decisions = service(taskGateway);
        StaffTask task = decisions.current(actor, caseId);
        decisions.followUp(actor, caseId, UUID.randomUUID(), Long.parseLong(task.taskKey()), task.version(), task.reviewToken(), "", 3, null);
        new CaseWaitWorkers(engine, waitService).save(engine, job(FOLLOW_UP_SAVE));
        FollowUpCommandService commands =
                new FollowUpCommandService(access, workflow, caseData, waitStore,
                        this.decisions, followUpChanges, new CamundaFollowUpGateway(
                                new StaticListableBeanFactory(Map.of("engine", engine)).getBeanProvider(CamundaClient.class), true), transactions);
        await(() -> commands.current(actor, caseId).available());
        FollowUpCommands.Current current = commands.current(actor, caseId);
        UUID operation = UUID.randomUUID();
        commands.submit(actor, caseId, operation, current.waitId(), current.version(), false, 8, null, "Neuer Termin");
        FollowUpCommandWorkers worker = new FollowUpCommandWorkers(engine, commands);
        worker.apply(engine, job(FOLLOW_UP_CHANGE));
        await(() -> commands.current(actor, caseId).available());
        assertEquals("APPLIED", commands.result(actor, caseId, operation));
        FollowUpCommands.Current changed = commands.current(actor, caseId);
        assertTrue(!current.waitId().equals(changed.waitId()));
        assertEquals("WAITING_FOR_FOLLOW_UP", status());
        UUID delete = UUID.randomUUID();
        commands.submit(actor, caseId, delete, changed.waitId(), changed.version(), true, null, null, "Weiter bearbeiten");
        worker.apply(engine, job(FOLLOW_UP_CHANGE));
        new StaffDecisionWorkers(engine, decisions).progress(engine, job(STAFF_PROGRESS));
        await(() -> tasks().size() == 1 && "Naechste_Mitarbeiteraktion_abwarten_USERTASK".equals(tasks().getFirst().getElementId()));
        assertEquals("APPLIED", commands.result(actor, caseId, delete));
        assertEquals("IN_PROGRESS", status());
        assertTrue(waitStore.active(caseId).isEmpty());
        assertEquals(2, count("pf_case_wait"));
    }

    @Test
    void followUpQuestionPublishesWithoutChangingTimerOrNativePhase() throws Exception {
        CamundaStaffTaskGateway taskGateway = new CamundaStaffTaskGateway(
                new StaticListableBeanFactory(Map.of("engine", engine)).getBeanProvider(CamundaClient.class), true);
        StaffDecisionService decisions = service(taskGateway);
        StaffTask task = decisions.current(actor, caseId);
        decisions.followUp(actor, caseId, UUID.randomUUID(), Long.parseLong(task.taskKey()), task.version(), task.reviewToken(), "", 3, null);
        CaseWaitWorkers worker = new CaseWaitWorkers(engine, waitService);
        ActivatedJob save = job(FOLLOW_UP_SAVE);
        worker.save(engine, save);
        UUID waitId = database.queryForObject("SELECT id FROM pf_case_wait", UUID.class);
        Timestamp dueAt = database.queryForObject("SELECT follow_up_due_at FROM pf_case", Timestamp.class);
        await(() -> !engine.newMessageSubscriptionSearchRequest().filter(filter -> filter.processInstanceKey(save.getProcessInstanceKey())
                .messageName(CamundaStaffMessageGateway.FOLLOW_UP_MESSAGE)).send().join().items().isEmpty());
        StaffMessageService communication =
                new StaffMessageService(access, workflow, caseData, reviewStore, staffMessages,
                        new CamundaCaseGateway(new StaticListableBeanFactory(Map.of("engine", engine)).getBeanProvider(CamundaClient.class), true),
                        taskGateway, new CamundaStaffMessageGateway(
                                new StaticListableBeanFactory(Map.of("engine", engine)).getBeanProvider(CamundaClient.class), true),
                        waitStore, this.decisions, new TenantReplyStore(database), new ClarificationQuestionStore(database), transactions);
        assertTrue(communication.available(actor, caseId).question());
        assertEquals("PENDING", communication.send(actor, caseId, "wait-question", "Welcher Raum ist betroffen?", true));
        new StaffMessageWorkers(engine, communication).publish(engine, job(STAFF_MESSAGE_PUBLISH));
        assertEquals("PUBLISHED", communication.result(actor, caseId, "wait-question"));
        assertEquals("WAITING_FOR_FOLLOW_UP", status());
        assertEquals(waitId, database.queryForObject("SELECT id FROM pf_case_wait WHERE state = 'ACTIVE'", UUID.class));
        assertEquals(dueAt, database.queryForObject("SELECT follow_up_due_at FROM pf_case", Timestamp.class));
        assertEquals("OPEN", database.queryForObject("SELECT state FROM pf_staff_answer_requirement", String.class));
        assertEquals(1, count("pf_staff_message_mail_order"));
        assertEquals("ACTIVE", engine.newProcessInstanceGetRequest(save.getProcessInstanceKey()).send().join().getState().name());
        assertTrue(tasks().isEmpty());
        assertEquals("PUBLISHED", communication.send(actor, caseId, "wait-question", "Welcher Raum ist betroffen?", true));
        assertEquals(1, count("pf_staff_message"));
    }

    @Test
    void durableContactInterruptsCurrentFollowUpBeforeAnyModelEvaluation() throws Exception {
        StaffDecisionService service = service(new CamundaStaffTaskGateway(
                new StaticListableBeanFactory(Map.of("engine", engine)).getBeanProvider(CamundaClient.class), true));
        StaffTask task = service.current(actor, caseId);
        service.followUp(actor, caseId, UUID.randomUUID(), Long.parseLong(task.taskKey()), task.version(), task.reviewToken(), "", 3, null);
        CaseWaitWorkers worker = new CaseWaitWorkers(engine, waitService);
        ActivatedJob save = job(FOLLOW_UP_SAVE);
        worker.save(engine, save);
        UUID accessId = database.queryForObject("SELECT id FROM pf_case_access WHERE case_id = ?", UUID.class, caseId);
        messages.save(new TenantMessageStore.Access(accessId, caseId),
                "contact", "Neue Information", leader, "<default>");
        new FollowUpContactDispatcher(waitStore, engine).dispatch();
        worker.stopContact(engine, job(FOLLOW_UP_STOP_CONTACT));
        reviews.startReview(engine, job(STAFF_START_REVIEW));
        await(() -> tasks().size() == 1 && "Fall_pruefen_und_Vorgehen_entscheiden_USERTASK".equals(tasks().getFirst().getElementId()));
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals("CONTACT", database.queryForObject("SELECT state FROM pf_case_wait", String.class));
        assertEquals(1, count("pf_wait_resolution"));
        await(() -> "TERMINATED".equals(engine.newProcessInstanceGetRequest(save.getProcessInstanceKey()).send().join().getState().name()));
    }

    @Test
    void newMandatoryReasonInterruptsExternalWaitAndCreatesOneCurrentReviewTask() throws Exception {
        StaffDecisionService service = service(new CamundaStaffTaskGateway(
                new StaticListableBeanFactory(Map.of("engine", engine)).getBeanProvider(CamundaClient.class), true));
        StaffTask task = service.current(actor, caseId);
        service.continueWork(actor, caseId, UUID.randomUUID(), Long.parseLong(task.taskKey()), task.version(), task.reviewToken());
        new StaffDecisionWorkers(engine, service).progress(engine, job(STAFF_PROGRESS));
        await(() -> tasks().size() == 1 && "Naechste_Mitarbeiteraktion_abwarten_USERTASK".equals(tasks().getFirst().getElementId()));
        StaffTask work = service.current(actor, caseId);
        service.waitExternal(actor, caseId, UUID.randomUUID(), Long.parseLong(work.taskKey()), work.version(), work.reviewToken());
        new CaseWaitWorkers(engine, waitService).external(engine, job(STAFF_WAIT_EXTERNAL));
        await(() -> tasks().size() == 1 && "Externe_Rueckmeldung_abwarten_USERTASK".equals(tasks().getFirst().getElementId()));
        long oldTask = tasks().getFirst().getUserTaskKey();
        database.update("""
                INSERT INTO pf_case_review_signal (id, case_id, source_type, source_ref, reason, leading_instance_key)
                VALUES (?, ?, 'TEST', ?, 'CRITICAL_URGENCY', ?)
                """, UUID.randomUUID(), caseId, UUID.randomUUID(), leader);
        new ReviewSignalDispatcher(reviewStore, engine).dispatch();
        reviews.check(engine, job(REVIEW_CHECK));
        reviews.interruptAutomatic(engine, job(REVIEW_INTERRUPT_AUTOMATIC));
        reviews.startReview(engine, job(STAFF_START_REVIEW));
        await(() -> tasks().size() == 1 && "Fall_pruefen_und_Vorgehen_entscheiden_USERTASK".equals(tasks().getFirst().getElementId()));
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertTrue(oldTask != tasks().getFirst().getUserTaskKey());
        assertEquals("REVIEW", database.queryForObject("SELECT state FROM pf_case_wait", String.class));
    }

    @Autowired
    private CaseClosureService closure;

    @Test
    void followUpClosureEndsActualChildrenAndCommitsOneClosure() throws Exception {
        FollowUpCommandService commands = enterFollowUp();
        FollowUpCommands.Current current = commands.current(actor, caseId);
        UUID operation = UUID.randomUUID();
        assertEquals("PENDING", commands.close(actor, caseId, operation, current.waitId(), current.version(), "Erledigung geprüft"));
        assertEquals("PENDING", commands.close(actor, caseId, operation, current.waitId(), current.version(), "Erledigung geprüft"));
        ActivatedJob preparing = job(FOLLOW_UP_CLOSE);
        FollowUpCommandWorkers worker = new FollowUpCommandWorkers(engine, commands);
        worker.close(engine, preparing);
        assertEquals("WAITING_FOR_FOLLOW_UP", status());
        assertEquals("APPLYING", database.queryForObject("SELECT state FROM pf_follow_up_command", String.class));
        assertEquals("ACTIVE", database.queryForObject("SELECT state FROM pf_case_wait", String.class));
        ActivatedJob closing = closingJob();
        UUID command = UUID.fromString(closing.getVariablesAsMap().get(STAFF_DECISION_REF).toString());
        long child = closure.context(caseId, leader, command).child();
        new CaseClosureWorkers(engine, closure).close(engine, closing);
        await(() -> "COMPLETED".equals(engine.newProcessInstanceGetRequest(leader).send().join().getState().name()));
        assertEquals("CLOSED", status());
        assertEquals("APPLIED", commands.result(actor, caseId, operation));
        assertEquals("APPLIED", commands.close(actor, caseId, operation, current.waitId(), current.version(), "Erledigung geprüft"));
        assertEquals("CLOSED", closure.close(caseId, leader, child, command).state());
        assertEquals(1, count("pf_case_closure"));
        assertEquals("CLOSED", database.queryForObject("SELECT state FROM pf_case_wait", String.class));
        assertEquals(0, database.queryForObject("SELECT count(*) FROM pf_case WHERE follow_up_due_at IS NOT NULL", Integer.class));
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_case_status_event WHERE to_status = 'CLOSED'", Integer.class));
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_case_closure WHERE follow_up_command_id = ?", Integer.class, command));
        assertTrue(new StaffChangeStore(database).history(caseId).stream()
                .anyMatch(change -> change.description().contains("Erledigung geprüft")));
    }

    @Test
    void newContactAfterAcceptedFollowUpClosureCreatesOneFreshReview() throws Exception {
        FollowUpCommandService commands = enterFollowUp();
        FollowUpCommands.Current current = commands.current(actor, caseId);
        commands.close(actor, caseId, UUID.randomUUID(), current.waitId(), current.version(), "");
        new FollowUpCommandWorkers(engine, commands).close(engine, job(FOLLOW_UP_CLOSE));
        ActivatedJob closing = closingJob();
        UUID accessId = database.queryForObject("SELECT id FROM pf_case_access WHERE case_id = ?", UUID.class, caseId);
        messages.save(new TenantMessageStore.Access(accessId, caseId), "closure-contact", "Das Problem besteht weiterhin.", leader, "<default>");
        new CaseClosureWorkers(engine, closure).close(engine, closing);
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals("REJECTED", database.queryForObject("SELECT state FROM pf_follow_up_command", String.class));
        assertEquals("CANCELLED", database.queryForObject("SELECT state FROM pf_case_wait", String.class));
        reviews.claim(engine, job(STAFF_CLAIM));
        reviews.register(engine, job(STAFF_REGISTER));
        reviews.startReview(engine, job(STAFF_START_REVIEW));
        await(() -> tasks().size() == 1 && "Fall_pruefen_und_Vorgehen_entscheiden_USERTASK".equals(tasks().getFirst().getElementId()));
        assertEquals(0, count("pf_case_closure"));
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_staff_continuation WHERE origin = 'CLOSE_CONFLICT'", Integer.class));
        assertEquals(0, database.queryForObject("SELECT count(*) FROM pf_case WHERE follow_up_due_at IS NOT NULL", Integer.class));
        UUID command = UUID.fromString(closing.getVariablesAsMap().get(STAFF_DECISION_REF).toString());
        assertEquals("STALE", closure.close(caseId, leader, closure.context(caseId, leader, command).child(), command).state());
    }

    @Test
    void revokedRoleBeforeFollowUpClosureReturnsToSameTimer() throws Exception {
        FollowUpCommandService commands = enterFollowUp();
        FollowUpCommands.Current current = commands.current(actor, caseId);
        commands.close(actor, caseId, UUID.randomUUID(), current.waitId(), current.version(), "");
        ActivatedJob closing = job(FOLLOW_UP_CLOSE);
        database.update("UPDATE pf_staff_account SET staff_role = FALSE WHERE id = ?", actor.accountId());
        new FollowUpCommandWorkers(engine, commands).close(engine, closing);
        assertEquals("WAITING_FOR_FOLLOW_UP", status());
        assertEquals("REJECTED", database.queryForObject("SELECT state FROM pf_follow_up_command", String.class));
        assertEquals("ACTIVE", database.queryForObject("SELECT state FROM pf_case_wait", String.class));
        await(() -> !engine.newMessageSubscriptionSearchRequest()
                .filter(filter -> filter.processInstanceKey(closing.getProcessInstanceKey()).messageName(CamundaFollowUpGateway.CLOSE))
                .send().join().items().isEmpty());
        assertEquals(0, count("pf_case_closure"));
    }

    @Test
    void inFlightReviewInterruptionRejectsAcceptedClosureWithoutBlockingFurtherActions() throws Exception {
        FollowUpCommandService commands = enterFollowUp();
        FollowUpCommands.Current current = commands.current(actor, caseId);
        commands.close(actor, caseId, UUID.randomUUID(), current.waitId(), current.version(), "");
        ActivatedJob preparing = job(FOLLOW_UP_CLOSE);
        UUID command = UUID.fromString(preparing.getVariablesAsMap().get(FOLLOW_UP_COMMAND_REF).toString());
        CaseWaitStore.Wait waiting = waitStore.get(current.waitId()).orElseThrow();
        assertTrue(commands.prepareClosure(command, leader, preparing.getProcessInstanceKey(), waiting.staff()));
        UUID signal = UUID.randomUUID();
        database.update("""
                INSERT INTO pf_case_review_signal (id, case_id, source_type, source_ref, reason, leading_instance_key)
                VALUES (?, ?, 'TEST', ?, 'CRITICAL_URGENCY', ?)
                """, signal, caseId, UUID.randomUUID(), leader);
        new ReviewSignalDispatcher(reviewStore, engine).dispatch();
        reviews.check(engine, job(REVIEW_CHECK));
        // Simuliert einen bereits unterwegs befindlichen Marker nach der Annahme des Abschlussauftrags.
        engine.newSetVariablesCommand(waiting.staff())
                .variables(Map.of(PENDING_REVIEW_FOLLOW_UP_VERSION, waiting.id().toString(), PHASE_REVIEW_SIGNAL_REF, signal.toString()))
                .local(true)
                .send()
                .join();
        new CaseWaitWorkers(engine, waitService).stopContact(engine, job(FOLLOW_UP_STOP_CONTACT));
        reviews.startReview(engine, job(STAFF_START_REVIEW));
        await(() -> tasks().size() == 1 && "Fall_pruefen_und_Vorgehen_entscheiden_USERTASK".equals(tasks().getFirst().getElementId()));
        assertEquals("REJECTED", followUpChanges.get(command).state());
        assertTrue(!followUpChanges.pending(caseId));
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals(0, count("pf_case_closure"));
    }

    @Test
    void roleRevocationAfterChildCompletionPreventsClosure() throws Exception {
        FollowUpCommandService commands = enterFollowUp();
        FollowUpCommands.Current current = commands.current(actor, caseId);
        commands.close(actor, caseId, UUID.randomUUID(), current.waitId(), current.version(), "");
        new FollowUpCommandWorkers(engine, commands).close(engine, job(FOLLOW_UP_CLOSE));
        ActivatedJob closing = closingJob();
        database.update("UPDATE pf_staff_account SET staff_role = FALSE WHERE id = ?", actor.accountId());
        new CaseClosureWorkers(engine, closure).close(engine, closing);
        reviews.claim(engine, job(STAFF_CLAIM));
        reviews.register(engine, job(STAFF_REGISTER));
        reviews.startReview(engine, job(STAFF_START_REVIEW));
        await(() -> tasks().size() == 1 && "Fall_pruefen_und_Vorgehen_entscheiden_USERTASK".equals(tasks().getFirst().getElementId()));
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals(0, count("pf_case_closure"));
        assertEquals("REJECTED", database.queryForObject("SELECT state FROM pf_follow_up_command", String.class));
    }

    private FollowUpCommandService enterFollowUp() throws Exception {
        StaffDecisionService staff = service(new CamundaStaffTaskGateway(
                new StaticListableBeanFactory(Map.of("engine", engine)).getBeanProvider(CamundaClient.class), true));
        StaffTask task = staff.current(actor, caseId);
        staff.followUp(actor, caseId, UUID.randomUUID(), Long.parseLong(task.taskKey()), task.version(), task.reviewToken(), "", 3, null);
        new CaseWaitWorkers(engine, waitService).save(engine, job(FOLLOW_UP_SAVE));
        FollowUpCommandService commands = new FollowUpCommandService(access, workflow, caseData, waitStore,
                decisions, followUpChanges, new CamundaFollowUpGateway(
                        new StaticListableBeanFactory(Map.of("engine", engine)).getBeanProvider(CamundaClient.class), true), transactions);
        await(() -> commands.current(actor, caseId).available());
        return commands;
    }

    private ActivatedJob closingJob() throws Exception {
        ActivatedJob closing = job(STAFF_CLOSE);
        UUID command = UUID.fromString(closing.getVariablesAsMap().get(STAFF_DECISION_REF).toString());
        CaseClosureService.ClosureContext context = closure.context(caseId, leader, command);
        await(() -> "COMPLETED".equals(engine.newProcessInstanceGetRequest(context.child()).send().join().getState().name())
                && "COMPLETED".equals(engine.newElementInstanceGetRequest(context.parentElement()).send().join().getState().name()));
        return closing;
    }

    private StaffDecisionService service(StaffTaskGateway gateway) {
        return new StaffDecisionService(access, workflow, caseData, reviewStore, decisions, gateway, transactions);
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
