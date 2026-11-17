package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.access.contract.SubmissionContext;
import com.pertegato.propertyflow.caseprocessing.contract.CaseAccepted;
import com.pertegato.propertyflow.caseprocessing.contract.CaseCreation;
import com.pertegato.propertyflow.caseprocessing.contract.CreateCaseCommand;
import com.pertegato.propertyflow.caseprocessing.delivery.PersonalLinkCodec;
import com.pertegato.propertyflow.caseprocessing.delivery.TenantMessageGateway;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseAnalysisStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseDataStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.StartOrder;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import com.pertegato.propertyflow.caseprocessing.persistence.ConversationEvaluationStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffReviewStore;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantMessageStore;
import com.pertegato.propertyflow.caseprocessing.persistence.UrgencyStore;
import com.pertegato.propertyflow.caseprocessing.service.AutomaticClosureRelease;
import com.pertegato.propertyflow.caseprocessing.service.CaseUrgencyService;
import com.pertegato.propertyflow.caseprocessing.service.CaseWorkflowService;
import com.pertegato.propertyflow.caseprocessing.service.ConversationEvaluationService;
import com.pertegato.propertyflow.caseprocessing.service.StaffReviewService;
import com.pertegato.propertyflow.caseprocessing.service.TenantMessageService;
import com.pertegato.propertyflow.recommendation.service.Recommendation;
import com.pertegato.propertyflow.triage.service.ConversationAnalysis;
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

import static com.pertegato.propertyflow.recommendation.ClosureAssessmentFixtures.noClosure;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "propertyflow.workflow.enabled=false")
@ActiveProfiles("propertyflow-backend")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_DATABASE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_CAMUNDA_URL", matches = ".+")
class ConversationEvaluationCamundaTest {

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
                .addResourceFromClasspath("workflow/conversation-automatic.bpmn").addResourceFromClasspath("workflow/closure-staff.bpmn")
                .addResourceFromClasspath("workflow/conversation-processing.bpmn")
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

    @Autowired
    private ConversationEvaluationStore evaluations;
    @Autowired
    private UrgencyStore urgencies;

    @Test
    void automaticKnowledgeFailureUsesCentralAuditAndCreatesOneRealReviewTask() throws Exception {
        ConversationEvaluationService central =
                new ConversationEvaluationService(workflow, messages, evaluations,
                        urgencies, input -> new ConversationAnalysis.Result(
                                "HIGH", false, List.of(), null, "test", "v1"),
                        input -> new Recommendation.Result(
                                null, null, List.of(), List.of(), true, "APPROVED_KNOWLEDGE_MISSING", "test", "v1"), noClosure(), transactions,
                new AutomaticClosureRelease());
        AutomaticEvaluationWorkers worker = new AutomaticEvaluationWorkers(engine, central);
        worker.evaluate(engine, job(AutomaticEvaluationWorkers.EVALUATE));
        worker.recommend(engine, job(AutomaticEvaluationWorkers.RECOMMEND));
        StaffReviewWorkers reviews = new StaffReviewWorkers(engine, reviewService);
        reviews.analysisFailure(engine, job("propertyflow.review.analysis-failure"));
        reviews.claim(engine, job("propertyflow.staff.claim"));
        reviews.register(engine, job("propertyflow.staff.register"));
        reviews.startReview(engine, job("propertyflow.staff.start-review"));
        await(() -> tasks().size() == 1);
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals(1, count("pf_conversation_evaluation"));
        assertEquals(1, count("pf_case_review_signal"));
        assertEquals(0, count("pf_case_analysis"));
        assertEquals(0, count("pf_urgency_event"));
    }

    @Test
    void staleResultReturnsToSharedCurrentEvaluationAndNeverPublishesOldUrgency() throws Exception {
        TenantMessageService tenant = service(gateway());
        tenant.send(accepted.personalAccessToken(), UUID.randomUUID(), "Seit gestern ist es kalt.");
        new TenantMessageWorkers(engine, tenant).identify(engine, job(TenantMessageWorkers.IDENTIFY));
        ConversationEvaluationService central =
                new ConversationEvaluationService(workflow, messages, evaluations,
                        urgencies, input -> new ConversationAnalysis.Result(
                                "HIGH", false, List.of(), null, "test", "v1"),
                        input -> new Recommendation.Result(
                                "Verwaltung", "Prüfen", List.of(), List.of(), false, null, "test", "v1"), noClosure(), transactions,
                new AutomaticClosureRelease());
        ConversationEvaluationWorkers worker = new ConversationEvaluationWorkers(engine, central);
        worker.evaluate(engine, job(ConversationEvaluationWorkers.EVALUATE));
        ActivatedJob stale = job(ConversationEvaluationWorkers.APPLY);
        database.update("UPDATE pf_case SET version = version + 1 WHERE id = ?", caseId);
        worker.apply(engine, stale);
        assertEquals(0, count("pf_urgency_event"));
        ActivatedJob current = job(ConversationEvaluationWorkers.EVALUATE);
        assertEquals("NCurrentEvaluation", current.getElementId());
        worker.evaluate(engine, current);
        ActivatedJob apply = job(ConversationEvaluationWorkers.APPLY);
        worker.apply(engine, apply);
        await(() -> "COMPLETED".equals(engine.newProcessInstanceGetRequest(apply.getProcessInstanceKey()).send().join().getState().name()));
        assertEquals(1, count("pf_evaluation_delivery"));
        assertEquals(1, count("pf_urgency_event"));
        assertEquals("UNDER_CLARIFICATION", status());
        assertEquals(0, engine.newVariableSearchRequest().filter(filter -> filter.scopeKey(leader).name("evaluationRef"))
                .send().join().items().size());
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
