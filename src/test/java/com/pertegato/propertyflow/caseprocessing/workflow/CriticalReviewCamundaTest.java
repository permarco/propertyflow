package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.access.contract.StaffAccountAccess;
import com.pertegato.propertyflow.access.contract.SubmissionContext;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.caseprocessing.contract.CaseCreation;
import com.pertegato.propertyflow.caseprocessing.contract.CreateCaseCommand;
import com.pertegato.propertyflow.caseprocessing.contract.StaffTaskDecisions.StaffTask;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.ConversationEvaluationBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseDataStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.StartOrder;
import com.pertegato.propertyflow.caseprocessing.persistence.ConversationEvaluationStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffDecisionStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffReviewStore;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantMessageStore;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantReplyStore;
import com.pertegato.propertyflow.caseprocessing.persistence.UrgencyStore;
import com.pertegato.propertyflow.caseprocessing.service.AutomaticClosureRelease;
import com.pertegato.propertyflow.caseprocessing.service.AutomaticClosureService;
import com.pertegato.propertyflow.caseprocessing.service.AutomaticQuestionService;
import com.pertegato.propertyflow.caseprocessing.service.CaseWorkflowService;
import com.pertegato.propertyflow.caseprocessing.service.ConversationEvaluationService;
import com.pertegato.propertyflow.caseprocessing.service.StaffDecisionService;
import com.pertegato.propertyflow.caseprocessing.service.StaffReviewService;
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
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants.JobType.AUTOMATIC_PREPARE_RESULT;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants.JobType.AUTOMATIC_RECOMMENDATION;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants.JobType.AUTOMATIC_VALIDATE_QUESTIONS;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants.JobType.REGISTER_AUTOMATIC;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants.JobType.START_CLARIFICATION;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.AUTOMATIC_ACCEPT_RESULT;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.PREPARE_INITIAL;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.PREPARE_PROCESS_KEY;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.REGISTER_CASE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.STAFF_CLAIM;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.ConversationEvaluationBpmnConstants.JobType.AUTOMATIC_ADVISE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.ConversationEvaluationBpmnConstants.JobType.AUTOMATIC_ANALYZE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.ConversationEvaluationBpmnConstants.JobType.AUTOMATIC_ASSESS_CLOSURE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants.JobType.STAFF_PROGRESS;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants.JobType.STAFF_REGISTER;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants.JobType.STAFF_START_REVIEW;
import static com.pertegato.propertyflow.recommendation.ClosureAssessmentFixtures.noClosure;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Durchgehender Nachweis zu UC-027: Ein kritischer KI-Befund führt zur Mitarbeiterprüfung,
 * und erst die begründete Fortsetzung durch einen Menschen setzt den Fall in Bearbeitung.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "propertyflow.workflow.enabled=false")
@ActiveProfiles("propertyflow-backend")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_DATABASE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_CAMUNDA_URL", matches = ".+")
class CriticalReviewCamundaTest {

    private static final String CRITICAL_REASON = "Kritische Dringlichkeit: menschliche Prüfung erforderlich.";
    private static final String STAFF_REASON = "Sanitär-Notdienst angerufen";

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
    private TenantMessageStore messages;

    @Autowired
    private TenantReplyStore replies;

    @Autowired
    private ConversationEvaluationStore evaluations;

    @Autowired
    private UrgencyStore urgencies;

    @Autowired
    private AutomaticQuestionService questions;

    @Autowired
    private StaffReviewService reviewService;

    @Autowired
    private StaffReviewStore reviewStore;

    @Autowired
    private StaffAccountAccess access;

    @Autowired
    private CaseDataStore caseData;

    @Autowired
    private StaffDecisionStore decisions;

    private CamundaClient engine;
    private WebAccountContext actor;
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
        caseId = creation.create(new SubmissionContext.Guest(UUID.randomUUID()), new CreateCaseCommand("critical", "Wasser im Keller",
                "Wohnung 3", "Im Keller steht Wasser, es kommt aus einem Rohr und wird immer mehr.", "keller@example.test")).caseId();
        StartOrder start = workflow.claim(definitionKey, "<default>", Duration.ofMinutes(2)).orElseThrow();
        assertEquals(ProcessStartGateway.StartResult.STARTED, new CamundaProcessStartGateway(engine).ensureStarted(start));
        leader = WorkflowTestJobs.rootInstanceKey(engine, start);
        InitialCaseWorkers initial = new InitialCaseWorkers(engine, initialService);
        initial.prepareKey(engine, job(PREPARE_PROCESS_KEY));
        initial.register(engine, job(REGISTER_CASE));
        initial.prepareInitial(engine, job(PREPARE_INITIAL));
        initial.registerAutomatic(engine, job(REGISTER_AUTOMATIC));
        initial.clarification(engine, job(START_CLARIFICATION));
        assertEquals("UNDER_CLARIFICATION", status());
        // Mit zugeordneter Wohnung führt allein der kritische Befund zur Mitarbeiterprüfung.
        database.update("UPDATE pf_case SET assigned_unit = 'Wohnung 3' WHERE id = ?", caseId);
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
    void criticalFindingRequiresStaffReviewAndReasonedContinuationMovesCaseToWork() throws Exception {
        evaluateAsCritical();
        AutomaticQuestionWorkers automatic = new AutomaticQuestionWorkers(engine, questions, Mockito.mock(AutomaticClosureService.class));
        automatic.validate(engine, job(AUTOMATIC_VALIDATE_QUESTIONS));
        assertEquals("REVIEW", database.queryForObject("SELECT state FROM pf_automatic_question_batch", String.class));
        automatic.finish(engine, job(AUTOMATIC_PREPARE_RESULT));
        accept(automatic);
        startReview();

        // Kritischer Befund: Mitarbeiterprüfung, sichtbarer Prüfgrund, keine Rückfrage und kein Abschluss.
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals("CRITICAL", database.queryForObject("SELECT urgency FROM pf_case", String.class));
        assertEquals(StaffProcessingBpmnConstants.UserTask.REVIEW_TASK, tasks().getFirst().getElementId());
        assertTrue(reviewStore.reasons(caseId).contains(CRITICAL_REASON));
        assertEquals(0, count("pf_staff_message"));
        assertEquals(0, count("pf_case_closure"));

        StaffDecisionService service = new StaffDecisionService(access, workflow, caseData, reviewStore, decisions, gateway(), transactions);
        StaffTask task = service.current(actor, caseId);
        assertTrue(task.available());
        UUID operation = UUID.randomUUID();
        service.continueWork(actor, caseId, operation, Long.parseLong(task.taskKey()), task.version(), task.reviewToken(), STAFF_REASON);
        new StaffDecisionWorkers(engine, service).progress(engine, job(STAFF_PROGRESS));
        await(() -> tasks().size() == 1 && StaffProcessingBpmnConstants.UserTask.WORK_TASK.equals(tasks().getFirst().getElementId()));

        // Die menschliche Entscheidung ist mit Person, Aufgabe und Begründung nachvollziehbar.
        assertEquals("IN_PROGRESS", status());
        StaffDecisionStore.Decision decision = decisions.find(caseId, actor.accountId(), operation).orElseThrow();
        assertEquals("APPLIED", decision.state());
        assertEquals(STAFF_REASON, decision.reason());
        assertEquals(task.taskKey(), Long.toString(decision.taskKey()));
        assertEquals(decision.id(), database.queryForObject(
                "SELECT considered_decision_id FROM pf_case_review_signal WHERE reason = 'CRITICAL_URGENCY'", UUID.class));
        assertFalse(reviewStore.reasons(caseId).contains(CRITICAL_REASON));
        assertEquals(0, count("pf_case_closure"));
    }

    private void evaluateAsCritical() throws Exception {
        ConversationEvaluationService central = new ConversationEvaluationService(workflow, messages, evaluations, urgencies,
                input -> new ConversationAnalysis.Result("CRITICAL", false, List.of(), null, "test", "critical-v1"),
                input -> new Recommendation.Result("Sanitär", "Sanitär-Notdienst aufbieten", List.of(), List.of(), false, null,
                        "test", "critical-v1"),
                noClosure(), transactions, new AutomaticClosureRelease(), replies);
        AutomaticEvaluationWorkers evaluation = new AutomaticEvaluationWorkers(engine, central);
        ActivatedJob analysis = job(AUTOMATIC_ANALYZE);
        evaluation.analyze(engine, analysis);
        if (hasNextStage(analysis.getProcessInstanceKey(), ConversationEvaluationBpmnConstants.ServiceTask.RECOMMEND_PROCEDURE)) {
            evaluation.advise(engine, job(AUTOMATIC_ADVISE));
        }
        if (hasNextStage(analysis.getProcessInstanceKey(), ConversationEvaluationBpmnConstants.ServiceTask.ASSESS_CLOSURE)) {
            evaluation.assessClosure(engine, job(AUTOMATIC_ASSESS_CLOSURE));
        }
        evaluation.recommend(engine, job(AUTOMATIC_RECOMMENDATION));
        assertEquals("PENDING", database.queryForObject(
                "SELECT state FROM pf_case_review_signal WHERE reason = 'CRITICAL_URGENCY'", String.class));
    }

    private void accept(AutomaticQuestionWorkers automatic) throws Exception {
        ActivatedJob result = job(AUTOMATIC_ACCEPT_RESULT);
        CaseWorkflowStore.Entry entry = workflow.entryForCase(caseId).orElseThrow();
        await(() -> "COMPLETED".equals(engine.newProcessInstanceGetRequest(entry.childKey()).send().join().getState().name())
                && "COMPLETED".equals(engine.newElementInstanceGetRequest(entry.parentElementKey()).send().join().getState().name()));
        automatic.accept(engine, result);
    }

    private void startReview() throws Exception {
        StaffReviewWorkers reviews = new StaffReviewWorkers(engine, reviewService);
        reviews.claim(engine, job(STAFF_CLAIM));
        reviews.register(engine, job(STAFF_REGISTER));
        reviews.startReview(engine, job(STAFF_START_REVIEW));
        await(() -> tasks().size() == 1);
    }

    private CamundaStaffTaskGateway gateway() {
        return new CamundaStaffTaskGateway(new StaticListableBeanFactory(Map.of("engine", engine)).getBeanProvider(CamundaClient.class), true);
    }

    private boolean hasNextStage(long process, String element) throws InterruptedException {
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
        throw new AssertionError("Auswertungsschritt wurde weder aktiv noch beendet: " + element);
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
        throw new AssertionError("Engine-Zustand hat sich nicht eingestellt");
    }

    private List<UserTask> tasks() {
        return engine.newUserTaskSearchRequest().filter(filter -> filter.state(UserTaskState.CREATED)).send().join().items();
    }

    private String status() {
        return database.queryForObject("SELECT status FROM pf_case", String.class);
    }

    private int count(String table) {
        return database.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
