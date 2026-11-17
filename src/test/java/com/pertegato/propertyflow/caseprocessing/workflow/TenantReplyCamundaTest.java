package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.access.contract.StaffAccountAccess;
import com.pertegato.propertyflow.access.contract.SubmissionContext;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.caseprocessing.contract.CaseAccepted;
import com.pertegato.propertyflow.caseprocessing.contract.CaseActionException;
import com.pertegato.propertyflow.caseprocessing.contract.CaseCreation;
import com.pertegato.propertyflow.caseprocessing.contract.CreateCaseCommand;
import com.pertegato.propertyflow.caseprocessing.contract.FollowUpCommands;
import com.pertegato.propertyflow.caseprocessing.contract.StaffCaseDataCommands.ClosureApproval;
import com.pertegato.propertyflow.caseprocessing.contract.StaffTaskDecisions.StaffTask;
import com.pertegato.propertyflow.caseprocessing.delivery.PersonalLinkCodec;
import com.pertegato.propertyflow.caseprocessing.persistence.AnswerRequirementStore;
import com.pertegato.propertyflow.caseprocessing.persistence.AutomaticQuestionStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseAnalysisStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseDataStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWaitStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.StartOrder;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import com.pertegato.propertyflow.caseprocessing.persistence.ClarificationQuestionStore;
import com.pertegato.propertyflow.caseprocessing.persistence.ConversationEvaluationStore;
import com.pertegato.propertyflow.caseprocessing.persistence.DirectFollowUpStore;
import com.pertegato.propertyflow.caseprocessing.persistence.FollowUpCommandStore;
import com.pertegato.propertyflow.caseprocessing.persistence.ReplyClosureStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffDecisionStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffMessageStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffReviewStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffTakeoverStore;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantMessageStore;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantReplyStore;
import com.pertegato.propertyflow.caseprocessing.persistence.UrgencyStore;
import com.pertegato.propertyflow.caseprocessing.service.AutomaticClosureRelease;
import com.pertegato.propertyflow.caseprocessing.service.AutomaticClosureService;
import com.pertegato.propertyflow.caseprocessing.service.CaseAnalysisService;
import com.pertegato.propertyflow.caseprocessing.service.CaseClosureService;
import com.pertegato.propertyflow.caseprocessing.service.CaseDataService;
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
import com.pertegato.propertyflow.recommendation.service.Recommendation;
import com.pertegato.propertyflow.triage.service.AnalyzeTool;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "propertyflow.workflow.enabled=false")
@ActiveProfiles("propertyflow-backend")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_DATABASE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_CAMUNDA_URL", matches = ".+")
class TenantReplyCamundaTest {

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
    private CaseClosureService clarificationClosures;

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
        long definitionKey = engine.newDeployResourceCommand().addResourceFromClasspath("workflow/reply-case.bpmn")
                .addResourceFromClasspath("workflow/follow-up.bpmn").addResourceFromClasspath("workflow/reply-processing.bpmn").addResourceFromClasspath("workflow/conversation-processing.bpmn")
                .addResourceFromClasspath("workflow/analysis-automatic.bpmn").addResourceFromClasspath("workflow/reply-staff.bpmn")
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

    @Autowired
    private TenantReplyStore replies;
    @Autowired
    private TenantMessageStore tenantMessages;
    @Autowired
    private ConversationEvaluationStore evaluations;
    @Autowired
    private UrgencyStore urgencies;
    @Autowired
    private PersonalLinkCodec links;

    private boolean answered;

    private ConversationEvaluationService central() {
        return new ConversationEvaluationService(workflow, tenantMessages, evaluations, urgencies, input -> {
            List<ConversationAnalysis.Answer> answers = answered ? input.questions().stream().map(question -> {
                ConversationAnalysis.Message last = input.messages().getLast();
                return new ConversationAnalysis.Answer(question.id(), last.id(), last.text());
            }).toList() : List.of();
            return new ConversationAnalysis.Result("NORMAL", false, answers, null, "test", "v1");
        }, input -> new Recommendation.Result("Verwaltung", "Prüfen", List.of(), List.of(), false, null, "test", "v1"), noClosure(), transactions,
                new AutomaticClosureRelease());
    }

    private TenantReplyWorkers replyWorkers() {
        return new TenantReplyWorkers(engine, replyService(), replies);
    }

    private TenantReplyService replyService() {
        return new TenantReplyService(workflow, replies, evaluations, central(), new AutomaticQuestionStore(database), transactions,
                org.mockito.Mockito.mock(AutomaticClosureService.class));
    }

    private void progress() throws Exception {
        review();
        StaffDecisionService decision = new StaffDecisionService(access, workflow, caseData, reviewStore, decisions, taskGateway(), transactions);
        StaffTask task = decision.current(actor, caseId);
        decision.continueWork(actor, caseId, UUID.randomUUID(), Long.parseLong(task.taskKey()), task.version(), task.reviewToken(), "");
        new StaffDecisionWorkers(engine, decision).progress(engine, job("propertyflow.staff.progress"));
        await(() -> tasks().stream().anyMatch(current -> "SWork".equals(current.getElementId())));
    }

    private void question() throws Exception {
        progress();
        assertTrue(service().available(actor, caseId).question());
        assertEquals("PENDING", service().send(actor, caseId, "Frage", "Wann passt Ihnen ein Termin?", true));
        new StaffMessageWorkers(engine, service()).question(engine, job("propertyflow.staff-question.publish"));
        assertEquals("PUBLISHED", service().send(actor, caseId, "Frage", "Wann passt Ihnen ein Termin?", true));
        replyWorkers().register(engine, job(TenantReplyWorkers.REGISTER));
        assertEquals("WAITING_FOR_TENANT", status());
        assertEquals(1, count("pf_staff_answer_requirement"));
        assertEquals(1, count("pf_reply_entry"));
    }

    private void answer(String text) throws Exception {
        StaticListableBeanFactory beans = new StaticListableBeanFactory(Map.of("engine", engine));
        TenantMessageService tenant = new TenantMessageService(tenantMessages, workflow, caseData, links,
                new CamundaCaseGateway(beans.getBeanProvider(CamundaClient.class), true),
                new CamundaTenantMessageGateway(beans.getBeanProvider(CamundaClient.class), true), transactions, accountId -> java.util.Optional.empty());
        tenant.send(accepted.personalAccessToken(), UUID.randomUUID(), text);
        new TenantMessageWorkers(engine, tenant).identify(engine, job(TenantMessageWorkers.IDENTIFY));
        if ("WAITING_FOR_FOLLOW_UP".equals(status())) {
            return;
        }
        ConversationEvaluationWorkers worker = new ConversationEvaluationWorkers(engine, central());
        worker.evaluate(engine, job(ConversationEvaluationWorkers.EVALUATE));
        worker.apply(engine, job(ConversationEvaluationWorkers.APPLY));
    }

    private void deliver() {
        database.update("UPDATE pf_reply_call SET next_attempt_at = CURRENT_TIMESTAMP");
        new TenantReplyDispatcher(replies, replyService(), engine).dispatch();
    }

    @Test
    void partialAnswerContinuesWaitingAndCompleteAnswerCreatesExactlyOneNewReview() throws Exception {
        question();
        int statusEvents = count("pf_case_status_event");
        answer("Ich muss meinen Kalender prüfen.");
        deliver();
        replyWorkers().evaluate(engine, job(TenantReplyWorkers.EVALUATE));
        replyWorkers().validate(engine, job(TenantReplyWorkers.VALIDATE));
        replyWorkers().register(engine, job(TenantReplyWorkers.REGISTER));
        assertEquals("WAITING_FOR_TENANT", status());
        assertEquals(statusEvents, count("pf_case_status_event"));
        assertEquals(1, count("pf_conversation_evaluation"));
        assertEquals("OPEN", database.queryForObject("SELECT state FROM pf_staff_answer_requirement", String.class));
        answered = true;
        answer("Am Montag um 10 Uhr.");
        deliver();
        replyWorkers().prepareReview(engine, job(TenantReplyWorkers.PREPARE_REVIEW));
        StaffReviewWorkers reviews = new StaffReviewWorkers(engine, reviewService);
        reviews.claim(engine, job("propertyflow.staff.claim"));
        reviews.register(engine, job("propertyflow.staff.register"));
        reviews.startReview(engine, job("propertyflow.staff.start-review"));
        await(() -> tasks().size() == 1);
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals("ANSWERED", database.queryForObject("SELECT state FROM pf_staff_answer_requirement", String.class));
        assertEquals(2, count("pf_reply_decision"));
        deliver();
        assertEquals(1, tasks().size());
    }

    @Test
    void changedCaseRejectsNativeQuestionAndReturnsToReviewWithoutPublishing() throws Exception {
        progress();
        service().send(actor, caseId, "Veraltet", "Noch gültig?", true);
        ActivatedJob question = job("propertyflow.staff-question.publish");
        database.update("UPDATE pf_case SET version = version + 1 WHERE id = ?", caseId);
        new StaffMessageWorkers(engine, service()).question(engine, question);
        new StaffReviewWorkers(engine, reviewService).startReview(engine, job("propertyflow.staff.start-review"));
        await(() -> tasks().size() == 1);
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals(0, count("pf_staff_message"));
        assertEquals(0, count("pf_reply_entry"));
    }

    @Test
    void evaluationBeforeWaitRegistrationIsDeliveredAfterRegistration() throws Exception {
        progress();
        service().send(actor, caseId, "Früh", "Welcher Termin passt?", true);
        new StaffMessageWorkers(engine, service()).question(engine, job("propertyflow.staff-question.publish"));
        ActivatedJob registration = job(TenantReplyWorkers.REGISTER);
        answer("Ich prüfe den Kalender.");
        deliver();
        assertEquals(0, count("pf_reply_call"));
        replyWorkers().register(engine, registration);
        deliver();
        replyWorkers().evaluate(engine, job(TenantReplyWorkers.EVALUATE));
        replyWorkers().validate(engine, job(TenantReplyWorkers.VALIDATE));
        replyWorkers().register(engine, job(TenantReplyWorkers.REGISTER));
        assertEquals("WAITING_FOR_TENANT", status());
        assertEquals(1, count("pf_reply_decision"));
        assertEquals("WAIT", database.queryForObject("SELECT outcome FROM pf_reply_decision", String.class));
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_case_status_event WHERE to_status = 'WAITING_FOR_TENANT'", Integer.class));
    }

    @Test
    void newerAnswerInvalidatesContinuationAndUsesSharedCurrentEvaluation() throws Exception {
        question();
        answer("Ich schaue nach.");
        deliver();
        replyWorkers().evaluate(engine, job(TenantReplyWorkers.EVALUATE));
        ActivatedJob old = job(TenantReplyWorkers.VALIDATE);
        answer("Der Kalender ist noch nicht verfügbar.");
        replyWorkers().validate(engine, old);
        assertEquals(0, count("pf_reply_decision"));
        ActivatedJob current = job(TenantReplyWorkers.EVALUATE);
        assertEquals("RCurrentAnalysis", current.getElementId());
        replyWorkers().evaluate(engine, current);
        replyWorkers().validate(engine, job(TenantReplyWorkers.VALIDATE));
        replyWorkers().register(engine, job(TenantReplyWorkers.REGISTER));
        assertEquals(2, count("pf_conversation_evaluation"));
        assertEquals(1, count("pf_reply_decision"));
        assertEquals("WAITING_FOR_TENANT", status());
    }

    @Test
    void staffMessagesKeepCurrentWaitAndLateSideCommandCannotPublishAfterContinuation() throws Exception {
        question();
        long child = replies.active(caseId, leader).orElseThrow().child();
        await(() -> !engine.newMessageSubscriptionSearchRequest().filter(filter -> filter.processInstanceKey(child)
                .messageName(CamundaStaffMessageGateway.REPLY_MESSAGE)).send().join().items().isEmpty());
        StaffMessageService service = service();
        assertTrue(service.available(actor, caseId).question());
        service.send(actor, caseId, "Zusatzfrage", "Welche Telefonnummer dürfen wir nutzen?", true);
        new StaffMessageWorkers(engine, service).publish(engine, job(StaffMessageWorkers.PUBLISH));
        assertEquals(child, replies.active(caseId, leader).orElseThrow().child());
        assertEquals(2, count("pf_staff_answer_requirement"));
        assertEquals("WAITING_FOR_TENANT", status());
        answer("Ich prüfe das noch.");
        deliver();
        replyWorkers().evaluate(engine, job(TenantReplyWorkers.EVALUATE));
        service.send(actor, caseId, "Alter Nebenauftrag", "Noch nicht veröffentlicht", false);
        ActivatedJob side = job(StaffMessageWorkers.PUBLISH);
        replyWorkers().validate(engine, job(TenantReplyWorkers.VALIDATE));
        new StaffMessageWorkers(engine, service).publish(engine, side);
        replyWorkers().register(engine, job(TenantReplyWorkers.REGISTER));
        assertEquals(2, count("pf_staff_message"));
        assertEquals("REJECTED", messages.latest(caseId, "Alter Nebenauftrag").orElseThrow().state());
        assertEquals("WAITING_FOR_TENANT", status());
    }

    @Autowired
    private DirectFollowUpStore directFollowUps;

    @Autowired
    private CaseWaitService waitService;

    private UUID requestDirectWait(CaseDataService data) {
        ClosureApproval approval = data.directFollowUp(actor, caseId);
        assertTrue(approval.available());
        data.setDirectFollowUp(actor, caseId, "direct-follow", approval.version(), approval.reviewToken(), 2, null, "Warten auf Ersatzteil");
        return caseData.latest(caseId, "direct-follow").orElseThrow().id();
    }

    private void enterDirectWait(CaseDataService data) throws Exception {
        DirectFollowUpWorkers worker = new DirectFollowUpWorkers(engine, data, caseData, directFollowUps, replies, reviewService);
        worker.apply(engine, job(DirectFollowUpWorkers.APPLY));
        assertEquals("APPLIED", caseData.latest(caseId, "direct-follow").orElseThrow().state());
        worker.signal(engine, job(DirectFollowUpWorkers.SIGNAL));
        worker.resume(engine, job(DirectFollowUpWorkers.CONTINUE));
        StaffReviewWorkers staff = new StaffReviewWorkers(engine, reviewService);
        staff.claim(engine, job("propertyflow.staff.claim"));
        staff.register(engine, job("propertyflow.staff.register"));
        new CaseWaitWorkers(engine, waitService).save(engine, job(CaseWaitWorkers.SAVE));
    }

    @Test
    void directFollowUpFromReplyKeepsQuestionsAndContactReturnsToOneReview() throws Exception {
        question();
        TenantReplyStore.Call previous = replies.active(caseId, leader).orElseThrow();
        CaseDataService data = caseDataService();
        UUID id = requestDirectWait(data);
        int mails = count("pf_case_mail_order");
        enterDirectWait(data);
        assertEquals("WAITING_FOR_FOLLOW_UP", status());
        assertEquals("INTERRUPTED", replies.get(previous.id()).state());
        assertEquals("OPEN", database.queryForObject("SELECT state FROM pf_staff_answer_requirement", String.class));
        assertEquals(mails + 1, count("pf_case_mail_order"));
        assertEquals(1, count("pf_case_wait"));
        assertEquals("OBSOLETE", replyService().evaluate(caseId, previous.entry(), leader, previous.child(), previous.tenant()).state());
        answer("Eine neue Information zur Wiedervorlage.");
        new FollowUpContactDispatcher(new CaseWaitStore(database), engine).dispatch();
        new CaseWaitWorkers(engine, waitService).stopContact(engine, job(CaseWaitWorkers.STOP));
        new StaffReviewWorkers(engine, reviewService).startReview(engine, job("propertyflow.staff.start-review"));
        await(() -> tasks().size() == 1);
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals("CONTACT", new CaseWaitStore(database).get(id).orElseThrow().state());
        assertEquals(1, count("pf_wait_resolution"));
    }

    @Test
    void validDirectDateThatBecomesDueDuringHandoverUsesTheOriginalTimer() throws Exception {
        question();
        CaseDataService data = caseDataService();
        UUID id = requestDirectWait(data);
        // Eine ursprünglich gültige Frist kann während eines Ausfalls verstreichen.
        database.update("UPDATE pf_direct_follow_up SET due_at = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE id = ?", id);
        enterDirectWait(data);
        new CaseWaitWorkers(engine, waitService).timer(engine, job(CaseWaitWorkers.TIMER));
        new StaffReviewWorkers(engine, reviewService).startReview(engine, job("propertyflow.staff.start-review"));
        await(() -> tasks().size() == 1);
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals("TIMER", new CaseWaitStore(database).get(id).orElseThrow().state());
        assertEquals(1, count("pf_case_wait"));
    }

    @Test
    void deletingDirectFollowUpWithOpenQuestionsReturnsToWorkAndOldMarkerDoesNotCancelNextReply() throws Exception {
        question();
        CaseDataService data = caseDataService();
        requestDirectWait(data);
        enterDirectWait(data);
        CaseWaitStore waitStore = new CaseWaitStore(database);
        FollowUpCommandService commands = new FollowUpCommandService(access, workflow, caseData, waitStore, decisions,
                new FollowUpCommandStore(database), new CamundaFollowUpGateway(
                        new StaticListableBeanFactory(Map.of("engine", engine)).getBeanProvider(CamundaClient.class), true), transactions);
        await(() -> commands.current(actor, caseId).available());
        FollowUpCommands.Current current = commands.current(actor, caseId);
        UUID operation = UUID.randomUUID();
        commands.submit(actor, caseId, operation, current.waitId(), current.version(), true, null, null, "Weiter bearbeiten");
        new FollowUpCommandWorkers(engine, commands).apply(engine, job(FollowUpCommandWorkers.APPLY));
        StaffDecisionService decision = new StaffDecisionService(access, workflow, caseData, reviewStore, decisions, taskGateway(), transactions);
        new StaffDecisionWorkers(engine, decision).progress(engine, job(StaffDecisionWorkers.PROGRESS));
        await(() -> tasks().size() == 1 && "SWork".equals(tasks().getFirst().getElementId()));
        assertEquals("IN_PROGRESS", status());
        assertEquals("OPEN", database.queryForObject("SELECT state FROM pf_staff_answer_requirement", String.class));
        service().send(actor, caseId, "after-direct-wait", "Noch eine Frage?", true);
        new StaffMessageWorkers(engine, service()).question(engine, job("propertyflow.staff-question.publish"));
        replyWorkers().register(engine, job(TenantReplyWorkers.REGISTER));
        assertEquals("WAITING_FOR_TENANT", status());
        assertEquals("WAITING", replies.active(caseId, leader).orElseThrow().state());
        assertEquals(2, count("pf_reply_call"));
    }

    @Test
    void obsoleteStaffContinuationHandsPendingReasonToActualReplyAndDoesNotStartAnotherStaffCall() throws Exception {
        question();
        UUID previous = database.queryForObject("SELECT id FROM pf_staff_continuation WHERE NOT superseded", UUID.class);
        TenantReplyStore.Call reply = replies.active(caseId, leader).orElseThrow();
        UUID signal = UUID.randomUUID();
        database.update("""
                INSERT INTO pf_case_review_signal (id, case_id, source_type, source_ref, reason, leading_instance_key, continuation_id, state)
                VALUES (?, ?, 'TEST', ?, 'HUMAN_DECISION_REQUIRED', ?, ?, 'CONFIRMED')
                """, signal, caseId, UUID.randomUUID(), leader, previous);
        StaffReviewWorkers staff = new StaffReviewWorkers(engine, reviewService);
        for (int index = 0; index < 2; index++) {
            engine.newModifyProcessInstanceCommand(leader).activateElement("StaffContinuationClaim")
                    .withVariable("staffEntryContextRef", previous.toString()).send().join();
            staff.claim(engine, job("propertyflow.staff.claim"));
            staff.reconcileContinuation(engine, job("propertyflow.staff.reconcile-continuation"));
        }
        assertEquals("ACTIVE", engine.newProcessInstanceGetRequest(reply.child()).send().join().getState().name());
        assertEquals(1, count("pf_staff_continuation"));
        assertEquals("PENDING", database.queryForObject("SELECT state FROM pf_case_review_signal WHERE id = ?", String.class, signal));
        assertEquals("WAITING_FOR_TENANT", status());
        new ReviewSignalDispatcher(reviewStore, engine).dispatch();
        staff.check(engine, job("propertyflow.review.check"));
        replyWorkers().wakeReview(engine, job(TenantReplyWorkers.WAKE_REVIEW));
        replyWorkers().prepareReview(engine, job(TenantReplyWorkers.PREPARE_REVIEW));
        staff.claim(engine, job("propertyflow.staff.claim"));
        staff.register(engine, job("propertyflow.staff.register"));
        staff.startReview(engine, job("propertyflow.staff.start-review"));
        await(() -> tasks().size() == 1);
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals(2, count("pf_staff_continuation"));
    }

    @Test
    void closingReplyAfterSuccessorCheckKeepsReconciliationPendingUntilTheProtectedClosure() throws Exception {
        question();
        UUID previous = database.queryForObject("SELECT id FROM pf_staff_continuation WHERE NOT superseded", UUID.class);
        TenantReplyStore.Call reply = replies.active(caseId, leader).orElseThrow();
        StaffReviewService.ExternalOwner snapshot = reviewService.externalOwner(caseId, leader, previous);
        StaffReviewWorkers staff = new StaffReviewWorkers(engine, reviewService);
        engine.newModifyProcessInstanceCommand(leader).activateElement("StaffContinuationClaim")
                .withVariable("staffEntryContextRef", previous.toString()).send().join();
        staff.claim(engine, job("propertyflow.staff.claim"));
        ActivatedJob reconciliation = job("propertyflow.staff.reconcile-continuation");
        CaseDataService data = caseDataService();
        requestReplyClosure(data);
        ReplyClosureWorkers closing = closureWorker(data);
        closing.check(engine, job(ReplyClosureWorkers.CHECK));
        assertThrows(IllegalStateException.class, () -> reviewService.claimExternal(caseId, leader, previous, snapshot));
        staff.reconcileContinuation(engine, reconciliation);
        ActivatedJob retry = job("propertyflow.staff.reconcile-continuation");
        assertEquals(reconciliation.getKey(), retry.getKey());
        assertTrue(retry.getRetries() > 0);
        assertEquals("WAITING_FOR_TENANT", status());
        assertEquals("ACTIVE", engine.newProcessInstanceGetRequest(leader).send().join().getState().name());
        closing.signal(engine, job(ReplyClosureWorkers.SIGNAL));
        closeReservedReply(reply);
        await(() -> "COMPLETED".equals(engine.newProcessInstanceGetRequest(leader).send().join().getState().name()));
        assertEquals("CLOSED", status());
        assertEquals(1, count("pf_case_closure"));
    }

    @Test
    void directClosureOfReplyReservesTheExactCallThenClosesWithoutNewMessageOrMail() throws Exception {
        question();
        TenantReplyStore.Call call = replies.active(caseId, leader).orElseThrow();
        int mails = count("pf_case_mail_order");
        CaseDataService data = caseDataService();
        requestReplyClosure(data);
        ReplyClosureWorkers worker = closureWorker(data);
        worker.check(engine, job(ReplyClosureWorkers.CHECK));
        assertEquals("CLOSING", replies.get(call.id()).state());
        assertEquals("WAITING_FOR_TENANT", status());
        assertEquals("PENDING", data.read(actor, caseId, "reply-close").state());
        assertEquals("OBSOLETE", replyService().evaluate(caseId, call.entry(), leader, call.child(), call.tenant()).state());
        UUID message = database.queryForObject("SELECT id FROM pf_staff_message", UUID.class);
        assertThrows(CaseActionException.class, () -> data.clearAnswer(actor, caseId, "too-late", version(), message));
        assertThrows(CaseActionException.class, () -> service().send(actor, caseId, "too-late-question", "Neue Frage?", true));
        worker.signal(engine, job(ReplyClosureWorkers.SIGNAL));
        closeReservedReply(call);
        await(() -> "COMPLETED".equals(engine.newProcessInstanceGetRequest(leader).send().join().getState().name()));
        assertEquals("CLOSED", status());
        assertEquals("APPLIED", data.read(actor, caseId, "reply-close").state());
        assertEquals(1, count("pf_case_closure"));
        assertEquals(mails, count("pf_case_mail_order"));
        assertEquals(1, count("pf_staff_message"));
        assertEquals(1, count("pf_staff_message_mail_order"));
        assertEquals("CASE_CLOSED", database.queryForObject("SELECT resolution_type FROM pf_staff_answer_requirement", String.class));
    }

    @Test
    void newInformationBeforeReplyClosureAcceptanceLeavesTheWaitActive() throws Exception {
        question();
        CaseDataService data = caseDataService();
        requestReplyClosure(data);
        answer("Eine zusätzliche Information.");
        closureWorker(data).check(engine, job(ReplyClosureWorkers.CHECK));
        assertEquals("REJECTED", data.read(actor, caseId, "reply-close").state());
        assertEquals("WAITING", replies.active(caseId, leader).orElseThrow().state());
        assertEquals(0, count("pf_case_closure"));
    }

    @Test
    void newInformationAfterReplyClosureAcceptanceCreatesOneCurrentReviewInsteadOfClosing() throws Exception {
        question();
        TenantReplyStore.Call call = replies.active(caseId, leader).orElseThrow();
        CaseDataService data = caseDataService();
        requestReplyClosure(data);
        ReplyClosureWorkers worker = closureWorker(data);
        worker.check(engine, job(ReplyClosureWorkers.CHECK));
        answer("Es gibt jetzt auch einen Wasserschaden.");
        worker.signal(engine, job(ReplyClosureWorkers.SIGNAL));
        closeReservedReply(call);
        finishClosureConflict();
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals("REJECTED", data.read(actor, caseId, "reply-close").state());
        assertEquals(0, count("pf_case_closure"));
        assertEquals("OPEN", database.queryForObject("SELECT state FROM pf_staff_answer_requirement", String.class));
        StaffDecisionService decision = new StaffDecisionService(access, workflow, caseData, reviewStore, decisions, taskGateway(), transactions);
        StaffTask task = decision.current(actor, caseId);
        decision.continueWork(actor, caseId, UUID.randomUUID(), Long.parseLong(task.taskKey()), task.version(), task.reviewToken(), "");
        new StaffDecisionWorkers(engine, decision).progress(engine, job("propertyflow.staff.progress"));
        await(() -> tasks().stream().anyMatch(current -> "SWork".equals(current.getElementId())));
        service().send(actor, caseId, "question-after-conflict", "Welcher neue Termin passt?", true);
        new StaffMessageWorkers(engine, service()).question(engine, job("propertyflow.staff-question.publish"));
        replyWorkers().register(engine, job(TenantReplyWorkers.REGISTER));
        assertEquals("WAITING_FOR_TENANT", status());
        assertEquals(2, count("pf_reply_call"));
        assertEquals("WAITING", replies.active(caseId, leader).orElseThrow().state());
    }

    @Test
    void undeliveredMandatoryReasonAfterReplyReservationPreventsClosure() throws Exception {
        question();
        TenantReplyStore.Call call = replies.active(caseId, leader).orElseThrow();
        CaseDataService data = caseDataService();
        requestReplyClosure(data);
        ReplyClosureWorkers worker = closureWorker(data);
        worker.check(engine, job(ReplyClosureWorkers.CHECK));
        database.update("""
                INSERT INTO pf_case_review_signal (id, case_id, source_type, source_ref, reason)
                VALUES (?, ?, 'TEST', ?, 'HUMAN_DECISION_REQUIRED')
                """, UUID.randomUUID(), caseId, UUID.randomUUID());
        worker.signal(engine, job(ReplyClosureWorkers.SIGNAL));
        closeReservedReply(call);
        finishClosureConflict();
        assertEquals(0, count("pf_case_closure"));
        assertEquals("STAFF_REVIEW_REQUIRED", status());
    }

    @Test
    void revokedRoleAfterReservationRequiresReviewAndCannotClose() throws Exception {
        question();
        TenantReplyStore.Call call = replies.active(caseId, leader).orElseThrow();
        CaseDataService data = caseDataService();
        requestReplyClosure(data);
        ReplyClosureWorkers worker = closureWorker(data);
        worker.check(engine, job(ReplyClosureWorkers.CHECK));
        database.update("UPDATE pf_staff_account SET staff_role = FALSE WHERE id = ?", actor.accountId());
        worker.signal(engine, job(ReplyClosureWorkers.SIGNAL));
        closeReservedReply(call);
        finishClosureConflict();
        assertEquals(0, count("pf_case_closure"));
        assertEquals("REJECTED", database.queryForObject("SELECT state FROM pf_case_data_command WHERE operation_id = 'reply-close'", String.class));
    }

    @Test
    void reservedClosureIsDurablyDeliveredAndLostCompletionDoesNotDuplicateItsEffects() throws Exception {
        question();
        TenantReplyStore.Call call = replies.active(caseId, leader).orElseThrow();
        CaseDataService data = caseDataService();
        requestReplyClosure(data);
        ReplyClosureWorkers worker = closureWorker(data);
        ActivatedJob checking = job(ReplyClosureWorkers.CHECK);
        UUID id = UUID.fromString(checking.getVariablesAsMap().get("replyClosureCommandRef").toString());
        assertEquals("ACCEPTED", data.replyClosureState(id, leader, "<default>"));
        assertEquals("ACCEPTED", data.replyClosureState(id, leader, "<default>"));
        assertEquals("ACCEPTED", data.rejectReplyClosure(id, leader, "<default>"));
        worker.dispatch();
        ActivatedJob closing = job(CaseClosureWorkers.CLOSE);
        awaitReplyTermination(call);
        assertEquals("CLOSED", clarificationClosures.close(caseId, leader, call.child(), id).state());
        new CaseClosureWorkers(engine, clarificationClosures).close(engine, closing);
        await(() -> "COMPLETED".equals(engine.newProcessInstanceGetRequest(leader).send().join().getState().name()));
        assertEquals(1, count("pf_case_closure"));
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_case_status_event WHERE to_status = 'CLOSED'", Integer.class));
    }

    private void requestReplyClosure(CaseDataService data) {
        ClosureApproval approval = data.replyClosure(actor, caseId);
        assertTrue(approval.available());
        assertEquals("PENDING", data.closeReply(actor, caseId, "reply-close", approval.version(), approval.reviewToken()).state());
    }

    private ReplyClosureWorkers closureWorker(CaseDataService data) {
        return new ReplyClosureWorkers(engine, data, caseData, new ReplyClosureStore(database), replies);
    }

    private long version() {
        return database.queryForObject("SELECT version FROM pf_case", Long.class);
    }

    private void closeReservedReply(TenantReplyStore.Call call) throws Exception {
        ActivatedJob closing = job(CaseClosureWorkers.CLOSE);
        awaitReplyTermination(call);
        new CaseClosureWorkers(engine, clarificationClosures).close(engine, closing);
    }

    private void awaitReplyTermination(TenantReplyStore.Call call) throws Exception {
        await(() -> "TERMINATED".equals(engine.newProcessInstanceGetRequest(call.child()).send().join().getState().name())
                && "TERMINATED".equals(engine.newElementInstanceGetRequest(call.parent()).send().join().getState().name()));
    }

    private void finishClosureConflict() throws Exception {
        StaffReviewWorkers worker = new StaffReviewWorkers(engine, reviewService);
        worker.claim(engine, job("propertyflow.staff.claim"));
        worker.register(engine, job("propertyflow.staff.register"));
        worker.startReview(engine, job("propertyflow.staff.start-review"));
        await(() -> tasks().size() == 1);
    }

    private CaseDataService caseDataService() {
        return new CaseDataService(access, workflow, caseData,
                new CamundaCaseGateway(new StaticListableBeanFactory(Map.of("engine", engine)).getBeanProvider(CamundaClient.class), true),
                urgencyService, new AnswerRequirementStore(database), new StaffTakeoverStore(database), clarificationClosures,
                new DirectFollowUpStore(database, new CaseWaitStore(database), new StaffTakeoverStore(database)), transactions);
    }

    private void clearAnswer(UUID message, String operation) throws Exception {
        CaseDataService data = caseDataService();
        long version = database.queryForObject("SELECT version FROM pf_case WHERE id = ?", Long.class, caseId);
        data.clearAnswer(actor, caseId, operation, version, message);
        new CaseDataWorkers(engine, data).clearAnswer(engine, job("propertyflow.answer.clear"));
        assertEquals("APPLIED", data.read(actor, caseId, operation).state());
        assertEquals("APPLIED", data.clearAnswer(actor, caseId, operation, version, message).state());
    }

    @Test
    void clearingLastAnswerRequirementCreatesOneWorkTaskWithoutModelEvaluation() throws Exception {
        question();
        UUID message = messages.find(caseId, "Frage").orElseThrow().id();
        clearAnswer(message, "Aufhebung");
        assertEquals("IN_PROGRESS", status());
        assertEquals("MANUAL", messages.history(caseId).getFirst().answerResolution());
        assertTrue(messages.history(caseId).getFirst().answerResolvedAt() != null);
        assertEquals(1, count("pf_answer_cancellation"));
        AnswerCancellationWorkers cancellation = new AnswerCancellationWorkers(new AnswerRequirementStore(database), reviewService, engine);
        cancellation.dispatch();
        cancellation.continueWork(engine, job("propertyflow.answer.continue"));
        StaffReviewWorkers reviews = new StaffReviewWorkers(engine, reviewService);
        reviews.claim(engine, job("propertyflow.staff.claim"));
        reviews.register(engine, job("propertyflow.staff.register"));
        await(() -> tasks().stream().anyMatch(task -> "SWork".equals(task.getElementId())));
        assertEquals(0, count("pf_conversation_evaluation"));
        assertEquals(1, tasks().size());
        cancellation.dispatch();
        assertEquals(1, tasks().size());
    }

    @Test
    void clearingOneOfTwoRequirementsKeepsWaitingAndPreservesOtherQuestion() throws Exception {
        question();
        long child = replies.active(caseId, leader).orElseThrow().child();
        await(() -> !engine.newMessageSubscriptionSearchRequest().filter(filter -> filter.processInstanceKey(child)
                .messageName(CamundaStaffMessageGateway.REPLY_MESSAGE)).send().join().items().isEmpty());
        service().send(actor, caseId, "Zweite Frage", "Welche Telefonnummer dürfen wir nutzen?", true);
        new StaffMessageWorkers(engine, service()).publish(engine, job(StaffMessageWorkers.PUBLISH));
        int events = count("pf_case_status_event");
        clearAnswer(messages.find(caseId, "Frage").orElseThrow().id(), "Eine aufheben");
        assertEquals("WAITING_FOR_TENANT", status());
        assertEquals(events, count("pf_case_status_event"));
        assertEquals(child, replies.active(caseId, leader).orElseThrow().child());
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_staff_answer_requirement WHERE state = 'OPEN'", Integer.class));
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_staff_answer_requirement WHERE state = 'CANCELLED'", Integer.class));
        assertEquals(0, count("pf_conversation_evaluation"));
    }

    @Test
    void roleRevocationBeforeCancellationCommitPreservesQuestionAndWait() throws Exception {
        question();
        CaseDataService data = caseDataService();
        data.clearAnswer(actor, caseId, "Rolle entzogen", database.queryForObject("SELECT version FROM pf_case", Long.class),
                messages.find(caseId, "Frage").orElseThrow().id());
        ActivatedJob action = job("propertyflow.answer.clear");
        database.update("UPDATE pf_staff_account SET staff_role = FALSE WHERE id = ?", actor.accountId());
        new CaseDataWorkers(engine, data).clearAnswer(engine, action);
        assertEquals("WAITING_FOR_TENANT", status());
        assertEquals("OPEN", database.queryForObject("SELECT state FROM pf_staff_answer_requirement", String.class));
        assertEquals(0, count("pf_answer_cancellation"));
        assertEquals("REJECTED", caseData.latest(caseId, "Rolle entzogen").orElseThrow().state());
    }

    @Test
    void cancellationInvalidatesRunningEvaluationAndCannotBeChangedIntoAnAnswer() throws Exception {
        question();
        answered = true;
        StaticListableBeanFactory beans = new StaticListableBeanFactory(Map.of("engine", engine));
        TenantMessageService tenant = new TenantMessageService(tenantMessages, workflow, caseData, links,
                new CamundaCaseGateway(beans.getBeanProvider(CamundaClient.class), true),
                new CamundaTenantMessageGateway(beans.getBeanProvider(CamundaClient.class), true), transactions, accountId -> java.util.Optional.empty());
        tenant.send(accepted.personalAccessToken(), UUID.randomUUID(), "Montag um 10 Uhr.");
        new TenantMessageWorkers(engine, tenant).identify(engine, job(TenantMessageWorkers.IDENTIFY));
        ConversationEvaluationWorkers worker = new ConversationEvaluationWorkers(engine, central());
        worker.evaluate(engine, job(ConversationEvaluationWorkers.EVALUATE));
        ActivatedJob outdated = job(ConversationEvaluationWorkers.APPLY);
        clearAnswer(messages.find(caseId, "Frage").orElseThrow().id(), "Während Auswertung");
        worker.apply(engine, outdated);
        assertEquals("CANCELLED", database.queryForObject("SELECT state FROM pf_staff_answer_requirement", String.class));
        assertEquals("MANUAL", database.queryForObject("SELECT resolution_type FROM pf_staff_answer_requirement", String.class));
        assertEquals("IN_PROGRESS", status());
        assertEquals(0, count("pf_evaluation_delivery"));
        assertEquals("OBSOLETE", replyService().review(replies.active(caseId, leader).orElseThrow().id()).state());
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
