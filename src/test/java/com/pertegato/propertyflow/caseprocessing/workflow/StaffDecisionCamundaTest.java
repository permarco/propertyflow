package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.access.contract.StaffAccountAccess;
import com.pertegato.propertyflow.access.contract.SubmissionContext;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.caseprocessing.contract.CaseActionException;
import com.pertegato.propertyflow.caseprocessing.contract.CaseCreation;
import com.pertegato.propertyflow.caseprocessing.contract.CreateCaseCommand;
import com.pertegato.propertyflow.caseprocessing.contract.StaffDecisionAction;
import com.pertegato.propertyflow.caseprocessing.contract.StaffTaskDecisions.StaffTask;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.delivery.StaffTaskGateway;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseDataStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.StartOrder;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffDecisionStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffReviewStore;
import com.pertegato.propertyflow.caseprocessing.service.CaseWorkflowService;
import com.pertegato.propertyflow.caseprocessing.service.StaffDecisionService;
import com.pertegato.propertyflow.caseprocessing.service.StaffReviewService;
import com.pertegato.propertyflow.triage.service.AnalyzeTool.AnalysisModel;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.response.UserTask;
import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.STAFF_CLAIM;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants.JobType.STAFF_PROGRESS;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants.JobType.STAFF_REGISTER;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants.JobType.STAFF_START_REVIEW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "propertyflow.workflow.enabled=false")
@ActiveProfiles("propertyflow-backend")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_DATABASE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_CAMUNDA_URL", matches = ".+")
class StaffDecisionCamundaTest {

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
        leader = WorkflowTestJobs.rootInstanceKey(engine, start);
        initial = new InitialCaseWorkers(engine, initialService);
        reviews = new StaffReviewWorkers(engine, reviewService);
        ActivatedJob preparing = job(PREPARE_PROCESS_KEY);
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
            if (leader != null) {
                engine.newCancelInstanceCommand(leader).send().join();
            }
            engine.close();
        }
    }

    @Test
    void explicitDecisionCompletesOnlyCurrentTaskThenWorkerCreatesWorkTaskAndOneStatusMail() throws Exception {
        StaffDecisionService service = service(gateway());
        StaffTask task = service.current(actor, caseId);
        assertTrue(task.available());
        service.continueWork(actor, caseId, UUID.randomUUID(), Long.parseLong(task.taskKey()), task.version(), task.reviewToken());
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        ActivatedJob progressing = job(STAFF_PROGRESS);
        StaffDecisionWorkers workers = new StaffDecisionWorkers(engine, service);
        workers.progress(engine, progressing);
        await(() -> tasks().size() == 1 && "Naechste_Mitarbeiteraktion_abwarten_USERTASK".equals(tasks().getFirst().getElementId()));
        assertEquals("IN_PROGRESS", status());
        assertEquals(3, count("pf_case_mail_order"));
        assertEquals("APPLIED", database.queryForObject("SELECT state FROM pf_staff_decision", String.class));
        assertThrows(CaseActionException.class,
                () -> service.continueWork(actor, caseId, UUID.randomUUID(), Long.parseLong(task.taskKey()), task.version(), task.reviewToken()));
        assertEquals("Naechste_Mitarbeiteraktion_abwarten_USERTASK", tasks().getFirst().getElementId());
    }

    @Test
    void changeAfterCompletionCreatesNewReviewAndOldTabCannotCompleteIt() throws Exception {
        StaffDecisionService service = service(gateway());
        StaffTask first = service.current(actor, caseId);
        service.continueWork(actor, caseId, UUID.randomUUID(), Long.parseLong(first.taskKey()), first.version(), first.reviewToken());
        database.update("UPDATE pf_case SET version = version + 1 WHERE id = ?", caseId);
        new StaffDecisionWorkers(engine, service).progress(engine, job(STAFF_PROGRESS));
        reviews.startReview(engine, job(STAFF_START_REVIEW));
        await(() -> tasks().size() == 1 && !Long.toString(tasks().getFirst().getUserTaskKey()).equals(first.taskKey()));
        StaffTask next = service.current(actor, caseId);
        assertTrue(next.available());
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals(2, count("pf_case_mail_order"));
        assertThrows(CaseActionException.class,
                () -> service.continueWork(actor, caseId, UUID.randomUUID(), Long.parseLong(first.taskKey()), next.version(), next.reviewToken()));
        assertEquals(next.taskKey(), Long.toString(tasks().getFirst().getUserTaskKey()));
        service.continueWork(actor, caseId, UUID.randomUUID(), Long.parseLong(next.taskKey()), next.version(), next.reviewToken());
        new StaffDecisionWorkers(engine, service).progress(engine, job(STAFF_PROGRESS));
        await(() -> tasks().size() == 1 && "Naechste_Mitarbeiteraktion_abwarten_USERTASK".equals(tasks().getFirst().getElementId()));
        assertEquals("IN_PROGRESS", status());
        assertEquals(3, count("pf_case_mail_order"));
    }

    @Test
    void lostNativeCompletionReplyStillAllowsWorkerToApplyOriginalDecision() throws Exception {
        StaffTaskGateway real = gateway();
        StaffTaskGateway lost = new StaffTaskGateway() {
            @Override
            public Optional<Long> currentTask(Binding binding) {
                return real.currentTask(binding);
            }

            @Override
            public void completeTask(Binding binding, long taskKey, UUID id, StaffDecisionAction action) {
                real.completeTask(binding, taskKey, id, action);
                throw new IllegalStateException("TEST_LOST_COMPLETION_REPLY");
            }
        };
        StaffDecisionService service = service(lost);
        StaffTask task = service.current(actor, caseId);
        assertThrows(IllegalStateException.class,
                () -> service.continueWork(actor, caseId, UUID.randomUUID(), Long.parseLong(task.taskKey()), task.version(), task.reviewToken()));
        assertEquals("PREPARED", database.queryForObject("SELECT state FROM pf_staff_decision", String.class));
        new StaffDecisionWorkers(engine, service).progress(engine, job(STAFF_PROGRESS));
        await(() -> tasks().size() == 1 && "Naechste_Mitarbeiteraktion_abwarten_USERTASK".equals(tasks().getFirst().getElementId()));
        assertEquals("IN_PROGRESS", status());
    }

    private StaffTaskGateway gateway() {
        return new CamundaStaffTaskGateway(new StaticListableBeanFactory(
                Map.of("engine", engine)).getBeanProvider(CamundaClient.class), true);
    }

    private StaffDecisionService service(StaffTaskGateway gateway) {
        return new StaffDecisionService(access, workflow, caseData, reviewStore, decisions, gateway, transactions);
    }

    private ActivatedJob job(String type) throws Exception {
        ActivatedJob job = WorkflowTestJobs.activateForRoot(engine, type, leader);
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

    private void await(BooleanSupplier condition) throws Exception {
        for (int attempt = 0; attempt < 200; attempt++) {
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
