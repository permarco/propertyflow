package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.PropertyflowApplication;
import com.pertegato.propertyflow.access.contract.VerificationCodeDelivery;
import com.pertegato.propertyflow.access.service.StaffCodeDelivery;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants;
import io.camunda.client.CamundaClient;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Störfall gemäss Architekturentscheid (Schnittstellenentwurf, Abschnitt 4) und Abnahmetest J2:
 * Bei erreichbarer Datenbank und ausgefallenem Camunda sowie abgeschaltetem KI-Modell erhalten
 * zehn Gäste und zehn verifizierte Mieter über die Ausfalldauer verteilt ihre Annahmebestätigung.
 * Nach einem Neustart besteht je Einreichung genau ein Fall mit einem offenen Startauftrag. Nach
 * dem Wiederanlauf von Camunda wird der Rückstau abgebaut, ohne dass ein Fall zwei führende
 * Prozesse erhält.
 *
 * <p>Die Ausfalldauer in Minuten kommt aus {@code PROPERTYFLOW_TEST_OUTAGE_MINUTES}; die Abnahme
 * verlangt zehn Minuten. Ohne diese Variable läuft der Test nicht, damit er die normale Regression
 * nicht verlängert.
 */
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_DATABASE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_CAMUNDA_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_OUTAGE_MINUTES", matches = "[1-9][0-9]*")
class CamundaOutageIntakeTest {

    private static final String UNREACHABLE_ENGINE = "http://127.0.0.1:1";
    private static final int SUBMISSIONS_PER_KIND = 10;
    private static final Duration CATCH_UP_LIMIT = Duration.ofMinutes(8);
    private static final Pattern CSRF = Pattern.compile("name=\"csrf\"[^>]*value=\"([^\"]+)\"");
    private static final Pattern OPERATION = Pattern.compile("name=\"operation\"[^>]*value=\"([^\"]+)\"");

    private final String schema = "outage_" + UUID.randomUUID().toString().replace("-", "");
    private final String encryptionKey = Base64.getEncoder().encodeToString(new byte[32]);
    private final Mailbox mailbox = new Mailbox();

    @Test
    void intakeSurvivesCamundaOutageAndBacklogIsTakenOverExactlyOnce() throws Exception {
        String engineUrl = System.getenv("PROPERTYFLOW_TEST_CAMUNDA_URL");
        Duration outage = Duration.ofMinutes(Long.parseLong(System.getenv("PROPERTYFLOW_TEST_OUTAGE_MINUTES")));

        try (CamundaClient engine = CamundaClient.newClientBuilder().restAddress(URI.create(engineUrl)).preferRestOverGrpc(true).build()) {
            long definitionKey = WorkflowTestModels.deploy(engine).getProcesses().stream()
                    .filter(process -> CaseProcessBpmnConstants.PROCESS_ID.equals(process.getBpmnProcessId()))
                    .findFirst()
                    .orElseThrow()
                    .getProcessDefinitionKey();

            List<Submission> accepted = submitDuringOutage(definitionKey, outage);
            verifyAfterRestartDuringOutage(definitionKey, accepted);
            verifyBacklogAfterRecovery(engine, engineUrl, definitionKey, accepted, outage);
        } finally {
            dropSchema();
        }
    }

    /**
     * Phase 1: Camunda ist unerreichbar; der Starter versucht laufend vergeblich, Prozesse zu starten.
     */
    private List<Submission> submitDuringOutage(long definitionKey, Duration outage) throws Exception {
        List<Submission> accepted = new ArrayList<>();
        try (ConfigurableApplicationContext unavailable = start(UNREACHABLE_ENGINE, definitionKey)) {
            URI base = baseUrl(unavailable);
            JdbcTemplate database = isolatedDatabase(unavailable);
            List<String> tenants = new ArrayList<>();
            List<Browser> tenantBrowsers = new ArrayList<>();
            for (int index = 0; index < SUBMISSIONS_PER_KIND; index++) {
                String email = "tenant-" + index + "-" + UUID.randomUUID() + "@example.test";
                Browser browser = new Browser(base);
                register(browser, email);
                tenants.add(email);
                tenantBrowsers.add(browser);
            }

            // Die Einreichungen werden gleichmässig über die gesamte Ausfalldauer verteilt.
            long interval = outage.toMillis() / (2L * SUBMISSIONS_PER_KIND);
            long outageStart = System.nanoTime();
            for (int index = 0; index < 2 * SUBMISSIONS_PER_KIND; index++) {
                waitUntil(outageStart + Duration.ofMillis(index * interval).toNanos());
                if (index % 2 == 0) {
                    accepted.add(submitAsGuest(database, new Browser(base), index, index == 0));
                } else {
                    accepted.add(submitAsTenant(tenantBrowsers.get(index / 2), tenants.get(index / 2), index));
                }
            }
            waitUntil(outageStart + outage.toNanos());

            assertEquals(2 * SUBMISSIONS_PER_KIND, count(database, "SELECT count(*) FROM pf_case"));
            assertEquals(0, count(database, "SELECT count(*) FROM pf_case_process"));
            assertEquals(0, count(database, "SELECT count(*) FROM pf_process_start_order WHERE state <> 'PENDING'"));
            assertEquals(0, count(database, "SELECT count(*) FROM pf_process_start_order WHERE attempts = 0"),
                    "Der Starter muss jeden Auftrag während des Ausfalls tatsächlich versucht haben");
            for (Browser browser : tenantBrowsers) {
                browser.close();
            }
        }
        return accepted;
    }

    /**
     * Phase 2: Neustart bei weiterhin ausgefallenem Camunda; nichts geht verloren, nichts entsteht doppelt.
     */
    private void verifyAfterRestartDuringOutage(long definitionKey, List<Submission> accepted) throws Exception {
        try (ConfigurableApplicationContext restarted = start(UNREACHABLE_ENGINE, definitionKey)) {
            URI base = baseUrl(restarted);
            JdbcTemplate database = isolatedDatabase(restarted);
            assertEquals(2 * SUBMISSIONS_PER_KIND, count(database, "SELECT count(*) FROM pf_case"));
            for (Submission submission : accepted) {
                assertEquals(1, count(database, "SELECT count(*) FROM pf_case WHERE id = ?", submission.caseId()));
                assertEquals(1, count(database, "SELECT count(*) FROM pf_process_start_order WHERE case_id = ?", submission.caseId()));
                assertEquals(1, count(database, "SELECT count(*) FROM pf_process_start_order WHERE case_id = ? AND state = 'PENDING'",
                        submission.caseId()));
            }
            for (Submission submission : accepted) {
                try (Browser browser = new Browser(base)) {
                    if (submission.tenantEmail() != null) {
                        // Sitzungen überleben den Neustart nicht; verifizierte Mieter melden sich neu an.
                        signIn(browser, submission.tenantEmail());
                    }
                    assertReadable(browser, submission);
                }
            }
        }
    }

    /**
     * Phase 3: Camunda ist wieder erreichbar; der Rückstau wird ohne doppelte führende Prozesse abgebaut.
     */
    private void verifyBacklogAfterRecovery(CamundaClient engine, String engineUrl, long definitionKey, List<Submission> accepted,
            Duration outage) throws Exception {
        try (ConfigurableApplicationContext recovered = start(engineUrl, definitionKey)) {
            JdbcTemplate database = isolatedDatabase(recovered);
            long recoveryStart = System.nanoTime();
            long deadline = recoveryStart + CATCH_UP_LIMIT.toNanos();
            while ((count(database, "SELECT count(*) FROM pf_case_process") < accepted.size()
                    || count(database, "SELECT count(*) FROM pf_process_start_order WHERE state = 'PENDING'") > 0)
                    && System.nanoTime() < deadline) {
                Thread.sleep(1000);
            }
            Duration catchUp = Duration.ofNanos(System.nanoTime() - recoveryStart);

            assertEquals(accepted.size(), count(database, "SELECT count(*) FROM pf_case_process"),
                    "Der Rückstau wurde nicht innerhalb von " + CATCH_UP_LIMIT + " abgebaut");
            for (Submission submission : accepted) {
                assertEquals(1, count(database, "SELECT count(*) FROM pf_case_process WHERE case_id = ?", submission.caseId()));
            }
            assertEquals(0, count(database, "SELECT count(*) FROM pf_process_start_order WHERE state = 'PENDING'"));
            System.out.printf("Störfall: %d Annahmen während %s Ausfall; Rückstau nach %d s abgebaut.%n",
                    accepted.size(), outage, catchUp.toSeconds());

            for (Long instance : database.queryForList("SELECT process_instance_key FROM pf_case_process", Long.class)) {
                engine.newCancelInstanceCommand(instance).send().join();
            }
        }
    }

    private Submission submitAsGuest(JdbcTemplate database, Browser browser, int index, boolean retry) throws Exception {
        String page = browser.get("/mieter/fall").body();
        String email = "guest-" + index + "-" + UUID.randomUUID() + "@example.test";
        Map<String, String> form = intake(match(CSRF, page), match(OPERATION, page), index);
        form.put("email", email);
        HttpResponse<String> response = browser.post("/mieter/fall", form);
        assertEquals(303, response.statusCode(), "Gasteinreichung " + index + " wurde nicht angenommen");
        String location = response.headers().firstValue("Location").orElseThrow();
        if (retry) {
            // Eine identische Wiederholung zählt einmal und liefert denselben Link.
            assertEquals(location, browser.post("/mieter/fall", form).headers().firstValue("Location").orElseThrow());
        }
        String caseId = database.queryForObject("SELECT id FROM pf_case WHERE contact_email = ?", String.class, email);
        browser.close();
        return new Submission(caseId, location, null);
    }

    private Submission submitAsTenant(Browser browser, String email, int index) throws Exception {
        String page = browser.get("/mieter/fall").body();
        Map<String, String> form = intake(match(CSRF, page), match(OPERATION, page), index);
        form.put("accountSubmission", "true");
        HttpResponse<String> response = browser.post("/mieter/fall", form);
        assertEquals(303, response.statusCode(), "Mietereinreichung " + index + " wurde nicht angenommen");
        String location = response.headers().firstValue("Location").orElseThrow();
        assertTrue(location.startsWith("/mieter/faelle/"));
        return new Submission(location.substring(location.lastIndexOf('/') + 1), location, email);
    }

    private void register(Browser browser, String email) throws Exception {
        String csrf = match(CSRF, browser.get("/registrieren").body());
        mailbox.forget(email);
        assertEquals(200, browser.post("/registrieren/code", Map.of("csrf", csrf, "email", email)).statusCode());
        assertEquals(303, browser.post("/registrieren/pruefen", Map.of("csrf", csrf, "code", mailbox.await(email))).statusCode());
    }

    private void signIn(Browser browser, String email) throws Exception {
        String csrf = match(CSRF, browser.get("/mieter/anmelden").body());
        mailbox.forget(email);
        assertEquals(200, browser.post("/mieter/anmelden/code", Map.of("csrf", csrf, "email", email)).statusCode());
        assertEquals(303, browser.post("/mieter/anmelden/pruefen", Map.of("csrf", csrf, "code", mailbox.await(email))).statusCode());
    }

    private void assertReadable(Browser browser, Submission submission) throws Exception {
        HttpResponse<String> page = browser.get(submission.location());
        assertEquals(200, page.statusCode(), "Fall " + submission.caseId() + " ist nach dem Neustart nicht lesbar");
        assertTrue(page.body().contains(submission.caseId()));
        assertTrue(page.body().contains("Eingegangen"));
    }

    private static Map<String, String> intake(String csrf, String operation, int index) {
        return new HashMap<>(Map.of("csrf", csrf, "operation", operation, "title", "Heizung kalt " + index, "unit", "Wohnung " + index,
                "description", "Seit dem Morgen bleiben die Heizkörper kalt."));
    }

    private ConfigurableApplicationContext start(String engineUrl, long definitionKey) {
        return new SpringApplicationBuilder(PropertyflowApplication.class)
                .web(WebApplicationType.SERVLET)
                .profiles("propertyflow-backend")
                .initializers(context -> ((GenericApplicationContext) context).registerBean(Mailbox.class, () -> mailbox,
                        definition -> definition.setPrimary(true)))
                .run("--server.address=127.0.0.1", "--server.port=0", "--spring.config.import=", "--spring.docker.compose.enabled=false",
                        "--server.servlet.session.persistent=false", "--server.servlet.session.cookie.secure=false",
                        "--spring.datasource.url=" + System.getenv("PROPERTYFLOW_TEST_DATABASE_URL"),
                        "--spring.datasource.username=propertyflow_test", "--spring.datasource.password=propertyflow_test",
                        "--spring.datasource.hikari.schema=" + schema, "--spring.flyway.default-schema=" + schema,
                        "--spring.flyway.schemas=" + schema,
                        "--propertyflow.links.encryption-key=" + encryptionKey,
                        "--propertyflow.case-mail.enabled=false", "--propertyflow.mcp.backend.enabled=false",
                        // Die Neuanmeldung nach dem Neustart darf bei kurzen lokalen Läufen nicht an der Code-Sperrfrist scheitern.
                        "--propertyflow.tenant.otp.resend-interval=1s",
                        "--spring.ai.model.chat=none",
                        "--propertyflow.workflow.enabled=true", "--propertyflow.workflow.definition-key=" + definitionKey,
                        "--propertyflow.workflow.initial-delay-ms=1000", "--propertyflow.workflow.poll-delay-ms=1000",
                        "--camunda.client.rest-address=" + engineUrl, "--camunda.client.prefer-rest-over-grpc=true",
                        "--camunda.client.request-timeout=2s", "--camunda.client.worker.defaults.stream-enabled=false",
                        "--camunda.client.worker.defaults.poll-interval=500ms",
                        "--logging.level.io.camunda.client=ERROR");
    }

    private JdbcTemplate isolatedDatabase(ConfigurableApplicationContext context) {
        JdbcTemplate database = context.getBean(JdbcTemplate.class);
        assertEquals("propertyflow_test", database.queryForObject("SELECT current_database()", String.class));
        assertEquals(schema, database.queryForObject("SELECT current_schema()", String.class));
        return database;
    }

    private void dropSchema() {
        try (ConfigurableApplicationContext cleanup = new SpringApplicationBuilder(PropertyflowApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("propertyflow-backend")
                .run("--spring.config.import=", "--spring.docker.compose.enabled=false",
                        "--spring.datasource.url=" + System.getenv("PROPERTYFLOW_TEST_DATABASE_URL"),
                        "--spring.datasource.username=propertyflow_test", "--spring.datasource.password=propertyflow_test",
                        "--spring.datasource.hikari.schema=" + schema, "--spring.flyway.default-schema=" + schema,
                        "--spring.flyway.schemas=" + schema, "--propertyflow.links.encryption-key=" + encryptionKey,
                        "--propertyflow.workflow.enabled=false")) {
            JdbcTemplate database = isolatedDatabase(cleanup);
            database.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    private static long count(JdbcTemplate database, String sql, Object... arguments) {
        return database.queryForObject(sql, Long.class, arguments);
    }

    private static String match(Pattern pattern, String html) {
        Matcher matcher = pattern.matcher(html);
        assertTrue(matcher.find(), "Formularfeld fehlt: " + pattern.pattern());
        return matcher.group(1);
    }

    private static void waitUntil(long nanoTime) throws InterruptedException {
        long remaining = nanoTime - System.nanoTime();
        if (remaining > 0) {
            Thread.sleep(Duration.ofNanos(remaining).toMillis());
        }
    }

    private static URI baseUrl(ConfigurableApplicationContext context) {
        return URI.create("http://127.0.0.1:" + context.getEnvironment().getRequiredProperty("local.server.port"));
    }

    private record Submission(String caseId, String location, String tenantEmail) {
    }

    private static final class Browser implements AutoCloseable {

        private final URI base;
        private final HttpClient client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        private Browser(URI base) {
            this.base = base;
        }

        HttpResponse<String> get(String path) throws Exception {
            return client.send(HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(10)).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }

        HttpResponse<String> post(String path, Map<String, String> fields) throws Exception {
            String body = fields.entrySet().stream()
                    .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "="
                            + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&"));
            HttpRequest request = HttpRequest.newBuilder(base.resolve(path))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }

        @Override
        public void close() {
            client.close();
        }
    }

    /**
     * Fängt Einmalcodes ab, damit sich Testmieter ohne Mailserver registrieren und neu anmelden können.
     */
    static final class Mailbox implements StaffCodeDelivery, VerificationCodeDelivery {

        private final Map<String, String> codes = new ConcurrentHashMap<>();

        @Override
        public void send(String email, String code) {
            codes.put(email, code);
        }

        void forget(String email) {
            codes.remove(email);
        }

        String await(String email) throws InterruptedException {
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (!codes.containsKey(email) && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertTrue(codes.containsKey(email), "Testcode wurde nicht zugestellt");
            return codes.get(email);
        }
    }
}
