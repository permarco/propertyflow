package com.pertegato.propertyflow.access.web;

import com.pertegato.propertyflow.access.contract.VerificationCodeDelivery;
import com.pertegato.propertyflow.access.service.StaffCodeDelivery;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Übertragene Demo-Verträge gegen echte Controller, Konten und PostgreSQL; ohne Ersatzdatenbank.
 * Die Webtests laufen unabhängig von der lokalen MCP-Konfiguration.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=",
        "propertyflow.workflow.enabled=false",
        "propertyflow.case-mail.enabled=false",
        "propertyflow.mcp.backend.enabled=false"
})
@ActiveProfiles("propertyflow-backend")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_DATABASE_URL", matches = ".+")
@Import(BackendAccessParityPostgresTest.MailConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BackendAccessParityPostgresTest {

    private static final String SCHEMA = "access_parity_" + UUID.randomUUID().toString().replace("-", "");
    private static final Pattern CSRF = Pattern.compile("name=\"csrf\"[^>]*value=\"([^\"]+)\"");

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate database;

    @Autowired
    private Mailbox mailbox;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry properties) {
        String url = System.getenv("PROPERTYFLOW_TEST_DATABASE_URL");
        if (url == null || !url.matches("jdbc:postgresql://[^/]+/propertyflow_test(?:\\?.*)?")) {
            throw new IllegalArgumentException("Nur die isolierte Datenbank propertyflow_test ist erlaubt.");
        }
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", () -> "propertyflow_test");
        properties.add("spring.datasource.password", () -> "propertyflow_test");
        properties.add("spring.datasource.hikari.schema", () -> SCHEMA);
        properties.add("spring.flyway.default-schema", () -> SCHEMA);
        properties.add("spring.flyway.schemas", () -> SCHEMA);
        properties.add("propertyflow.links.encryption-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
    }

    @BeforeEach
    void onlyUseIsolatedTestDatabase() {
        assertEquals("propertyflow_test", database.queryForObject("SELECT current_database()", String.class));
        assertEquals(SCHEMA, database.queryForObject("SELECT current_schema()", String.class));
    }

    @AfterAll
    void removeOnlyOwnSchema() {
        assertEquals("propertyflow_test", database.queryForObject("SELECT current_database()", String.class));
        database.execute("DROP SCHEMA " + SCHEMA + " CASCADE");
    }

    @Test
    void intakeCsrfValidationAndChangedRetryPreservePersistentState() throws Exception {
        try (Browser browser = new Browser()) {
            String csrf = csrf(browser.get("/mieter/fall"));
            Map<String, String> fields = intake(csrf, "csrf-" + UUID.randomUUID());
            int before = count("pf_case");
            fields.put("csrf", "wrong");
            assertEquals(403, browser.post("/mieter/fall", fields).statusCode());
            assertEquals(before, count("pf_case"));
            fields.put("csrf", csrf);
            fields.put("description", "x".repeat(2001));
            assertEquals(400, browser.post("/mieter/fall", fields).statusCode());
            assertEquals(before, count("pf_case"));
            fields.put("description", "Kalt.\r\nBitte prüfen.");
            HttpResponse<String> accepted = browser.post("/mieter/fall", fields);
            assertEquals(303, accepted.statusCode());
            String location = accepted.headers().firstValue("Location").orElseThrow();
            assertEquals(location, browser.post("/mieter/fall", fields).headers().firstValue("Location").orElseThrow());
            fields.put("title", "Geänderter Wiederholungsversuch");
            assertEquals(409, browser.post("/mieter/fall", fields).statusCode());
            assertEquals(before + 1, count("pf_case"));
            assertTrue(browser.get(location).body().contains("Kalt.\nBitte prüfen."));
        }
    }

    @Test
    void guestOperationNamespacesAreIsolatedAndHtmlStaysEscaped() throws Exception {
        try (Browser first = new Browser(); Browser second = new Browser()) {
            String operation = UUID.randomUUID().toString();
            Map<String, String> a = intake(csrf(first.get("/mieter/fall")), operation);
            Map<String, String> b = intake(csrf(second.get("/mieter/fall")), operation);
            a.put("title", "<script>alert(1)</script>");
            String firstLocation = first.post("/mieter/fall", a).headers().firstValue("Location").orElseThrow();
            String secondLocation = second.post("/mieter/fall", b).headers().firstValue("Location").orElseThrow();
            assertNotEquals(firstLocation, secondLocation);
            String html = first.get(firstLocation).body();
            assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
            assertFalse(html.contains("<script>alert(1)</script>"));
            assertFalse(html.contains("staff-memo"));
        }
    }

    @Test
    void staffOtpCsrfRotationLogoutAndRevocationUseRealPersistentAccount() throws Exception {
        String email = staff();
        try (Browser browser = new Browser()) {
            String csrf = csrf(browser.get("/anmelden"));
            String oldSession = browser.sessionId();
            assertEquals(403, browser.post("/anmelden/code", Map.of("csrf", "wrong", "email", email)).statusCode());
            assertFalse(mailbox.codes.containsKey(email));
            assertEquals(200, browser.post("/anmelden/code", Map.of("csrf", csrf, "email", email)).statusCode());
            String code = mailbox.await(email);
            assertEquals(403, browser.post("/anmelden/pruefen", Map.of("csrf", "wrong", "code", code)).statusCode());
            HttpResponse<String> verified = browser.post("/anmelden/pruefen", Map.of("csrf", csrf, "code", code));
            assertEquals(303, verified.statusCode());
            assertProtectedSessionCookie(verified);
            assertNotEquals(oldSession, browser.sessionId());
            HttpResponse<String> page = browser.get("/mitarbeiter/faelle");
            assertEquals(200, page.statusCode());
            String fresh = csrf(page);
            assertNotEquals(csrf, fresh);
            assertEquals(403, browser.post("/abmelden", Map.of("csrf", csrf)).statusCode());
            assertEquals(200, browser.get("/mitarbeiter/faelle").statusCode());
            database.update("UPDATE pf_staff_account SET staff_role = false WHERE email = ?", email);
            assertEquals(403, browser.get("/mitarbeiter/faelle").statusCode());
            assertEquals(303, browser.post("/abmelden", Map.of("csrf", fresh)).statusCode());
            assertEquals(303, browser.get("/mitarbeiter/faelle").statusCode());
        }
    }

    @Test
    void unknownStaffAndDeniedResendDoNotRevealIdentityOrConsumeValidCode() throws Exception {
        String email = staff();
        try (Browser browser = new Browser(); Browser unknown = new Browser()) {
            String csrf = csrf(browser.get("/anmelden"));
            assertEquals(200, browser.post("/anmelden/code", Map.of("csrf", csrf, "email", email)).statusCode());
            String code = mailbox.await(email);
            HttpResponse<String> denied = browser.post("/anmelden/code", Map.of("csrf", csrf));
            assertEquals(429, denied.statusCode());
            assertTrue(denied.body().contains("login-code-verify"));
            assertEquals(303, browser.post("/anmelden/pruefen", Map.of("csrf", csrf, "code", code)).statusCode());
            String unknownEmail = UUID.randomUUID() + "@example.test";
            String unknownCsrf = csrf(unknown.get("/anmelden"));
            HttpResponse<String> response = unknown.post("/anmelden/code", Map.of("csrf", unknownCsrf, "email", unknownEmail));
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("Wenn für diese Adresse ein berechtigtes Konto besteht"));
            assertFalse(mailbox.codes.containsKey(unknownEmail));
            assertEquals(400, unknown.post("/anmelden/pruefen", Map.of("csrf", unknownCsrf, "code", "123456")).statusCode());
        }
    }

    @Test
    void tenantCanRegisterWithoutCasesAndSubmitFirstCaseWithVerifiedAddress() throws Exception {
        String email = "tenant-" + UUID.randomUUID() + "@example.test";
        try (Browser tenant = new Browser()) {
            HttpResponse<String> anonymous = tenant.get("/mieter/faelle");
            assertEquals(303, anonymous.statusCode());
            String loginPath = anonymous.headers().firstValue("Location").orElseThrow();
            assertEquals("/mieter/anmelden", loginPath);
            assertTrue(tenant.get(loginPath).body().contains("href=\"/registrieren\">Mieterkonto eröffnen"));
            int casesBeforeRegistration = count("pf_case");
            HttpResponse<String> registration = tenant.get("/registrieren");
            assertTrue(registration.body().contains("Ihr erstes Anliegen aus dem Konto melden"));
            String csrf = csrf(registration);
            assertEquals(200, tenant.post("/registrieren/code", Map.of("csrf", csrf, "email", email)).statusCode());
            assertEquals(0, database.queryForObject("SELECT count(*) FROM pf_tenant_account WHERE email = ?", Integer.class, email));
            String code = mailbox.await(email);
            assertEquals(403, tenant.post("/registrieren/pruefen", Map.of("csrf", "wrong", "code", code)).statusCode());
            HttpResponse<String> verified = tenant.post("/registrieren/pruefen", Map.of("csrf", csrf, "code", code));
            assertEquals(303, verified.statusCode());
            assertProtectedSessionCookie(verified);
            assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_tenant_account WHERE email = ?", Integer.class, email));
            assertEquals(casesBeforeRegistration, count("pf_case"));
            assertEquals("/mieter/faelle", verified.headers().firstValue("Location").orElseThrow());
            HttpResponse<String> emptyOverview = tenant.get("/mieter/faelle");
            assertEquals(200, emptyOverview.statusCode());
            assertTrue(emptyOverview.body().contains("noch keine Fälle vorhanden"));
            assertTrue(emptyOverview.body().contains("href=\"/mieter/fall\">Neues Anliegen melden"));
            assertEquals(303, tenant.get("/mitarbeiter/faelle").statusCode());
            Map<String, String> fields = intake(csrf(tenant.get("/mieter/fall")), UUID.randomUUID().toString());
            fields.put("accountSubmission", "true");
            fields.put("email", "spoofed@example.test");
            HttpResponse<String> accepted = tenant.post("/mieter/fall", fields);
            assertEquals(303, accepted.statusCode());
            String location = accepted.headers().firstValue("Location").orElseThrow();
            assertTrue(location.startsWith("/mieter/faelle/"));
            String caseId = location.substring(location.lastIndexOf('/') + 1);
            assertEquals(email, database.queryForObject("SELECT contact_email FROM pf_case WHERE id = ?", String.class, caseId));
            assertEquals(200, tenant.get(location).statusCode());
            assertTrue(tenant.get("/mieter/faelle").body().contains("href=\"" + location + "\""));
            try (Browser stranger = new Browser()) {
                assertEquals(404, stranger.get(location).statusCode());
            }
            String logoutCsrf = csrf(tenant.get("/mieter/faelle"));
            assertEquals(303, tenant.post("/abmelden", Map.of("csrf", logoutCsrf)).statusCode());
            HttpResponse<String> rejected = tenant.post("/mieter/fall", fields);
            assertEquals(403, rejected.statusCode());
            assertTrue(rejected.body().contains(fields.get("title")));
            assertTrue(rejected.body().contains(fields.get("description")));
            fields.put("csrf", csrf(tenant.get("/mieter/fall")));
            assertEquals(401, tenant.post("/mieter/fall", fields).statusCode());
            // Eine neue Gastanlage verlangt ein frisches Formular und eine bewusst angegebene Kontaktadresse.
            String guestEmail = "guest-" + UUID.randomUUID() + "@example.test";
            fields.put("accountSubmission", "false");
            fields.put("email", guestEmail);
            HttpResponse<String> guest = tenant.post("/mieter/fall", fields);
            assertEquals(303, guest.statusCode());
            assertTrue(guest.headers().firstValue("Location").orElseThrow().startsWith("/mieter/fall/zugang/"));
            assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_case WHERE contact_email = ?", Integer.class, guestEmail));
            assertEquals(email, database.queryForObject("SELECT contact_email FROM pf_case WHERE id = ?", String.class, caseId));
        }
    }

    @Test
    void publicDeniedAndHeadResponsesCarryProtectionAndUrlSessionsCannotAuthenticate() throws Exception {
        try (Browser browser = new Browser()) {
            for (String path : new String[] {"/mieter/fall", "/mieter/fall/zugang/invalid", "/mitarbeiter/faelle", "/anmelden"}) {
                for (String method : new String[] {"GET", "HEAD"}) {
                    HttpResponse<String> response = browser.send(path, method, null);
                    assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
                    assertEquals("no-referrer", response.headers().firstValue("Referrer-Policy").orElseThrow());
                    assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElseThrow());
                    assertTrue(response.headers().firstValue("Content-Security-Policy").orElseThrow().contains("frame-ancestors 'none'"));
                }
            }
            String email = staff();
            login(browser, email);
            assertEquals(200, browser.get("/mitarbeiter/faelle").statusCode());
            try (Browser anonymous = new Browser()) {
                HttpResponse<String> response = anonymous.get("/mitarbeiter/faelle;jsessionid=" + browser.sessionId());
                assertEquals(303, response.statusCode());
                assertEquals("/anmelden", response.headers().firstValue("Location").orElseThrow());
                assertEquals(303, anonymous.get("/mitarbeiter/faelle").statusCode());
            }
            assertTrue(browser.cookies.getCookieStore().getCookies().stream().allMatch(cookie -> cookie.isHttpOnly()));
        }
    }

    @Test
    void allSevenColumnsSortPersistentValuesAndKeepMissingFollowUpsLast() throws Exception {
        String marker = "Sortierung" + UUID.randomUUID().toString().replace("-", "");
        try (Browser browser = new Browser()) {
            List<String> ids = new ArrayList<>();
            for (String title : List.of("alpha", "Beta", "Zulu")) {
                Map<String, String> fields = intake(csrf(browser.get("/mieter/fall")), UUID.randomUUID().toString());
                fields.put("title", marker + " " + title);
                fields.put("description", "Boiler-Störung: Wasser bleibt kalt.");
                assertEquals(303, browser.post("/mieter/fall", fields).statusCode());
                ids.add(database.queryForObject("SELECT id FROM pf_case WHERE title = ?", String.class, fields.get("title")));
            }
            String first = ids.get(0);
            String second = ids.get(1);
            String third = ids.get(2);
            // Nur Lesedaten im eigenen Testschema vorbereiten; Statusübergänge prüfen die separaten Camunda-Tests.
            listFixture(first, "zulu", 2, 3, 1, "HIGH", "RECEIVED");
            listFixture(second, "Alpha", 3, 1, null, "LOW", "IN_PROGRESS");
            listFixture(third, "beta", 1, 2, 2, null, "CLOSED");
            login(browser, staff());
            Map<String, List<String>> ascending = Map.of(
                    "title", List.of(first, second, third), "unit", List.of(second, third, first),
                    "received", List.of(third, first, second), "contact", List.of(second, third, first),
                    "followUp", List.of(first, third, second), "urgency", List.of(third, first, second),
                    "status", List.of(third, first, second));
            for (Map.Entry<String, List<String>> column : ascending.entrySet()) {
                assertEquals(column.getValue(), listedIds(browser, marker, column.getKey(), false), column.getKey());
                List<String> descending = "followUp".equals(column.getKey())
                        ? List.of(third, first, second) : column.getValue().reversed();
                assertEquals(descending, listedIds(browser, marker, column.getKey(), true), column.getKey() + " absteigend");
            }
            assertEquals(List.of(third, first, second), listedIds(browser, marker, "default", false));
            assertEquals(List.of(third, first, second), listedIds(browser, marker, "default", true));
            assertEquals(List.of(first), listedIds(browser, marker + " ALPHA boilerstörung zulu", "title", false));
            assertTrue(listedIds(browser, marker + " fremderBegriff", "title", false).isEmpty());
            assertTrue(listedIds(browser, marker + " guest@example.test", "title", false).isEmpty());
            database.update("UPDATE pf_case SET title = ?, last_tenant_contact_at = received_at WHERE id IN (?, ?, ?)",
                    marker, first, second, third);
            assertEquals(ids.stream().sorted().toList(), listedIds(browser, marker, "title", false));
            assertEquals(ids.stream().sorted().toList(), listedIds(browser, marker, "title", true));
        }
    }

    private void listFixture(String caseId, String unit, int received, int contact, Integer followUp, String urgency, String status) {
        Instant base = Instant.parse("2026-01-01T12:00:00Z");
        database.update("UPDATE pf_case SET assigned_unit = ?, received_at = ?, last_tenant_contact_at = ?, "
                + "follow_up_due_at = ?, urgency = ?, urgency_source = ?, status = ? WHERE id = ?",
                unit, Timestamp.from(base.plusSeconds(received * 86400L)),
                Timestamp.from(base.plusSeconds(contact * 86400L)),
                followUp == null ? null : Timestamp.from(base.plusSeconds(followUp * 86400L)),
                urgency, urgency == null ? null : "STAFF", status, caseId);
    }

    private List<String> listedIds(Browser browser, String query, String sort, boolean descending) throws Exception {
        HttpResponse<String> response = browser.get("/mitarbeiter/faelle?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&closed=true&sort=" + sort + "&desc=" + descending);
        assertEquals(200, response.statusCode());
        return Pattern.compile("data-case=\"([^\"]+)\"").matcher(response.body()).results().map(match -> match.group(1)).toList();
    }

    private String staff() {
        String email = "staff-" + UUID.randomUUID() + "@example.test";
        database.update("INSERT INTO pf_staff_account(id, name, email, active, staff_role, account_administration) "
                + "VALUES (?, ?, ?, true, true, false)",
                UUID.randomUUID(), "Testmitarbeiter", email);
        return email;
    }

    private void login(Browser browser, String email) throws Exception {
        String csrf = csrf(browser.get("/anmelden"));
        assertEquals(200, browser.post("/anmelden/code", Map.of("csrf", csrf, "email", email)).statusCode());
        assertEquals(303, browser.post("/anmelden/pruefen", Map.of("csrf", csrf, "code", mailbox.await(email))).statusCode());
    }

    private int count(String table) {
        return database.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private static Map<String, String> intake(String csrf, String operation) {
        return new HashMap<>(Map.of("csrf", csrf, "operation", operation, "title", "Heizung kalt", "unit", "Wohnung 4",
                "email", "guest@example.test", "description", "Bitte prüfen."));
    }

    private static void assertProtectedSessionCookie(HttpResponse<String> response) {
        assertTrue(response.headers().allValues("Set-Cookie").stream()
                .anyMatch(cookie -> cookie.startsWith("JSESSIONID=") && cookie.contains("HttpOnly") && cookie.contains("SameSite=Lax")));
    }

    private static String csrf(HttpResponse<String> response) {
        Matcher match = CSRF.matcher(response.body());
        assertTrue(match.find(), "CSRF-Feld fehlt in Antwort " + response.statusCode());
        return match.group(1);
    }

    private final class Browser implements AutoCloseable {
        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        private final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).followRedirects(HttpClient.Redirect.NEVER).build();

        HttpResponse<String> get(String path) throws Exception {
            return send(path, "GET", null);
        }

        HttpResponse<String> post(String path, Map<String, String> fields) throws Exception {
            String body = fields.entrySet().stream().map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                    + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
            return send(path, "POST", body);
        }

        HttpResponse<String> send(String path, String method, String body) throws Exception {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(10));
            request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
            if (body != null) {
                request.header("Content-Type", "application/x-www-form-urlencoded");
            }
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }

        String sessionId() {
            return cookies.getCookieStore().getCookies().stream().filter(cookie -> cookie.getName().equals("JSESSIONID"))
                    .findFirst().orElseThrow().getValue();
        }

        @Override
        public void close() {
            client.close();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MailConfiguration {
        @Bean
        @Primary
        Mailbox mailbox() {
            return new Mailbox();
        }
    }

    static class Mailbox implements StaffCodeDelivery, VerificationCodeDelivery {
        private final Map<String, String> codes = new ConcurrentHashMap<>();

        @Override
        public void send(String email, String code) {
            codes.put(email, code);
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
