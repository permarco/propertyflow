package com.pertegato.propertyflow.caseprocessing.mcp;

import com.pertegato.propertyflow.access.service.StaffCodeDelivery;
import com.pertegato.propertyflow.access.service.TenantAccountService;
import java.io.InputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
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
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=",
        "propertyflow.workflow.enabled=false",
        "propertyflow.case-mail.enabled=false",
        "propertyflow.mcp.backend.enabled=true",
        "propertyflow.mcp.oauth.issuer=" + FallstandMcpHttpTest.ISSUER,
        "propertyflow.mcp.oauth.client-id=" + FallstandMcpHttpTest.CLIENT_ID,
        "propertyflow.mcp.oauth.redirect-uri=" + FallstandMcpHttpTest.CALLBACK
})
@ActiveProfiles("propertyflow-backend")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_DATABASE_URL", matches = ".+")
@Import(FallstandMcpHttpTest.MailConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FallstandMcpHttpTest {

    private static final String TENANT_EMAIL = "fallstand-wire@example.test";
    static final String ISSUER = "https://pf.example.test";
    static final String CLIENT_ID = "fallstand-wire";
    static final String CALLBACK = "https://client.example.test/callback";
    private static final String RESOURCE = ISSUER + "/mcp";
    private static final String SCOPE = "tenant:cases:read";
    private static final String STATE = "fallstand-wire-state";
    private static final String VERIFIER = "01234567890123456789012345678901234567890123456789";
    private static final String PROTOCOL_VERSION = "2025-11-25";
    private static final String TOOL_NAME = "fallstand_abrufen";
    private static final String LOGIN = "/mieter/anmelden";
    private static final String AUTHORIZE = "/oauth2/authorize";
    private static final String CONNECTIONS = "/mieter/verbundene-apps";
    private static final String SCHEMA = "mcp_wire_" + UUID.randomUUID().toString().replace("-", "");
    private static final Pattern CSRF = Pattern.compile("name=\"csrf\"[^>]*value=\"([^\"]+)\"");
    private static final Pattern CONSENT_STATE = Pattern.compile("name=\"state\"\\s+value=\"([^\"]+)\"");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate database;

    @Autowired
    private TestMailbox mailbox;

    @Autowired
    private TenantAccountService tenantAccounts;

    private int lastRequestId;

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry properties) {
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
    void verifyTestSchema() {
        assertEquals("propertyflow_test", database.queryForObject("SELECT current_database()", String.class));
        assertEquals(SCHEMA, database.queryForObject("SELECT current_schema()", String.class));
    }

    @AfterAll
    void removeTestSchema() {
        verifyTestSchema();
        database.execute("DROP SCHEMA " + SCHEMA + " CASCADE");
    }

    @Test
    void tenantDelegationExposesOnlyTheirCaseStatusAndStopsAfterRevocation() throws Exception {
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        try (HttpClient browser = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
                HttpClient agent = newClient()) {
            String ownCaseId = submitCase(browser, TENANT_EMAIL);
            String foreignCaseId = submitCase(browser, "foreign-case@example.test");
            assertTrue(tenantAccounts.authenticate(TENANT_EMAIL, true).isPresent(), "Das Mietertestkonto konnte nicht angelegt werden.");
            String bearer = authorize(browser, agent);
            assertEquals(401, mcpStatus(browser, null, null, request("tools/list", Map.of())));

            JsonNode initialized = rpc(agent, bearer, "initialize", Map.of("protocolVersion", PROTOCOL_VERSION,
                    "capabilities", Map.of(), "clientInfo", Map.of("name", "propertyflow-wire-test", "version", "1.0")));
            assertEquals(PROTOCOL_VERSION, initialized.path("result").path("protocolVersion").asString());
            assertEquals(202, mcpStatus(agent, bearer, null, Map.of("jsonrpc", "2.0", "method", "notifications/initialized")));

            JsonNode tools = rpc(agent, bearer, "tools/list", Map.of()).path("result").path("tools");
            assertTrue(tools.isArray());
            JsonNode tool = null;
            for (JsonNode listed : tools) {
                String name = listed.path("name").asString();
                assertFalse(name.startsWith("mitarbeiter_"), "Mieterfreigaben dürfen keine Mitarbeiterwerkzeuge enthalten.");
                if (TOOL_NAME.equals(name)) {
                    tool = listed;
                }
            }
            assertNotNull(tool, "Das Werkzeug zum Lesen des Fallstands fehlt.");
            assertEquals(Set.of("caseId"), Set.copyOf(tool.path("inputSchema").path("properties").propertyNames()));
            assertFalse(tool.path("inputSchema").path("additionalProperties").asBoolean(true));

            JsonNode own = call(agent, bearer, Map.of("caseId", ownCaseId)).path("result");
            assertFalse(own.path("isError").asBoolean(false));
            JsonNode result = own.path("structuredContent");
            assertEquals(Set.of("caseId", "status", "urgency"), Set.copyOf(result.propertyNames()));
            assertEquals(ownCaseId, result.path("caseId").asString());
            assertEquals("Eingegangen", result.path("status").asString());
            assertEquals("Noch nicht bewertet", result.path("urgency").asString());
            for (JsonNode content : own.path("content")) {
                assertEquals("text", content.path("type").asString());
                assertTrue(result.equals(JSON.readTree(content.path("text").asString())),
                        "Der Textinhalt darf nur dieselben drei Felder enthalten");
            }

            JsonNode foreign = call(agent, bearer, Map.of("caseId", foreignCaseId)).path("result");
            JsonNode missing = call(agent, bearer, Map.of("caseId", "REQ-2026-999")).path("result");
            assertTrue(foreign.path("isError").asBoolean(false));
            assertFalse(foreign.hasNonNull("structuredContent"));
            JsonNode denialContent = foreign.path("content");
            assertTrue(denialContent.isArray());
            assertEquals(1, denialContent.size());
            assertEquals("Der Fall ist nicht verfügbar.", denialContent.path(0).path("text").asString());
            assertTrue(foreign.equals(missing), "Fremde und fehlende Fälle müssen dieselbe Fehlerrückmeldung liefern");

            JsonNode forged = call(agent, bearer, Map.of("caseId", ownCaseId, "email", "mieter@example.ch"));
            assertTrue(forged.hasNonNull("error") || forged.path("result").path("isError").asBoolean(false));
            assertFalse(forged.path("result").hasNonNull("structuredContent"));
            assertEquals(403, mcpStatus(agent, bearer, "https://other.example.test", request("tools/list", Map.of())));

            HttpResponse<String> connections = get(browser, CONNECTIONS);
            assertEquals(200, connections.statusCode());
            assertFalse(connections.body().contains(bearer), "Die Liste der verbundenen Apps darf kein Zugriffstoken enthalten.");
            String revokeCsrf = extract(CSRF, connections, "Die Widerrufsseite enthält kein CSRF-Feld");
            assertEquals(403, postForm(browser, CONNECTIONS + "/widerrufen", Map.of("csrf", "wrong")).statusCode());
            assertEquals(200, mcpStatus(agent, bearer, null, request("tools/list", Map.of())));
            HttpResponse<String> revoked = postForm(browser, CONNECTIONS + "/widerrufen", Map.of("csrf", revokeCsrf));
            assertEquals(303, revoked.statusCode());
            assertEquals(401, mcpStatus(agent, bearer, null, request("tools/call",
                    Map.of("name", TOOL_NAME, "arguments", Map.of("caseId", ownCaseId)))));
        }
    }

    @Test
    void unauthenticatedClientsCannotListTools() throws Exception {
        try (HttpClient agent = newClient()) {
            assertEquals(401, mcpStatus(agent, null, null, request("tools/list", Map.of())));
            assertEquals(401, mcpStatus(agent, "invalid-token", null, request("tools/list", Map.of())));
            assertEquals(401, mcpStatus(agent, "x".repeat(43), null, request("tools/list", Map.of())));
        }
    }

    private String submitCase(HttpClient browser, String email) throws Exception {
        HttpResponse<String> form = get(browser, "/mieter/fall");
        assertEquals(200, form.statusCode());
        String csrf = extract(CSRF, form, "Das Fallformular enthält kein CSRF-Feld.");
        HttpResponse<String> accepted = postForm(browser, "/mieter/fall", Map.of("csrf", csrf, "operation", UUID.randomUUID().toString(),
                "title", "Prüffall", "unit", "Testwohnung", "email", email, "description", "Beschreibung für den Integrationstest"));
        assertEquals(303, accepted.statusCode());
        return database.queryForObject("SELECT id FROM pf_case WHERE contact_email = ?", String.class, email);
    }

    private String authorize(HttpClient browser, HttpClient agent) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(VERIFIER.getBytes(StandardCharsets.US_ASCII));
        String authorizeUrl = AUTHORIZE + "?" + formBody(Map.of("response_type", "code", "client_id", CLIENT_ID, "redirect_uri", CALLBACK,
                "scope", SCOPE, "state", STATE, "resource", RESOURCE, "code_challenge_method", "S256",
                "code_challenge", Base64.getUrlEncoder().withoutPadding().encodeToString(digest)));
        HttpResponse<String> loginRequired = get(browser, authorizeUrl);
        assertEquals(303, loginRequired.statusCode());
        assertEquals(LOGIN, location(loginRequired));

        HttpResponse<String> authenticated = login(browser);
        assertTrue(location(authenticated).startsWith(AUTHORIZE + "?"), "Die Anmeldung muss zur OAuth-Freigabe zurückführen.");
        HttpResponse<String> consent = get(browser, location(authenticated));
        assertEquals(200, consent.statusCode());
        String consentState = extract(CONSENT_STATE, consent, "Die Einwilligungsseite enthält keine Vorgangsreferenz.");
        HttpResponse<String> allowed = postForm(browser, AUTHORIZE, Map.of("client_id", CLIENT_ID, "state", consentState, "scope", SCOPE));
        assertEquals(302, allowed.statusCode());
        URI redirect = URI.create(location(allowed));
        assertEquals(CALLBACK, redirect.getScheme() + "://" + redirect.getAuthority() + redirect.getPath());
        MultiValueMap<String, String> parameters = UriComponentsBuilder.fromUri(redirect).build().getQueryParams();
        assertEquals(STATE, parameters.getFirst("state"));
        String code = parameters.getFirst("code");
        assertNotNull(code, "Der OAuth-Callback enthält keinen Autorisierungscode.");

        HttpResponse<String> grant = postForm(agent, "/oauth2/token", Map.of("grant_type", "authorization_code", "client_id", CLIENT_ID,
                "redirect_uri", CALLBACK, "code", code, "code_verifier", VERIFIER, "resource", RESOURCE));
        assertEquals(200, grant.statusCode());
        String cacheControl = grant.headers().firstValue("Cache-Control").orElseThrow();
        assertTrue(Arrays.stream(cacheControl.split(",")).map(String::strip).anyMatch("no-store"::equalsIgnoreCase));
        JsonNode token = JSON.readTree(grant.body());
        assertEquals("Bearer", token.path("token_type").asString());
        assertTrue(token.path("access_token").isTextual(), "Die Tokenantwort enthält kein Zugriffstoken.");
        return token.path("access_token").asString();
    }

    private HttpResponse<String> login(HttpClient browser) throws Exception {
        HttpResponse<String> form = get(browser, LOGIN);
        assertEquals(200, form.statusCode());
        String csrf = extract(CSRF, form, "Anmeldeseite enthält kein CSRF-Feld");
        assertEquals(200, postForm(browser, LOGIN + "/code", Map.of("csrf", csrf, "email", TENANT_EMAIL)).statusCode());
        String code = mailbox.codes.poll(5, TimeUnit.SECONDS);
        assertNotNull(code, "Für das Mieterkonto wurde kein Einmalcode versendet");
        HttpResponse<String> loggedIn = postForm(browser, LOGIN + "/pruefen", Map.of("csrf", csrf, "code", code));
        assertEquals(303, loggedIn.statusCode());
        return loggedIn;
    }

    private JsonNode call(HttpClient agent, String bearer, Map<String, String> arguments) throws Exception {
        return rpc(agent, bearer, "tools/call", Map.of("name", TOOL_NAME, "arguments", arguments));
    }

    private JsonNode rpc(HttpClient agent, String bearer, String method, Map<String, ?> params) throws Exception {
        Map<String, Object> request = request(method, params);
        HttpResponse<String> response = sendMcp(agent, bearer, null, request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), method);
        JsonNode envelope = JSON.readTree(response.body());
        assertEquals("2.0", envelope.path("jsonrpc").asString());
        assertEquals(request.get("id"), envelope.path("id").asInt());
        return envelope;
    }

    private Map<String, Object> request(String method, Map<String, ?> params) {
        return Map.of("jsonrpc", "2.0", "id", ++lastRequestId, "method", method, "params", params);
    }

    private int mcpStatus(HttpClient client, String bearer, String origin, Map<String, ?> payload) throws Exception {
        HttpResponse<InputStream> response = sendMcp(client, bearer, origin, payload, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream ignored = response.body()) {
            return response.statusCode();
        }
    }

    private <T> HttpResponse<T> sendMcp(HttpClient client, String bearer, String origin, Map<String, ?> payload,
            HttpResponse.BodyHandler<T> bodyHandler) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri("/mcp"))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("MCP-Protocol-Version", PROTOCOL_VERSION);
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        if (origin != null) {
            request.header("Origin", origin);
        }
        return client.send(request.POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(payload))).build(), bodyHandler);
    }

    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postForm(HttpClient client, String path, Map<String, String> form) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody(form)))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String formBody(Map<String, String> form) {
        return form.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                        + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private static String location(HttpResponse<String> response) {
        return response.headers().firstValue("Location").orElseThrow();
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port).resolve(path);
    }

    private static HttpClient newClient() {
        return HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
    }

    private static String extract(Pattern pattern, HttpResponse<String> response, String failure) {
        Matcher matcher = pattern.matcher(response.body());
        assertTrue(matcher.find(), failure);
        return matcher.group(1);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MailConfiguration {

        @Bean
        @Primary
        TestMailbox fallstandMailbox() {
            return new TestMailbox();
        }
    }

    static class TestMailbox implements StaffCodeDelivery {

        private final BlockingQueue<String> codes = new LinkedBlockingQueue<>();

        @Override
        public void send(String email, String code) {
            if (TENANT_EMAIL.equals(email)) {
                codes.add(code);
            }
        }
    }
}
