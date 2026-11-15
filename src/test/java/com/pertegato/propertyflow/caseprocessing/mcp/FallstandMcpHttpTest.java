package com.pertegato.propertyflow.caseprocessing.mcp;

import com.pertegato.propertyflow.access.service.StaffCodeDelivery;
import com.pertegato.propertyflow.caseprocessing.demo.DemoCases;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=",
        "propertyflow.mcp.enabled=true",
        "propertyflow.tenant.demo.email=" + FallstandMcpHttpTest.TENANT_EMAIL
})
@ActiveProfiles("frontend-demo")
@Import(FallstandMcpHttpTest.MailConfiguration.class)
class FallstandMcpHttpTest {

    static final String TENANT_EMAIL = "fallstand-wire@example.test";
    private static final String PROTOCOL_VERSION = "2025-11-25";
    private static final String TOOL_NAME = "fallstand_abrufen";
    private static final String ACCESS_PAGE = "/mcp-zugriff";
    private static final Pattern CSRF = Pattern.compile("name=\"csrf\"[^>]*value=\"([^\"]+)\"");
    private static final Pattern ACCESS_TOKEN = Pattern.compile("<code id=\"mcp-access-token\">([^<]+)</code>");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    int port;

    @Autowired
    DemoCases cases;

    @Autowired
    TestMailbox mailbox;

    private int lastRequestId;

    @Test
    void tenantDelegationExposesOnlyTheirCaseStatusAndStopsAfterRevocation() throws Exception {
        String ownCaseToken = cases.create("fallstand-wire", "Prüffall", "Testwohnung", TENANT_EMAIL,
                "Beschreibung für den Integrationstest", UUID.randomUUID().toString());
        String ownCaseId = cases.tenant(ownCaseToken).id();
        String foreignCaseId = "REQ-2026-001";
        assertEquals("mieter@example.ch", cases.get(foreignCaseId).email());

        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        try (HttpClient browser = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
                HttpClient agent = newClient()) {
            login(browser);

            HttpResponse<String> accessPage = get(browser, ACCESS_PAGE);
            assertEquals(200, accessPage.statusCode());
            String csrf = extract(CSRF, accessPage, "Delegationsseite enthält kein CSRF-Feld");
            assertFalse(ACCESS_TOKEN.matcher(accessPage.body()).find());
            assertEquals(403, postForm(browser, ACCESS_PAGE + "/freigeben", Map.of("csrf", "wrong", "consent", "true")).statusCode());
            assertEquals(400, postForm(browser, ACCESS_PAGE + "/freigeben", Map.of("csrf", csrf)).statusCode());

            HttpResponse<String> grant = postForm(browser, ACCESS_PAGE + "/freigeben", Map.of("csrf", csrf, "consent", "true"));
            assertEquals(200, grant.statusCode());
            assertEquals("no-store", grant.headers().firstValue("Cache-Control").orElseThrow());
            String bearer = extract(ACCESS_TOKEN, grant, "Freigabeantwort enthält kein Zugriffstoken");
            HttpResponse<String> grantedPage = get(browser, ACCESS_PAGE);
            assertEquals(200, grantedPage.statusCode());
            assertFalse(ACCESS_TOKEN.matcher(grantedPage.body()).find());
            assertEquals(401, sendMcp(browser, null, null, request("tools/list", Map.of())).statusCode());

            JsonNode initialized = rpc(agent, bearer, "initialize", Map.of("protocolVersion", PROTOCOL_VERSION,
                    "capabilities", Map.of(), "clientInfo", Map.of("name", "propertyflow-wire-test", "version", "1.0")));
            assertEquals(PROTOCOL_VERSION, initialized.path("result").path("protocolVersion").asString());
            assertEquals(202, sendMcp(agent, bearer, null, Map.of("jsonrpc", "2.0", "method", "notifications/initialized")).statusCode());

            JsonNode tools = rpc(agent, bearer, "tools/list", Map.of()).path("result").path("tools");
            assertTrue(tools.isArray());
            assertEquals(1, tools.size());
            JsonNode tool = tools.path(0);
            assertEquals(TOOL_NAME, tool.path("name").asString());
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
            assertEquals(403, sendMcp(agent, bearer, "https://attacker.example.test", request("tools/list", Map.of())).statusCode());

            String revokeCsrf = extract(CSRF, grantedPage, "Die Widerrufsseite enthält kein CSRF-Feld");
            HttpResponse<String> revoked = postForm(browser, ACCESS_PAGE + "/widerrufen", Map.of("csrf", revokeCsrf));
            assertEquals(303, revoked.statusCode());
            assertEquals(401, sendMcp(agent, bearer, null, request("tools/call",
                    Map.of("name", TOOL_NAME, "arguments", Map.of("caseId", ownCaseId)))).statusCode());
        }
    }

    @Test
    void unauthenticatedClientsCannotListTools() throws Exception {
        try (HttpClient agent = newClient()) {
            assertEquals(401, sendMcp(agent, null, null, request("tools/list", Map.of())).statusCode());
            assertEquals(401, sendMcp(agent, "invalid-token", null, request("tools/list", Map.of())).statusCode());
            assertEquals(401, sendMcp(agent, "x".repeat(43), null, request("tools/list", Map.of())).statusCode());
        }
    }

    private void login(HttpClient browser) throws Exception {
        HttpResponse<String> form = get(browser, "/anmelden");
        assertEquals(200, form.statusCode());
        String csrf = extract(CSRF, form, "Anmeldeseite enthält kein CSRF-Feld");
        assertEquals(200, postForm(browser, "/anmelden/code", Map.of("csrf", csrf, "email", TENANT_EMAIL)).statusCode());
        String code = mailbox.codes.poll(5, TimeUnit.SECONDS);
        assertNotNull(code, "Für das Mieterkonto wurde kein Einmalcode versendet");
        assertEquals(303, postForm(browser, "/anmelden/pruefen", Map.of("csrf", csrf, "code", code)).statusCode());
    }

    private JsonNode call(HttpClient agent, String bearer, Map<String, String> arguments) throws Exception {
        return rpc(agent, bearer, "tools/call", Map.of("name", TOOL_NAME, "arguments", arguments));
    }

    private JsonNode rpc(HttpClient agent, String bearer, String method, Map<String, ?> params) throws Exception {
        Map<String, Object> request = request(method, params);
        HttpResponse<String> response = sendMcp(agent, bearer, null, request);
        assertEquals(200, response.statusCode(), method);
        JsonNode envelope = JSON.readTree(response.body());
        assertEquals("2.0", envelope.path("jsonrpc").asString());
        assertEquals(request.get("id"), envelope.path("id").asInt());
        return envelope;
    }

    private Map<String, Object> request(String method, Map<String, ?> params) {
        return Map.of("jsonrpc", "2.0", "id", ++lastRequestId, "method", method, "params", params);
    }

    private HttpResponse<String> sendMcp(HttpClient client, String bearer, String origin, Map<String, ?> payload) throws Exception {
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
        return client.send(request.POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(payload))).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postForm(HttpClient client, String path, Map<String, String> form) throws Exception {
        String body = form.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                        + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
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
