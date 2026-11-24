package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.access.contract.SubmissionContext;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.access.web.StaffWebSession;
import com.pertegato.propertyflow.caseprocessing.contract.CaseAccepted;
import com.pertegato.propertyflow.caseprocessing.contract.CaseActionException;
import com.pertegato.propertyflow.caseprocessing.contract.CaseCreation;
import com.pertegato.propertyflow.caseprocessing.contract.CaseReading;
import com.pertegato.propertyflow.caseprocessing.contract.CreateCaseCommand;
import com.pertegato.propertyflow.caseprocessing.contract.StaffCaseListing;
import com.pertegato.propertyflow.caseprocessing.contract.StaffCaseQuery;
import com.pertegato.propertyflow.caseprocessing.contract.StaffDecisionAction;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants.UserTask;
import com.pertegato.propertyflow.caseprocessing.delivery.CaseProcessGateway;
import com.pertegato.propertyflow.caseprocessing.delivery.StaffMessageGateway;
import com.pertegato.propertyflow.caseprocessing.delivery.StaffTaskGateway;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.Entry;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.StartOrder;
import com.pertegato.propertyflow.caseprocessing.persistence.MailDeliveryStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffDecisionStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffMessageStore;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "propertyflow.workflow.enabled=false")
@ActiveProfiles("propertyflow-backend")
@Import(StaffMessagePostgresTest.Configuration.class)
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_DATABASE_URL", matches = ".+")
class StaffMessagePostgresTest {

    @Autowired
    private CaseCreation creation;
    @Autowired
    private CaseReading tenant;
    @Autowired
    private StaffCaseListing listing;
    @Autowired
    private CaseWorkflowStore workflow;
    @Autowired
    private CaseWorkflowService initial;
    @Autowired
    private StaffMessageService service;
    @Autowired
    private CaseDataService memos;
    @Autowired
    private StaffReviewService reviews;
    @Autowired
    private StaffDecisionStore decisions;
    @Autowired
    private MailDeliveryStore deliveries;
    @Autowired
    private StaffMessageStore store;
    @Autowired
    private Engine availability;
    @Autowired
    private JdbcTemplate database;
    @Autowired
    private WebApplicationContext web;

    private CaseAccepted accepted;
    private WebAccountContext actor;
    private MockMvc browser;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("PROPERTYFLOW_TEST_DATABASE_URL"));
        properties.add("spring.datasource.username", () -> "propertyflow_test");
        properties.add("spring.datasource.password", () -> "propertyflow_test");
        properties.add("propertyflow.links.encryption-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
    }

    @BeforeEach
    void setup() {
        assertEquals("propertyflow_test", database.queryForObject("SELECT current_database()", String.class));
        database.execute("TRUNCATE pf_case CASCADE");
        actor = new WebAccountContext(UUID.randomUUID());
        database.update("""
                INSERT INTO pf_staff_account (id, name, email, active, staff_role, account_administration)
                VALUES (?, 'Mitarbeiter', ?, TRUE, TRUE, FALSE)
                """,
                actor.accountId(), actor.accountId() + "@example.test");
        accepted = creation.create(new SubmissionContext.Guest(UUID.randomUUID()),
                new CreateCaseCommand("memo", "Heizung", "Wohnung", "Kalt", "tenant@example.test"));
        StartOrder order = workflow.claim(100, "<default>", Duration.ofMinutes(2)).orElseThrow();
        initial.register(accepted.caseId(), order.id(), 200, 100, "<default>");
        Entry entry = initial.prepareInitial(accepted.caseId(), 200, 300);
        initial.registerAutomatic(accepted.caseId(), entry.id(), entry.callRef(), 200, 400, 350);
        initial.startClarification(accepted.caseId(), entry.id(), entry.callRef(), 200, 400);
        availability.active = true;
        availability.correlationFails = false;
        availability.correlationRejected = false;
        availability.deliveries = 0;
        availability.nativeCompletions = 0;
        availability.task = 700L;
        availability.onDeliver = null;
        browser = MockMvcBuilders.webAppContextSetup(web).build();
    }

    @Test
    void followUpClosureEndpointRejectsMissingCsrfAndAdditionalStatusFields() throws Exception {
        String path = "/mitarbeiter/faelle/" + accepted.caseId() + "/wiedervorlage-abschliessen";
        browser.perform(signed(post(path))).andExpect(status().isForbidden());
        browser.perform(signed(post(path)).param("csrf", "csrf-test").param("operationId", UUID.randomUUID().toString())
                .param("waitId", UUID.randomUUID().toString()).param("version", "0").param("status", "CLOSED"))
                .andExpect(status().isBadRequest());
        assertEquals(0, database.queryForObject("SELECT count(*) FROM pf_follow_up_command", Integer.class));
    }

    @Test
    void publishedInformationIsEscapedVisibleSearchableAndCreatesOneDurableMailWithoutStatusChange() throws Exception {
        availability.onDeliver = id -> service.publish(id, 200, store.get(id).target(), "<default>");
        String path = "/mitarbeiter/faelle/" + accepted.caseId() + "/externe-nachricht-veroeffentlichen";
        browser.perform(signed(post(path)).param("csrf", "csrf-test").param("operation", "Information 1")
                        .param("text", "Terminabstimmung <script> "))
                .andExpect(status().isOk()).andExpect(content().string(""));
        assertEquals(2, tenant.readByToken(accepted.personalAccessToken()).messages().size());
        assertEquals("Terminabstimmung <script> ", tenant.readByToken(accepted.personalAccessToken()).messages().getLast().text());
        assertEquals(1, listing.listForStaff(actor, new StaffCaseQuery("Terminabstimmung", "", false, "default", false, 1)).total());
        assertEquals("UNDER_CLARIFICATION", database.queryForObject("SELECT status FROM pf_case", String.class));
        assertEquals(1, count("pf_case_status_event"));
        assertEquals(1, count("pf_staff_message_mail_order"));
        assertEquals(0, count("pf_staff_answer_requirement"));
        assertEquals(database.queryForObject("SELECT received_at FROM pf_case", Timestamp.class),
                database.queryForObject("SELECT last_tenant_contact_at FROM pf_case", Timestamp.class));
        deliveries.discoverOrders();
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_mail_delivery WHERE notification_type = 'STAFF_MESSAGE'", Integer.class));
        browser.perform(get("/mieter/fall/zugang/" + accepted.personalAccessToken()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Terminabstimmung &lt;script&gt;")))
                .andExpect(content().string(not(containsString("Terminabstimmung <script>"))));
        assertEquals("PUBLISHED", service.send(actor, accepted.caseId(), "Information 1", "Terminabstimmung <script> ", false));
        assertEquals(1, availability.deliveries);
    }

    @Test
    void externalWaitQuestionKeepsTheSameNativeTaskAndWaitingStatus() {
        review();
        database.update("UPDATE pf_case SET status = 'WAITING_FOR_EXTERNAL' WHERE id = ?", accepted.caseId());
        assertTrue(service.available(actor, accepted.caseId()).question());
        availability.onDeliver = id -> service.publish(id, 200, 600, "<default>");
        assertEquals("PUBLISHED", service.send(actor, accepted.caseId(), "external-question", "Weitere Angaben?", true));
        assertEquals("WAITING_FOR_EXTERNAL", database.queryForObject("SELECT status FROM pf_case", String.class));
        assertEquals(UserTask.EXTERNAL_WAIT_TASK, store.find(accepted.caseId(), "external-question").orElseThrow().element());
        assertEquals("OPEN", database.queryForObject("SELECT state FROM pf_staff_answer_requirement", String.class));
        assertEquals(700L, availability.task);
    }

    @Test
    void questionDuringReviewPreservesTaskAndStatusAndInformationDoesNotCancelAnswerRequirement() throws Exception {
        review();
        service.send(actor, accepted.caseId(), "Frage", "Wann passt der Termin?", true);
        UUID question = store.find(accepted.caseId(), "Frage").orElseThrow().id();
        assertEquals("PUBLISHED", service.publish(question, 200, 600, "<default>"));
        assertEquals("STAFF_REVIEW_REQUIRED", database.queryForObject("SELECT status FROM pf_case", String.class));
        assertEquals(700L, availability.task);
        assertEquals("OPEN", database.queryForObject("SELECT state FROM pf_staff_answer_requirement", String.class));
        service.send(actor, accepted.caseId(), "Information", "Die Verwaltung prüft das Anliegen.", false);
        assertEquals("PUBLISHED", service.publish(store.find(accepted.caseId(), "Information").orElseThrow().id(), 200, 600, "<default>"));
        assertEquals(1, count("pf_staff_answer_requirement"));
        assertEquals(2, count("pf_staff_message"));
        assertEquals(2, count("pf_case_status_event"));
        browser.perform(get("/mieter/fall/zugang/" + accepted.personalAccessToken()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Offener Antwortbedarf")));
        browser.perform(signed(get("/mitarbeiter/faelle/" + accepted.caseId())))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Offener Antwortbedarf")));
    }

    @Test
    void lostCorrelationResponseKeepsOneCommandAndPublicationRemainsSuccessAfterClosure() {
        availability.correlationFails = true;
        assertThrows(CaseActionException.class, () -> service.send(actor, accepted.caseId(), "Timeout", "Wichtige Information", false));
        UUID id = store.find(accepted.caseId(), "Timeout").orElseThrow().id();
        assertEquals(0, count("pf_staff_message"));
        assertEquals("PUBLISHED", service.publish(id, 200, 200, "<default>"));
        database.update("UPDATE pf_case SET status = 'CLOSED'");
        availability.active = false;
        assertEquals("PUBLISHED", service.send(actor, accepted.caseId(), "Timeout", "Wichtige Information", false));
        assertEquals("PUBLISHED", service.publish(id, 200, 200, "<default>"));
        assertEquals(1, availability.deliveries);
        assertEquals(1, count("pf_staff_message_mail_order"));
        assertThrows(CaseActionException.class, () -> service.send(actor, accepted.caseId(), "Timeout", "Anderer Text", false));
    }

    @Test
    void oldTaskAndChangedCaseRejectUnpublishedCommandsWithoutAffectingANewTask() {
        review();
        service.send(actor, accepted.caseId(), "Alter Auftrag", "Noch nicht veröffentlicht", false);
        UUID first = store.find(accepted.caseId(), "Alter Auftrag").orElseThrow().id();
        availability.task = 701L;
        assertEquals("REJECTED", service.publish(first, 200, 600, "<default>"));
        assertEquals(701L, availability.task);
        service.send(actor, accepted.caseId(), "Alter Auftrag", "Neu bestätigter Text", false);
        UUID second = store.find(accepted.caseId(), "Alter Auftrag").orElseThrow().id();
        database.update("UPDATE pf_case SET version = version + 1");
        assertEquals("REJECTED", service.publish(second, 200, 600, "<default>"));
        assertEquals(0, count("pf_staff_message"));
        assertEquals(0, count("pf_staff_message_mail_order"));
    }

    @Test
    void preparedNativeDecisionFencesPublicationEvenWhenEngineSearchStillShowsOldTask() {
        UUID continuation = review();
        service.send(actor, accepted.caseId(), "Nebenauftrag", "Nicht mehr veröffentlichen", false);
        UUID command = store.find(accepted.caseId(), "Nebenauftrag").orElseThrow().id();
        long version = database.queryForObject("SELECT version FROM pf_case", Long.class);
        decisions.prepare(accepted.caseId(), continuation, actor.accountId(), UUID.randomUUID(), 700, version,
                decisions.reviewFingerprint(accepted.caseId(), 200), StaffDecisionAction.CONTINUE, UserTask.REVIEW_TASK, "");
        assertEquals("REJECTED", service.publish(command, 200, 600, "<default>"));
        assertEquals(0, count("pf_staff_message"));
        assertEquals(700L, availability.task);
    }

    @Test
    void memoVisibilityAndExternalPublicationCannotReuseTheSameOperation() {
        memos.submitMemo(actor, accepted.caseId(), "Gemeinsame ID", "Intern");
        assertThrows(CaseActionException.class, () -> service.send(actor, accepted.caseId(), "Gemeinsame ID", "Intern", false));
        service.send(actor, accepted.caseId(), "Extern", "Bestätigt extern", false);
        assertThrows(CaseActionException.class, () -> memos.submitMemo(actor, accepted.caseId(), "Extern", "Bestätigt extern"));
        assertEquals(0, count("pf_staff_message"));
    }

    @Test
    void informationInWorkKeepsStatusAndQuestionRequiresNativeContinuation() {
        review();
        database.update("UPDATE pf_case SET status = 'IN_PROGRESS'");
        assertTrue(service.available(actor, accepted.caseId()).information());
        assertTrue(service.available(actor, accepted.caseId()).question());
        service.send(actor, accepted.caseId(), "Information", "Zwischenstand", false);
        assertEquals("PUBLISHED", service.publish(store.find(accepted.caseId(), "Information").orElseThrow().id(), 200, 600, "<default>"));
        assertEquals("IN_PROGRESS", database.queryForObject("SELECT status FROM pf_case", String.class));
        assertEquals(0, count("pf_staff_answer_requirement"));
        assertEquals("PENDING", service.send(actor, accepted.caseId(), "Frage", "Frage?", true));
        UUID command = store.find(accepted.caseId(), "Frage").orElseThrow().id();
        assertEquals(1, availability.nativeCompletions);
        assertEquals("PENDING", service.send(actor, accepted.caseId(), "Frage", "Frage?", true));
        assertEquals(1, availability.nativeCompletions);
        assertEquals("PUBLISHED", service.publishQuestion(command, 200, 600, "<default>"));
        assertEquals("PUBLISHED", service.publishQuestion(command, 200, 600, "<default>"));
        assertEquals(1, count("pf_reply_entry"));
        assertEquals(1, count("pf_staff_answer_requirement"));
    }

    @Test
    void httpRequiresSessionCsrfAndOnlyConfirmedFields() throws Exception {
        String path = "/mitarbeiter/faelle/" + accepted.caseId() + "/externe-nachricht-veroeffentlichen";
        browser.perform(post(path)).andExpect(status().isUnauthorized());
        browser.perform(signed(post(path)).param("csrf", "wrong")).andExpect(status().isForbidden());
        browser.perform(signed(post(path)).param("csrf", "csrf-test").param("operation", "ID").param("text", "Text")
                        .param("author", "SYSTEM"))
                .andExpect(status().isBadRequest());
        browser.perform(signed(post(path)).param("csrf", "csrf-test").param("operation", "ID").param("text", "Text"))
                .andExpect(status().isInternalServerError());
        assertEquals(0, count("pf_staff_message"));
        assertEquals(1, count("pf_staff_message_command"));
    }

    @Test
    void concurrentIdenticalSendPublishesOnlyOnce() throws Exception {
        availability.onDeliver = id -> service.publish(id, 200, 200, "<default>");
        try (ExecutorService threads = Executors.newFixedThreadPool(2)) {
            Future<String> first = threads.submit(() -> service.send(actor, accepted.caseId(), "Parallel", "Information", false));
            Future<String> second = threads.submit(() -> service.send(actor, accepted.caseId(), "Parallel", "Information", false));
            assertEquals("PUBLISHED", first.get());
            assertEquals("PUBLISHED", second.get());
        }
        assertEquals(1, count("pf_staff_message"));
        assertEquals(1, count("pf_staff_message_mail_order"));
    }

    @Test
    void revokedRoleBeforeWorkerCommitCannotPublishOrCreateMail() {
        service.send(actor, accepted.caseId(), "Berechtigung", "Bestätigte Nachricht", false);
        UUID id = store.find(accepted.caseId(), "Berechtigung").orElseThrow().id();
        database.update("UPDATE pf_staff_account SET staff_role = FALSE WHERE id = ?", actor.accountId());
        assertEquals("REJECTED", service.publish(id, 200, 200, "<default>"));
        assertEquals(0, count("pf_staff_message"));
        assertEquals(0, count("pf_staff_message_mail_order"));
        assertThrows(CaseActionException.class, () -> service.result(actor, accepted.caseId(), "Berechtigung"));
    }

    @Test
    void cancellationEndpointRequiresCsrfAndPreservesExternalWaitWithPublicResolutionTime() throws Exception {
        review();
        service.send(actor, accepted.caseId(), "Aufzuheben", "Wann passt es?", true);
        UUID message = store.find(accepted.caseId(), "Aufzuheben").orElseThrow().id();
        service.publish(message, 200, 600, "<default>");
        database.update("UPDATE pf_case SET status = 'WAITING_FOR_EXTERNAL'");
        String version = database.queryForObject("SELECT version::text FROM pf_case", String.class);
        String path = "/mitarbeiter/faelle/" + accepted.caseId() + "/antwortbedarf-aufheben";
        browser.perform(signed(post(path)).param("operationId", "clear").param("version", version).param("messageId", message.toString()))
                .andExpect(status().isForbidden());
        browser.perform(signed(post(path)).param("csrf", "csrf-test").param("operationId", "clear").param("version", version)
                .param("messageId", message.toString()).param("status", "CLOSED")).andExpect(status().isBadRequest());
        assertEquals(0, count("pf_answer_cancellation"));
        availability.onDeliver = command -> memos.apply(command, 200, "<default>");
        browser.perform(signed(post(path)).param("csrf", "csrf-test").param("operationId", "clear").param("version", version)
                .param("messageId", message.toString())).andExpect(status().isOk());
        assertEquals("WAITING_FOR_EXTERNAL", database.queryForObject("SELECT status FROM pf_case", String.class));
        assertEquals(1, count("pf_answer_cancellation"));
        assertEquals(actor.accountId(), database.queryForObject("SELECT actor_id FROM pf_answer_cancellation", UUID.class));
        browser.perform(get("/mieter/fall/zugang/" + accepted.personalAccessToken())).andExpect(status().isOk())
                .andExpect(content().string(containsString("Antwortbedarf manuell aufgehoben")));
    }

    @Test
    void takeoverEndpointRequiresCsrfRejectsStatusFieldsAndPublishesConfirmedStateOnly() throws Exception {
        String path = "/mitarbeiter/faelle/" + accepted.caseId() + "/uebernehmen";
        String version = database.queryForObject("SELECT version::text FROM pf_case", String.class);
        browser.perform(signed(get(path + "/verfuegbar"))).andExpect(status().isOk());
        browser.perform(signed(post(path)).param("operationId", "takeover").param("version", version)).andExpect(status().isForbidden());
        browser.perform(signed(post(path)).param("csrf", "csrf-test").param("operationId", "takeover").param("version", version)
                .param("status", "CLOSED")).andExpect(status().isBadRequest());
        assertEquals(0, count("pf_staff_takeover"));
        availability.onDeliver = command -> memos.apply(command, 200, "<default>");
        browser.perform(signed(post(path)).param("csrf", "csrf-test").param("operationId", "takeover").param("version", version))
                .andExpect(status().isOk());
        assertEquals("IN_PROGRESS", database.queryForObject("SELECT status FROM pf_case", String.class));
        assertEquals(1, count("pf_staff_takeover"));
        assertEquals(1, count("pf_staff_continuation"));
    }

    @Test
    void directClosureEndpointRequiresCsrfRejectsStatusFieldsAndUsesTheConfirmedFingerprint() throws Exception {
        String path = "/mitarbeiter/faelle/" + accepted.caseId() + "/abklaerung-abschliessen";
        String version = database.queryForObject("SELECT version::text FROM pf_case", String.class);
        String fingerprint = memos.clarificationClosure(actor, accepted.caseId()).reviewToken();
        browser.perform(signed(get(path + "/verfuegbar"))).andExpect(status().isOk());
        browser.perform(signed(post(path)).param("operationId", "close").param("version", version).param("reviewToken", fingerprint))
                .andExpect(status().isForbidden());
        browser.perform(signed(post(path)).param("csrf", "csrf-test").param("operationId", "close").param("version", version)
                .param("reviewToken", fingerprint).param("status", "CLOSED")).andExpect(status().isBadRequest());
        browser.perform(signed(post(path)).param("csrf", "csrf-test").param("operationId", "close").param("version", version)
                .param("reviewToken", "other")).andExpect(status().isConflict());
        assertEquals(0, count("pf_case_closure"));
        availability.onDeliver = command -> memos.apply(command, 200, "<default>");
        browser.perform(signed(post(path)).param("csrf", "csrf-test").param("operationId", "close").param("version", version)
                .param("reviewToken", fingerprint)).andExpect(status().isOk());
        assertEquals("CLOSED", database.queryForObject("SELECT status FROM pf_case", String.class));
        assertEquals(1, count("pf_case_closure"));
        assertEquals(actor.accountId(), database.queryForObject("SELECT actor_id FROM pf_case_closure", UUID.class));
        assertEquals(1, count("pf_case_mail_order"));
    }

    @Test
    void replyClosureEndpointReservesTheWaitWithoutReportingAnEarlyClosure() throws Exception {
        service.send(actor, accepted.caseId(), "reply-source", "Wann passt Ihnen ein Termin?", true);
        UUID message = store.find(accepted.caseId(), "reply-source").orElseThrow().id();
        assertEquals("PUBLISHED", service.publish(message, 200, 200, "<default>"));
        database.update("""
                INSERT INTO pf_reply_call (id, entry_id, case_id, leading_instance_key, child_instance_key, parent_element_key, tenant_id, state)
                VALUES (?, ?, ?, 200, 700, 650, '<default>', 'WAITING')
                """, UUID.randomUUID(), message, accepted.caseId());
        String path = "/mitarbeiter/faelle/" + accepted.caseId() + "/mieterwarten-abschliessen";
        String version = database.queryForObject("SELECT version::text FROM pf_case", String.class);
        String fingerprint = memos.replyClosure(actor, accepted.caseId()).reviewToken();
        browser.perform(signed(get(path + "/verfuegbar"))).andExpect(status().isOk());
        browser.perform(signed(post(path)).param("operationId", "reply-close").param("version", version).param("reviewToken", fingerprint))
                .andExpect(status().isForbidden());
        browser.perform(signed(post(path)).param("csrf", "csrf-test").param("operationId", "reply-close").param("version", version)
                .param("reviewToken", fingerprint).param("status", "CLOSED")).andExpect(status().isBadRequest());
        browser.perform(signed(post(path)).param("csrf", "csrf-test").param("operationId", "reply-close").param("version", version)
                .param("reviewToken", "other")).andExpect(status().isConflict());
        availability.onDeliver = command -> memos.replyClosureState(command, 200, "<default>");
        browser.perform(signed(post(path)).param("csrf", "csrf-test").param("operationId", "reply-close").param("version", version)
                .param("reviewToken", fingerprint)).andExpect(status().isAccepted());
        assertEquals("WAITING_FOR_TENANT", database.queryForObject("SELECT status FROM pf_case", String.class));
        assertEquals("CLOSING", database.queryForObject("SELECT state FROM pf_reply_call", String.class));
        assertEquals(0, count("pf_case_closure"));
        assertEquals("OPEN", database.queryForObject("SELECT state FROM pf_staff_answer_requirement", String.class));
    }

    @Test
    void directFollowUpEndpointValidatesFieldsDateAndReviewBasisAndRecordsInternalReason() throws Exception {
        String path = "/mitarbeiter/faelle/" + accepted.caseId() + "/direkte-wiedervorlage";
        String version = database.queryForObject("SELECT version::text FROM pf_case", String.class);
        String fingerprint = memos.directFollowUp(actor, accepted.caseId()).reviewToken();
        browser.perform(signed(get(path + "/verfuegbar"))).andExpect(status().isOk());
        browser.perform(signed(post(path)).param("operationId", "direct-http").param("version", version)
                .param("reviewToken", fingerprint).param("days", "2")).andExpect(status().isForbidden());
        browser.perform(signed(post(path)).param("csrf", "csrf-test").param("operationId", "direct-http").param("version", version)
                .param("reviewToken", fingerprint).param("days", "2").param("status", "CLOSED")).andExpect(status().isBadRequest());
        browser.perform(signed(post(path)).param("csrf", "csrf-test").param("operationId", "direct-http").param("version", version)
                .param("reviewToken", fingerprint).param("days", "2").param("date", "2030-01-15")).andExpect(status().isBadRequest());
        browser.perform(signed(post(path)).param("csrf", "csrf-test").param("operationId", "direct-http").param("version", version)
                .param("reviewToken", "old").param("days", "2")).andExpect(status().isConflict());
        availability.onDeliver = command -> memos.directFollowUpState(command, 200, "<default>");
        browser.perform(signed(post(path)).param("csrf", "csrf-test").param("operationId", "direct-http").param("version", version)
                .param("reviewToken", fingerprint).param("days", "2").param("reason", "Ersatzteil"))
                .andExpect(status().isOk());
        assertEquals("WAITING_FOR_FOLLOW_UP", database.queryForObject("SELECT status FROM pf_case", String.class));
        assertEquals(1, count("pf_case_wait"));
        assertTrue(new com.pertegato.propertyflow.caseprocessing.persistence.StaffChangeStore(database)
                .history(accepted.caseId()).getFirst().description().contains("Ersatzteil"));
        assertEquals(0, count("pf_staff_message"));
    }

    private UUID review() {
        UUID signal = UUID.randomUUID();
        database.update("""
                INSERT INTO pf_case_review_signal (id, case_id, source_type, source_ref, reason, leading_instance_key)
                VALUES (?, ?, 'ANALYSIS', ?, 'ANALYSIS_FAILED', 200)
                """, signal, accepted.caseId(), UUID.randomUUID());
        UUID continuation = reviews.check(accepted.caseId(), 200, signal).continuationId();
        reviews.claim(accepted.caseId(), 200, continuation, 500, null);
        reviews.register(accepted.caseId(), 200, continuation, 600, 550);
        reviews.startReview(accepted.caseId(), 200, continuation, 600);
        return continuation;
    }

    private int count(String table) {
        return database.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private MockHttpServletRequestBuilder signed(MockHttpServletRequestBuilder request) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(StaffWebSession.ACCOUNT, actor.accountId());
        session.setAttribute(StaffWebSession.CSRF, "csrf-test");
        return request.session(session).with(servlet -> {
            servlet.setRequestedSessionId(session.getId());
            servlet.setRequestedSessionIdFromCookie(true);
            return servlet;
        });
    }

    static class Engine implements CaseProcessGateway, StaffMessageGateway, StaffTaskGateway {
        private boolean active;
        private boolean correlationFails;
        private boolean correlationRejected;
        private int deliveries;
        private Long task;
        private Consumer<UUID> onDeliver;
        private int nativeCompletions;

        @Override
        public boolean active(long key, String tenant) {
            return active;
        }

        @Override
        public void deliverCaseData(UUID command, long key, String tenant) {
        }

        @Override
        public void deliverClearAnswer(UUID command, long key, String tenant) {
            if (onDeliver != null) {
                onDeliver.accept(command);
            }
        }

        @Override
        public void deliverTakeover(UUID command, long key, String tenant) {
            if (onDeliver != null) {
                onDeliver.accept(command);
            }
        }

        @Override
        public void deliverClarificationClosure(UUID command, long key, String tenant) {
            if (onDeliver != null) {
                onDeliver.accept(command);
            }
        }

        @Override
        public void deliverReplyClosure(UUID command, long key, String tenant) {
            if (onDeliver != null) {
                onDeliver.accept(command);
            }
        }

        @Override
        public void deliverDirectFollowUp(UUID command, long key, String tenant) {
            if (onDeliver != null) {
                onDeliver.accept(command);
            }
        }

        @Override
        public Optional<Long> currentTask(Binding binding) {
            return Optional.ofNullable(task);
        }

        @Override
        public void completeTask(Binding binding, long key, UUID decision, StaffDecisionAction action) {
            assertEquals(StaffDecisionAction.QUESTION, action);
            nativeCompletions++;
        }

        @Override
        public void correlateFollowUp(UUID command, long target, String tenant, String correlationKey) {
            correlate(command, target, tenant, false, correlationKey);
        }

        @Override
        public void correlateReply(UUID command, long target, String tenant, String correlationKey) {
            correlate(command, target, tenant, false, correlationKey);
        }

        @Override
        public void correlate(UUID command, long target, String tenant, boolean automatic, String correlationKey) {
            deliveries++;
            if (correlationRejected) {
                throw new CaseActionException("CASE_NOT_ACTIVE");
            }
            if (correlationFails) {
                throw new IllegalStateException("TEST_UNKNOWN_CORRELATION");
            }
            if (onDeliver != null) {
                onDeliver.accept(command);
            }
        }
    }

    @TestConfiguration
    static class Configuration {
        @Bean
        @Primary
        Engine engine() {
            return new Engine();
        }
    }
}
