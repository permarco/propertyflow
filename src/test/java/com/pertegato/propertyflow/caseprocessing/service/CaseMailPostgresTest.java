package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.access.contract.SubmissionContext;
import com.pertegato.propertyflow.caseprocessing.contract.CaseAccepted;
import com.pertegato.propertyflow.caseprocessing.contract.CaseCreation;
import com.pertegato.propertyflow.caseprocessing.contract.CreateCaseCommand;
import com.pertegato.propertyflow.caseprocessing.delivery.CaseMailSender;
import com.pertegato.propertyflow.caseprocessing.delivery.PersonalLinkCodec;
import com.pertegato.propertyflow.caseprocessing.persistence.MailDeliveryStore;
import com.pertegato.propertyflow.caseprocessing.persistence.MailDeliveryStore.MailStart;
import com.pertegato.propertyflow.caseprocessing.service.CaseMailService.MailOutcome;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "propertyflow.workflow.enabled=false")
@ActiveProfiles("propertyflow-backend")
@Import(CaseMailPostgresTest.MailConfiguration.class)
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_DATABASE_URL", matches = ".+")
class CaseMailPostgresTest {

    @Autowired
    private CaseCreation creation;

    @Autowired
    private CaseMailService service;

    @Autowired
    private MailDeliveryStore store;

    @Autowired
    private PersonalLinkCodec links;

    @Autowired
    private RecordingSender sender;

    @Autowired
    private JdbcTemplate database;

    private CaseAccepted accepted;
    private MailStart start;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("PROPERTYFLOW_TEST_DATABASE_URL"));
        properties.add("spring.datasource.username", () -> "propertyflow_test");
        properties.add("spring.datasource.password", () -> "propertyflow_test");
        properties.add("propertyflow.links.encryption-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
    }

    @BeforeEach
    void setup() {
        assertEquals("propertyflow_test", database.queryForObject("SELECT current_database()", String.class));
        database.execute("TRUNCATE pf_case CASCADE");
        sender.reset();
        accepted = creation.create(new SubmissionContext.Guest(UUID.randomUUID()),
                new CreateCaseCommand("mail", "Private subject", "Apartment 4", "Private description", "old@example.test"));
        store.discoverOrders();
        start = store.claimStart(100, "<default>").orElseThrow();
    }

    @Test
    void openQuestionMailExplicitlyRequestsAnAnswerWithoutDisclosingTheQuestion() {
        UUID message = publicMessage(true);
        service.prepare(start.id(), 200, 100, "<default>");
        service.send(start.id(), 200);
        store.discoverOrders();
        MailStart question = store.claimStart(100, "<default>").orElseThrow();
        assertEquals(message, question.id());
        service.prepare(question.id(), 201, 100, "<default>");
        service.send(question.id(), 201);
        String information = sender.messages.getLast().information();
        assertTrue(information.contains("Ihre Antwort ist erforderlich"));
        assertFalse(information.contains("Private Frage"));
    }

    @Test
    void cancelledQuestionMailDoesNotRecreateAnAnswerObligation() {
        publicMessage(true);
        database.update("UPDATE pf_staff_answer_requirement SET state = 'CANCELLED', resolution_type = 'MANUAL', resolved_at = CURRENT_TIMESTAMP");
        service.prepare(start.id(), 200, 100, "<default>");
        service.send(start.id(), 200);
        store.discoverOrders();
        MailStart question = store.claimStart(100, "<default>").orElseThrow();
        service.prepare(question.id(), 201, 100, "<default>");
        service.send(question.id(), 201);
        assertFalse(sender.messages.getLast().information().contains("Antwort ist erforderlich"));
        assertTrue(sender.messages.getLast().information().contains("neue Nachricht"));
    }

    private UUID publicMessage(boolean answerRequired) {
        UUID id = UUID.randomUUID();
        database.update("UPDATE pf_case SET status = 'UNDER_CLARIFICATION'");
        database.update("""
                INSERT INTO pf_staff_message_command (id, case_id, operation_id, actor_id, expected_version,
                    leading_instance_key, tenant_id, target_instance_key, body, answer_required, state)
                VALUES (?, ?, ?, ?, 1, 700, '<default>', 700, 'Private Frage', ?, 'PUBLISHED')
                """, id, accepted.caseId(), id.toString(), UUID.randomUUID(), answerRequired);
        database.update("""
                INSERT INTO pf_staff_message (id, staff_command_id, case_id, body, answer_required, analysis_version)
                VALUES (?, ?, ?, 'Private Frage', ?, 1)
                """, id, id, accepted.caseId(), answerRequired);
        if (answerRequired) {
            database.update("INSERT INTO pf_staff_answer_requirement (message_id, case_id) VALUES (?, ?)", id, accepted.caseId());
        }
        database.update("INSERT INTO pf_staff_message_mail_order (id, case_id) VALUES (?, ?)", id, accepted.caseId());
        return id;
    }

    @Test
    void durableProjectionAndWorkerReplaySendExactlyOneKnownSuccessfulMail() {
        store.discoverOrders();
        assertEquals(1, database.queryForObject("SELECT COUNT(*) FROM pf_mail_delivery", Integer.class));
        assertEquals("MESSAGE", service.prepare(start.id(), 200, 100, "<default>"));
        assertEquals(MailOutcome.SENT, service.send(start.id(), 200));
        assertEquals(MailOutcome.SENT, service.send(start.id(), 200));
        assertEquals("SKIP", service.prepare(start.id(), 200, 100, "<default>"));
        assertEquals(1, sender.messages.size());
        CaseMailSender.Mail mail = sender.messages.getFirst();
        assertTrue(mail.personalUrl().endsWith(accepted.personalAccessToken()));
        assertFalse(mail.information().contains("Private"));
        assertFalse(mail.toString().contains(accepted.personalAccessToken()));
        assertEquals("SENT", database.queryForObject("SELECT state FROM pf_acceptance_mail_order", String.class));
    }

    @Test
    void addressAndLinkAreResolvedAtSendTime() {
        service.prepare(start.id(), 200, 100, "<default>");
        UUID accessId = UUID.randomUUID();
        String replacement = links.generateToken();
        database.update("UPDATE pf_case SET contact_email = 'new@example.test'");
        database.update("UPDATE pf_case_access SET revoked_at = CURRENT_TIMESTAMP");
        database.update("INSERT INTO pf_case_access (id, case_id, token_hash, encrypted_token) VALUES (?, ?, ?, ?)",
                accessId, accepted.caseId(), links.hash(replacement), links.encrypt(accessId, replacement));
        assertEquals(MailOutcome.SENT, service.send(start.id(), 200));
        assertEquals("new@example.test", sender.messages.getFirst().recipient());
        assertTrue(sender.messages.getFirst().personalUrl().endsWith(replacement));
    }

    @Test
    void closedCaseStillReceivesAnAlreadyOwedMail() {
        service.prepare(start.id(), 200, 100, "<default>");
        database.update("UPDATE pf_case SET status = 'CLOSED'");
        assertEquals(MailOutcome.SENT, service.send(start.id(), 200));
        assertEquals("CLOSED", database.queryForObject("SELECT status FROM pf_case", String.class));
    }

    @Test
    void temporaryFailureRetriesSameLogicalOrderAndStopsAtBound() {
        service.prepare(start.id(), 200, 100, "<default>");
        sender.result = CaseMailSender.SendResult.RETRYABLE;
        for (int attempt = 1; attempt < 5; attempt++) {
            assertEquals(MailOutcome.RETRY, service.send(start.id(), 200));
        }
        assertEquals(MailOutcome.REVIEW, service.send(start.id(), 200));
        assertEquals(MailOutcome.REVIEW, service.send(start.id(), 200));
        assertEquals(5, sender.messages.size());
        assertTrue(sender.messages.stream().allMatch(mail -> mail.orderId().equals(start.id())));
        assertEquals("FAILED", database.queryForObject("SELECT state FROM pf_mail_delivery", String.class));
    }

    @Test
    void unknownSmtpOutcomeIsNotRetriedOrReportedAsAccepted() {
        service.prepare(start.id(), 200, 100, "<default>");
        sender.result = CaseMailSender.SendResult.UNKNOWN;
        assertEquals(MailOutcome.REVIEW, service.send(start.id(), 200));
        assertEquals(MailOutcome.REVIEW, service.send(start.id(), 200));
        assertEquals(1, sender.messages.size());
        assertEquals("UNCERTAIN", database.queryForObject("SELECT state FROM pf_mail_delivery", String.class));
        assertEquals(0, database.queryForObject("SELECT COUNT(*) FROM pf_mail_delivery WHERE accepted_at IS NOT NULL", Integer.class));
    }

    @Test
    void interruptedAttemptBecomesUncertainAfterLeaseWithoutSendingAgain() {
        service.prepare(start.id(), 200, 100, "<default>");
        database.update("""
                UPDATE pf_mail_delivery SET state = 'SENDING', attempt_id = ?,
                    attempt_started_at = CURRENT_TIMESTAMP - INTERVAL '3 minutes'
                """, UUID.randomUUID());
        assertEquals(MailOutcome.REVIEW, service.send(start.id(), 200));
        assertTrue(sender.messages.isEmpty());
        assertEquals("UNCERTAIN", database.queryForObject("SELECT state FROM pf_mail_delivery", String.class));
    }

    @Test
    void concurrentSendWaitsForExistingAttemptWithoutSecondSmtpCall() throws Exception {
        service.prepare(start.id(), 200, 100, "<default>");
        sender.entered = new CountDownLatch(1);
        sender.release = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<MailOutcome> first = executor.submit(() -> service.send(start.id(), 200));
            try {
                assertTrue(sender.entered.await(5, TimeUnit.SECONDS));
                assertEquals(MailOutcome.BUSY, service.send(start.id(), 200));
            } finally {
                sender.release.countDown();
            }
            assertEquals(MailOutcome.SENT, first.get(5, TimeUnit.SECONDS));
        }
        assertEquals(1, sender.messages.size());
    }

    @Test
    void revokedLinkCreatesOneReviewSignalAndNeverReopensClosedCase() {
        service.prepare(start.id(), 200, 100, "<default>");
        database.update("UPDATE pf_case_access SET revoked_at = CURRENT_TIMESTAMP");
        database.update("UPDATE pf_case SET status = 'CLOSED'");
        assertEquals(MailOutcome.REVIEW, service.send(start.id(), 200));
        service.recordProblem(start.id(), 200);
        service.recordProblem(start.id(), 200);
        assertTrue(sender.messages.isEmpty());
        assertEquals(1, database.queryForObject("SELECT COUNT(*) FROM pf_case_review_signal", Integer.class));
        assertEquals("NOTE", database.queryForObject("SELECT state FROM pf_case_review_signal", String.class));
        assertEquals("CLOSED", database.queryForObject("SELECT status FROM pf_case", String.class));
    }

    @Test
    void wrongDefinitionTenantOrMailInstanceCannotSend() {
        assertThrows(WorkflowRejectedException.class, () -> service.prepare(start.id(), 200, 101, "<default>"));
        assertThrows(WorkflowRejectedException.class, () -> service.prepare(start.id(), 200, 100, "other"));
        service.prepare(start.id(), 200, 100, "<default>");
        assertThrows(WorkflowRejectedException.class, () -> service.prepare(start.id(), 201, 100, "<default>"));
        assertThrows(WorkflowRejectedException.class, () -> service.send(start.id(), 201));
        assertTrue(sender.messages.isEmpty());
    }

    @TestConfiguration
    static class MailConfiguration {
        @Bean
        @Primary
        RecordingSender recordingSender() {
            return new RecordingSender();
        }
    }

    static class RecordingSender implements CaseMailSender {
        private final List<Mail> messages = new ArrayList<>();
        private SendResult result = SendResult.ACCEPTED;
        private CountDownLatch entered;
        private CountDownLatch release;

        @Override
        public SendResult send(Mail mail) {
            messages.add(mail);
            if (entered != null) {
                entered.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Test sender timed out");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            }
            return result;
        }

        private void reset() {
            messages.clear();
            result = SendResult.ACCEPTED;
            entered = null;
            release = null;
        }
    }
}
