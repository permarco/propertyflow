package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.PropertyflowApplication;
import com.pertegato.propertyflow.access.contract.SubmissionContext;
import com.pertegato.propertyflow.caseprocessing.contract.CaseAccepted;
import com.pertegato.propertyflow.caseprocessing.contract.CaseCreation;
import com.pertegato.propertyflow.caseprocessing.contract.CaseReading;
import com.pertegato.propertyflow.caseprocessing.contract.CaseStatus;
import com.pertegato.propertyflow.caseprocessing.contract.CreateCaseCommand;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants;
import io.camunda.client.CamundaClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_DATABASE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_CAMUNDA_URL", matches = ".+")
class WorkflowRestartCamundaTest {

    @Test
    void actualSpringWorkersRecoverDurableIntakeAfterUnavailableEngineAndApplicationRestart() throws Exception {
        String engineUrl = System.getenv("PROPERTYFLOW_TEST_CAMUNDA_URL");
        try (CamundaClient engine = CamundaClient.newClientBuilder().restAddress(URI.create(engineUrl)).preferRestOverGrpc(true).build()) {
            long definitionKey = WorkflowTestModels.deploy(engine).getProcesses().stream()
                    .filter(process -> CaseProcessBpmnConstants.PROCESS_ID.equals(process.getBpmnProcessId()))
                    .findFirst().orElseThrow().getProcessDefinitionKey();
            CaseAccepted accepted;
            try (ConfigurableApplicationContext unavailable = start("http://127.0.0.1:1", definitionKey)) {
                JdbcTemplate database = unavailable.getBean(JdbcTemplate.class);
                assertEquals("propertyflow_test", database.queryForObject("SELECT current_database()", String.class));
                database.execute("TRUNCATE pf_case CASCADE");
                accepted = unavailable.getBean(CaseCreation.class).create(new SubmissionContext.Guest(UUID.randomUUID()),
                        new CreateCaseCommand("restart", "Heizung kalt", "Wohnung 4", "Seit gestern kalt.", "tenant@example.test"));
                unavailable.getBean(ProcessStartDispatcher.class).dispatch();
                assertEquals(CaseStatus.RECEIVED, unavailable.getBean(CaseReading.class)
                        .readByToken(accepted.personalAccessToken()).state().status());
                assertEquals("UNKNOWN", database.queryForObject("SELECT last_result FROM pf_process_start_order", String.class));
                database.update("UPDATE pf_process_start_order SET next_attempt_at = CURRENT_TIMESTAMP");
            }
            try (ConfigurableApplicationContext recovered = start(engineUrl, definitionKey)) {
                recovered.getBean(ProcessStartDispatcher.class).dispatch();
                CaseReading reading = recovered.getBean(CaseReading.class);
                long deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos();
                while (reading.readByToken(accepted.personalAccessToken()).state().status() != CaseStatus.UNDER_CLARIFICATION
                        && System.nanoTime() < deadline) {
                    Thread.sleep(200);
                }
                assertEquals(CaseStatus.UNDER_CLARIFICATION, reading.readByToken(accepted.personalAccessToken()).state().status());
                JdbcTemplate database = recovered.getBean(JdbcTemplate.class);
                assertEquals(1, database.queryForObject("SELECT COUNT(*) FROM pf_case_status_event", Integer.class));
                assertEquals(1, database.queryForObject("SELECT COUNT(*) FROM pf_case_mail_order", Integer.class));
                int port = recovered.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
                try (HttpClient browser = HttpClient.newHttpClient()) {
                    HttpResponse<String> page = browser.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                            + "/mieter/fall/zugang/" + accepted.personalAccessToken())).GET().build(), HttpResponse.BodyHandlers.ofString());
                    assertEquals(200, page.statusCode());
                    assertTrue(page.body().contains("In Abklärung"));
                }
                long instanceKey = database.queryForObject("SELECT process_instance_key FROM pf_case_process", Long.class);
                assertTrue(instanceKey > 0);
                engine.newCancelInstanceCommand(instanceKey).send().join();
            }
        }
    }

    private ConfigurableApplicationContext start(String engineUrl, long definitionKey) {
        return new SpringApplicationBuilder(PropertyflowApplication.class)
                .web(WebApplicationType.SERVLET)
                .profiles("propertyflow-backend")
                .run("--server.port=0", "--spring.config.import=", "--spring.docker.compose.enabled=false",
                        "--spring.datasource.url=" + System.getenv("PROPERTYFLOW_TEST_DATABASE_URL"),
                        "--spring.datasource.username=propertyflow_test", "--spring.datasource.password=propertyflow_test",
                        "--propertyflow.links.encryption-key=" + Base64.getEncoder().encodeToString(new byte[32]),
                        "--propertyflow.workflow.enabled=true", "--propertyflow.workflow.definition-key=" + definitionKey,
                        "--propertyflow.workflow.initial-delay-ms=3600000",
                        "--camunda.client.rest-address=" + engineUrl, "--camunda.client.prefer-rest-over-grpc=true",
                        "--camunda.client.request-timeout=2s", "--camunda.client.worker.defaults.stream-enabled=false",
                        "--camunda.client.worker.defaults.poll-interval=100ms", "--camunda.client.worker.defaults.max-jobs-active=4",
                        "--logging.level.io.camunda.client=WARN");
    }
}
