package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.access.contract.SubmissionContext;
import com.pertegato.propertyflow.caseprocessing.contract.CaseAccepted;
import com.pertegato.propertyflow.caseprocessing.contract.CaseCreation;
import com.pertegato.propertyflow.caseprocessing.contract.CreateCaseCommand;
import com.pertegato.propertyflow.caseprocessing.persistence.MailDeliveryStore;
import com.pertegato.propertyflow.caseprocessing.persistence.MailDeliveryStore.MailStart;
import com.pertegato.propertyflow.caseprocessing.service.CaseMailService;
import com.pertegato.propertyflow.caseprocessing.workflow.ProcessStartGateway.StartResult;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "propertyflow.workflow.enabled=false")
@ActiveProfiles("propertyflow-backend")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_DATABASE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_CAMUNDA_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_SMTP_PORT", matches = ".+")
class CaseMailCamundaTest {

    @Autowired
    private CaseCreation creation;

    @Autowired
    private CaseMailService service;

    @Autowired
    private MailDeliveryStore store;

    @Autowired
    private JdbcTemplate database;

    private CamundaClient engine;
    private CaseMailWorkers workers;
    private MailStart start;
    private CaseAccepted accepted;
    private String recipient;
    private Long instanceKey;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("PROPERTYFLOW_TEST_DATABASE_URL"));
        properties.add("spring.datasource.username", () -> "propertyflow_test");
        properties.add("spring.datasource.password", () -> "propertyflow_test");
        properties.add("propertyflow.links.encryption-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
        properties.add("spring.mail.host", () -> "127.0.0.1");
        properties.add("spring.mail.port", () -> System.getenv("PROPERTYFLOW_TEST_SMTP_PORT"));
    }

    @BeforeEach
    void setup() {
        assertEquals("propertyflow_test", database.queryForObject("SELECT current_database()", String.class));
        database.execute("TRUNCATE pf_case CASCADE");
        engine = CamundaClient.newClientBuilder().restAddress(URI.create(System.getenv("PROPERTYFLOW_TEST_CAMUNDA_URL")))
                .preferRestOverGrpc(true).defaultRequestTimeout(Duration.ofSeconds(5)).build();
        long definitionKey = engine.newDeployResourceCommand()
                .addResourceFromClasspath("bpmn/fallbenachrichtigung-versenden.bpmn")
                .send().join().getProcesses().getFirst().getProcessDefinitionKey();
        recipient = "mail-" + UUID.randomUUID() + "@example.test";
        accepted = creation.create(new SubmissionContext.Guest(UUID.randomUUID()),
                new CreateCaseCommand("mail", "Private subject", "Apartment 4", "Private message", recipient));
        store.discoverOrders();
        start = store.claimStart(definitionKey, "<default>").orElseThrow();
        workers = new CaseMailWorkers(engine, service);
        assertEquals(StartResult.STARTED, new CamundaProcessStartGateway(engine)
                .ensureStarted(start.id(), start.definitionKey(), start.tenantId(), Map.of("mailOrderRef", start.id().toString())));
    }

    @AfterEach
    void close() {
        if (engine != null) {
            if (instanceKey != null) {
                try {
                    engine.newCancelInstanceCommand(instanceKey).send().join();
                } catch (RuntimeException ignored) {
                    // A successfully completed instance no longer needs cancellation.
                }
            }
            engine.close();
        }
    }

    @Test
    void canonicalMailProcessHandsOffToRealSmtpAndKeepsCaseInactive() throws Exception {
        workers.prepare(engine, job("propertyflow.mail.prepare"));
        ActivatedJob sending = job("propertyflow.mail.send");
        workers.send(engine, sending);
        assertEquals("SENT", database.queryForObject("SELECT state FROM pf_mail_delivery", String.class));
        assertEquals("RECEIVED", database.queryForObject("SELECT status FROM pf_case", String.class));
        assertEquals(0, database.queryForObject("SELECT COUNT(*) FROM pf_case_process", Integer.class));
        JsonNode mail = deliveredMail();
        assertTrue(mail.path("Text").asString().contains(accepted.personalAccessToken()));
        assertFalse(mail.path("Text").asString().contains("Private message"));
        assertTrue(mail.path("MessageID").asString().contains(start.id().toString()));
        awaitCompletion(sending.getProcessInstanceKey());
    }

    @Test
    void canonicalBoundaryErrorCreatesOneDurableReviewSignalWithoutReopeningClosedCase() throws Exception {
        workers.prepare(engine, job("propertyflow.mail.prepare"));
        database.update("UPDATE pf_case_access SET revoked_at = CURRENT_TIMESTAMP");
        database.update("UPDATE pf_case SET status = 'CLOSED'");
        workers.send(engine, job("propertyflow.mail.send"));
        ActivatedJob problem = job("propertyflow.mail.record-problem");
        workers.recordProblem(engine, problem);
        service.recordProblem(start.id(), problem.getProcessInstanceKey());
        assertEquals("FAILED", database.queryForObject("SELECT state FROM pf_mail_delivery", String.class));
        assertEquals(1, database.queryForObject("SELECT COUNT(*) FROM pf_case_review_signal", Integer.class));
        assertEquals("NOTE", database.queryForObject("SELECT state FROM pf_case_review_signal", String.class));
        assertEquals("CLOSED", database.queryForObject("SELECT status FROM pf_case", String.class));
        awaitCompletion(problem.getProcessInstanceKey());
    }

    @Test
    void urgencyChangeUsesCanonicalChangeRouteAndSmtpWithoutInternalReason() throws Exception {
        workers.prepare(engine, job("propertyflow.mail.prepare"));
        ActivatedJob acceptance = job("propertyflow.mail.send");
        workers.send(engine, acceptance);
        awaitCompletion(acceptance.getProcessInstanceKey());
        UUID command = UUID.randomUUID();
        UUID event = UUID.randomUUID();
        UUID order = UUID.randomUUID();
        // Persisted event fixture; domain authorization/versioning is covered by CaseDataPostgresTest.
        database.update("""
                INSERT INTO pf_case_data_command (id, operation_id, case_id, actor_id, action_type, payload, expected_version,
                    leading_instance_key, tenant_id, state) VALUES (?, ?, ?, ?, 'SET_MANUAL_URGENCY', 'HIGH', 1, 700, '<default>', 'APPLIED')
                """, command, UUID.randomUUID(), accepted.caseId(), UUID.randomUUID());
        database.update("""
                INSERT INTO pf_urgency_event (id, case_id, command_id, current_urgency, source, actor_id, internal_reason, public_change)
                VALUES (?, ?, ?, 'HIGH', 'STAFF', ?, 'Geheime interne Begründung', TRUE)
                """, event, accepted.caseId(), command, UUID.randomUUID());
        database.update("INSERT INTO pf_urgency_mail_order (id, case_id, event_id) VALUES (?, ?, ?)", order, accepted.caseId(), event);
        store.discoverOrders();
        start = store.claimStart(start.definitionKey(), "<default>").orElseThrow();
        assertEquals(order, start.id());
        assertEquals(StartResult.STARTED, new CamundaProcessStartGateway(engine)
                .ensureStarted(start.id(), start.definitionKey(), start.tenantId(), Map.of("mailOrderRef", start.id().toString())));
        workers.prepare(engine, job("propertyflow.mail.prepare"));
        ActivatedJob sending = job("propertyflow.mail.send");
        assertEquals("MailSendChange", sending.getElementId());
        workers.send(engine, sending);
        assertEquals("SENT", database.queryForObject("SELECT state FROM pf_urgency_mail_order", String.class));
        JsonNode mail = deliveredMail();
        assertTrue(mail.path("Text").asString().contains("Dringlichkeit Ihres Falls wurde geändert: Hoch"));
        assertFalse(mail.path("Text").asString().contains("Geheime interne Begründung"));
        awaitCompletion(sending.getProcessInstanceKey());
    }

    @Test
    void publishedStaffMessageMailSurvivesCaseClosureAndContainsOnlyThePortalNotice() throws Exception {
        workers.prepare(engine, job("propertyflow.mail.prepare"));
        ActivatedJob acceptance = job("propertyflow.mail.send");
        workers.send(engine, acceptance);
        awaitCompletion(acceptance.getProcessInstanceKey());
        UUID message = UUID.randomUUID();
        // The publication itself is covered by StaffMessagePostgresTest and StaffMessageCamundaTest.
        database.update("""
                INSERT INTO pf_staff_message_command (id, case_id, operation_id, actor_id, expected_version,
                    leading_instance_key, tenant_id, target_instance_key, body, answer_required, state)
                VALUES (?, ?, 'staff-mail', ?, 1, 700, '<default>', 700, 'Vertraulicher Nachrichtentext', FALSE, 'PUBLISHED')
                """, message, accepted.caseId(), UUID.randomUUID());
        database.update("""
                INSERT INTO pf_staff_message (id, staff_command_id, case_id, body, answer_required, analysis_version)
                VALUES (?, ?, ?, 'Vertraulicher Nachrichtentext', FALSE, 1)
                """, message, message, accepted.caseId());
        database.update("INSERT INTO pf_staff_message_mail_order (id, case_id) VALUES (?, ?)", message, accepted.caseId());
        database.update("UPDATE pf_case SET status = 'CLOSED'");
        store.discoverOrders();
        start = store.claimStart(start.definitionKey(), "<default>").orElseThrow();
        assertEquals(message, start.id());
        assertEquals(StartResult.STARTED, new CamundaProcessStartGateway(engine)
                .ensureStarted(start.id(), start.definitionKey(), start.tenantId(), Map.of("mailOrderRef", start.id().toString())));
        workers.prepare(engine, job("propertyflow.mail.prepare"));
        ActivatedJob sending = job("propertyflow.mail.send");
        assertEquals("MailSendMessage", sending.getElementId());
        workers.send(engine, sending);
        assertEquals("SENT", database.queryForObject("SELECT state FROM pf_staff_message_mail_order", String.class));
        JsonNode mail = deliveredMail();
        assertTrue(mail.path("Text").asString().contains("Nachricht Ihrer Immobilienverwaltung"));
        assertTrue(mail.path("Text").asString().contains(accepted.personalAccessToken()));
        assertFalse(mail.path("Text").asString().contains("Vertraulicher Nachrichtentext"));
        assertEquals("CLOSED", database.queryForObject("SELECT status FROM pf_case", String.class));
        awaitCompletion(sending.getProcessInstanceKey());
    }

    private ActivatedJob job(String type) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            List<ActivatedJob> jobs = engine.newActivateJobsCommand().jobType(type).maxJobsToActivate(1)
                    .timeout(Duration.ofSeconds(30)).send().join().getJobs();
            if (!jobs.isEmpty()) {
                ActivatedJob job = jobs.getFirst();
                instanceKey = job.getProcessInstanceKey();
                awaitMetadata(instanceKey);
                return job;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Missing mail job: " + type);
    }

    private void awaitMetadata(long key) throws Exception {
        RuntimeException lastFailure = null;
        for (int attempt = 0; attempt < 100; attempt++) {
            try {
                engine.newProcessInstanceGetRequest(key).send().join();
                return;
            } catch (RuntimeException exception) {
                lastFailure = exception;
                Thread.sleep(100);
            }
        }
        throw new AssertionError("Process metadata did not become visible", lastFailure);
    }

    private JsonNode deliveredMail() throws Exception {
        String base = System.getenv().getOrDefault("PROPERTYFLOW_TEST_MAILPIT_URL", "http://127.0.0.1:8027");
        try (HttpClient browser = HttpClient.newHttpClient()) {
            JsonMapper json = JsonMapper.builder().build();
            for (int attempt = 0; attempt < 30; attempt++) {
                HttpResponse<String> response = browser.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/messages"))
                        .GET().build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(200, response.statusCode());
                for (JsonNode mail : json.readTree(response.body()).path("messages")) {
                    if (recipient.equals(mail.path("To").path(0).path("Address").asString())) {
                        HttpResponse<String> detail = browser.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/message/"
                                + mail.path("ID").asString())).GET().build(), HttpResponse.BodyHandlers.ofString());
                        assertEquals(200, detail.statusCode());
                        JsonNode delivered = json.readTree(detail.body());
                        if (delivered.path("MessageID").asString().contains(start.id().toString())) {
                            return delivered;
                        }
                    }
                }
                Thread.sleep(100);
            }
        }
        throw new AssertionError("No SMTP acceptance observed in isolated Mailpit");
    }

    private void awaitCompletion(long instanceKey) throws Exception {
        for (int attempt = 0; attempt < 50; attempt++) {
            if ("COMPLETED".equals(engine.newProcessInstanceGetRequest(instanceKey).send().join().getState().name())) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Mail process did not complete");
    }
}
