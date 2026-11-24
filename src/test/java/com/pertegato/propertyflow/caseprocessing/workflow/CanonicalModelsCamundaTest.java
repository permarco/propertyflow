package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.access.contract.StaffAccountAccess;
import com.pertegato.propertyflow.access.contract.SubmissionContext;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.ConversationEvaluationBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.TenantMessageBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.TenantReplyBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.CaseAccepted;
import com.pertegato.propertyflow.caseprocessing.contract.CaseCreation;
import com.pertegato.propertyflow.caseprocessing.contract.CreateCaseCommand;
import com.pertegato.propertyflow.caseprocessing.contract.FollowUpCommands;
import com.pertegato.propertyflow.caseprocessing.contract.StaffCaseDataCommands.ClosureApproval;
import com.pertegato.propertyflow.caseprocessing.contract.StaffDecisionAction;
import com.pertegato.propertyflow.caseprocessing.contract.StaffTaskDecisions.StaffTask;
import com.pertegato.propertyflow.caseprocessing.contract.Urgency;
import com.pertegato.propertyflow.caseprocessing.delivery.PersonalLinkCodec;
import com.pertegato.propertyflow.caseprocessing.persistence.AnswerRequirementStore;
import com.pertegato.propertyflow.caseprocessing.persistence.AutomaticClosureStore;
import com.pertegato.propertyflow.caseprocessing.persistence.AutomaticQuestionStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseClosureStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseDataStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseReopenStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWaitStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.CaseState;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.StartOrder;
import com.pertegato.propertyflow.caseprocessing.persistence.ClarificationQuestionStore;
import com.pertegato.propertyflow.caseprocessing.persistence.ConversationEvaluationStore;
import com.pertegato.propertyflow.caseprocessing.persistence.DirectFollowUpStore;
import com.pertegato.propertyflow.caseprocessing.persistence.FollowUpCommandStore;
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
import com.pertegato.propertyflow.caseprocessing.service.CaseReopenService;
import com.pertegato.propertyflow.caseprocessing.service.CaseUrgencyService;
import com.pertegato.propertyflow.caseprocessing.service.CaseWaitService;
import com.pertegato.propertyflow.caseprocessing.service.CaseWorkflowService;
import com.pertegato.propertyflow.caseprocessing.service.ConversationEvaluationService;
import com.pertegato.propertyflow.caseprocessing.service.FollowUpCommandService;
import com.pertegato.propertyflow.caseprocessing.service.StaffDecisionService;
import com.pertegato.propertyflow.caseprocessing.service.StaffMessageService;
import com.pertegato.propertyflow.caseprocessing.service.StaffReviewService;
import com.pertegato.propertyflow.caseprocessing.service.TenantMessageService;
import com.pertegato.propertyflow.caseprocessing.service.TenantReplyService;
import com.pertegato.propertyflow.recommendation.AutomaticClosureFixtures;
import com.pertegato.propertyflow.recommendation.service.ClosureAssessment;
import com.pertegato.propertyflow.recommendation.service.ClosureMessage;
import com.pertegato.propertyflow.recommendation.service.ClosureMessageService;
import com.pertegato.propertyflow.recommendation.service.Recommendation;
import com.pertegato.propertyflow.triage.service.ConversationAnalysis;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.search.enums.MessageSubscriptionState;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.client.CamundaClient;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.function.BooleanSupplier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.JsonNode;

import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants.JobType.AUTOMATIC_CHECK_CLOSURE_MESSAGE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants.JobType.AUTOMATIC_CLOSE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants.JobType.AUTOMATIC_CONFIRM_CLOSURE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants.JobType.AUTOMATIC_PREPARE_RESULT;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants.JobType.AUTOMATIC_PUBLISH_QUESTIONS;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants.JobType.AUTOMATIC_RECOMMENDATION;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants.JobType.AUTOMATIC_VALIDATE_QUESTIONS;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants.JobType.AUTOMATIC_WRITE_CLOSURE_MESSAGE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants.JobType.REGISTER_AUTOMATIC;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants.JobType.START_CLARIFICATION;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.ErrorCode.UNKNOWN_CASE_ACTION;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.ANSWER_CLEAR;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.ANSWER_CONTINUE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.AUTOMATIC_ACCEPT_RESULT;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.AUTOMATIC_PREPARE_CLOSE_ROUND;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.CASE_DATA_APPLY;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.CASE_REOPEN;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.CLARIFICATION_CLOSE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.DIRECT_FOLLOW_UP_APPLY;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.DIRECT_FOLLOW_UP_CONTINUE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.DIRECT_FOLLOW_UP_SIGNAL;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.PREPARE_INITIAL;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.PREPARE_PROCESS_KEY;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.REGISTER_CASE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.REPLY_CLOSURE_CHECK;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.REPLY_PREPARE_REVIEW;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.REVIEW_ANALYSIS_FAILURE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.REVIEW_CHECK;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.REVIEW_INTERRUPT_AUTOMATIC;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.REVIEW_PREPARE_STAFF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.STAFF_CLAIM;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.STAFF_CLOSE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.TAKEOVER_APPLY;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.TAKEOVER_SIGNAL;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.ACTION_TYPE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.ANSWER_CANCELLATION_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.CASE_DATA_COMMAND_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.CLARIFICATION_CLOSURE_COMMAND_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.CLEAR_ANSWER_COMMAND_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.CLEAR_ANSWER_OUTCOME;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.CLEARED_ANSWER_CALL_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.DIRECT_FOLLOW_COMMAND_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.DIRECT_FOLLOW_CONTINUATION_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.MANUAL_QUESTION_PREVIOUS_REPLY_ENTRY_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.REPLY_CLOSURE_COMMAND_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.REPLY_CONTINUATION_SOURCE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.REPLY_DECISION_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.REPLY_EXIT_REASON;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.STAFF_CLOSE_REPLY_CALL_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.STAFF_CLOSE_REPLY_ENTRY_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.STAFF_FOLLOW_COMMAND_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.STAFF_FOLLOW_REPLY_CALL_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.STAFF_FOLLOW_REPLY_ENTRY_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.STAFF_OUTCOME;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.STAFF_REPLY_CLOSURE_DECISION_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.STAFF_TAKEOVER_COMMAND_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.STAFF_TAKEOVER_REPLY_ENTRY_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.JobType.STAFF_MESSAGE_PUBLISH;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.AUTOMATIC_INTERRUPTION_OUTCOME;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.ENTRY_CONTEXT_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.EVALUATION_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.PENDING_REVIEW_FOLLOW_UP_VERSION;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.PHASE_REVIEW_SIGNAL_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.REPLY_ENTRY_CONTEXT_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.REVIEW_NEED_REPLY_CALL_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.REVIEW_SIGNAL_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.STAFF_DECISION_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.STAFF_ENTRY_CONTEXT_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.STAFF_MESSAGE_COMMAND_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.TENANT_MESSAGE_ID;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.ConversationEvaluationBpmnConstants.JobType.AUTOMATIC_ADVISE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.ConversationEvaluationBpmnConstants.JobType.AUTOMATIC_ANALYZE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.ConversationEvaluationBpmnConstants.JobType.AUTOMATIC_ASSESS_CLOSURE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.FollowUpBpmnConstants.JobType.FOLLOW_UP_CHANGE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.FollowUpBpmnConstants.JobType.FOLLOW_UP_SAVE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants.JobType.FOLLOW_UP_STOP_CONTACT;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants.JobType.STAFF_PROGRESS;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants.JobType.STAFF_QUESTION_PUBLISH;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants.JobType.STAFF_REGISTER;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants.JobType.STAFF_START_REVIEW;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants.JobType.STAFF_WAIT_EXTERNAL;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.TenantMessageBpmnConstants.JobType.CONVERSATION_APPLY;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.TenantMessageBpmnConstants.JobType.CONVERSATION_CONTEXT;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.TenantMessageBpmnConstants.JobType.CONVERSATION_RECOMMENDATION;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.TenantMessageBpmnConstants.JobType.TENANT_MESSAGE_IDENTIFY;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.TenantReplyBpmnConstants.JobType.REPLY_APPLY_EVALUATION;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.TenantReplyBpmnConstants.JobType.REPLY_REGISTER;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.TenantReplyBpmnConstants.JobType.REPLY_VALIDATE;
import static com.pertegato.propertyflow.recommendation.ClosureAssessmentFixtures.noClosure;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prüft implementierte Pfade direkt aus den vollständigen Quelldateien.
 * Vier noch offene Abschlussaufgaben erhalten nur im Test unerfüllte Jobtypen.
 * Ihre Ausführung wäre ein Testfehler; eine fachliche Abschlussfreigabe folgt daraus nicht.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "propertyflow.workflow.enabled=false")
@ActiveProfiles("propertyflow-backend")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_DATABASE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_CAMUNDA_URL", matches = ".+")
class CanonicalModelsCamundaTest {


    @Autowired
    private CaseCreation creation;
    @Autowired
    private CaseWorkflowStore workflow;
    @Autowired
    private CaseWorkflowService initialService;
    @Autowired
    private StaffReviewService reviewService;
    @Autowired
    private JdbcTemplate database;
    @Autowired
    private PlatformTransactionManager transactions;
    @Autowired
    private CaseClosureService closures;
    @Autowired
    private CaseDataStore caseData;
    @Autowired
    private StaffAccountAccess access;
    @Autowired
    private CaseUrgencyService urgency;

    @Autowired
    private StaffReviewStore reviewStore;
    @Autowired
    private StaffDecisionStore decisionStore;
    @Autowired
    private CaseWaitService waitService;
    @Autowired
    private CaseWaitStore waitStore;
    @Autowired
    private FollowUpCommandStore followUpStore;
    @Autowired
    private CaseReopenStore reopenStore;
    @Autowired
    private TenantMessageStore tenantMessages;
    @Autowired
    private TenantReplyStore replyStore;
    @Autowired
    private ConversationEvaluationStore evaluations;
    @Autowired
    private UrgencyStore urgencies;
    @Autowired
    private PersonalLinkCodec links;
    @Autowired
    private AutomaticQuestionService questions;
    @Autowired
    private StaffMessageStore staffMessages;

    private CaseAccepted accepted;
    private CamundaClient engine;
    private String caseId;
    private Long leader;
    private WebAccountContext actor;
    private CaseDataService data;

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
        engine = CamundaClient.newClientBuilder()
                .restAddress(URI.create(System.getenv("PROPERTYFLOW_TEST_CAMUNDA_URL")))
                .preferRestOverGrpc(true)
                .defaultRequestTimeout(Duration.ofSeconds(5))
                .build();
        DeploymentEvent deployment = WorkflowTestModels.deploy(engine);
        long definition = deployment.getProcesses().stream()
                .filter(process -> CaseProcessBpmnConstants.PROCESS_ID.equals(process.getBpmnProcessId()))
                .findFirst().orElseThrow().getProcessDefinitionKey();
        accepted = creation.create(new SubmissionContext.Guest(UUID.randomUUID()),
                new CreateCaseCommand(UUID.randomUUID().toString(), "Heizung", "Wohnung", "Kalt", "canonical@example.test"));
        caseId = accepted.caseId();
        StartOrder start = workflow.claim(definition, "<default>", Duration.ofMinutes(2)).orElseThrow();
        assertEquals(ProcessStartGateway.StartResult.STARTED, new CamundaProcessStartGateway(engine).ensureStarted(start));
        InitialCaseWorkers initial = new InitialCaseWorkers(engine, initialService);
        ActivatedJob prepare = job(PREPARE_PROCESS_KEY);
        leader = prepare.getProcessInstanceKey();
        initial.prepareKey(engine, prepare);
        initial.register(engine, job(REGISTER_CASE));
        initial.prepareInitial(engine, job(PREPARE_INITIAL));
        initial.registerAutomatic(engine, job(REGISTER_AUTOMATIC));
        initial.clarification(engine, job(START_CLARIFICATION));
        assertEquals("UNDER_CLARIFICATION", status());
        actor = new WebAccountContext(UUID.randomUUID());
        database.update("""
                INSERT INTO pf_staff_account (id, name, email, active, staff_role, account_administration)
                VALUES (?, 'Modelltest', ?, TRUE, TRUE, FALSE)
                """, actor.accountId(), actor.accountId() + "@example.test");
        database.update("UPDATE pf_case SET assigned_unit = 'Wohnung A' WHERE id = ?", caseId);
        data = new CaseDataService(access, workflow, caseData,
                new CamundaCaseGateway(new StaticListableBeanFactory(Map.of("engine", engine)).getBeanProvider(CamundaClient.class), true),
                urgency, new AnswerRequirementStore(database), new StaffTakeoverStore(database), closures,
                new DirectFollowUpStore(database, new CaseWaitStore(database), new StaffTakeoverStore(database)), transactions);
    }

    @AfterEach
    void cleanup() {
        if (engine != null) {
            if (leader != null && "ACTIVE".equals(engine.newProcessInstanceGetRequest(leader).send().join().getState().name())) {
                engine.newCancelInstanceCommand(leader).send().join();
            }
            engine.close();
        }
    }

    @Test
    void completeModelsSupportIntakeAndHumanTakeoverWithoutAutomaticClosureJobs() throws Exception {
        takeOver();
        assertEquals("IN_PROGRESS", status());
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_staff_continuation", Integer.class));
    }

    @Test
    void completeModelsTerminateTheWholeCaseAfterCommittedHumanClosure() throws Exception {
        closeClarification();
    }

    @Test
    void sharedReceptionRoutesAllNineActionTypesToTheirOwnPendingJob() throws Exception {
        List<ActionProbe> probes = List.of(
                new ActionProbe(CaseActionMessage.TENANT_MESSAGE, TENANT_MESSAGE_IDENTIFY,
                        TenantMessageBpmnConstants.ServiceTask.CHECK_MESSAGE, TENANT_MESSAGE_ID),
                new ActionProbe(CaseActionMessage.CLEAR_ANSWER, ANSWER_CLEAR,
                        CaseProcessBpmnConstants.ServiceTask.CLEAR_ANSWER_REQUIREMENT, CLEAR_ANSWER_COMMAND_REF),
                new ActionProbe(CaseActionMessage.CLOSE_REPLY, REPLY_CLOSURE_CHECK,
                        CaseProcessBpmnConstants.ServiceTask.CHECK_CLOSURE_DURING_REPLY, REPLY_CLOSURE_COMMAND_REF),
                new ActionProbe(CaseActionMessage.REVIEW_REQUIRED, REVIEW_CHECK,
                        CaseProcessBpmnConstants.ServiceTask.CHECK_REVIEW_REASON, REVIEW_SIGNAL_REF),
                new ActionProbe(CaseActionMessage.STAFF_QUESTION, STAFF_MESSAGE_PUBLISH,
                        CaseProcessBpmnConstants.ServiceTask.SAVE_STAFF_MESSAGE, STAFF_MESSAGE_COMMAND_REF),
                new ActionProbe(CaseActionMessage.CLOSE_CLARIFICATION, CLARIFICATION_CLOSE,
                        CaseProcessBpmnConstants.ServiceTask.CLOSE_DURING_CLARIFICATION, CLARIFICATION_CLOSURE_COMMAND_REF),
                new ActionProbe(CaseActionMessage.DIRECT_FOLLOW_UP, DIRECT_FOLLOW_UP_APPLY,
                        CaseProcessBpmnConstants.ServiceTask.SAVE_DIRECT_FOLLOW_UP, DIRECT_FOLLOW_COMMAND_REF),
                new ActionProbe(CaseActionMessage.STAFF_TAKEOVER, TAKEOVER_APPLY,
                        CaseProcessBpmnConstants.ServiceTask.SAVE_STAFF_TAKEOVER, STAFF_TAKEOVER_COMMAND_REF),
                new ActionProbe(CaseActionMessage.CASE_DATA, CASE_DATA_APPLY,
                        CaseProcessBpmnConstants.ServiceTask.APPLY_CASE_DATA, CASE_DATA_COMMAND_REF));

        // Erst alle Eingänge zustellen. Kein Worker darf zur Freigabe des nächsten Empfangs fertig werden müssen.
        for (ActionProbe probe : probes) {
            assertEquals(leader, probe.action().correlate(engine, Long.toString(leader), "<default>", probe.reference()).getProcessInstanceKey(),
                    probe.action().name());
        }
        for (ActionProbe probe : probes) {
            ActivatedJob pending = job(probe.jobType());
            assertEquals(probe.element(), pending.getElementId(), probe.action().name());
            Map<String, Object> variables = pending.getVariablesAsMap();
            assertEquals(probe.reference().toString(), variables.get(probe.referenceField()), probe.action().name());
            for (ActionProbe other : probes) {
                if (other != probe) {
                    assertNull(variables.get(other.referenceField()), probe.action().name() + ": " + other.referenceField());
                }
            }
        }
        assertEquals("UNDER_CLARIFICATION", status());
    }

    @Test
    void groupedReceptionStillRejectsAnUnknownAction() throws Exception {
        engine.newCorrelateMessageCommand()
                .messageName(CaseActionMessage.MESSAGE_NAME)
                .correlationKey(Long.toString(leader))
                .tenantId("<default>")
                .variables(Map.of(ACTION_TYPE, "UNKNOWN"))
                .send()
                .join();

        await(() -> engine.newIncidentSearchRequest()
                .filter(filter -> filter.processInstanceKey(leader))
                .send()
                .join()
                .items()
                .stream()
                .anyMatch(incident -> CaseProcessBpmnConstants.EndEvent.UNKNOWN_STAFF_ACTION_END.equals(incident.getElementId())
                        && incident.getErrorMessage().contains(UNKNOWN_CASE_ACTION)));
        assertEquals("UNDER_CLARIFICATION", status());
    }

    @Test
    void sharedReceptionKeepsPendingMessagesSeparateAndAcceptsClosureWhileTheyAreOpen() throws Exception {
        TenantMessageService tenant = tenantMessageService();
        String token = accepted.personalAccessToken();
        UUID first = tenant.send(token, "first-message", "Die Heizung ist kalt.");
        UUID second = tenant.send(token, "second-message", "Jetzt bleibt auch das Wasser kalt.");
        assertEquals(first, tenant.send(token, "first-message", "Die Heizung ist kalt."));
        assertEquals(2, database.queryForObject("SELECT count(*) FROM pf_tenant_message WHERE case_id = ?", Integer.class, caseId));

        ActivatedJob firstPending = job(TENANT_MESSAGE_IDENTIFY);
        ActivatedJob secondPending = job(TENANT_MESSAGE_IDENTIFY);
        assertEquals(Set.of(first.toString(), second.toString()),
                Set.of(firstPending.getVariablesAsMap().get(TENANT_MESSAGE_ID), secondPending.getVariablesAsMap().get(TENANT_MESSAGE_ID)));
        assertTrue(firstPending.getProcessInstanceKey() != secondPending.getProcessInstanceKey());

        ClosureApproval approval = data.clarificationClosure(actor, caseId);
        assertTrue(approval.available());
        data.closeClarification(actor, caseId, "close-with-pending-messages", approval.version(), approval.reviewToken());
        new ClarificationClosureWorkers(engine, data).close(engine, job(CLARIFICATION_CLOSE));
        await(() -> "COMPLETED".equals(engine.newProcessInstanceGetRequest(leader).send().join().getState().name()));
        assertEquals("CLOSED", status());
        assertEquals("TERMINATED", engine.newProcessInstanceGetRequest(firstPending.getProcessInstanceKey()).send().join().getState().name());
        assertEquals("TERMINATED", engine.newProcessInstanceGetRequest(secondPending.getProcessInstanceKey()).send().join().getState().name());
        await(() -> engine.newMessageSubscriptionSearchRequest()
                .filter(filter -> filter.processInstanceKey(leader).elementId(CaseProcessBpmnConstants.StartEvent.ACTION_RECEIVER)
                        .messageSubscriptionState(MessageSubscriptionState.CREATED))
                .send().join().items().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void initialEvaluationRunsEachModelInItsOwnJobAndChecksTheCompletedResult(boolean foundationChanged) throws Exception {
        List<String> calls = new ArrayList<>();
        ConversationEvaluationService central = new ConversationEvaluationService(workflow, tenantMessages, evaluations, urgencies,
                input -> {
                    calls.add("analysis");
                    return new ConversationAnalysis.Result("NORMAL", false, List.of(), null, "test", "canonical-v1");
                }, input -> {
                    calls.add("advice");
                    return new Recommendation.Result("Verwaltung", "Prüfen", List.of(), List.of("RQ-001"),
                            false, null, "test", "canonical-v1");
                }, messages -> {
                    calls.add("closure");
                    return noClosure().assess(messages);
                }, transactions, new AutomaticClosureRelease(), replyStore);
        AutomaticEvaluationWorkers evaluation = new AutomaticEvaluationWorkers(engine, central);
        evaluation.analyze(engine, job(AUTOMATIC_ANALYZE));
        assertEquals(List.of("analysis"), calls);

        if (foundationChanged) {
            database.update("UPDATE pf_case SET version = version + 1, analysis_version = analysis_version + 1 WHERE id = ?", caseId);
            evaluation.advise(engine, job(AUTOMATIC_ADVISE));
            assertEquals(List.of("analysis"), calls);
            evaluation.analyze(engine, job(AUTOMATIC_ANALYZE));
            assertEquals(List.of("analysis", "analysis"), calls);
        }

        int analysisCalls = calls.size();
        evaluation.advise(engine, job(AUTOMATIC_ADVISE));
        assertEquals(List.of("advice"), calls.subList(analysisCalls, calls.size()));
        evaluation.assessClosure(engine, job(AUTOMATIC_ASSESS_CLOSURE));
        assertEquals(List.of("advice", "closure"), calls.subList(analysisCalls, calls.size()));

        ActivatedJob check = job(AUTOMATIC_RECOMMENDATION);
        assertEquals(AutomaticClarificationBpmnConstants.ServiceTask.CHECK_EVALUATION, check.getElementId());
        evaluation.recommend(engine, check);
        job(AUTOMATIC_VALIDATE_QUESTIONS);
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_conversation_evaluation WHERE state = 'APPLIED'", Integer.class));
        assertEquals(List.of("advice", "closure"), calls.subList(analysisCalls, calls.size()));
    }

    @Test
    void staleInitialResultCheckReturnsToTheSharedProcessBeforeCheckingTheNewResult() throws Exception {
        List<String> closureCalls = new ArrayList<>();
        ConversationEvaluationService central = central(false, List.of("RQ-001"), messages -> {
            closureCalls.add("closure");
            return noClosure().assess(messages);
        });
        AutomaticEvaluationWorkers evaluation = new AutomaticEvaluationWorkers(engine, central);
        runAutomaticEvaluationStages(evaluation);
        ActivatedJob stale = job(AUTOMATIC_RECOMMENDATION);
        assertEquals(List.of("closure"), closureCalls);

        advanceCaseBasis();
        evaluation.recommend(engine, stale);
        assertEquals(List.of("closure"), closureCalls);
        runAutomaticEvaluationStages(evaluation);
        ActivatedJob refreshed = job(AUTOMATIC_RECOMMENDATION);
        assertEquals(stale.getElementId(), refreshed.getElementId());
        assertEquals(stale.getProcessInstanceKey(), refreshed.getProcessInstanceKey());
        assertNotEquals(stale.getVariablesAsMap().get(EVALUATION_REF), refreshed.getVariablesAsMap().get(EVALUATION_REF));
        evaluation.recommend(engine, refreshed);
        job(AUTOMATIC_VALIDATE_QUESTIONS);
        assertEquals(List.of("closure", "closure"), closureCalls);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void invalidInitialAnalysisOrAdviceSkipsLaterModelJobsAndOpensStaffReview(boolean analysisFails) throws Exception {
        ConversationEvaluationService central = new ConversationEvaluationService(workflow, tenantMessages, evaluations, urgencies,
                input -> new ConversationAnalysis.Result("NORMAL", false, List.of(), analysisFails ? "ANALYSIS_UNAVAILABLE" : null,
                        "test", "canonical-v1"),
                input -> new Recommendation.Result(null, null, List.of(), List.of(), true, "APPROVED_KNOWLEDGE_MISSING",
                        "test", "canonical-v1"),
                noClosure(), transactions, new AutomaticClosureRelease(), replyStore);
        AutomaticEvaluationWorkers evaluation = new AutomaticEvaluationWorkers(engine, central);
        evaluation.analyze(engine, job(AUTOMATIC_ANALYZE));
        if (!analysisFails) {
            evaluation.advise(engine, job(AUTOMATIC_ADVISE));
        }
        evaluation.recommend(engine, job(AUTOMATIC_RECOMMENDATION));

        StaffReviewWorkers staff = new StaffReviewWorkers(engine, reviewService);
        staff.analysisFailure(engine, job(REVIEW_ANALYSIS_FAILURE));
        staff.claim(engine, job(STAFF_CLAIM));
        staff.register(engine, job(STAFF_REGISTER));
        staff.startReview(engine, job(STAFF_START_REVIEW));
        awaitTask(StaffProcessingBpmnConstants.UserTask.REVIEW_TASK);
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_case_review_signal WHERE reason = 'ANALYSIS_FAILED'", Integer.class));
    }

    @Test
    void tenantMessageRefreshUsesTheSameStagesAndAppliesOnlyTheLatestResult() throws Exception {
        job(AUTOMATIC_ANALYZE);
        List<String> calls = new ArrayList<>();
        ConversationEvaluationService central = new ConversationEvaluationService(workflow, tenantMessages, evaluations, urgencies,
                input -> {
                    calls.add("analysis");
                    return new ConversationAnalysis.Result("HIGH", false, List.of(), null, "test", "canonical-v1");
                }, input -> {
                    calls.add("advice");
                    return new Recommendation.Result("Verwaltung", "Prüfen", List.of(), List.of(), false, null, "test", "canonical-v1");
                }, messages -> {
                    calls.add("closure");
                    return noClosure().assess(messages);
                }, transactions, new AutomaticClosureRelease(), replyStore);
        ConversationEvaluationWorkers tenant = prepareTenantEvaluation("Seit gestern ist es in der Wohnung kalt.", central);
        AutomaticEvaluationWorkers evaluation = new AutomaticEvaluationWorkers(engine, central);
        ActivatedJob first = sharedAnalysisJob(TenantMessageBpmnConstants.CallActivity.EVALUATE_MESSAGE);
        runEvaluationStages(evaluation, first, true);
        assertEquals(List.of("analysis", "advice", "closure"), calls);
        tenant.recommendation(engine, job(CONVERSATION_RECOMMENDATION));
        ActivatedJob stale = job(CONVERSATION_APPLY);
        assertEquals(0, database.queryForObject("SELECT count(*) FROM pf_urgency_event", Integer.class));

        advanceCaseBasis();
        tenant.apply(engine, stale);
        ActivatedJob refreshed = sharedAnalysisJob(TenantMessageBpmnConstants.CallActivity.REFRESH_EVALUATION);
        assertEquals(first.getElementId(), refreshed.getElementId());
        runEvaluationStages(evaluation, refreshed, true);
        assertEquals(List.of("analysis", "advice", "closure", "analysis", "advice", "closure"), calls);
        ActivatedJob current = job(CONVERSATION_APPLY);
        assertEquals(stale.getProcessInstanceKey(), current.getProcessInstanceKey());
        assertEquals(0, database.queryForObject("SELECT count(*) FROM pf_urgency_event", Integer.class));
        tenant.apply(engine, current);
        await(() -> "COMPLETED".equals(engine.newProcessInstanceGetRequest(current.getProcessInstanceKey()).send().join().getState().name()));
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_urgency_event", Integer.class));
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_evaluation_delivery", Integer.class));
        assertEquals("UNDER_CLARIFICATION", status());
    }

    @Test
    void failedTenantAnalysisUsesExistingErrorContextAndPreservesUrgency() throws Exception {
        job(AUTOMATIC_ANALYZE);
        ConversationEvaluationService central = new ConversationEvaluationService(workflow, tenantMessages, evaluations, urgencies,
                input -> new ConversationAnalysis.Result("NORMAL", false, List.of(), "ANALYSIS_UNAVAILABLE", "test", "canonical-v1"),
                input -> {
                    throw new AssertionError("Advice after failed analysis");
                },
                noClosure(), transactions, new AutomaticClosureRelease(), replyStore);
        ConversationEvaluationWorkers tenant = prepareTenantEvaluation("Die Störung ist weiterhin offen.", central);
        AutomaticEvaluationWorkers evaluation = new AutomaticEvaluationWorkers(engine, central);
        evaluation.analyze(engine, sharedAnalysisJob(TenantMessageBpmnConstants.CallActivity.EVALUATE_MESSAGE));
        tenant.recommendation(engine, job(CONVERSATION_RECOMMENDATION));
        ActivatedJob failure = job(CONVERSATION_CONTEXT);
        assertEquals(TenantMessageBpmnConstants.ServiceTask.RECORD_EVALUATION_FAILURE, failure.getElementId());
        tenant.context(engine, failure);
        tenant.apply(engine, job(CONVERSATION_APPLY));
        assertEquals(0, database.queryForObject("SELECT count(*) FROM pf_urgency_event", Integer.class));
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_case_review_signal WHERE reason = 'ANALYSIS_FAILED'", Integer.class));
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_evaluation_delivery", Integer.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unapprovedPositiveOrFailedClosureAssessmentReachesOneRealStaffReview(boolean failure) throws Exception {
        ConversationEvaluationService central = central(false, List.of("RQ-001"), context ->
                new ClosureAssessment.Result(failure ? ClosureAssessment.Outcome.REVIEW : ClosureAssessment.Outcome.RESOLVED,
                        null, null, List.of(), "Synthetischer Vorschlag für den Rückwegtest",
                        failure ? "CLOSURE_ASSESSMENT_UNAVAILABLE" : null, "test", "fixture-v1", "fixture-validation-v1"));
        AutomaticEvaluationWorkers evaluation = new AutomaticEvaluationWorkers(engine, central);
        finishInitialEvaluation(evaluation);
        AutomaticQuestionWorkers automatic = new AutomaticQuestionWorkers(engine, questions, org.mockito.Mockito.mock(AutomaticClosureService.class));
        automatic.validate(engine, job(AUTOMATIC_VALIDATE_QUESTIONS));
        automatic.finish(engine, job(AUTOMATIC_PREPARE_RESULT));
        ActivatedJob result = job(AUTOMATIC_ACCEPT_RESULT);
        CaseWorkflowStore.Entry entry = workflow.entryForCase(caseId).orElseThrow();
        await(() -> "COMPLETED".equals(engine.newProcessInstanceGetRequest(entry.childKey()).send().join().getState().name())
                && "COMPLETED".equals(engine.newElementInstanceGetRequest(entry.parentElementKey()).send().join().getState().name()));
        automatic.accept(engine, result);
        StaffReviewWorkers staff = new StaffReviewWorkers(engine, reviewService);
        staff.claim(engine, job(STAFF_CLAIM));
        staff.register(engine, job(STAFF_REGISTER));
        staff.startReview(engine, job(STAFF_START_REVIEW));
        awaitTask(StaffProcessingBpmnConstants.UserTask.REVIEW_TASK);
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals(1, tasks().size());
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_conversation_evaluation", Integer.class));
        assertEquals(0, database.queryForObject("SELECT count(*) FROM pf_case_closure", Integer.class));
        assertEquals(0, database.queryForObject("SELECT count(*) FROM pf_staff_message", Integer.class));
    }

    @Test
    void staleValidationReusesTheEvaluationStagesAndStillReviewsAFailedClosureAssessment() throws Exception {
        AutomaticEvaluationWorkers initial = new AutomaticEvaluationWorkers(engine, central(false, List.of("RQ-001")));
        finishInitialEvaluation(initial);
        database.update("UPDATE pf_case SET version = version + 1, analysis_version = analysis_version + 1 WHERE id = ?", caseId);
        AutomaticQuestionWorkers automatic = new AutomaticQuestionWorkers(engine, questions,
                org.mockito.Mockito.mock(AutomaticClosureService.class));
        automatic.validate(engine, job(AUTOMATIC_VALIDATE_QUESTIONS));

        ConversationEvaluationService central = central(false, List.of("RQ-001"), messages ->
                new ClosureAssessment.Result(ClosureAssessment.Outcome.REVIEW, null, null, List.of(),
                        "Synthetischer Fehler bei der erneuten Abschlussbeurteilung", "CLOSURE_ASSESSMENT_UNAVAILABLE",
                        "test", "fixture-v1", "fixture-validation-v1"));
        runAutomaticEvaluationStages(new AutomaticEvaluationWorkers(engine, central));
        automatic.validate(engine, job(AUTOMATIC_VALIDATE_QUESTIONS));
        automatic.finish(engine, job(AUTOMATIC_PREPARE_RESULT));
        acceptAutomatic(automatic);

        StaffReviewWorkers staff = new StaffReviewWorkers(engine, reviewService);
        staff.claim(engine, job(STAFF_CLAIM));
        staff.register(engine, job(STAFF_REGISTER));
        staff.startReview(engine, job(STAFF_START_REVIEW));
        awaitTask(StaffProcessingBpmnConstants.UserTask.REVIEW_TASK);
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_case_review_signal WHERE reason = 'ANALYSIS_FAILED'", Integer.class));
        assertEquals(0, database.queryForObject("SELECT count(*) FROM pf_staff_message", Integer.class));
    }

    @ParameterizedTest
    @EnumSource(ClosureScenario.class)
    void allClosureTasksRunWithSyntheticApprovalAndRealBoundaries(ClosureScenario scenario) throws Exception {
        database.update("UPDATE pf_case SET description = ? WHERE id = ?",
                "Die Heizungsstörung ist vollständig behoben. Keine weitere Hilfe nötig.", caseId);
        ClosureAssessment assessment = AutomaticClosureFixtures.assessment();
        AutomaticClosureFixtures.Release release = new AutomaticClosureFixtures.Release();
        List<String> calls = new ArrayList<>();
        AutomaticClosureService closure = closureService(assessment, release, closureCommunication(scenario, calls));
        ConversationEvaluationService central = central(false, List.of(), assessment, release);
        AutomaticQuestionWorkers worker = closureWorker(closure);
        AutomaticEvaluationWorkers evaluation = new AutomaticEvaluationWorkers(engine, central);
        finishInitialEvaluation(evaluation);
        worker.validate(engine, job(AUTOMATIC_VALIDATE_QUESTIONS));
        if (scenario == ClosureScenario.STALE_BEFORE_WRITE) {
            advanceCaseBasis();
            worker.writeClosureMessage(engine, job(AUTOMATIC_WRITE_CLOSURE_MESSAGE));
            assertTrue(calls.isEmpty());
            assertClosureDeliveries(0);
            refreshClosureProposal(worker, evaluation);
        }

        ActivatedJob write = job(AUTOMATIC_WRITE_CLOSURE_MESSAGE);
        worker.writeClosureMessage(engine, write);
        assertEquals(List.of("GENERATE"), calls);
        assertClosureDeliveries(0);
        if (scenario == ClosureScenario.WRITE_TECHNICAL_ERROR) {
            assertClosureMessageIncident(worker, write, true);
            assertEquals(List.of("GENERATE", "GENERATE", "GENERATE"), calls);
            return;
        }
        if (scenario == ClosureScenario.INVALID_DRAFT) {
            assertClosureMessageReview(worker);
            return;
        }

        if (scenario == ClosureScenario.STALE_BEFORE_CHECK) {
            advanceCaseBasis();
            worker.checkClosureMessage(engine, job(AUTOMATIC_CHECK_CLOSURE_MESSAGE));
            assertEquals(List.of("GENERATE"), calls);
            assertClosureDeliveries(0);
            refreshClosureProposal(worker, evaluation);
            worker.writeClosureMessage(engine, job(AUTOMATIC_WRITE_CLOSURE_MESSAGE));
        }
        if (scenario == ClosureScenario.LOST_DRAFT) {
            worker = closureWorker(closureService(assessment, release, closureCommunication(scenario, calls)));
            worker.checkClosureMessage(engine, job(AUTOMATIC_CHECK_CLOSURE_MESSAGE));
            assertEquals(List.of("GENERATE"), calls);
            assertClosureDeliveries(0);
            worker.writeClosureMessage(engine, job(AUTOMATIC_WRITE_CLOSURE_MESSAGE));
        }
        if (scenario == ClosureScenario.DRAFT_RECOVERY_EXHAUSTED) {
            for (int recovery = 0; recovery < 3; recovery++) {
                worker = closureWorker(closureService(assessment, release, closureCommunication(scenario, calls)));
                worker.checkClosureMessage(engine, job(AUTOMATIC_CHECK_CLOSURE_MESSAGE));
                worker.writeClosureMessage(engine, job(AUTOMATIC_WRITE_CLOSURE_MESSAGE));
            }
            worker = closureWorker(closureService(assessment, release, closureCommunication(scenario, calls)));
            ActivatedJob check = job(AUTOMATIC_CHECK_CLOSURE_MESSAGE);
            worker.checkClosureMessage(engine, check);
            await(() -> engine.newIncidentSearchRequest().filter(filter -> filter.processInstanceKey(check.getProcessInstanceKey()))
                    .send().join().items().stream().anyMatch(incident -> check.getElementId().equals(incident.getElementId())));
            assertEquals(List.of("GENERATE", "GENERATE", "GENERATE", "GENERATE"), calls);
            assertClosureDeliveries(0);
            return;
        }
        ActivatedJob check = job(AUTOMATIC_CHECK_CLOSURE_MESSAGE);
        worker.checkClosureMessage(engine, check);
        List<String> expectedCalls = switch (scenario) {
            case STALE_BEFORE_CHECK, LOST_DRAFT -> List.of("GENERATE", "GENERATE", "VALIDATE");
            default -> List.of("GENERATE", "VALIDATE");
        };
        assertEquals(expectedCalls, calls);
        if (scenario == ClosureScenario.CHECK_TECHNICAL_ERROR) {
            assertClosureMessageIncident(worker, check, false);
            assertEquals(List.of("GENERATE", "VALIDATE", "VALIDATE", "VALIDATE"), calls);
            return;
        }
        if (scenario == ClosureScenario.INVALID_CHECK) {
            assertClosureMessageReview(worker);
            return;
        }

        assertClosureDeliveries(1);
        if (scenario == ClosureScenario.STALE_AFTER_PUBLICATION) {
            advanceCaseBasis();
            worker.close(engine, job(AUTOMATIC_CLOSE));
            refreshClosureProposal(worker, evaluation);
            publishClosureMessage(worker);
            assertEquals(List.of("GENERATE", "VALIDATE"), calls);
        }
        finishAutomaticClosure(worker);
        assertClosureDeliveries(1);
    }

    private enum ClosureScenario {
        CLOSED,
        INVALID_DRAFT,
        INVALID_CHECK,
        STALE_AFTER_PUBLICATION,
        STALE_BEFORE_WRITE,
        STALE_BEFORE_CHECK,
        LOST_DRAFT,
        DRAFT_RECOVERY_EXHAUSTED,
        WRITE_TECHNICAL_ERROR,
        CHECK_TECHNICAL_ERROR
    }

    private ClosureMessage closureCommunication(ClosureScenario scenario, List<String> calls) {
        JsonMapper json = JsonMapper.builder().build();
        String text = AutomaticClosureFixtures.message().text();
        return new ClosureMessageService(request -> {
            JsonNode input = json.readTree(request);
            String operation = input.path("operation").asString();
            calls.add(operation);
            return switch (operation) {
                case "GENERATE" -> {
                    if (scenario == ClosureScenario.WRITE_TECHNICAL_ERROR) {
                        throw new IllegalStateException("MODEL_UNAVAILABLE");
                    }
                    String draft = scenario == ClosureScenario.INVALID_DRAFT ? "Ihre Störung wurde vollständig behoben?" : text;
                    yield json.writeValueAsString(Map.of("text", draft, "ground", "RESOLVED"));
                }
                case "VALIDATE" -> {
                    assertEquals(text, input.path("text").asString());
                    assertEquals(ClosureMessageService.digest(text), input.path("digest").asString());
                    if (scenario == ClosureScenario.CHECK_TECHNICAL_ERROR) {
                        throw new IllegalStateException("MODEL_UNAVAILABLE");
                    }
                    yield json.writeValueAsString(Map.of("approved", scenario != ClosureScenario.INVALID_CHECK,
                            "explanation", "Synthetische Inhaltsprüfung", "digest", input.path("digest").asString()));
                }
                default -> throw new AssertionError(operation);
            };
        }, "synthetic-fixture");
    }

    private void advanceCaseBasis() {
        database.update("UPDATE pf_case SET version = version + 1, analysis_version = analysis_version + 1 WHERE id = ?", caseId);
    }

    private void refreshClosureProposal(AutomaticQuestionWorkers worker, AutomaticEvaluationWorkers evaluation) throws Exception {
        runAutomaticEvaluationStages(evaluation);
        worker.validate(engine, job(AUTOMATIC_VALIDATE_QUESTIONS));
    }

    private void assertClosureMessageIncident(AutomaticQuestionWorkers worker, ActivatedJob failed, boolean write) throws Exception {
        for (int retries = 2; retries > 0; retries--) {
            ActivatedJob retry = job(failed.getType());
            assertEquals(retries, retry.getRetries());
            if (write) {
                worker.writeClosureMessage(engine, retry);
            } else {
                worker.checkClosureMessage(engine, retry);
            }
        }
        await(() -> engine.newIncidentSearchRequest().filter(filter -> filter.processInstanceKey(failed.getProcessInstanceKey()))
                .send().join().items().stream().anyMatch(incident -> failed.getElementId().equals(incident.getElementId())));
        assertClosureDeliveries(0);
        assertEquals(0, database.queryForObject("SELECT count(*) FROM pf_case_review_signal", Integer.class));
        assertEquals("UNDER_CLARIFICATION", status());
    }

    private void assertClosureMessageReview(AutomaticQuestionWorkers worker) throws Exception {
        worker.finish(engine, job(AUTOMATIC_PREPARE_RESULT));
        acceptAutomatic(worker);
        StaffReviewWorkers staff = new StaffReviewWorkers(engine, reviewService);
        staff.claim(engine, job(STAFF_CLAIM));
        staff.register(engine, job(STAFF_REGISTER));
        staff.startReview(engine, job(STAFF_START_REVIEW));
        awaitTask(StaffProcessingBpmnConstants.UserTask.REVIEW_TASK);
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_case_review_signal WHERE reason = 'INVALID_CLOSURE_MESSAGE'",
                Integer.class));
        assertClosureDeliveries(0);
        assertEquals(0, database.queryForObject("SELECT count(*) FROM pf_case_closure", Integer.class));
    }

    private void assertClosureDeliveries(int expected) {
        assertEquals(expected, database.queryForObject("SELECT count(*) FROM pf_staff_message WHERE closure_publication_id IS NOT NULL",
                Integer.class));
        assertEquals(expected, database.queryForObject("SELECT count(*) FROM pf_staff_message_mail_order", Integer.class));
    }

    private void publishClosureMessage(AutomaticQuestionWorkers worker) throws Exception {
        worker.writeClosureMessage(engine, job(AUTOMATIC_WRITE_CLOSURE_MESSAGE));
        worker.checkClosureMessage(engine, job(AUTOMATIC_CHECK_CLOSURE_MESSAGE));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void replyCloseStartsANewAutomaticEntryAndCancelsOpenQuestionsOnlyAtCommit(boolean answered) throws Exception {
        AutomaticQuestionWorkers initial = new AutomaticQuestionWorkers(engine, questions,
                org.mockito.Mockito.mock(AutomaticClosureService.class));
        AutomaticEvaluationWorkers first = new AutomaticEvaluationWorkers(engine, central(false, List.of("RQ-001")));
        finishInitialEvaluation(first);
        initial.validate(engine, job(AUTOMATIC_VALIDATE_QUESTIONS));
        initial.publish(engine, job(AUTOMATIC_PUBLISH_QUESTIONS));
        initial.finish(engine, job(AUTOMATIC_PREPARE_RESULT));
        acceptAutomatic(initial);
        ClosureAssessment assessment = AutomaticClosureFixtures.assessment();
        AutomaticClosureFixtures.Release release = new AutomaticClosureFixtures.Release();
        AutomaticClosureService closure = closureService(assessment, release, closureCommunication(ClosureScenario.CLOSED, new ArrayList<>()));
        ConversationEvaluationService central = central(answered, List.of(), assessment, release);
        TenantReplyService service = new TenantReplyService(workflow, replyStore, evaluations, central,
                new AutomaticQuestionStore(database), transactions, closure);
        TenantReplyWorkers reply = new TenantReplyWorkers(engine, service, replyStore);
        reply.register(engine, job(REPLY_REGISTER));
        receive("Die Heizungsstörung ist vollständig behoben. Keine weitere Hilfe nötig.", central);
        UUID tenantEvaluation = database.queryForObject("SELECT evaluation_id FROM pf_tenant_message WHERE case_id = ?", UUID.class, caseId);
        assertNotNull(tenantEvaluation);
        database.update("UPDATE pf_reply_call SET next_attempt_at = CURRENT_TIMESTAMP");
        new TenantReplyDispatcher(replyStore, service, engine).dispatch();
        applyCachedReplyEvaluation(reply, central);
        ActivatedJob replyValidation = job(REPLY_VALIDATE);
        assertEquals(tenantEvaluation.toString(), replyValidation.getVariablesAsMap().get(EVALUATION_REF));
        reply.validate(engine, replyValidation);
        ActivatedJob prepare = job(AUTOMATIC_PREPARE_CLOSE_ROUND);
        UUID decision = UUID.fromString((String) prepare.getVariablesAsMap().get(REPLY_DECISION_REF));
        TenantReplyStore.Call source = replyStore.automaticSource(decision, caseId, leader, "CLOSE");
        await(() -> "COMPLETED".equals(engine.newProcessInstanceGetRequest(source.child()).send().join().getState().name())
                && "COMPLETED".equals(engine.newElementInstanceGetRequest(source.parent()).send().join().getState().name()));
        new AutomaticQuestionRoundWorkers(engine, new AutomaticQuestionRoundService(workflow, replyStore)).prepareClose(engine, prepare);
        new InitialCaseWorkers(engine, initialService).registerAutomatic(engine, job(REGISTER_AUTOMATIC));
        assertEquals("WAITING_FOR_TENANT", status());
        assertEquals("CLOSE", workflow.entryKind(workflow.entryForCase(caseId).orElseThrow().id()));
        AutomaticEvaluationWorkers current = new AutomaticEvaluationWorkers(engine, central);
        ActivatedJob analysis = sharedAnalysisJob(AutomaticClarificationBpmnConstants.CallActivity.EVALUATE_CONVERSATION);
        current.analyze(engine, analysis);
        AutomaticQuestionWorkers worker = closureWorker(closure);
        ActivatedJob validation = job(AUTOMATIC_VALIDATE_QUESTIONS);
        assertEquals(tenantEvaluation.toString(), validation.getVariablesAsMap().get(EVALUATION_REF));
        worker.validate(engine, validation);
        publishClosureMessage(worker);
        assertEquals(answered ? "ANSWERED" : "OPEN", database.queryForObject("SELECT state FROM pf_staff_answer_requirement", String.class));
        finishAutomaticClosure(worker);
        assertEquals(answered ? "ANSWERED" : "CANCELLED", database.queryForObject("SELECT state FROM pf_staff_answer_requirement", String.class));
        assertEquals(answered ? "ANSWERED" : "CASE_CLOSED", database.queryForObject("SELECT resolution_type FROM pf_staff_answer_requirement", String.class));
    }

    private AutomaticClosureService closureService(ClosureAssessment assessment, AutomaticClosureRelease release, ClosureMessage message) {
        return new AutomaticClosureService(workflow, evaluations, new AutomaticQuestionStore(database), new AutomaticClosureStore(database),
                new CaseClosureStore(database), reviewStore, assessment, message, release, transactions);
    }

    private AutomaticQuestionWorkers closureWorker(AutomaticClosureService closure) {
        // Die direkt konstruierten Testdienste erhalten dieselbe Transaktionsgrenze wie der Spring-Proxy.
        org.springframework.aop.framework.ProxyFactory proxy = new org.springframework.aop.framework.ProxyFactory(
                new AutomaticQuestionService(workflow, evaluations, new AutomaticQuestionStore(database), reviewStore, closure));
        proxy.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(transactions,
                new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        return new AutomaticQuestionWorkers(engine, (AutomaticQuestionService) proxy.getProxy(), closure);
    }

    private void finishAutomaticClosure(AutomaticQuestionWorkers worker) throws Exception {
        int mailOrders = database.queryForObject("SELECT count(*) FROM pf_case_mail_order", Integer.class);
        worker.close(engine, job(AUTOMATIC_CLOSE));
        worker.closed(engine, job(AUTOMATIC_CONFIRM_CLOSURE));
        acceptAutomatic(worker);
        await(() -> "COMPLETED".equals(engine.newProcessInstanceGetRequest(leader).send().join().getState().name()));
        assertEquals("CLOSED", status());
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_case_closure WHERE source = 'SYSTEM'", Integer.class));
        assertEquals(mailOrders, database.queryForObject("SELECT count(*) FROM pf_case_mail_order", Integer.class));
    }

    private void acceptAutomatic(AutomaticQuestionWorkers worker) throws Exception {
        ActivatedJob result = job(AUTOMATIC_ACCEPT_RESULT);
        CaseWorkflowStore.Entry entry = workflow.entryForCase(caseId).orElseThrow();
        await(() -> "COMPLETED".equals(engine.newProcessInstanceGetRequest(entry.childKey()).send().join().getState().name())
                && "COMPLETED".equals(engine.newElementInstanceGetRequest(entry.parentElementKey()).send().join().getState().name()));
        worker.accept(engine, result);
    }

    private void closeClarification() throws Exception {
        job(AUTOMATIC_ANALYZE);
        ClosureApproval approval = data.clarificationClosure(actor, caseId);
        assertTrue(approval.available());
        data.closeClarification(actor, caseId, "canonical-close", approval.version(), approval.reviewToken());
        new ClarificationClosureWorkers(engine, data).close(engine, job(CLARIFICATION_CLOSE));
        await(() -> "COMPLETED".equals(engine.newProcessInstanceGetRequest(leader).send().join().getState().name()));
        assertEquals("CLOSED", status());
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_case_closure", Integer.class));
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_case_mail_order", Integer.class));
    }

    @Test
    void externalWaitCanContinueIntoFollowUpThenDeleteAndCloseThroughNativeTasks() throws Exception {
        takeOver();
        StaffDecisionService decisions = decisions();
        StaffTask task = decisions.current(actor, caseId);
        decisions.waitExternal(actor, caseId, UUID.randomUUID(), Long.parseLong(task.taskKey()), task.version(), task.reviewToken());
        new CaseWaitWorkers(engine, waitService).external(engine, job(STAFF_WAIT_EXTERNAL));
        awaitTask(StaffProcessingBpmnConstants.UserTask.EXTERNAL_WAIT_TASK);
        assertEquals("WAITING_FOR_EXTERNAL", status());
        task = decisions.current(actor, caseId);
        decisions.followUp(actor, caseId, UUID.randomUUID(), Long.parseLong(task.taskKey()), task.version(), task.reviewToken(), "", 3, null);
        new CaseWaitWorkers(engine, waitService).save(engine, job(FOLLOW_UP_SAVE));
        assertEquals("WAITING_FOR_FOLLOW_UP", status());
        FollowUpCommandService followUp = new FollowUpCommandService(access, workflow, caseData, waitStore, decisionStore,
                followUpStore, new CamundaFollowUpGateway(clients(), true), transactions);
        await(() -> followUp.current(actor, caseId).available());
        FollowUpCommands.Current current = followUp.current(actor, caseId);
        followUp.submit(actor, caseId, UUID.randomUUID(), current.waitId(), current.version(), true, null, null, "Weiter bearbeiten");
        new FollowUpCommandWorkers(engine, followUp).apply(engine, job(FOLLOW_UP_CHANGE));
        new StaffDecisionWorkers(engine, decisions).progress(engine, job(STAFF_PROGRESS));
        awaitTask(StaffProcessingBpmnConstants.UserTask.WORK_TASK);
        assertEquals("IN_PROGRESS", status());
        task = decisions.current(actor, caseId);
        decisions.closeCase(actor, caseId, UUID.randomUUID(), Long.parseLong(task.taskKey()), task.version(), task.reviewToken());
        ActivatedJob closing = job(STAFF_CLOSE);
        CaseClosureService.ClosureContext context = closures.context(caseId, leader,
                UUID.fromString(closing.getVariablesAsMap().get(STAFF_DECISION_REF).toString()));
        await(() -> "COMPLETED".equals(engine.newProcessInstanceGetRequest(context.child()).send().join().getState().name())
                && "COMPLETED".equals(engine.newElementInstanceGetRequest(context.parentElement()).send().join().getState().name()));
        new CaseClosureWorkers(engine, closures).close(engine, closing);
        await(() -> "COMPLETED".equals(engine.newProcessInstanceGetRequest(leader).send().join().getState().name()));
        assertEquals("CLOSED", status());
    }

    @Test
    void directFollowUpReceivesTenantContactInTheFullCentralMessageProcess() throws Exception {
        ClosureApproval approval = data.directFollowUp(actor, caseId);
        data.setDirectFollowUp(actor, caseId, "canonical-follow-up", approval.version(), approval.reviewToken(), 4, null, "Abklärung");
        DirectFollowUpWorkers direct = new DirectFollowUpWorkers(engine, data, caseData, new DirectFollowUpStore(database, waitStore,
                new StaffTakeoverStore(database)), replyStore, reviewService);
        direct.apply(engine, job(DIRECT_FOLLOW_UP_APPLY));
        direct.signal(engine, job(DIRECT_FOLLOW_UP_SIGNAL));
        StaffReviewWorkers staff = new StaffReviewWorkers(engine, reviewService);
        staff.prepareStaff(engine, job(REVIEW_PREPARE_STAFF));
        staff.claim(engine, job(STAFF_CLAIM));
        staff.register(engine, job(STAFF_REGISTER));
        new CaseWaitWorkers(engine, waitService).save(engine, job(FOLLOW_UP_SAVE));
        assertEquals("WAITING_FOR_FOLLOW_UP", status());
        receive("Die Heizung ist weiterhin kalt.", false);
        awaitTask(StaffProcessingBpmnConstants.UserTask.REVIEW_TASK);
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_wait_resolution", Integer.class));
    }

    @Test
    void mandatoryReviewInterruptsTheCurrentFollowUpAndOpensOneReviewInTheFullModel() throws Exception {
        takeOver();
        StaffDecisionService decisions = decisions();
        StaffTask task = decisions.current(actor, caseId);
        decisions.followUp(actor, caseId, UUID.randomUUID(), Long.parseLong(task.taskKey()), task.version(), task.reviewToken(), "", 3, null);
        CaseWaitWorkers waits = new CaseWaitWorkers(engine, waitService);
        ActivatedJob save = job(FOLLOW_UP_SAVE);
        waits.save(engine, save);
        CaseWaitStore.Wait waiting = waitStore.active(caseId).orElseThrow();
        assertEquals("WAITING_FOR_FOLLOW_UP", status());

        data.submitUrgency(actor, caseId, "canonical-mandatory-review", version(), Urgency.CRITICAL, "Neuer zwingender Prüfbedarf");
        new CaseDataWorkers(engine, data).apply(engine, job(CASE_DATA_APPLY));
        new ReviewSignalDispatcher(reviewStore, engine).dispatch();
        StaffReviewWorkers staff = new StaffReviewWorkers(engine, reviewService);
        staff.check(engine, job(REVIEW_CHECK));
        staff.interruptAutomatic(engine, job(REVIEW_INTERRUPT_AUTOMATIC));
        ActivatedJob stop = job(FOLLOW_UP_STOP_CONTACT);
        assertEquals(StaffProcessingBpmnConstants.ServiceTask.STOP_FOLLOW_UP, stop.getElementId());
        assertEquals(waiting.id().toString(), stop.getVariablesAsMap().get(PENDING_REVIEW_FOLLOW_UP_VERSION));
        UUID signal = UUID.fromString((String) stop.getVariablesAsMap().get(PHASE_REVIEW_SIGNAL_REF));
        waits.stopContact(engine, stop);
        staff.startReview(engine, job(STAFF_START_REVIEW));

        awaitTask(StaffProcessingBpmnConstants.UserTask.REVIEW_TASK);
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals("REVIEW", waitStore.get(waiting.id()).orElseThrow().state());
        assertEquals("CONSUMED", reviewStore.signal(signal, caseId).state());
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_wait_resolution", Integer.class));
        await(() -> "TERMINATED".equals(engine.newProcessInstanceGetRequest(save.getProcessInstanceKey()).send().join().getState().name()));
    }

    @Test
    void catalogQuestionsAndACompleteTenantAnswerReachOneReviewWithFullModels() throws Exception {
        ConversationEvaluationService central = central(false, List.of("RQ-001"));
        TenantReplyService service = replyService(central);
        TenantReplyWorkers reply = new TenantReplyWorkers(engine, service, replyStore);
        enterCatalogReplyWait(central, reply);
        receive("Die Störung besteht seit gestern.", true);
        database.update("UPDATE pf_reply_call SET next_attempt_at = CURRENT_TIMESTAMP");
        new TenantReplyDispatcher(replyStore, service, engine).dispatch();
        reply.prepareReview(engine, job(REPLY_PREPARE_REVIEW));
        StaffReviewWorkers staff = new StaffReviewWorkers(engine, reviewService);
        staff.claim(engine, job(STAFF_CLAIM));
        staff.register(engine, job(STAFF_REGISTER));
        staff.startReview(engine, job(STAFF_START_REVIEW));
        awaitTask(StaffProcessingBpmnConstants.UserTask.REVIEW_TASK);
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals("ANSWERED", database.queryForObject("SELECT state FROM pf_staff_answer_requirement", String.class));
        assertEquals(1, tasks().size());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void newerTenantMessageReturnsToTheSharedEvaluationAndKeepsWaiting(boolean beforeApply) throws Exception {
        List<String> modelCalls = new ArrayList<>();
        ConversationEvaluationService central = new ConversationEvaluationService(workflow, tenantMessages, evaluations, urgencies,
                input -> {
                    modelCalls.add("analysis");
                    return new ConversationAnalysis.Result("NORMAL", false, List.of(), null, "test", "canonical-v1");
                }, input -> {
                    modelCalls.add("advice");
                    return new Recommendation.Result("Verwaltung", "Prüfen", List.of(), List.of(), false, null, "test", "canonical-v1");
                }, messages -> {
                    modelCalls.add("closure");
                    return noClosure().assess(messages);
                }, transactions, new AutomaticClosureRelease(), replyStore);
        TenantReplyService service = replyService(central);
        TenantReplyWorkers reply = new TenantReplyWorkers(engine, service, replyStore);
        AutomaticEvaluationWorkers evaluation = new AutomaticEvaluationWorkers(engine, central);
        enterCatalogReplyWait(central(false, List.of("RQ-001")), reply);
        TenantReplyStore.Call call = replyStore.active(caseId, leader).orElseThrow();

        receive("Den Zeitpunkt der Störung muss ich noch nachsehen.", false);
        database.update("UPDATE pf_reply_call SET next_attempt_at = CURRENT_TIMESTAMP WHERE id = ?", call.id());
        new TenantReplyDispatcher(replyStore, service, engine).dispatch();
        if (beforeApply) {
            tenantMessageService().send(accepted.personalAccessToken(), UUID.randomUUID(), "Ich suche noch nach der Information.");
        }
        ActivatedJob first = sharedAnalysisJob(TenantReplyBpmnConstants.CallActivity.EVALUATE_CONVERSATION);
        List<String> stages = List.of("analysis", "advice", "closure");
        if (beforeApply) {
            runEvaluationStages(evaluation, first, true);
            assertEquals(stages, modelCalls);
        } else {
            evaluation.analyze(engine, first);
            assertTrue(modelCalls.isEmpty());
        }
        ActivatedJob firstApply = job(REPLY_APPLY_EVALUATION);
        assertEquals(TenantReplyBpmnConstants.ServiceTask.APPLY_EVALUATION, firstApply.getElementId());
        assertEquals(call.child(), firstApply.getProcessInstanceKey());
        ActivatedJob stale = firstApply;
        if (!beforeApply) {
            reply.applyEvaluation(engine, firstApply);
            stale = job(REPLY_VALIDATE);
        }

        tenantMessageService().send(accepted.personalAccessToken(), UUID.randomUUID(), "Ich habe die Angaben noch nicht gefunden.");
        if (beforeApply) {
            reply.applyEvaluation(engine, stale);
        } else {
            reply.validate(engine, stale);
        }
        assertTrue(replyStore.decision(call.id()).isEmpty());
        ActivatedJob refreshed = sharedAnalysisJob(TenantReplyBpmnConstants.CallActivity.EVALUATE_CONVERSATION);
        assertEquals(first.getElementId(), refreshed.getElementId());
        assertNotEquals(first.getProcessInstanceKey(), refreshed.getProcessInstanceKey());
        modelCalls.clear();
        runEvaluationStages(evaluation, refreshed, true);
        assertEquals(stages, modelCalls);
        ActivatedJob refreshedApply = job(REPLY_APPLY_EVALUATION);
        assertEquals(call.child(), refreshedApply.getProcessInstanceKey());
        assertNotEquals(firstApply.getVariablesAsMap().get(EVALUATION_REF), refreshedApply.getVariablesAsMap().get(EVALUATION_REF));
        reply.applyEvaluation(engine, refreshedApply);
        reply.validate(engine, job(REPLY_VALIDATE));

        reply.register(engine, job(REPLY_REGISTER));
        assertEquals("WAIT", replyStore.decision(call.id()).orElseThrow().outcome());
        assertEquals("WAITING_FOR_TENANT", status());
        assertEquals("OPEN", database.queryForObject("SELECT state FROM pf_staff_answer_requirement", String.class));
    }

    @ParameterizedTest
    @EnumSource(ReplyReviewTrigger.class)
    void failedReplyAnalysisOrPendingReviewKeepsTheExistingStaffReviewOutcome(ReplyReviewTrigger trigger) throws Exception {
        List<String> modelCalls = new ArrayList<>();
        ConversationEvaluationService central = new ConversationEvaluationService(workflow, tenantMessages, evaluations, urgencies,
                input -> {
                    modelCalls.add("analysis");
                    return new ConversationAnalysis.Result("NORMAL", false, List.of(), "ANALYSIS_UNAVAILABLE", "test", "canonical-v1");
                }, input -> {
                    throw new AssertionError("Advice after failed reply analysis or pending review");
                }, noClosure(), transactions, new AutomaticClosureRelease(), replyStore);
        TenantReplyService service = replyService(central);
        TenantReplyWorkers reply = new TenantReplyWorkers(engine, service, replyStore);
        enterCatalogReplyWait(central(false, List.of("RQ-001")), reply);
        TenantReplyStore.Call call = replyStore.active(caseId, leader).orElseThrow();

        receive("Die Ursache kenne ich noch nicht.", false);
        tenantMessageService().send(accepted.personalAccessToken(), UUID.randomUUID(), "Mit der Anlage stimmt weiterhin etwas nicht.");
        database.update("UPDATE pf_reply_call SET next_attempt_at = CURRENT_TIMESTAMP WHERE id = ?", call.id());
        new TenantReplyDispatcher(replyStore, service, engine).dispatch();
        Integer urgencyEvents = database.queryForObject("SELECT count(*) FROM pf_urgency_event", Integer.class);
        if (trigger == ReplyReviewTrigger.BEFORE_ANALYSIS) {
            UUID earlier = database.queryForObject("SELECT evaluation_id FROM pf_tenant_message WHERE case_id = ? AND evaluation_id IS NOT NULL",
                    UUID.class, caseId);
            evaluations.signal(evaluations.get(earlier), "ANALYSIS_FAILED");
        }

        AutomaticEvaluationWorkers evaluation = new AutomaticEvaluationWorkers(engine, central);
        evaluation.analyze(engine, sharedAnalysisJob(TenantReplyBpmnConstants.CallActivity.EVALUATE_CONVERSATION));
        ActivatedJob apply = job(REPLY_APPLY_EVALUATION);
        if (trigger == ReplyReviewTrigger.BEFORE_BLOCKED_APPLY) {
            UUID reference = UUID.fromString((String) apply.getVariablesAsMap().get(EVALUATION_REF));
            assertEquals("READY", evaluations.get(reference).state());
            CaseState state = workflow.lockCase(caseId).orElseThrow();
            UUID automaticCall = workflow.entryForCase(caseId).orElseThrow().callRef();
            UUID continuation = reviewStore.prepare(state, leader, automaticCall).id();
            decisionStore.prepare(caseId, continuation, actor.accountId(), UUID.randomUUID(), 100L, state.version(),
                    decisionStore.reviewFingerprint(caseId, leader), StaffDecisionAction.CONTINUE,
                    StaffProcessingBpmnConstants.UserTask.REVIEW_TASK, null);
            assertEquals("BUSY", service.applyEvaluation(caseId, call.entry(), leader, call.child(), call.tenant(), reference).state());
            evaluations.signal(evaluations.get(reference), "ANALYSIS_FAILED");
        }
        reply.applyEvaluation(engine, apply);
        ActivatedJob validate = job(REPLY_VALIDATE);
        if (trigger != ReplyReviewTrigger.ANALYSIS_FAILURE) {
            assertEquals("", validate.getVariablesAsMap().get(EVALUATION_REF));
        }
        assertEquals(trigger == ReplyReviewTrigger.BEFORE_ANALYSIS ? List.of() : List.of("analysis"), modelCalls);
        reply.validate(engine, validate);

        assertEquals("REVIEW", replyStore.decision(call.id()).orElseThrow().outcome());
        assertEquals(urgencyEvents, database.queryForObject("SELECT count(*) FROM pf_urgency_event", Integer.class));
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_case_review_signal WHERE reason = 'ANALYSIS_FAILED'", Integer.class));
        assertEquals("OPEN", database.queryForObject("SELECT state FROM pf_staff_answer_requirement", String.class));
    }

    private enum ReplyReviewTrigger {
        ANALYSIS_FAILURE,
        BEFORE_ANALYSIS,
        BEFORE_BLOCKED_APPLY
    }

    @ParameterizedTest
    @EnumSource(ReplyExit.class)
    void oneExitCancelsTheCurrentReplyChildAndKeepsTheExistingContinuation(ReplyExit exit) throws Exception {
        ConversationEvaluationService central = central(false, List.of("RQ-001"));
        enterCatalogReplyWait(central, new TenantReplyWorkers(engine, replyService(central), replyStore));
        TenantReplyStore.Call call = replyStore.active(caseId, leader).orElseThrow();
        String confirmation = UUID.randomUUID().toString();
        engine.newSetVariablesCommand(leader).variables(exit.markers(call, confirmation)).send().join();

        ActivatedJob next = job(exit.jobType);
        assertEquals(exit.elementId, next.getElementId());
        assertEquals(exit.name(), next.getVariablesAsMap().get(REPLY_EXIT_REASON));
        await(() -> "TERMINATED".equals(engine.newProcessInstanceGetRequest(call.child()).send().join().getState().name()));
        if (exit == ReplyExit.CLOSE) {
            assertEquals("CLOSE", next.getVariablesAsMap().get(STAFF_OUTCOME));
            assertEquals(confirmation, next.getVariablesAsMap().get(STAFF_DECISION_REF));
            return;
        }
        if (exit == ReplyExit.FOLLOW_UP) {
            assertEquals(confirmation, next.getVariablesAsMap().get(DIRECT_FOLLOW_CONTINUATION_REF));
        }
        String continuation = UUID.randomUUID().toString();
        engine.newCompleteCommand(next.getKey()).variables(exit.continuation(continuation)).send().join();
        if (exit == ReplyExit.MANUAL_QUESTION) {
            ActivatedJob waitingAgain = job(REPLY_REGISTER);
            assertTrue(waitingAgain.getProcessInstanceKey() != call.child());
            assertEquals(continuation, waitingAgain.getVariablesAsMap().get(ENTRY_CONTEXT_REF));
        } else {
            ActivatedJob staff = job(STAFF_CLAIM);
            assertEquals(CaseProcessBpmnConstants.ServiceTask.CLAIM_STAFF_CONTINUATION, staff.getElementId());
            assertEquals(continuation, staff.getVariablesAsMap().get(STAFF_ENTRY_CONTEXT_REF));
        }
    }

    @Test
    void simultaneousReplyMarkersKeepThePreviousTakeoverPriority() throws Exception {
        ConversationEvaluationService central = central(false, List.of("RQ-001"));
        enterCatalogReplyWait(central, new TenantReplyWorkers(engine, replyService(central), replyStore));
        TenantReplyStore.Call call = replyStore.active(caseId, leader).orElseThrow();
        Map<String, Object> markers = new HashMap<>();
        for (ReplyExit exit : ReplyExit.values()) {
            markers.putAll(exit.markers(call, UUID.randomUUID().toString()));
        }
        engine.newSetVariablesCommand(leader).variables(markers).send().join();

        ActivatedJob next = job(ReplyExit.TAKEOVER.jobType);
        assertEquals(ReplyExit.TAKEOVER.elementId, next.getElementId());
        assertEquals("TAKEOVER", next.getVariablesAsMap().get(REPLY_EXIT_REASON));
        assertNull(next.getVariablesAsMap().get(STAFF_DECISION_REF));
        assertNull(next.getVariablesAsMap().get(DIRECT_FOLLOW_CONTINUATION_REF));
        await(() -> "TERMINATED".equals(engine.newProcessInstanceGetRequest(call.child()).send().join().getState().name()));
    }

    private void enterCatalogReplyWait(ConversationEvaluationService central, TenantReplyWorkers reply) throws Exception {
        AutomaticQuestionWorkers automatic = new AutomaticQuestionWorkers(engine, questions, org.mockito.Mockito.mock(AutomaticClosureService.class));
        automatic.accept(engine, prepareCatalogHandover(central, automatic));
        reply.register(engine, job(REPLY_REGISTER));
        assertEquals("WAITING_FOR_TENANT", status());
    }

    @ParameterizedTest
    @CsvSource({
        "manualQuestionCallRef, AUTOMATIC",
        "staffTakeoverCallRef, AUTOMATIC",
        "staffFollowAutomaticCallRef, AUTOMATIC",
        "clearedAutomaticCallRef, CLEAR",
        "clearedAutomaticCallRef+manualQuestionCallRef, AUTOMATIC",
        "clearedAutomaticCallRef+staffTakeoverCallRef, AUTOMATIC"
    })
    void automaticResultInterruptionKeepsItsExistingContinuation(String markerNames, String source) throws Exception {
        ConversationEvaluationService central = central(false, List.of("RQ-001"));
        AutomaticQuestionWorkers automatic = new AutomaticQuestionWorkers(engine, questions, org.mockito.Mockito.mock(AutomaticClosureService.class));
        ActivatedJob result = prepareCatalogHandover(central, automatic);
        String callRef = workflow.entryForCase(caseId).orElseThrow().callRef().toString();
        Map<String, Object> variables = new HashMap<>();
        for (String marker : markerNames.split("\\+")) {
            variables.put(marker, callRef);
        }
        engine.newSetVariablesCommand(leader).variables(variables).send().join();

        boolean clear = "CLEAR".equals(source);
        ActivatedJob next = job(clear ? ANSWER_CONTINUE : REVIEW_PREPARE_STAFF);
        assertEquals(clear ? CaseProcessBpmnConstants.ServiceTask.CONTINUE_WITHOUT_ANSWER
                : CaseProcessBpmnConstants.ServiceTask.PREPARE_CONFIRMED_INTERRUPTION, next.getElementId());
        assertEquals(source, next.getVariablesAsMap().get(REPLY_CONTINUATION_SOURCE));
        await(() -> "TERMINATED".equals(engine.newElementInstanceGetRequest(result.getElementInstanceKey()).send().join().getState().name()));
    }

    private ActivatedJob prepareCatalogHandover(ConversationEvaluationService central, AutomaticQuestionWorkers automatic) throws Exception {
        AutomaticEvaluationWorkers evaluation = new AutomaticEvaluationWorkers(engine, central);
        finishInitialEvaluation(evaluation);
        automatic.validate(engine, job(AUTOMATIC_VALIDATE_QUESTIONS));
        automatic.publish(engine, job(AUTOMATIC_PUBLISH_QUESTIONS));
        automatic.finish(engine, job(AUTOMATIC_PREPARE_RESULT));
        ActivatedJob result = job(AUTOMATIC_ACCEPT_RESULT);
        CaseWorkflowStore.Entry entry = workflow.entryForCase(caseId).orElseThrow();
        await(() -> "COMPLETED".equals(engine.newProcessInstanceGetRequest(entry.childKey()).send().join().getState().name()));
        return result;
    }

    private TenantReplyService replyService(ConversationEvaluationService central) {
        return new TenantReplyService(workflow, replyStore, evaluations, central,
                new AutomaticQuestionStore(database), transactions, org.mockito.Mockito.mock(AutomaticClosureService.class));
    }

    private enum ReplyExit {
        CLEAR_ANSWER(ANSWER_CONTINUE, CaseProcessBpmnConstants.ServiceTask.CONTINUE_WITHOUT_ANSWER),
        CLOSE(STAFF_CLOSE, CaseProcessBpmnConstants.ServiceTask.SAVE_STAFF_CLOSURE),
        FOLLOW_UP(DIRECT_FOLLOW_UP_CONTINUE, CaseProcessBpmnConstants.ServiceTask.CONTINUE_TO_FOLLOW_UP),
        MANUAL_QUESTION(REVIEW_PREPARE_STAFF, CaseProcessBpmnConstants.ServiceTask.PREPARE_CONFIRMED_INTERRUPTION),
        REVIEW(REPLY_PREPARE_REVIEW, CaseProcessBpmnConstants.ServiceTask.PREPARE_STAFF_REVIEW),
        TAKEOVER(REVIEW_PREPARE_STAFF, CaseProcessBpmnConstants.ServiceTask.PREPARE_CONFIRMED_INTERRUPTION);

        private final String jobType;
        private final String elementId;

        ReplyExit(String jobType, String elementId) {
            this.jobType = jobType;
            this.elementId = elementId;
        }

        private Map<String, String> markers(TenantReplyStore.Call call, String confirmation) {
            String callRef = call.id().toString();
            String entryRef = call.entry().toString();
            return switch (this) {
                case CLEAR_ANSWER -> Map.of(CLEARED_ANSWER_CALL_REF, callRef, ANSWER_CANCELLATION_REF, confirmation);
                case CLOSE -> Map.of(STAFF_CLOSE_REPLY_CALL_REF, callRef, STAFF_CLOSE_REPLY_ENTRY_REF, entryRef,
                        STAFF_REPLY_CLOSURE_DECISION_REF, confirmation);
                case FOLLOW_UP -> Map.of(STAFF_FOLLOW_REPLY_CALL_REF, callRef, STAFF_FOLLOW_REPLY_ENTRY_REF, entryRef,
                        STAFF_FOLLOW_COMMAND_REF, confirmation);
                case MANUAL_QUESTION -> Map.of(MANUAL_QUESTION_PREVIOUS_REPLY_ENTRY_REF, entryRef);
                case REVIEW -> Map.of(REVIEW_NEED_REPLY_CALL_REF, callRef);
                case TAKEOVER -> Map.of(STAFF_TAKEOVER_REPLY_ENTRY_REF, entryRef);
            };
        }

        private Map<String, Object> continuation(String reference) {
            return switch (this) {
                case CLEAR_ANSWER -> Map.of(CLEAR_ANSWER_OUTCOME, "STAFF", STAFF_ENTRY_CONTEXT_REF, reference);
                case FOLLOW_UP, REVIEW -> Map.of(STAFF_ENTRY_CONTEXT_REF, reference);
                case MANUAL_QUESTION -> Map.of(AUTOMATIC_INTERRUPTION_OUTCOME, "WAIT_FOR_REPLY", REPLY_ENTRY_CONTEXT_REF, reference);
                case TAKEOVER -> Map.of(AUTOMATIC_INTERRUPTION_OUTCOME, "STAFF", STAFF_ENTRY_CONTEXT_REF, reference);
                case CLOSE -> throw new IllegalStateException("Abschluss liegt ausserhalb der Antwortfortsetzung");
            };
        }
    }

    @Test
    void closedCanonicalGenerationReopensWithANewLeaderAndTheSamePublicAccess() throws Exception {
        closeClarification();
        long previous = leader;
        CaseReopenService reopen = new CaseReopenService(access, workflow, reopenStore, new CamundaReopenProcessGateway(clients(), true),
                transactions);
        UUID order = reopen.request(actor, caseId, UUID.randomUUID(), version());
        CaseReopenWorkers worker = new CaseReopenWorkers(engine, reopen, reopenStore);
        worker.dispatch();
        ActivatedJob prepare = job(PREPARE_PROCESS_KEY);
        leader = prepare.getProcessInstanceKey();
        assertTrue(previous != leader);
        new InitialCaseWorkers(engine, initialService).prepareKey(engine, prepare);
        ActivatedJob reopening = job(CASE_REOPEN);
        await(() -> !engine.newMessageSubscriptionSearchRequest().filter(filter -> filter.processInstanceKey(leader)
                .elementId(CaseProcessBpmnConstants.StartEvent.ACTION_RECEIVER)
                .messageSubscriptionState(MessageSubscriptionState.CREATED)).send().join().items().isEmpty());
        worker.reopen(engine, reopening);
        StaffReviewWorkers staff = new StaffReviewWorkers(engine, reviewService);
        staff.claim(engine, job(STAFF_CLAIM));
        staff.register(engine, job(STAFF_REGISTER));
        awaitTask(StaffProcessingBpmnConstants.UserTask.WORK_TASK);
        assertEquals("IN_PROGRESS", status());
        assertEquals("APPLIED", reopenStore.get(order).orElseThrow().state());
        assertEquals(leader, workflow.leadingInstance(caseId).orElseThrow());
        receive("Bitte nochmals prüfen.", false);
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_acceptance_mail_order", Integer.class));
        assertEquals(2, database.queryForObject("SELECT count(*) FROM pf_case_process_history", Integer.class));
    }

    @Test
    void nativeStaffQuestionKeepsWaitingAfterPartialReplyAndReviewsACompleteReply() throws Exception {
        takeOver();
        StaffMessageService messages = new StaffMessageService(access, workflow, caseData, reviewStore, staffMessages,
                new CamundaCaseGateway(clients(), true), new CamundaStaffTaskGateway(clients(), true),
                new CamundaStaffMessageGateway(clients(), true), waitStore, decisionStore, replyStore,
                new ClarificationQuestionStore(database), transactions);
        assertEquals("PENDING", messages.send(actor, caseId, "canonical-question", "Wann passt Ihnen ein Termin?", true));
        new StaffMessageWorkers(engine, messages).question(engine, job(STAFF_QUESTION_PUBLISH));
        ConversationEvaluationService central = central(false, List.of());
        TenantReplyService replyService = replyService(central);
        TenantReplyWorkers reply = new TenantReplyWorkers(engine, replyService, replyStore);
        reply.register(engine, job(REPLY_REGISTER));
        engine.newSetVariablesCommand(leader).variables(Map.of(REPLY_EXIT_REASON, "CLOSE")).send().join();
        receive("Ich prüfe noch meinen Kalender.", false);
        database.update("UPDATE pf_reply_call SET next_attempt_at = CURRENT_TIMESTAMP");
        new TenantReplyDispatcher(replyStore, replyService, engine).dispatch();
        applyCachedReplyEvaluation(reply, central);
        reply.validate(engine, job(REPLY_VALIDATE));
        reply.register(engine, job(REPLY_REGISTER));
        assertEquals("WAITING_FOR_TENANT", status());
        assertEquals("OPEN", database.queryForObject("SELECT state FROM pf_staff_answer_requirement", String.class));
        receive("Am Montag um 10 Uhr.", true);
        database.update("UPDATE pf_reply_call SET next_attempt_at = CURRENT_TIMESTAMP");
        new TenantReplyDispatcher(replyStore, replyService, engine).dispatch();
        reply.prepareReview(engine, job(REPLY_PREPARE_REVIEW));
        StaffReviewWorkers staff = new StaffReviewWorkers(engine, reviewService);
        staff.claim(engine, job(STAFF_CLAIM));
        staff.register(engine, job(STAFF_REGISTER));
        staff.startReview(engine, job(STAFF_START_REVIEW));
        awaitTask(StaffProcessingBpmnConstants.UserTask.REVIEW_TASK);
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals("ANSWERED", database.queryForObject("SELECT state FROM pf_staff_answer_requirement", String.class));
    }

    private ObjectProvider<CamundaClient> clients() {
        return new StaticListableBeanFactory(Map.of("engine", engine)).getBeanProvider(CamundaClient.class);
    }

    private StaffDecisionService decisions() {
        return new StaffDecisionService(access, workflow, caseData, reviewStore, decisionStore,
                new CamundaStaffTaskGateway(clients(), true), transactions);
    }

    private void takeOver() throws Exception {
        ActivatedJob analysis = job(AUTOMATIC_ANALYZE);
        assertEquals("PENDING", data.takeOver(actor, caseId, "canonical-takeover", version()).state());
        StaffTakeoverWorkers takeover = new StaffTakeoverWorkers(engine, data, new StaffTakeoverStore(database));
        takeover.apply(engine, job(TAKEOVER_APPLY));
        takeover.signal(engine, job(TAKEOVER_SIGNAL));
        StaffReviewWorkers staff = new StaffReviewWorkers(engine, reviewService);
        staff.prepareStaff(engine, job(REVIEW_PREPARE_STAFF));
        staff.claim(engine, job(STAFF_CLAIM));
        staff.register(engine, job(STAFF_REGISTER));
        awaitTask(StaffProcessingBpmnConstants.UserTask.WORK_TASK);
        assertEquals("TERMINATED", engine.newProcessInstanceGetRequest(analysis.getProcessInstanceKey()).send().join().getState().name());
    }

    private void finishInitialEvaluation(AutomaticEvaluationWorkers evaluation) throws Exception {
        runAutomaticEvaluationStages(evaluation);
        evaluation.recommend(engine, job(AUTOMATIC_RECOMMENDATION));
    }

    private void runAutomaticEvaluationStages(AutomaticEvaluationWorkers evaluation) throws Exception {
        runEvaluationStages(evaluation, sharedAnalysisJob(AutomaticClarificationBpmnConstants.CallActivity.EVALUATE_CONVERSATION), true);
    }

    private ActivatedJob sharedAnalysisJob(String call) throws Exception {
        ActivatedJob analysis = job(AUTOMATIC_ANALYZE);
        assertEquals(ConversationEvaluationBpmnConstants.PROCESS_ID, analysis.getBpmnProcessId());
        assertEquals(ConversationEvaluationBpmnConstants.ServiceTask.ANALYZE_CONVERSATION, analysis.getElementId());
        ProcessInstance evaluation = engine.newProcessInstanceGetRequest(analysis.getProcessInstanceKey()).send().join();
        assertEquals(call, engine.newElementInstanceGetRequest(evaluation.getParentElementInstanceKey()).send().join().getElementId());
        return analysis;
    }

    private void runEvaluationStages(AutomaticEvaluationWorkers evaluation, ActivatedJob analysis, boolean assessClosure) throws Exception {
        evaluation.analyze(engine, analysis);
        ActivatedJob advice = job(AUTOMATIC_ADVISE);
        assertEquals(ConversationEvaluationBpmnConstants.ServiceTask.RECOMMEND_PROCEDURE, advice.getElementId());
        assertEquals(analysis.getProcessInstanceKey(), advice.getProcessInstanceKey());
        evaluation.advise(engine, advice);
        if (assessClosure) {
            ActivatedJob assessment = job(AUTOMATIC_ASSESS_CLOSURE);
            assertEquals(ConversationEvaluationBpmnConstants.ServiceTask.ASSESS_CLOSURE, assessment.getElementId());
            assertEquals(analysis.getProcessInstanceKey(), assessment.getProcessInstanceKey());
            evaluation.assessClosure(engine, assessment);
        }
    }

    private void applyCachedReplyEvaluation(TenantReplyWorkers reply, ConversationEvaluationService central) throws Exception {
        ActivatedJob analysis = sharedAnalysisJob(TenantReplyBpmnConstants.CallActivity.EVALUATE_CONVERSATION);
        new AutomaticEvaluationWorkers(engine, central).analyze(engine, analysis);
        reply.applyEvaluation(engine, job(REPLY_APPLY_EVALUATION));
    }

    private ConversationEvaluationService central(boolean answered, List<String> selected) {
        return central(answered, selected, noClosure());
    }

    private ConversationEvaluationService central(boolean answered, List<String> selected, ClosureAssessment closure) {
        return central(answered, selected, closure, new AutomaticClosureRelease());
    }

    private ConversationEvaluationService central(boolean answered, List<String> selected, ClosureAssessment closure, AutomaticClosureRelease release) {
        return new ConversationEvaluationService(workflow, tenantMessages, evaluations, urgencies, input -> {
            List<ConversationAnalysis.Answer> answers = answered ? input.questions().stream().map(question -> {
                ConversationAnalysis.Message last = input.messages().getLast();
                return new ConversationAnalysis.Answer(question.id(), last.id(), last.text());
            }).toList() : List.of();
            return new ConversationAnalysis.Result("NORMAL", false, answers, null, "test", "canonical-v1");
        }, input -> new Recommendation.Result("Verwaltung", "Prüfen", List.of(), selected, false, null, "test", "canonical-v1"),
                closure, transactions, release, replyStore);
    }

    private void receive(String text, boolean answered) throws Exception {
        receive(text, central(answered, List.of()));
    }

    private void receive(String text, ConversationEvaluationService central) throws Exception {
        ConversationEvaluationWorkers tenant = prepareTenantEvaluation(text, central);
        boolean assessClosure = Set.of("UNDER_CLARIFICATION", "WAITING_FOR_TENANT").contains(status());
        ActivatedJob analysis = sharedAnalysisJob(TenantMessageBpmnConstants.CallActivity.EVALUATE_MESSAGE);
        runEvaluationStages(new AutomaticEvaluationWorkers(engine, central), analysis, assessClosure);
        tenant.recommendation(engine, job(CONVERSATION_RECOMMENDATION));
        tenant.apply(engine, job(CONVERSATION_APPLY));
    }

    private ConversationEvaluationWorkers prepareTenantEvaluation(String text, ConversationEvaluationService central) throws Exception {
        TenantMessageService tenant = tenantMessageService();
        tenant.send(accepted.personalAccessToken(), UUID.randomUUID(), text);
        new TenantMessageWorkers(engine, tenant).identify(engine, job(TENANT_MESSAGE_IDENTIFY));
        if ("WAITING_FOR_FOLLOW_UP".equals(status())) {
            // Der Kontakt beendet den Termin vor der KI-Auswertung; NWake wartet auf diese Übergabe.
            new FollowUpContactDispatcher(waitStore, engine).dispatch();
            new CaseWaitWorkers(engine, waitService).stopContact(engine, job(FOLLOW_UP_STOP_CONTACT));
            new StaffReviewWorkers(engine, reviewService).startReview(engine, job(STAFF_START_REVIEW));
            awaitTask(StaffProcessingBpmnConstants.UserTask.REVIEW_TASK);
        }
        ConversationEvaluationWorkers evaluation = new ConversationEvaluationWorkers(engine, central);
        evaluation.context(engine, job(CONVERSATION_CONTEXT));
        return evaluation;
    }

    private TenantMessageService tenantMessageService() {
        return new TenantMessageService(tenantMessages, workflow, caseData, links,
                new CamundaCaseGateway(clients(), true), new CamundaTenantMessageGateway(clients(), true), transactions,
                accountId -> java.util.Optional.empty());
    }

    private List<UserTask> tasks() {
        return engine.newUserTaskSearchRequest().filter(filter -> filter.state(UserTaskState.CREATED)).send().join().items();
    }

    private void awaitTask(String element) throws Exception {
        await(() -> tasks().size() == 1 && element.equals(tasks().getFirst().getElementId()));
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
        }
        throw new AssertionError("Job fehlt im vollständigen Modell: " + type);
    }

    private void await(BooleanSupplier condition) throws Exception {
        for (int attempt = 0; attempt < 60; attempt++) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Engine-Zustand noch nicht sichtbar");
    }

    private String status() {
        return database.queryForObject("SELECT status FROM pf_case WHERE id = ?", String.class, caseId);
    }

    private long version() {
        return database.queryForObject("SELECT version FROM pf_case WHERE id = ?", Long.class, caseId);
    }

    private record ActionProbe(CaseActionMessage action, String jobType, String element, String referenceField) {
        private UUID reference() {
            return UUID.nameUUIDFromBytes(action.name().getBytes(StandardCharsets.UTF_8));
        }
    }
}
