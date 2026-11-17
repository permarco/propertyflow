package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.access.contract.StaffAccountAccess;
import com.pertegato.propertyflow.access.contract.SubmissionContext;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.caseprocessing.contract.CaseActionException;
import com.pertegato.propertyflow.caseprocessing.contract.CaseCreation;
import com.pertegato.propertyflow.caseprocessing.contract.CreateCaseCommand;
import com.pertegato.propertyflow.caseprocessing.contract.StaffTaskDecisions.StaffTask;
import com.pertegato.propertyflow.caseprocessing.delivery.StaffTaskGateway;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseAnalysisStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseDataStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.StartOrder;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffDecisionStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffReviewStore;
import com.pertegato.propertyflow.caseprocessing.service.CaseAnalysisService;
import com.pertegato.propertyflow.caseprocessing.service.CaseClosureService;
import com.pertegato.propertyflow.caseprocessing.service.CaseClosureService.ClosureContext;
import com.pertegato.propertyflow.caseprocessing.service.CaseUrgencyService;
import com.pertegato.propertyflow.caseprocessing.service.CaseWorkflowService;
import com.pertegato.propertyflow.caseprocessing.service.StaffDecisionService;
import com.pertegato.propertyflow.caseprocessing.service.StaffReviewService;
import com.pertegato.propertyflow.triage.service.AnalyzeTool;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "propertyflow.workflow.enabled=false")
@ActiveProfiles("propertyflow-backend")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_DATABASE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_CAMUNDA_URL", matches = ".+")
class CaseClosureCamundaTest {

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
    private CaseUrgencyService urgencyService;

    @Autowired
    private CaseAnalysisStore analyses;

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
        long definitionKey = engine.newDeployResourceCommand().addResourceFromClasspath("workflow/closure-case.bpmn")
                .addResourceFromClasspath("workflow/analysis-automatic.bpmn").addResourceFromClasspath("workflow/closure-staff.bpmn")
                .send().join().getProcesses().stream()
                .filter(process -> InitialCaseWorkers.CASE_PROCESS.equals(process.getBpmnProcessId()))
                .findFirst().orElseThrow().getProcessDefinitionKey();
        caseId = creation.create(new SubmissionContext.Guest(UUID.randomUUID()),
                new CreateCaseCommand("review", "Heizung", "Wohnung", "Kalt", "review@example.test")).caseId();
        StartOrder start = workflow.claim(definitionKey, "<default>", Duration.ofMinutes(2)).orElseThrow();
        assertEquals(ProcessStartGateway.StartResult.STARTED, new CamundaProcessStartGateway(engine).ensureStarted(start));
        initial = new InitialCaseWorkers(engine, initialService);
        reviews = new StaffReviewWorkers(engine, reviewService);
        ActivatedJob preparing = job(InitialCaseWorkers.PREPARE_KEY);
        leader = preparing.getProcessInstanceKey();
        initial.prepareKey(engine, preparing);
        initial.register(engine, job(InitialCaseWorkers.REGISTER));
        initial.prepareInitial(engine, job(InitialCaseWorkers.PREPARE_INITIAL));
        initial.registerAutomatic(engine, job(InitialCaseWorkers.REGISTER_AUTOMATIC));
        initial.clarification(engine, job(InitialCaseWorkers.CLARIFICATION));
        assertEquals("UNDER_CLARIFICATION", status());
        worker(text -> "invalid").analyze(engine, job(TriageWorkers.INITIAL));
        reviews.analysisFailure(engine, job("propertyflow.review.analysis-failure"));
        reviews.claim(engine, job("propertyflow.staff.claim"));
        reviews.register(engine, job("propertyflow.staff.register"));
        reviews.startReview(engine, job("propertyflow.staff.start-review"));
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

    @Test
    void explicitReviewClosureEndsLeadingProcessAndKeepsExistingMailObligations() throws Exception {
        StaffDecisionService service = service(gateway());
        StaffTask task = service.current(actor, caseId);
        service.closeCase(actor, caseId, UUID.randomUUID(), Long.parseLong(task.taskKey()), task.version(), task.reviewToken());
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        ActivatedJob close = closingJob();
        new CaseClosureWorkers(engine, closure).close(engine, close);
        await(() -> "COMPLETED".equals(engine.newProcessInstanceGetRequest(leader).send().join().getState().name()));
        assertEquals("CLOSED", status());
        assertEquals(0, tasks().size());
        assertEquals(1, count("pf_case_closure"));
        assertEquals(2, count("pf_case_mail_order"));
        assertEquals(0, count("pf_internal_memo"));
        assertEquals("CLOSED", closure.close(caseId, leader,
                database.queryForObject("SELECT child_instance_key FROM pf_staff_continuation", Long.class),
                UUID.fromString(close.getVariablesAsMap().get("staffDecisionRef").toString())).state());
    }

    @Test
    void workClosureUsesActualWorkTaskAndProducesNoExtraClosureMail() throws Exception {
        StaffDecisionService service = service(gateway());
        StaffTask first = service.current(actor, caseId);
        service.continueWork(actor, caseId, UUID.randomUUID(), Long.parseLong(first.taskKey()), first.version(), first.reviewToken());
        new StaffDecisionWorkers(engine, service).progress(engine, job(StaffDecisionWorkers.PROGRESS));
        await(() -> tasks().size() == 1 && "SWork".equals(tasks().getFirst().getElementId()));
        StaffTask work = service.current(actor, caseId);
        assertTrue(work.canClose());
        assertFalse(work.canContinue());
        service.closeCase(actor, caseId, UUID.randomUUID(), Long.parseLong(work.taskKey()), work.version(), work.reviewToken());
        new CaseClosureWorkers(engine, closure).close(engine, closingJob());
        await(() -> "COMPLETED".equals(engine.newProcessInstanceGetRequest(leader).send().join().getState().name()));
        assertEquals("CLOSED", status());
        assertEquals(3, count("pf_case_mail_order"));
    }

    @Test
    void staleClosureReturnsThroughNewStaffCallAndOldTaskCannotCloseNewReview() throws Exception {
        StaffDecisionService service = service(gateway());
        StaffTask original = service.current(actor, caseId);
        long oldChild = tasks().getFirst().getProcessInstanceKey();
        service.closeCase(actor, caseId, UUID.randomUUID(), Long.parseLong(original.taskKey()), original.version(), original.reviewToken());
        database.update("UPDATE pf_case SET version = version + 1 WHERE id = ?", caseId);
        new CaseClosureWorkers(engine, closure).close(engine, closingJob());
        reviews.claim(engine, job("propertyflow.staff.claim"));
        reviews.register(engine, job("propertyflow.staff.register"));
        reviews.startReview(engine, job("propertyflow.staff.start-review"));
        await(() -> tasks().size() == 1 && tasks().getFirst().getProcessInstanceKey() != oldChild);
        StaffTask current = service.current(actor, caseId);
        assertTrue(current.canClose());
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals(0, count("pf_case_closure"));
        assertEquals(2, count("pf_case_mail_order"));
        assertEquals(2, count("pf_staff_continuation"));
        assertThrows(CaseActionException.class,
                () -> service.closeCase(actor, caseId, UUID.randomUUID(), Long.parseLong(original.taskKey()), current.version(), current.reviewToken()));
        service.closeCase(actor, caseId, UUID.randomUUID(), Long.parseLong(current.taskKey()), current.version(), current.reviewToken());
        new CaseClosureWorkers(engine, closure).close(engine, closingJob());
        await(() -> "COMPLETED".equals(engine.newProcessInstanceGetRequest(leader).send().join().getState().name()));
        assertEquals("CLOSED", status());
        assertEquals(1, count("pf_case_closure"));
        assertEquals(2, count("pf_case_mail_order"));
    }

    private ActivatedJob closingJob() throws Exception {
        ActivatedJob job = job(CaseClosureWorkers.CLOSE);
        ClosureContext context = closure.context(caseId, leader,
                UUID.fromString(job.getVariablesAsMap().get("staffDecisionRef").toString()));
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

    private TriageWorkers worker(AnalyzeTool.AnalysisModel model) {
        return new TriageWorkers(engine, new CaseAnalysisService(
                workflow, reviewStore, analyses, urgencyService, new AnalyzeTool(model), transactions));
    }

    private ActivatedJob job(String type) throws Exception {
        for (int attempt = 0; attempt < 200; attempt++) {
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
