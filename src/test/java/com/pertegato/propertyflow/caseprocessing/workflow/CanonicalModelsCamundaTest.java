package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.access.contract.StaffAccountAccess;
import com.pertegato.propertyflow.access.contract.SubmissionContext;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.caseprocessing.contract.CaseAccepted;
import com.pertegato.propertyflow.caseprocessing.contract.CaseCreation;
import com.pertegato.propertyflow.caseprocessing.contract.CreateCaseCommand;
import com.pertegato.propertyflow.caseprocessing.contract.FollowUpCommands;
import com.pertegato.propertyflow.caseprocessing.contract.StaffCaseDataCommands.ClosureApproval;
import com.pertegato.propertyflow.caseprocessing.contract.StaffTaskDecisions.StaffTask;
import com.pertegato.propertyflow.caseprocessing.delivery.PersonalLinkCodec;
import com.pertegato.propertyflow.caseprocessing.persistence.AnswerRequirementStore;
import com.pertegato.propertyflow.caseprocessing.persistence.AutomaticClosureStore;
import com.pertegato.propertyflow.caseprocessing.persistence.AutomaticQuestionStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseClosureStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseDataStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseReopenStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWaitStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.StartOrder;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
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
import com.pertegato.propertyflow.recommendation.service.Recommendation;
import com.pertegato.propertyflow.triage.service.ConversationAnalysis;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.search.enums.MessageSubscriptionState;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.response.UserTask;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import static com.pertegato.propertyflow.recommendation.ClosureAssessmentFixtures.noClosure;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    private static final String BPMN = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String ZEEBE = "http://camunda.org/schema/zeebe/1.0";

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
        DeploymentEvent deployment = engine.newDeployResourceCommand()
                .addResourceStringUtf8(model("propertyflow-fallprozess-entwurf"), "case.bpmn")
                .addResourceStringUtf8(model("automatische-abklaerung"), "automatic.bpmn")
                .addResourceStringUtf8(model("mitarbeiterbearbeitung"), "staff.bpmn")
                .addResourceStringUtf8(model("mieterantwort-pruefen"), "reply.bpmn")
                .addResourceStringUtf8(model("mieternachricht-verarbeiten"), "message.bpmn")
                .addResourceStringUtf8(model("wiedervorlage"), "follow-up.bpmn")
                .addResourceStringUtf8(model("fallbenachrichtigung-versenden"), "mail.bpmn")
                .send().join();
        long definition = deployment.getProcesses().stream()
                .filter(process -> InitialCaseWorkers.CASE_PROCESS.equals(process.getBpmnProcessId()))
                .findFirst().orElseThrow().getProcessDefinitionKey();
        accepted = creation.create(new SubmissionContext.Guest(UUID.randomUUID()),
                new CreateCaseCommand(UUID.randomUUID().toString(), "Heizung", "Wohnung", "Kalt", "canonical@example.test"));
        caseId = accepted.caseId();
        StartOrder start = workflow.claim(definition, "<default>", Duration.ofMinutes(2)).orElseThrow();
        assertEquals(ProcessStartGateway.StartResult.STARTED, new CamundaProcessStartGateway(engine).ensureStarted(start));
        InitialCaseWorkers initial = new InitialCaseWorkers(engine, initialService);
        ActivatedJob prepare = job(InitialCaseWorkers.PREPARE_KEY);
        leader = prepare.getProcessInstanceKey();
        initial.prepareKey(engine, prepare);
        initial.register(engine, job(InitialCaseWorkers.REGISTER));
        initial.prepareInitial(engine, job(InitialCaseWorkers.PREPARE_INITIAL));
        initial.registerAutomatic(engine, job(InitialCaseWorkers.REGISTER_AUTOMATIC));
        initial.clarification(engine, job(InitialCaseWorkers.CLARIFICATION));
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

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unapprovedPositiveOrFailedClosureAssessmentReachesOneRealStaffReview(boolean failure) throws Exception {
        ConversationEvaluationService central = central(false, List.of("RQ-001"), context ->
                new ClosureAssessment.Result(failure ? ClosureAssessment.Outcome.REVIEW : ClosureAssessment.Outcome.RESOLVED,
                        null, null, List.of(), "Synthetischer Vorschlag für den Rückwegtest",
                        failure ? "CLOSURE_ASSESSMENT_UNAVAILABLE" : null, "test", "fixture-v1", "fixture-validation-v1"));
        AutomaticEvaluationWorkers evaluation = new AutomaticEvaluationWorkers(engine, central);
        evaluation.evaluate(engine, job(AutomaticEvaluationWorkers.EVALUATE));
        evaluation.recommend(engine, job(AutomaticEvaluationWorkers.RECOMMEND));
        AutomaticQuestionWorkers automatic = new AutomaticQuestionWorkers(engine, questions, org.mockito.Mockito.mock(AutomaticClosureService.class));
        automatic.validate(engine, job(AutomaticQuestionWorkers.VALIDATE));
        automatic.finish(engine, job(AutomaticQuestionWorkers.FINISH));
        ActivatedJob result = job(AutomaticQuestionWorkers.ACCEPT);
        CaseWorkflowStore.Entry entry = workflow.entryForCase(caseId).orElseThrow();
        await(() -> "COMPLETED".equals(engine.newProcessInstanceGetRequest(entry.childKey()).send().join().getState().name())
                && "COMPLETED".equals(engine.newElementInstanceGetRequest(entry.parentElementKey()).send().join().getState().name()));
        automatic.accept(engine, result);
        StaffReviewWorkers staff = new StaffReviewWorkers(engine, reviewService);
        staff.claim(engine, job("propertyflow.staff.claim"));
        staff.register(engine, job("propertyflow.staff.register"));
        staff.startReview(engine, job("propertyflow.staff.start-review"));
        awaitTask("SReview");
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals(1, tasks().size());
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_conversation_evaluation", Integer.class));
        assertEquals(0, database.queryForObject("SELECT count(*) FROM pf_case_closure", Integer.class));
        assertEquals(0, database.queryForObject("SELECT count(*) FROM pf_staff_message", Integer.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"closed", "invalid", "stale", "publication-stale", "technical"})
    void allClosureTasksRunWithSyntheticApprovalAndRealBoundaries(String scenario) throws Exception {
        database.update("UPDATE pf_case SET description = ? WHERE id = ?",
                "Die Heizungsstörung ist vollständig behoben. Keine weitere Hilfe nötig.", caseId);
        ClosureAssessment assessment = AutomaticClosureFixtures.assessment();
        AutomaticClosureFixtures.Release release = new AutomaticClosureFixtures.Release();
        AutomaticClosureService closure = closureService(assessment, release, (messages, ground) -> {
            if ("technical".equals(scenario)) {
                throw new IllegalStateException("MODEL_UNAVAILABLE");
            }
            return "invalid".equals(scenario) ? new ClosureMessage.Result(null, false, "fixture", "v1", "v1")
                    : AutomaticClosureFixtures.message();
        });
        ConversationEvaluationService central = central(false, List.of(), assessment, release);
        AutomaticQuestionWorkers worker = closureWorker(closure);
        AutomaticEvaluationWorkers evaluation = new AutomaticEvaluationWorkers(engine, central);
        evaluation.evaluate(engine, job(AutomaticEvaluationWorkers.EVALUATE));
        evaluation.recommend(engine, job(AutomaticEvaluationWorkers.RECOMMEND));
        worker.validate(engine, job(AutomaticQuestionWorkers.VALIDATE));
        if ("publication-stale".equals(scenario)) {
            database.update("UPDATE pf_case SET version = version + 1, analysis_version = analysis_version + 1 WHERE id = ?", caseId);
            worker.closureMessage(engine, job(AutomaticQuestionWorkers.CLOSURE_MESSAGE));
            evaluation.evaluate(engine, job(AutomaticEvaluationWorkers.EVALUATE));
            worker.validate(engine, job(AutomaticQuestionWorkers.VALIDATE));
        }
        ActivatedJob message = job(AutomaticQuestionWorkers.CLOSURE_MESSAGE);
        worker.closureMessage(engine, message);
        if ("technical".equals(scenario)) {
            for (int retries = 2; retries > 0; retries--) {
                ActivatedJob retry = job(AutomaticQuestionWorkers.CLOSURE_MESSAGE);
                assertEquals(retries, retry.getRetries());
                worker.closureMessage(engine, retry);
            }
            await(() -> !engine.newIncidentSearchRequest().filter(filter -> filter.processInstanceKey(message.getProcessInstanceKey()))
                    .send().join().items().isEmpty());
            assertEquals(0, database.queryForObject("SELECT count(*) FROM pf_staff_message", Integer.class));
            assertEquals(0, database.queryForObject("SELECT count(*) FROM pf_case_review_signal", Integer.class));
            assertEquals("UNDER_CLARIFICATION", status());
            return;
        }
        if ("invalid".equals(scenario)) {
            worker.finish(engine, job(AutomaticQuestionWorkers.FINISH));
            acceptAutomatic(worker);
            StaffReviewWorkers staff = new StaffReviewWorkers(engine, reviewService);
            staff.claim(engine, job("propertyflow.staff.claim"));
            staff.register(engine, job("propertyflow.staff.register"));
            staff.startReview(engine, job("propertyflow.staff.start-review"));
            awaitTask("SReview");
            assertEquals("STAFF_REVIEW_REQUIRED", status());
            assertEquals(0, database.queryForObject("SELECT count(*) FROM pf_staff_message", Integer.class));
            return;
        }
        if ("stale".equals(scenario)) {
            database.update("UPDATE pf_case SET version = version + 1, analysis_version = analysis_version + 1 WHERE id = ?", caseId);
            worker.close(engine, job(AutomaticQuestionWorkers.CLOSE));
            evaluation.evaluate(engine, job(AutomaticEvaluationWorkers.EVALUATE));
            worker.validate(engine, job(AutomaticQuestionWorkers.VALIDATE));
            worker.closureMessage(engine, job(AutomaticQuestionWorkers.CLOSURE_MESSAGE));
        }
        finishAutomaticClosure(worker);
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_staff_message", Integer.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void replyCloseStartsANewAutomaticEntryAndCancelsOpenQuestionsOnlyAtCommit(boolean answered) throws Exception {
        AutomaticQuestionWorkers initial = new AutomaticQuestionWorkers(engine, questions,
                org.mockito.Mockito.mock(AutomaticClosureService.class));
        AutomaticEvaluationWorkers first = new AutomaticEvaluationWorkers(engine, central(false, List.of("RQ-001")));
        first.evaluate(engine, job(AutomaticEvaluationWorkers.EVALUATE));
        first.recommend(engine, job(AutomaticEvaluationWorkers.RECOMMEND));
        initial.validate(engine, job(AutomaticQuestionWorkers.VALIDATE));
        initial.publish(engine, job(AutomaticQuestionWorkers.PUBLISH));
        initial.finish(engine, job(AutomaticQuestionWorkers.FINISH));
        acceptAutomatic(initial);
        ClosureAssessment assessment = AutomaticClosureFixtures.assessment();
        AutomaticClosureFixtures.Release release = new AutomaticClosureFixtures.Release();
        AutomaticClosureService closure = closureService(assessment, release, (messages, ground) -> AutomaticClosureFixtures.message());
        ConversationEvaluationService central = central(answered, List.of(), assessment, release);
        TenantReplyService service = new TenantReplyService(workflow, replyStore, evaluations, central,
                new AutomaticQuestionStore(database), transactions, closure);
        TenantReplyWorkers reply = new TenantReplyWorkers(engine, service, replyStore);
        reply.register(engine, job(TenantReplyWorkers.REGISTER));
        receive("Die Heizungsstörung ist vollständig behoben. Keine weitere Hilfe nötig.", central);
        database.update("UPDATE pf_reply_call SET next_attempt_at = CURRENT_TIMESTAMP");
        new TenantReplyDispatcher(replyStore, service, engine).dispatch();
        reply.evaluate(engine, job(TenantReplyWorkers.EVALUATE));
        reply.validate(engine, job(TenantReplyWorkers.VALIDATE));
        ActivatedJob prepare = job(AutomaticQuestionRoundWorkers.PREPARE_CLOSE);
        UUID decision = UUID.fromString((String) prepare.getVariablesAsMap().get("replyDecisionRef"));
        TenantReplyStore.Call source = replyStore.automaticSource(decision, caseId, leader, "CLOSE");
        await(() -> "COMPLETED".equals(engine.newProcessInstanceGetRequest(source.child()).send().join().getState().name())
                && "COMPLETED".equals(engine.newElementInstanceGetRequest(source.parent()).send().join().getState().name()));
        new AutomaticQuestionRoundWorkers(engine, new AutomaticQuestionRoundService(workflow, replyStore)).prepareClose(engine, prepare);
        new InitialCaseWorkers(engine, initialService).registerAutomatic(engine, job(InitialCaseWorkers.REGISTER_AUTOMATIC));
        assertEquals("WAITING_FOR_TENANT", status());
        assertEquals("CLOSE", workflow.entryKind(workflow.entryForCase(caseId).orElseThrow().id()));
        AutomaticEvaluationWorkers current = new AutomaticEvaluationWorkers(engine, central);
        current.evaluate(engine, job(AutomaticEvaluationWorkers.EVALUATE));
        AutomaticQuestionWorkers worker = closureWorker(closure);
        worker.validate(engine, job(AutomaticQuestionWorkers.VALIDATE));
        worker.closureMessage(engine, job(AutomaticQuestionWorkers.CLOSURE_MESSAGE));
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
        worker.close(engine, job(AutomaticQuestionWorkers.CLOSE));
        worker.closed(engine, job(AutomaticQuestionWorkers.CLOSED));
        acceptAutomatic(worker);
        await(() -> "COMPLETED".equals(engine.newProcessInstanceGetRequest(leader).send().join().getState().name()));
        assertEquals("CLOSED", status());
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_case_closure WHERE source = 'SYSTEM'", Integer.class));
        assertEquals(mailOrders, database.queryForObject("SELECT count(*) FROM pf_case_mail_order", Integer.class));
    }

    private void acceptAutomatic(AutomaticQuestionWorkers worker) throws Exception {
        ActivatedJob result = job(AutomaticQuestionWorkers.ACCEPT);
        CaseWorkflowStore.Entry entry = workflow.entryForCase(caseId).orElseThrow();
        await(() -> "COMPLETED".equals(engine.newProcessInstanceGetRequest(entry.childKey()).send().join().getState().name())
                && "COMPLETED".equals(engine.newElementInstanceGetRequest(entry.parentElementKey()).send().join().getState().name()));
        worker.accept(engine, result);
    }

    private void closeClarification() throws Exception {
        job(AutomaticEvaluationWorkers.EVALUATE);
        ClosureApproval approval = data.clarificationClosure(actor, caseId);
        assertTrue(approval.available());
        data.closeClarification(actor, caseId, "canonical-close", approval.version(), approval.reviewToken());
        new ClarificationClosureWorkers(engine, data).close(engine, job(ClarificationClosureWorkers.APPLY));
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
        new CaseWaitWorkers(engine, waitService).external(engine, job(CaseWaitWorkers.EXTERNAL));
        awaitTask("SExternalWait");
        assertEquals("WAITING_FOR_EXTERNAL", status());
        task = decisions.current(actor, caseId);
        decisions.followUp(actor, caseId, UUID.randomUUID(), Long.parseLong(task.taskKey()), task.version(), task.reviewToken(), "", 3, null);
        new CaseWaitWorkers(engine, waitService).save(engine, job(CaseWaitWorkers.SAVE));
        assertEquals("WAITING_FOR_FOLLOW_UP", status());
        FollowUpCommandService followUp = new FollowUpCommandService(access, workflow, caseData, waitStore, decisionStore,
                followUpStore, new CamundaFollowUpGateway(clients(), true), transactions);
        await(() -> followUp.current(actor, caseId).available());
        FollowUpCommands.Current current = followUp.current(actor, caseId);
        followUp.submit(actor, caseId, UUID.randomUUID(), current.waitId(), current.version(), true, null, null, "Weiter bearbeiten");
        new FollowUpCommandWorkers(engine, followUp).apply(engine, job(FollowUpCommandWorkers.APPLY));
        new StaffDecisionWorkers(engine, decisions).progress(engine, job(StaffDecisionWorkers.PROGRESS));
        awaitTask("SWork");
        assertEquals("IN_PROGRESS", status());
        task = decisions.current(actor, caseId);
        decisions.closeCase(actor, caseId, UUID.randomUUID(), Long.parseLong(task.taskKey()), task.version(), task.reviewToken());
        ActivatedJob closing = job(CaseClosureWorkers.CLOSE);
        CaseClosureService.ClosureContext context = closures.context(caseId, leader,
                UUID.fromString(closing.getVariablesAsMap().get("staffDecisionRef").toString()));
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
        direct.apply(engine, job(DirectFollowUpWorkers.APPLY));
        direct.signal(engine, job(DirectFollowUpWorkers.SIGNAL));
        StaffReviewWorkers staff = new StaffReviewWorkers(engine, reviewService);
        staff.prepareStaff(engine, job("propertyflow.review.prepare-staff"));
        staff.claim(engine, job("propertyflow.staff.claim"));
        staff.register(engine, job("propertyflow.staff.register"));
        new CaseWaitWorkers(engine, waitService).save(engine, job(CaseWaitWorkers.SAVE));
        assertEquals("WAITING_FOR_FOLLOW_UP", status());
        receive("Die Heizung ist weiterhin kalt.", false);
        awaitTask("SReview");
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_wait_resolution", Integer.class));
    }

    @Test
    void catalogQuestionsAndACompleteTenantAnswerReachOneReviewWithFullModels() throws Exception {
        ConversationEvaluationService central = central(false, List.of("RQ-001"));
        AutomaticEvaluationWorkers evaluation = new AutomaticEvaluationWorkers(engine, central);
        evaluation.evaluate(engine, job(AutomaticEvaluationWorkers.EVALUATE));
        evaluation.recommend(engine, job(AutomaticEvaluationWorkers.RECOMMEND));
        AutomaticQuestionWorkers automatic = new AutomaticQuestionWorkers(engine, questions, org.mockito.Mockito.mock(AutomaticClosureService.class));
        automatic.validate(engine, job(AutomaticQuestionWorkers.VALIDATE));
        automatic.publish(engine, job(AutomaticQuestionWorkers.PUBLISH));
        automatic.finish(engine, job(AutomaticQuestionWorkers.FINISH));
        ActivatedJob result = job(AutomaticQuestionWorkers.ACCEPT);
        CaseWorkflowStore.Entry entry = workflow.entryForCase(caseId).orElseThrow();
        await(() -> "COMPLETED".equals(engine.newProcessInstanceGetRequest(entry.childKey()).send().join().getState().name()));
        automatic.accept(engine, result);
        TenantReplyService replyService = new TenantReplyService(workflow, replyStore, evaluations, central,
                new AutomaticQuestionStore(database), transactions, org.mockito.Mockito.mock(AutomaticClosureService.class));
        TenantReplyWorkers reply = new TenantReplyWorkers(engine, replyService, replyStore);
        reply.register(engine, job(TenantReplyWorkers.REGISTER));
        assertEquals("WAITING_FOR_TENANT", status());
        receive("Die Störung besteht seit gestern.", true);
        database.update("UPDATE pf_reply_call SET next_attempt_at = CURRENT_TIMESTAMP");
        new TenantReplyDispatcher(replyStore, replyService, engine).dispatch();
        reply.prepareReview(engine, job(TenantReplyWorkers.PREPARE_REVIEW));
        StaffReviewWorkers staff = new StaffReviewWorkers(engine, reviewService);
        staff.claim(engine, job("propertyflow.staff.claim"));
        staff.register(engine, job("propertyflow.staff.register"));
        staff.startReview(engine, job("propertyflow.staff.start-review"));
        awaitTask("SReview");
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals("ANSWERED", database.queryForObject("SELECT state FROM pf_staff_answer_requirement", String.class));
        assertEquals(1, tasks().size());
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
        ActivatedJob prepare = job(InitialCaseWorkers.PREPARE_KEY);
        leader = prepare.getProcessInstanceKey();
        assertTrue(previous != leader);
        new InitialCaseWorkers(engine, initialService).prepareKey(engine, prepare);
        ActivatedJob reopening = job(CaseReopenWorkers.REOPEN);
        await(() -> !engine.newMessageSubscriptionSearchRequest().filter(filter -> filter.processInstanceKey(leader).elementId("NStart")
                .messageSubscriptionState(MessageSubscriptionState.CREATED)).send().join().items().isEmpty());
        worker.reopen(engine, reopening);
        StaffReviewWorkers staff = new StaffReviewWorkers(engine, reviewService);
        staff.claim(engine, job("propertyflow.staff.claim"));
        staff.register(engine, job("propertyflow.staff.register"));
        awaitTask("SWork");
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
        new StaffMessageWorkers(engine, messages).question(engine, job("propertyflow.staff-question.publish"));
        TenantReplyService replyService = new TenantReplyService(workflow, replyStore, evaluations, central(false, List.of()),
                new AutomaticQuestionStore(database), transactions, org.mockito.Mockito.mock(AutomaticClosureService.class));
        TenantReplyWorkers reply = new TenantReplyWorkers(engine, replyService, replyStore);
        reply.register(engine, job(TenantReplyWorkers.REGISTER));
        receive("Ich prüfe noch meinen Kalender.", false);
        database.update("UPDATE pf_reply_call SET next_attempt_at = CURRENT_TIMESTAMP");
        new TenantReplyDispatcher(replyStore, replyService, engine).dispatch();
        reply.evaluate(engine, job(TenantReplyWorkers.EVALUATE));
        reply.validate(engine, job(TenantReplyWorkers.VALIDATE));
        reply.register(engine, job(TenantReplyWorkers.REGISTER));
        assertEquals("WAITING_FOR_TENANT", status());
        assertEquals("OPEN", database.queryForObject("SELECT state FROM pf_staff_answer_requirement", String.class));
        receive("Am Montag um 10 Uhr.", true);
        database.update("UPDATE pf_reply_call SET next_attempt_at = CURRENT_TIMESTAMP");
        new TenantReplyDispatcher(replyStore, replyService, engine).dispatch();
        reply.prepareReview(engine, job(TenantReplyWorkers.PREPARE_REVIEW));
        StaffReviewWorkers staff = new StaffReviewWorkers(engine, reviewService);
        staff.claim(engine, job("propertyflow.staff.claim"));
        staff.register(engine, job("propertyflow.staff.register"));
        staff.startReview(engine, job("propertyflow.staff.start-review"));
        awaitTask("SReview");
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
        ActivatedJob analysis = job(AutomaticEvaluationWorkers.EVALUATE);
        assertEquals("PENDING", data.takeOver(actor, caseId, "canonical-takeover", version()).state());
        StaffTakeoverWorkers takeover = new StaffTakeoverWorkers(engine, data, new StaffTakeoverStore(database));
        takeover.apply(engine, job(StaffTakeoverWorkers.APPLY));
        takeover.signal(engine, job(StaffTakeoverWorkers.SIGNAL));
        StaffReviewWorkers staff = new StaffReviewWorkers(engine, reviewService);
        staff.prepareStaff(engine, job("propertyflow.review.prepare-staff"));
        staff.claim(engine, job("propertyflow.staff.claim"));
        staff.register(engine, job("propertyflow.staff.register"));
        awaitTask("SWork");
        assertEquals("TERMINATED", engine.newProcessInstanceGetRequest(analysis.getProcessInstanceKey()).send().join().getState().name());
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
                closure, transactions, release);
    }

    private void receive(String text, boolean answered) throws Exception {
        receive(text, central(answered, List.of()));
    }

    private void receive(String text, ConversationEvaluationService central) throws Exception {
        TenantMessageService tenant = new TenantMessageService(tenantMessages, workflow, caseData, links,
                new CamundaCaseGateway(clients(), true), new CamundaTenantMessageGateway(clients(), true), transactions, accountId -> java.util.Optional.empty());
        tenant.send(accepted.personalAccessToken(), UUID.randomUUID(), text);
        new TenantMessageWorkers(engine, tenant).identify(engine, job(TenantMessageWorkers.IDENTIFY));
        if ("WAITING_FOR_FOLLOW_UP".equals(status())) {
            // Der Kontakt beendet den Termin vor der KI-Auswertung; NWake wartet auf diese Übergabe.
            new FollowUpContactDispatcher(waitStore, engine).dispatch();
            new CaseWaitWorkers(engine, waitService).stopContact(engine, job(CaseWaitWorkers.STOP));
            new StaffReviewWorkers(engine, reviewService).startReview(engine, job("propertyflow.staff.start-review"));
            awaitTask("SReview");
        }
        ConversationEvaluationWorkers evaluation = new ConversationEvaluationWorkers(engine, central);
        evaluation.context(engine, job(ConversationEvaluationWorkers.CONTEXT));
        evaluation.evaluate(engine, job(ConversationEvaluationWorkers.EVALUATE));
        evaluation.recommendation(engine, job(ConversationEvaluationWorkers.RECOMMENDATION));
        evaluation.apply(engine, job(ConversationEvaluationWorkers.APPLY));
    }

    private List<UserTask> tasks() {
        return engine.newUserTaskSearchRequest().filter(filter -> filter.state(UserTaskState.CREATED)).send().join().items();
    }

    private void awaitTask(String element) throws Exception {
        await(() -> tasks().size() == 1 && element.equals(tasks().getFirst().getElementId()));
    }

    private String model(String name) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        byte[] resource;
        try (InputStream input = getClass().getResourceAsStream("/bpmn/" + name + ".bpmn")) {
            assertNotNull(input, "BPMN fehlt im Anwendungsklassenpfad: " + name);
            resource = input.readAllBytes();
        }
        Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(resource));
        NodeList processes = document.getElementsByTagNameNS(BPMN, "process");
        for (int index = 0; index < processes.getLength(); index++) {
            assertEquals("true", ((Element) processes.item(index)).getAttribute("isExecutable"), name);
        }
        NodeList tasks = document.getElementsByTagNameNS(BPMN, "serviceTask");
        for (int index = 0; index < tasks.getLength(); index++) {
            Element task = (Element) tasks.item(index);
            assertEquals(1, task.getElementsByTagNameNS(ZEEBE, "taskDefinition").getLength(), task.getAttribute("id"));
        }
        return new String(resource, StandardCharsets.UTF_8);
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
}
