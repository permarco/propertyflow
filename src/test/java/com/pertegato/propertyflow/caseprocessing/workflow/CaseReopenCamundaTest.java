package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.access.contract.StaffAccountAccess;
import com.pertegato.propertyflow.access.contract.SubmissionContext;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.caseprocessing.contract.CaseCreation;
import com.pertegato.propertyflow.caseprocessing.contract.CreateCaseCommand;
import com.pertegato.propertyflow.caseprocessing.contract.StaffTaskDecisions.StaffTask;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.delivery.StaffTaskGateway;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseDataStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseReopenStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.StartOrder;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffDecisionStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffReviewStore;
import com.pertegato.propertyflow.caseprocessing.service.CaseClosureService;
import com.pertegato.propertyflow.caseprocessing.service.CaseClosureService.ClosureContext;
import com.pertegato.propertyflow.caseprocessing.service.CaseReopenService;
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
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.CASE_REOPEN;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.PREPARE_INITIAL;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.PREPARE_PROCESS_KEY;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.REGISTER_CASE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.REVIEW_ANALYSIS_FAILURE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.STAFF_CLAIM;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.STAFF_CLOSE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.STAFF_DECISION_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants.JobType.STAFF_REGISTER;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants.JobType.STAFF_START_REVIEW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "propertyflow.workflow.enabled=false")
@ActiveProfiles("propertyflow-backend")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_DATABASE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_CAMUNDA_URL", matches = ".+")
class CaseReopenCamundaTest {

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

    @Autowired
    private CaseClosureService closure;

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
    private CaseReopenStore reopens;

    @Test
    void completedGenerationIsReplacedOnlyAfterNewReceiverIsReady() throws Exception {
        StaffDecisionService decisionsService = service(gateway());
        StaffTask task = decisionsService.current(actor, caseId);
        decisionsService.closeCase(actor, caseId, UUID.randomUUID(), Long.parseLong(task.taskKey()), task.version(), task.reviewToken());
        new CaseClosureWorkers(engine, closure).close(engine, closingJob());
        await(() -> "COMPLETED".equals(engine.newProcessInstanceGetRequest(leader).send().join().getState().name()));
        long previous = leader;
        CaseReopenService reopening =
                new CaseReopenService(access, workflow, reopens,
                        new CamundaReopenProcessGateway(new StaticListableBeanFactory(Map.of("engine", engine)).getBeanProvider(CamundaClient.class), true),
                        transactions);
        UUID id = reopening.request(actor, caseId, UUID.randomUUID(), database.queryForObject("SELECT version FROM pf_case WHERE id = ?", Long.class, caseId));
        CaseReopenWorkers worker = new CaseReopenWorkers(engine, reopening, reopens);
        worker.dispatch();
        ActivatedJob preparing = job(PREPARE_PROCESS_KEY);
        leader = preparing.getProcessInstanceKey();
        assertTrue(leader != previous);
        initial.prepareKey(engine, preparing);
        ActivatedJob reopen = job(CASE_REOPEN);
        await(() -> !engine.newMessageSubscriptionSearchRequest().filter(filter -> filter.processInstanceKey(leader).elementId("Auf_Fallaktion_warten_START")
                .messageSubscriptionState(io.camunda.client.api.search.enums.MessageSubscriptionState.CREATED)).send().join().items().isEmpty());
        worker.reopen(engine, reopen);
        assertEquals("IN_PROGRESS", status());
        assertEquals(leader, workflow.leadingInstance(caseId).orElseThrow());
        reviews.claim(engine, job(STAFF_CLAIM));
        reviews.register(engine, job(STAFF_REGISTER));
        await(() -> tasks().size() == 1 && "Naechste_Mitarbeiteraktion_abwarten_USERTASK".equals(tasks().getFirst().getElementId()));
        assertEquals("APPLIED", reopens.get(id).orElseThrow().state());
        assertEquals(2, count("pf_case_process_history"));
        assertEquals(1, count("pf_case_closure"));
        assertEquals(1, count("pf_acceptance_mail_order"));
        assertEquals(3, count("pf_case_mail_order"));
        assertEquals("COMPLETED", engine.newProcessInstanceGetRequest(previous).send().join().getState().name());
    }

    private ActivatedJob closingJob() throws Exception {
        ActivatedJob job = job(STAFF_CLOSE);
        ClosureContext context = closure.context(caseId, leader,
                UUID.fromString(job.getVariablesAsMap().get(STAFF_DECISION_REF).toString()));
        await(() -> "COMPLETED".equals(engine.newProcessInstanceGetRequest(context.child()).send().join().getState().name())
                && "COMPLETED".equals(engine.newElementInstanceGetRequest(context.parentElement()).send().join().getState().name()));
        return job;
    }

    private StaffTaskGateway gateway() {
        return new CamundaStaffTaskGateway(new StaticListableBeanFactory(
                Map.of("engine", engine)).getBeanProvider(CamundaClient.class), true);
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
