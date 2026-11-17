package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.access.contract.SubmissionContext;
import com.pertegato.propertyflow.caseprocessing.contract.CaseAccepted;
import com.pertegato.propertyflow.caseprocessing.contract.CaseCreation;
import com.pertegato.propertyflow.caseprocessing.contract.CreateCaseCommand;
import com.pertegato.propertyflow.caseprocessing.delivery.PersonalLinkCodec;
import com.pertegato.propertyflow.caseprocessing.delivery.TenantMessageGateway;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseAnalysisStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseDataStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.StartOrder;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffReviewStore;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantMessageStore;
import com.pertegato.propertyflow.caseprocessing.service.CaseAnalysisService;
import com.pertegato.propertyflow.caseprocessing.service.CaseUrgencyService;
import com.pertegato.propertyflow.caseprocessing.service.CaseWorkflowService;
import com.pertegato.propertyflow.caseprocessing.service.StaffReviewService;
import com.pertegato.propertyflow.caseprocessing.service.TenantMessageService;
import com.pertegato.propertyflow.triage.service.AnalyzeTool;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.response.UserTask;
import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
class TenantMessageCamundaTest {

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
    private TenantMessageStore messages;

    @Autowired
    private PersonalLinkCodec links;

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
        long definitionKey = engine.newDeployResourceCommand().addResourceFromClasspath("workflow/message-case.bpmn")
                .addResourceFromClasspath("workflow/analysis-automatic.bpmn").addResourceFromClasspath("workflow/closure-staff.bpmn")
                .addResourceFromClasspath("workflow/message-processing.bpmn")
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
    void independentCorrelationsKeepTheirLocalMessageAndOnlyOneChildOwnsEachInput() throws Exception {
        TenantMessageService service = service(gateway());
        UUID first = service.send(accepted.personalAccessToken(), UUID.randomUUID(), "Erste Nachricht <script>");
        UUID second = service.send(accepted.personalAccessToken(), UUID.randomUUID(), "Zweite Nachricht");
        gateway().correlate(first, leader, "<default>");
        TenantMessageWorkers worker = new TenantMessageWorkers(engine, service);
        Set<UUID> seen = new HashSet<>();
        for (int index = 0; index < 3; index++) {
            ActivatedJob input = job(TenantMessageWorkers.IDENTIFY);
            assertEquals(TenantMessageWorkers.PROCESS, input.getBpmnProcessId());
            assertEquals(caseId, input.getVariablesAsMap().get("caseId"));
            UUID messageId = UUID.fromString(input.getVariablesAsMap().get("tenantMessageId").toString());
            seen.add(messageId);
            assertFalse(input.getVariables().contains("Erste Nachricht"));
            assertFalse(input.getVariables().contains(accepted.personalAccessToken()));
            worker.identify(engine, input);
        }
        assertEquals(Set.of(first, second), seen);
        assertEquals(2, database.queryForObject("SELECT count(*) FROM pf_tenant_message WHERE state = 'RECEIVED'", Integer.class));
        assertTrue(!messages.get(first).child().equals(messages.get(second).child()));
        await(() -> engine.newProcessInstanceSearchRequest().filter(filter -> filter.parentProcessInstanceKey(leader)
                .processDefinitionId(TenantMessageWorkers.PROCESS)).send().join().items().stream()
                .filter(instance -> "COMPLETED".equals(instance.getState().name())).count() == 1);
        assertEquals(0, engine.newVariableSearchRequest().filter(filter -> filter.scopeKey(leader).name("tenantMessageId"))
                .send().join().items().size());
        assertEquals("UNDER_CLARIFICATION", status());
    }

    @Test
    void lostAcknowledgementRetriesSameDurableInputWithoutChangingCasePhase() throws Exception {
        TenantMessageGateway real = gateway();
        TenantMessageService losing = service((id, root, tenant) -> {
            real.correlate(id, root, tenant);
            throw new IllegalStateException("TEST_LOST_REPLY");
        });
        UUID operation = UUID.randomUUID();
        assertThrows(IllegalStateException.class, () -> losing.send(accepted.personalAccessToken(), operation, "Gespeichert trotz Timeout"));
        ActivatedJob input = job(TenantMessageWorkers.IDENTIFY);
        new TenantMessageWorkers(engine, losing).identify(engine, input);
        UUID id = service(real).send(accepted.personalAccessToken(), operation, "Gespeichert trotz Timeout");
        assertEquals(input.getVariablesAsMap().get("tenantMessageId"), id.toString());
        assertEquals(1, count("pf_tenant_message"));
        assertEquals(1, count("pf_message_mail_order"));
        assertEquals("RECEIVED", messages.get(id).state());
        assertEquals("UNDER_CLARIFICATION", status());
    }

    @Test
    void tenantMessageDuringReviewPreservesTheSameNativeStaffTask() throws Exception {
        TriageWorkers triage = new TriageWorkers(engine, new CaseAnalysisService(workflow, reviewStore, analyses, urgencyService,
                new AnalyzeTool(text -> "invalid"), transactions));
        triage.analyze(engine, job(TriageWorkers.INITIAL));
        StaffReviewWorkers reviews = new StaffReviewWorkers(engine, reviewService);
        reviews.analysisFailure(engine, job("propertyflow.review.analysis-failure"));
        reviews.claim(engine, job("propertyflow.staff.claim"));
        reviews.register(engine, job("propertyflow.staff.register"));
        reviews.startReview(engine, job("propertyflow.staff.start-review"));
        await(() -> tasks().size() == 1);
        long task = tasks().getFirst().getUserTaskKey();
        TenantMessageService service = service(gateway());
        service.send(accepted.personalAccessToken(), UUID.randomUUID(), "Es funktioniert wieder");
        new TenantMessageWorkers(engine, service).identify(engine, job(TenantMessageWorkers.IDENTIFY));
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals(task, tasks().getFirst().getUserTaskKey());
        assertEquals(1, tasks().size());
    }

    private TenantMessageGateway gateway() {
        return new CamundaTenantMessageGateway(new StaticListableBeanFactory(Map.of("engine", engine)).getBeanProvider(CamundaClient.class), true);
    }

    private TenantMessageService service(TenantMessageGateway gateway) {
        CamundaCaseGateway availability = new CamundaCaseGateway(
                new StaticListableBeanFactory(Map.of("engine", engine)).getBeanProvider(CamundaClient.class), true);
        return new TenantMessageService(messages, workflow, caseData, links, availability, gateway, transactions, accountId -> java.util.Optional.empty());
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
