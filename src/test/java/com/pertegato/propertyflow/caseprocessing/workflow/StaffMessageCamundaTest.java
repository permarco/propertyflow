package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.access.contract.StaffAccountAccess;
import com.pertegato.propertyflow.access.contract.SubmissionContext;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.caseprocessing.contract.CaseAccepted;
import com.pertegato.propertyflow.caseprocessing.contract.CaseCreation;
import com.pertegato.propertyflow.caseprocessing.contract.CreateCaseCommand;
import com.pertegato.propertyflow.caseprocessing.contract.StaffTaskDecisions.StaffTask;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseAnalysisStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseDataStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWaitStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.StartOrder;
import com.pertegato.propertyflow.caseprocessing.persistence.ClarificationQuestionStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffDecisionStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffMessageStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffReviewStore;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantReplyStore;
import com.pertegato.propertyflow.caseprocessing.service.CaseAnalysisService;
import com.pertegato.propertyflow.caseprocessing.service.CaseUrgencyService;
import com.pertegato.propertyflow.caseprocessing.service.CaseWorkflowService;
import com.pertegato.propertyflow.caseprocessing.service.StaffDecisionService;
import com.pertegato.propertyflow.caseprocessing.service.StaffMessageService;
import com.pertegato.propertyflow.caseprocessing.service.StaffReviewService;
import com.pertegato.propertyflow.triage.service.AnalyzeTool;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "propertyflow.workflow.enabled=false")
@ActiveProfiles("propertyflow-backend")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_DATABASE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_CAMUNDA_URL", matches = ".+")
class StaffMessageCamundaTest {

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
    private CaseDataStore caseData;


    private CamundaClient engine;
    private InitialCaseWorkers initial;

    @Autowired
    private StaffMessageStore messages;
    @Autowired
    private StaffAccountAccess access;
    @Autowired
    private StaffDecisionStore decisions;
    private WebAccountContext actor;

    @Autowired
    private StaffReviewService reviewService;

    @Autowired
    private StaffReviewStore reviewStore;

    @Autowired
    private CaseAnalysisStore analyses;

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
        long definitionKey = engine.newDeployResourceCommand().addResourceFromClasspath("workflow/staff-message-case.bpmn")
                .addResourceFromClasspath("workflow/analysis-automatic.bpmn").addResourceFromClasspath("workflow/staff-message-staff.bpmn")
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
    void automaticSidePublishesInformationOnceWithLocalReferencesAndUnchangedCaseStatus() throws Exception {
        StaffMessageService service = service();
        assertEquals("PENDING", service.send(actor, caseId, "Information 1", "Ihre Verwaltung prüft das Anliegen.", false));
        UUID id = messages.find(caseId, "Information 1").orElseThrow().id();
        gateway().correlate(id, leader, "<default>", true, Long.toString(leader));
        StaffMessageWorkers worker = new StaffMessageWorkers(engine, service);
        for (int index = 0; index < 2; index++) {
            ActivatedJob job = job(StaffMessageWorkers.PUBLISH);
            assertEquals("StaffQuestionPublish", job.getElementId());
            assertEquals(id.toString(), job.getVariablesAsMap().get("staffMessageCommandRef"));
            assertFalse(job.getVariables().contains("Ihre Verwaltung"));
            worker.publish(engine, job);
        }
        assertEquals(1, count("pf_staff_message"));
        assertEquals(1, count("pf_staff_message_mail_order"));
        assertEquals("UNDER_CLARIFICATION", status());
        assertEquals(0, engine.newVariableSearchRequest().filter(filter -> filter.scopeKey(leader).name("staffMessageCommandRef"))
                .send().join().items().size());
        assertEquals("PUBLISHED", service.send(actor, caseId, "Information 1", "Ihre Verwaltung prüft das Anliegen.", false));
    }

    @Test
    void questionTargetsActualStaffChildAndKeepsItsNativeReviewTaskOpen() throws Exception {
        review();
        long task = tasks().getFirst().getUserTaskKey();
        StaffMessageService service = service();
        assertEquals("PENDING", service.send(actor, caseId, "Rückfrage", "Welcher Termin passt Ihnen?", true));
        ActivatedJob job = job(StaffMessageWorkers.PUBLISH);
        assertEquals("SMessagePublish", job.getElementId());
        assertEquals(StaffReviewWorkers.STAFF_PROCESS, job.getBpmnProcessId());
        new StaffMessageWorkers(engine, service).publish(engine, job);
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals(task, tasks().getFirst().getUserTaskKey());
        assertEquals(1, count("pf_staff_answer_requirement"));
        assertEquals("OPEN", database.queryForObject("SELECT state FROM pf_staff_answer_requirement", String.class));
        assertEquals("PUBLISHED", service.result(actor, caseId, "Rückfrage"));
    }

    @Test
    void oldSideJobCannotPublishAfterContinueAndCannotAffectTheNewWorkTask() throws Exception {
        review();
        StaffMessageService service = service();
        service.send(actor, caseId, "Alter Auftrag", "Noch nicht veröffentlicht", false);
        ActivatedJob old = job(StaffMessageWorkers.PUBLISH);
        StaffDecisionService decision = new StaffDecisionService(access, workflow, caseData, reviewStore, decisions, taskGateway(), transactions);
        StaffTask task = decision.current(actor, caseId);
        decision.continueWork(actor, caseId, UUID.randomUUID(), Long.parseLong(task.taskKey()), task.version(), task.reviewToken(), "");
        new StaffDecisionWorkers(engine, decision).progress(engine, job("propertyflow.staff.progress"));
        await(() -> !engine.newUserTaskSearchRequest().filter(filter -> filter.elementId("SWork").state(UserTaskState.CREATED))
                .send().join().items().isEmpty());
        new StaffMessageWorkers(engine, service).publish(engine, old);
        assertEquals("IN_PROGRESS", status());
        assertEquals(0, count("pf_staff_message"));
        service.send(actor, caseId, "Neue Information", "Zwischenstand der Bearbeitung", false);
        new StaffMessageWorkers(engine, service).publish(engine, job(StaffMessageWorkers.PUBLISH));
        assertEquals(1, count("pf_staff_message"));
        assertEquals("IN_PROGRESS", status());
        assertTrue(decision.current(actor, caseId).available());
    }

    private void review() throws Exception {
        TriageWorkers triage = new TriageWorkers(engine, new CaseAnalysisService(workflow, reviewStore, analyses, urgencyService,
                new AnalyzeTool(text -> "invalid"), transactions));
        triage.analyze(engine, job(TriageWorkers.INITIAL));
        StaffReviewWorkers reviews = new StaffReviewWorkers(engine, reviewService);
        reviews.analysisFailure(engine, job("propertyflow.review.analysis-failure"));
        reviews.claim(engine, job("propertyflow.staff.claim"));
        reviews.register(engine, job("propertyflow.staff.register"));
        reviews.startReview(engine, job("propertyflow.staff.start-review"));
        await(() -> tasks().size() == 1);
    }

    private CamundaStaffMessageGateway gateway() {
        return new CamundaStaffMessageGateway(new StaticListableBeanFactory(Map.of("engine", engine)).getBeanProvider(CamundaClient.class), true);
    }

    private CamundaStaffTaskGateway taskGateway() {
        return new CamundaStaffTaskGateway(new StaticListableBeanFactory(Map.of("engine", engine)).getBeanProvider(CamundaClient.class), true);
    }

    private StaffMessageService service() {
        CamundaCaseGateway availability = new CamundaCaseGateway(
                new StaticListableBeanFactory(Map.of("engine", engine)).getBeanProvider(CamundaClient.class), true);
        return new StaffMessageService(access, workflow, caseData, reviewStore, messages, availability, taskGateway(), gateway(), new CaseWaitStore(database), new StaffDecisionStore(database), new TenantReplyStore(database), new ClarificationQuestionStore(database), transactions);
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
        long child = reviewStore.forCall(caseId, workflow.entryForCase(caseId).orElseThrow().callRef()).orElseThrow().childKey();
        return engine.newUserTaskSearchRequest().filter(filter -> filter.processInstanceKey(child).state(UserTaskState.CREATED))
                .send().join().items();
    }

    private String status() {
        return database.queryForObject("SELECT status FROM pf_case", String.class);
    }

    private int count(String table) {
        return database.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
