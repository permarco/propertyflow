package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.access.contract.StaffAccountAccess;
import com.pertegato.propertyflow.access.contract.SubmissionContext;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.caseprocessing.contract.CaseCreation;
import com.pertegato.propertyflow.caseprocessing.contract.CreateCaseCommand;
import com.pertegato.propertyflow.caseprocessing.contract.Urgency;
import com.pertegato.propertyflow.caseprocessing.persistence.AnswerRequirementStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseDataStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWaitStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.StartOrder;
import com.pertegato.propertyflow.caseprocessing.persistence.DirectFollowUpStore;
import com.pertegato.propertyflow.caseprocessing.persistence.MailDeliveryStore;
import com.pertegato.propertyflow.caseprocessing.persistence.MailDeliveryStore.MailStart;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffReviewStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffTakeoverStore;
import com.pertegato.propertyflow.caseprocessing.service.CaseClosureService;
import com.pertegato.propertyflow.caseprocessing.service.CaseDataService;
import com.pertegato.propertyflow.caseprocessing.service.CaseMailService;
import com.pertegato.propertyflow.caseprocessing.service.CaseMailService.MailOutcome;
import com.pertegato.propertyflow.caseprocessing.service.CaseUrgencyService;
import com.pertegato.propertyflow.caseprocessing.service.CaseWorkflowService;
import com.pertegato.propertyflow.caseprocessing.service.StaffReviewService;
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
class StaffReviewCamundaTest {

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
    private MailDeliveryStore mails;

    @Autowired
    private CaseMailService mailService;

    @Autowired
    private JdbcTemplate database;

    @Autowired
    private CaseDataStore memoStore;

    @Autowired
    private StaffAccountAccess staffAccess;

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private CaseClosureService clarificationClosures;

    @Autowired
    private CaseUrgencyService urgencyService;

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
        long definitionKey = engine.newDeployResourceCommand().addResourceFromClasspath("workflow/review-case.bpmn")
                .addResourceFromClasspath("workflow/initial-automatic.bpmn").addResourceFromClasspath("workflow/review-staff.bpmn")
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
    void obsoleteContinuationEndsOnlyItsDuplicatePathAndTransfersTheReasonToTheLiveReview() throws Exception {
        mailProblem();
        new ReviewSignalDispatcher(reviewStore, engine).dispatch();
        reviews.check(engine, job("propertyflow.review.check"));
        reviews.interruptAutomatic(engine, job("propertyflow.review.interrupt-automatic"));
        finishReview();
        long task = tasks().getFirst().getUserTaskKey();
        UUID current = database.queryForObject("SELECT id FROM pf_staff_continuation WHERE NOT superseded", UUID.class);
        UUID obsolete = UUID.randomUUID();
        database.update("""
                INSERT INTO pf_staff_continuation (id, case_id, leading_instance_key, automatic_call_ref, case_version, superseded, claim_element_key)
                SELECT ?, case_id, leading_instance_key, automatic_call_ref, case_version, TRUE, 123 FROM pf_staff_continuation WHERE id = ?
                """, obsolete, current);
        UUID signal = UUID.randomUUID();
        database.update("""
                INSERT INTO pf_case_review_signal (id, case_id, source_type, source_ref, reason, leading_instance_key, continuation_id, state)
                VALUES (?, ?, 'TEST', ?, 'HUMAN_DECISION_REQUIRED', ?, ?, 'CONFIRMED')
                """, signal, caseId, UUID.randomUUID(), leader, obsolete);
        for (int index = 0; index < 2; index++) {
            engine.newModifyProcessInstanceCommand(leader).activateElement("StaffContinuationClaim")
                    .withVariable("staffEntryContextRef", obsolete.toString()).send().join();
            reviews.claim(engine, job("propertyflow.staff.claim"));
            reviews.reconcileContinuation(engine, job("propertyflow.staff.reconcile-continuation"));
        }
        assertEquals(current, database.queryForObject("SELECT continuation_id FROM pf_case_review_signal WHERE id = ?", UUID.class, signal));
        assertEquals("PENDING", database.queryForObject("SELECT state FROM pf_case_review_signal WHERE id = ?", String.class, signal));
        new ReviewSignalDispatcher(reviewStore, engine).dispatch();
        reviews.check(engine, job("propertyflow.review.check"));
        reviews.merge(engine, job("propertyflow.review.merge"));
        assertEquals(task, tasks().getFirst().getUserTaskKey());
        assertEquals(1, tasks().size());
        assertEquals("ACTIVE", engine.newProcessInstanceGetRequest(leader).send().join().getState().name());
        assertEquals(2, count("pf_case_status_event"));
        assertEquals(2, count("pf_case_mail_order"));
    }

    @Test
    void duplicateCurrentContinuationMergesWithoutStartingAnotherStaffCall() throws Exception {
        mailProblem();
        new ReviewSignalDispatcher(reviewStore, engine).dispatch();
        reviews.check(engine, job("propertyflow.review.check"));
        reviews.interruptAutomatic(engine, job("propertyflow.review.interrupt-automatic"));
        finishReview();
        long task = tasks().getFirst().getUserTaskKey();
        engine.newModifyProcessInstanceCommand(leader).activateElement("StaffContinuationClaim").send().join();
        reviews.claim(engine, job("propertyflow.staff.claim"));
        reviews.mergeContinuation(engine, job("propertyflow.staff.merge-continuation"));
        assertEquals(task, tasks().getFirst().getUserTaskKey());
        assertEquals(1, tasks().size());
        assertEquals("ACTIVE", engine.newProcessInstanceGetRequest(leader).send().join().getState().name());
        assertEquals(1, count("pf_staff_continuation"));
    }

    @Test
    void unconfirmedStaffEntryKeepsTheDuplicateJobPendingUntilTheSuccessorIsReady() throws Exception {
        mailProblem();
        new ReviewSignalDispatcher(reviewStore, engine).dispatch();
        reviews.check(engine, job("propertyflow.review.check"));
        reviews.interruptAutomatic(engine, job("propertyflow.review.interrupt-automatic"));
        reviews.prepareStaff(engine, job("propertyflow.review.prepare-staff"));
        reviews.claim(engine, job("propertyflow.staff.claim"));
        reviews.register(engine, job("propertyflow.staff.register"));
        ActivatedJob startingReview = job("propertyflow.staff.start-review");
        engine.newModifyProcessInstanceCommand(leader).activateElement("StaffContinuationClaim").send().join();
        ActivatedJob duplicate = job("propertyflow.staff.claim");
        reviews.claim(engine, duplicate);
        assertTrue(tasks().isEmpty());
        assertEquals("UNDER_CLARIFICATION", status());
        assertEquals(1, count("pf_staff_continuation"));
        reviews.startReview(engine, startingReview);
        ActivatedJob retry = job("propertyflow.staff.claim");
        assertEquals(duplicate.getKey(), retry.getKey());
        assertTrue(retry.getRetries() > 0);
        reviews.claim(engine, retry);
        reviews.mergeContinuation(engine, job("propertyflow.staff.merge-continuation"));
        await(() -> tasks().size() == 1);
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals(2, count("pf_case_status_event"));
        assertEquals(1, count("pf_staff_continuation"));
    }

    @Test
    void mailProblemInterruptsEvenAnIncidentAndStartsExactlyOneRealUserTask() throws Exception {
        ActivatedJob triage = job("propertyflow-test-unimplemented-triage");
        engine.newFailCommand(triage.getKey()).retries(0).errorMessage("TEST_BLOCKED_ANALYSIS").send().join();
        await(() -> engine.newIncidentSearchRequest().filter(filter -> filter.processInstanceKey(triage.getProcessInstanceKey()))
                .send().join().items().size() == 1);
        mailProblem();
        new ReviewSignalDispatcher(reviewStore, engine).dispatch();
        reviews.check(engine, job("propertyflow.review.check"));
        assertEquals("UNDER_CLARIFICATION", status());
        assertTrue(database.queryForObject("SELECT effects_blocked FROM pf_automatic_entry", Boolean.class));
        reviews.interruptAutomatic(engine, job("propertyflow.review.interrupt-automatic"));
        finishReview();
        await(() -> "TERMINATED".equals(engine.newProcessInstanceGetRequest(triage.getProcessInstanceKey()).send().join().getState().name()));
        List<UserTask> tasks = tasks();
        assertEquals(1, tasks.size());
        assertEquals("SReview", tasks.getFirst().getElementId());
        assertEquals(leader, tasks.getFirst().getRootProcessInstanceKey());
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals(2, count("pf_case_mail_order"));
        assertEquals(1, count("pf_case_process"));
        assertEquals("CONSUMED", database.queryForObject("SELECT state FROM pf_case_review_signal", String.class));
    }

    @Test
    void parallelSignalsStayLocalAndLateRepeatKeepsSameUserTask() throws Exception {
        mailProblem();
        // A second independent source arrives while the first event subprocess is still active.
        UUID second = UUID.randomUUID();
        database.update("""
                INSERT INTO pf_case_review_signal (id, case_id, source_type, source_ref, reason, leading_instance_key)
                VALUES (?, ?, 'MAIL', ?, 'MAIL_DELIVERY_REQUIRES_REVIEW', ?)
                """, second, caseId, UUID.randomUUID(), leader);
        new ReviewSignalDispatcher(reviewStore, engine).dispatch();
        ActivatedJob firstCheck = job("propertyflow.review.check");
        ActivatedJob secondCheck = job("propertyflow.review.check");
        assertFalse(firstCheck.getVariablesAsMap().get("reviewSignalRef").equals(secondCheck.getVariablesAsMap().get("reviewSignalRef")));
        reviews.check(engine, firstCheck);
        reviews.check(engine, secondCheck);
        ActivatedJob firstOther = job("propertyflow.review.interrupt-automatic");
        ActivatedJob lateOther = job("propertyflow.review.interrupt-automatic");
        reviews.interruptAutomatic(engine, firstOther);
        finishReview();
        long taskKey = tasks().getFirst().getUserTaskKey();
        reviews.interruptAutomatic(engine, lateOther);
        assertEquals(taskKey, tasks().getFirst().getUserTaskKey());
        assertEquals(1, count("pf_staff_continuation"));
        assertEquals(2, count("pf_case_mail_order"));
        // Use a fresh message id for a genuinely new source, rather than waiting for broker deduplication TTL.
        UUID third = UUID.randomUUID();
        database.update("""
                INSERT INTO pf_case_review_signal (id, case_id, source_type, source_ref, reason, leading_instance_key)
                VALUES (?, ?, 'MAIL', ?, 'MAIL_DELIVERY_REQUIRES_REVIEW', ?)
                """, third, caseId, UUID.randomUUID(), leader);
        new ReviewSignalDispatcher(reviewStore, engine).dispatch();
        reviews.check(engine, job("propertyflow.review.check"));
        reviews.merge(engine, job("propertyflow.review.merge"));
        assertEquals(taskKey, tasks().getFirst().getUserTaskKey());
        assertEquals(2, count("pf_case_mail_order"));
    }

    private void finishReview() throws Exception {
        reviews.prepareStaff(engine, job("propertyflow.review.prepare-staff"));
        reviews.claim(engine, job("propertyflow.staff.claim"));
        reviews.register(engine, job("propertyflow.staff.register"));
        ActivatedJob status = job("propertyflow.staff.start-review");
        reviews.startReview(engine, status);
        // Replaying the committed service operation cannot create another event or task.
        reviewService.startReview(caseId, leader, UUID.fromString((String) status.getVariablesAsMap().get("entryContextRef")),
                status.getProcessInstanceKey());
        await(() -> tasks().size() == 1);
    }

    @Test
    void staffMemoUsesNonInterruptingSideProcessAndPreservesReviewTask() throws Exception {
        mailProblem();
        new ReviewSignalDispatcher(reviewStore, engine).dispatch();
        reviews.check(engine, job("propertyflow.review.check"));
        reviews.interruptAutomatic(engine, job("propertyflow.review.interrupt-automatic"));
        finishReview();
        long taskKey = tasks().getFirst().getUserTaskKey();
        UUID actorId = UUID.randomUUID();
        database.update("""
                INSERT INTO pf_staff_account (id, name, email, active, staff_role, account_administration)
                VALUES (?, 'Review test', ?, TRUE, TRUE, FALSE)
                """, actorId, actorId + "@example.test");
        CamundaCaseGateway gateway = new CamundaCaseGateway(new StaticListableBeanFactory(Map.of("camunda", engine))
                .getBeanProvider(CamundaClient.class), true);
        CaseDataService memos = new CaseDataService(staffAccess, workflow, memoStore, gateway, urgencyService, new AnswerRequirementStore(database), new StaffTakeoverStore(database), clarificationClosures,
                new DirectFollowUpStore(database, new CaseWaitStore(database), new StaffTakeoverStore(database)), transactions);
        UUID operation = UUID.randomUUID();
        WebAccountContext actor = new WebAccountContext(actorId);
        assertEquals("PENDING", memos.submit(actor, caseId, operation, 2, "Vertrauliche interne Abklärung").state());
        CaseDataWorkers data = new CaseDataWorkers(engine, memos);
        ActivatedJob saving = job("propertyflow.case-data.apply");
        assertFalse(saving.getVariables().contains("Vertrauliche"));
        data.apply(engine, saving);
        UUID commandId = UUID.fromString((String) saving.getVariablesAsMap().get("caseDataCommandRef"));
        memos.apply(commandId, leader, "<default>");
        assertEquals("APPLIED", memos.read(actor, caseId, operation).state());
        assertEquals(1, count("pf_internal_memo"));
        assertEquals(taskKey, tasks().getFirst().getUserTaskKey());
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals(2, count("pf_case_mail_order"));
    }

    @Test
    void criticalManualUrgencyUsesCaseDataJobThenIndependentReviewEntry() throws Exception {
        UUID actorId = UUID.randomUUID();
        database.update("""
                INSERT INTO pf_staff_account (id, name, email, active, staff_role, account_administration)
                VALUES (?, 'Urgency test', ?, TRUE, TRUE, FALSE)
                """, actorId, actorId + "@example.test");
        CamundaCaseGateway gateway = new CamundaCaseGateway(new StaticListableBeanFactory(Map.of("camunda", engine))
                .getBeanProvider(CamundaClient.class), true);
        CaseDataService dataService = new CaseDataService(staffAccess, workflow, memoStore, gateway, urgencyService, new AnswerRequirementStore(database), new StaffTakeoverStore(database), clarificationClosures,
                new DirectFollowUpStore(database, new CaseWaitStore(database), new StaffTakeoverStore(database)), transactions);
        UUID operation = UUID.randomUUID();
        dataService.submitUrgency(new WebAccountContext(actorId), caseId, operation, 1, Urgency.CRITICAL, "Interner Entscheid");
        CaseDataWorkers data = new CaseDataWorkers(engine, dataService);
        ActivatedJob change = job("propertyflow.case-data.apply");
        assertFalse(change.getVariables().contains("Interner Entscheid"));
        data.apply(engine, change);
        assertEquals("UNDER_CLARIFICATION", status());
        assertEquals("CRITICAL", database.queryForObject("SELECT urgency FROM pf_case", String.class));
        new ReviewSignalDispatcher(reviewStore, engine).dispatch();
        reviews.check(engine, job("propertyflow.review.check"));
        reviews.interruptAutomatic(engine, job("propertyflow.review.interrupt-automatic"));
        finishReview();
        assertEquals(1, tasks().size());
        assertEquals("STAFF_REVIEW_REQUIRED", status());
        assertEquals(1, count("pf_urgency_mail_order"));
        assertEquals(2, count("pf_case_mail_order"));
    }

    private void mailProblem() {
        mails.discoverOrders();
        MailStart mail = mails.claimStart(900, "<default>").orElseThrow();
        mailService.prepare(mail.id(), 901, 900, "<default>");
        database.update("UPDATE pf_case_access SET revoked_at = CURRENT_TIMESTAMP");
        assertEquals(MailOutcome.REVIEW, mailService.send(mail.id(), 901));
        mailService.recordProblem(mail.id(), 901);
    }

    private ActivatedJob job(String type) throws Exception {
        for (int attempt = 0; attempt < 200; attempt++) {
            List<ActivatedJob> jobs = engine.newActivateJobsCommand().jobType(type).maxJobsToActivate(1)
                    .timeout(Duration.ofMinutes(2)).send().join().getJobs();
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
