package com.pertegato.propertyflow.intake.controller;

import com.pertegato.propertyflow.PropertyflowApplication;
import com.pertegato.propertyflow.access.contract.SubmissionContext.Guest;
import com.pertegato.propertyflow.caseprocessing.contract.CaseAccepted;
import com.pertegato.propertyflow.caseprocessing.contract.CreateCaseCommand;
import com.pertegato.propertyflow.intake.contract.CaseSubmission;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_DATABASE_URL", matches = ".+")
class GuestCaseRestartPostgresTest {

    private static final String FORM_PATH = "/mieter/fall";
    private static final String LINK_PREFIX = FORM_PATH + "/zugang/";
    private static final String TITLE = "Heizung kalt";
    private static final String UNIT = "Wohnung 3";
    private static final String DESCRIPTION = "Seit gestern funktionieren die Heizkörper nicht.";

    @Test
    void guestCaseAndStartOrderSurviveAppRestartAndRetryKeepsOriginalLink() throws Exception {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        String encodedKey = Base64.getEncoder().encodeToString(key);
        String email = UUID.randomUUID() + "@example.test";

        try (HttpClient originalBrowser = client(true); HttpClient linkReader = client(false)) {
            String location;
            String caseId;
            String startOrderId;
            UUID scope;
            CreateCaseCommand command;
            Map<String, String> form;

            try (ConfigurableApplicationContext app = start(encodedKey)) {
                JdbcTemplate database = testDatabase(app);
                URI base = baseUrl(app);
                HttpResponse<String> page = get(originalBrowser, base, FORM_PATH);
                assertEquals(200, page.statusCode());
                command = new CreateCaseCommand(field(page.body(), "operation"), TITLE, UNIT, DESCRIPTION, email);
                form = Map.of("csrf", field(page.body(), "csrf"), "operation", command.operationId(),
                        "title", TITLE, "unit", UNIT, "description", DESCRIPTION, "email", email);

                HttpResponse<String> accepted = post(originalBrowser, base, form);
                assertEquals(303, accepted.statusCode());
                assertProtection(accepted);
                location = accepted.headers().firstValue("Location").orElseThrow();
                assertTrue(location.startsWith(LINK_PREFIX) && location.substring(LINK_PREFIX.length()).matches("[A-Za-z0-9_-]+"),
                        "Expected one URL-safe token segment in a local redirect");

                caseId = database.queryForObject("select id from pf_case where contact_email = ?", String.class, email);
                startOrderId = startOrderId(database, caseId);
                scope = database.queryForObject("select guest_scope from pf_submission where case_id = ?", UUID.class, caseId);
                assertCaseLink(linkReader, base, location, caseId, email);

                HttpResponse<String> repeated = post(originalBrowser, base, form);
                assertEquals(303, repeated.statusCode());
                assertTrue(repeated.headers().firstValue("Location").filter(location::equals).isPresent(),
                        "An identical retry must keep the original link");
                HttpResponse<String> conflict = post(originalBrowser, base, Map.of(
                        "csrf", form.get("csrf"), "operation", command.operationId(),
                        "title", "Geänderter Betreff", "unit", UNIT, "description", DESCRIPTION, "email", email));
                assertEquals(409, conflict.statusCode());
                assertTrue(conflict.headers().firstValue("Location").isEmpty());
                assertSingleCaseAndStartOrder(database, email, caseId, startOrderId);
            }

            try (ConfigurableApplicationContext restarted = start(encodedKey)) {
                JdbcTemplate database = testDatabase(restarted);
                URI base = baseUrl(restarted);
                assertCaseLink(linkReader, base, location, caseId, email);
                assertEquals(403, post(originalBrowser, base, form).statusCode(), "A lost session must never be adopted as a new guest context");

                CaseAccepted durableReplay = restarted.getBean(CaseSubmission.class).submit(new Guest(scope), command);
                assertTrue(durableReplay.replayed());
                assertEquals(caseId, durableReplay.caseId());
                assertTrue(location.equals(LINK_PREFIX + durableReplay.personalAccessToken()),
                        "The stored replay must keep the original link after restart");
                assertSingleCaseAndStartOrder(database, email, caseId, startOrderId);
            }
        }
    }

    private ConfigurableApplicationContext start(String key) {
        return new SpringApplicationBuilder(PropertyflowApplication.class)
                .profiles("propertyflow-backend")
                .run("--spring.config.import=",
                        "--spring.datasource.url=" + System.getenv("PROPERTYFLOW_TEST_DATABASE_URL"),
                        "--spring.datasource.username=" + environment("PROPERTYFLOW_TEST_DATABASE_USER", "propertyflow_test"),
                        "--spring.datasource.password=" + environment("PROPERTYFLOW_TEST_DATABASE_PASSWORD", "propertyflow_test"),
                        "--propertyflow.links.encryption-key=" + key,
                        "--server.address=127.0.0.1",
                        "--server.port=0",
                        "--server.servlet.session.persistent=false",
                        "--server.servlet.session.cookie.secure=false",
                        "--spring.docker.compose.enabled=false");
    }

    private JdbcTemplate testDatabase(ConfigurableApplicationContext context) {
        JdbcTemplate database = context.getBean(JdbcTemplate.class);
        assertEquals("propertyflow_test", database.queryForObject("select current_database()", String.class));
        assertTrue(database.queryForObject("select version()", String.class).startsWith("PostgreSQL "));
        return database;
    }

    private String startOrderId(JdbcTemplate database, String caseId) {
        return database.queryForObject(
                "select cast(id as text) from pf_process_start_order where case_id = ? and state = 'PENDING'",
                String.class, caseId);
    }

    private void assertSingleCaseAndStartOrder(JdbcTemplate database, String email, String caseId, String startOrderId) {
        assertEquals(1L, database.queryForObject("select count(*) from pf_case where contact_email = ?", Long.class, email));
        assertEquals(1L, database.queryForObject("select count(*) from pf_process_start_order where case_id = ?", Long.class, caseId));
        assertEquals(startOrderId, startOrderId(database, caseId));
    }

    private void assertCaseLink(HttpClient reader, URI base, String location, String caseId, String email) throws Exception {
        HttpResponse<String> read = get(reader, base, location);
        assertEquals(200, read.statusCode());
        assertProtection(read);
        assertTrue(read.body().contains(caseId));
        assertTrue(read.body().contains(DESCRIPTION));
        assertTrue(read.body().contains("Eingegangen"));
        assertFalse(read.body().contains(email));
    }

    private void assertProtection(HttpResponse<String> response) {
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals("no-referrer", response.headers().firstValue("Referrer-Policy").orElseThrow());
    }

    private HttpClient client(boolean session) {
        HttpClient.Builder builder = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5));
        if (session) {
            builder.cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
        }
        return builder.build();
    }

    private HttpResponse<String> get(HttpClient client, URI base, String path) throws Exception {
        return client.send(request(base, path).GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> post(HttpClient client, URI base, Map<String, String> form) throws Exception {
        String encoded = form.entrySet().stream()
                .map(field -> field.getKey() + "=" + URLEncoder.encode(field.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        HttpRequest post = request(base, FORM_PATH)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encoded))
                .build();
        return client.send(post, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpRequest.Builder request(URI base, String path) {
        return HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(10));
    }

    private String field(String html, String name) {
        Matcher field = Pattern.compile("name=\"" + name + "\"[^>]*value=\"([^\"]+)\"").matcher(html);
        assertTrue(field.find(), "Required form value missing: " + name);
        return field.group(1);
    }

    private URI baseUrl(ConfigurableApplicationContext context) {
        WebServerApplicationContext application = (WebServerApplicationContext) context;
        return URI.create("http://127.0.0.1:" + application.getWebServer().getPort());
    }

    private String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
