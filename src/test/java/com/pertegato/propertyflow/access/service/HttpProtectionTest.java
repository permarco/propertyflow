package com.pertegato.propertyflow.access.service;

import com.pertegato.propertyflow.access.demo.DemoAccounts;
import com.pertegato.propertyflow.caseprocessing.demo.DemoCases;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=",
        "server.servlet.context-path=/http-test",
        "propertyflow.staff.bootstrap.email=staff-http@example.test",
        "propertyflow.tenant.demo.email=tenant-http@example.test"
})
@ActiveProfiles("frontend-demo")
@Import(HttpProtectionTest.LoginConfiguration.class)
class HttpProtectionTest {

    private static final String CONTEXT = "/http-test";
    private static final String STAFF_EMAIL = "staff-http@example.test";
    private static final String TENANT_EMAIL = "tenant-http@example.test";
    private static final Pattern CSRF = Pattern.compile("name=\"csrf\"[^>]*value=\"([^\"]+)\"");
    private static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; "
            + "object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'";

    @LocalServerPort
    int port;

    @Autowired
    DemoCases cases;

    @Autowired
    TestMailbox mailbox;

    @Autowired
    LoginClock clock;

    private final List<Client> clients = new ArrayList<>();

    @AfterEach
    void closeClients() {
        clients.forEach(client -> client.http.close());
    }

    @ParameterizedTest
    @CsvSource({"/anmelden,200", "/registrieren,200", "/mieter/fall,200", "/mieter/faelle,401",
            "/mitarbeiter/faelle,303", "/mieter/fall/zugang/unknown,404"})
    void publicAndDeniedGetAndHeadResponsesCarryProtection(String path, int status) throws Exception {
        Client client = client();
        protectedResponse(client.send("GET", path, Map.of()), status);
        HttpResponse<String> head = client.send("HEAD", path, Map.of());
        protectedResponse(head, status);
        assertEquals("", head.body());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void realCookiesRotateAtLoginAndOldOrUrlSessionsCannotAuthenticate(boolean tenant) throws Exception {
        Client client = client();
        HttpResponse<String> form = client.send("GET", "/anmelden", Map.of());
        cookieProtection(form);
        String oldId = client.sessionId();
        String oldCsrf = csrf(form);
        HttpResponse<String> verified = login(client, tenant, oldCsrf);
        cookieProtection(verified);
        String currentId = client.sessionId();
        assertNotEquals(oldId, currentId);
        String path = tenant ? "/mieter/faelle" : "/mitarbeiter/faelle";
        assertEquals(CONTEXT + path, verified.headers().firstValue("Location").orElseThrow());
        HttpResponse<String> page = client.send("GET", path, Map.of());
        protectedResponse(page, 200);
        assertNotEquals(oldCsrf, csrf(page));
        assertFalse(page.body().contains(";jsessionid="));

        for (String id : List.of(oldId, currentId)) {
            Client outsider = client();
            HttpResponse<String> rewritten = outsider.send("GET", path + ";jsessionid=" + id, Map.of());
            protectedResponse(rewritten, tenant ? 401 : 303);
        }
        Client oldSession = client();
        HttpCookie oldCookie = new HttpCookie("JSESSIONID", oldId);
        oldCookie.setPath(CONTEXT);
        oldSession.cookies.getCookieStore().add(base(), oldCookie);
        protectedResponse(oldSession.send("GET", path, Map.of()), tenant ? 401 : 303);

        protectedResponse(client.post("/abmelden", Map.of("csrf", oldCsrf)), 403);
        protectedResponse(client.send("GET", path, Map.of()), 200);
        protectedResponse(client.post("/abmelden", Map.of("csrf", csrf(page))), 303);
        protectedResponse(client.send("GET", path, Map.of()), tenant ? 401 : 303);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void authenticatedSuccessAndWriteErrorsKeepHeadersAndProtectCaseState(boolean tenant) throws Exception {
        Client client = client();
        login(client, tenant, csrf(client.send("GET", "/anmelden", Map.of())));
        String token = cases.create(UUID.randomUUID().toString(), "HTTP-Schutz", "Wohnung 1",
                tenant ? TENANT_EMAIL : STAFF_EMAIL, "Vertraulicher Fallinhalt", UUID.randomUUID().toString());
        String id = cases.tenant(token).id();
        String path = (tenant ? "/mieter/faelle/" : "/mitarbeiter/faelle/") + id;
        HttpResponse<String> page = client.send("GET", path, Map.of());
        protectedResponse(page, 200);
        protectedResponse(client.send("HEAD", path, Map.of()), 200);
        String csrf = csrf(page);
        DemoCases.View before = cases.get(id);
        String operation = UUID.randomUUID().toString();
        protectedResponse(client.post(path + "/nachrichten",
                Map.of("csrf", "wrong", "text", "Nicht speichern", "operation", operation)), 403);
        protectedResponse(client.post(path + "/nachrichten",
                Map.of("csrf", csrf, "text", " ", "operation", operation)), 400);
        assertEquals(before, cases.get(id));

        HttpResponse<String> saved = client.post(path + "/nachrichten",
                Map.of("csrf", csrf, "text", "Einmal speichern", "operation", operation));
        protectedResponse(saved, 303);
        assertEquals(CONTEXT + path, URI.create(saved.headers().firstValue("Location").orElseThrow()).getPath());
        DemoCases.View after = cases.get(id);
        protectedResponse(client.post(path + "/nachrichten",
                Map.of("csrf", csrf, "text", "Anderer Inhalt", "operation", operation)), 409);
        assertEquals(after, cases.get(id));
        protectedResponse(client.send("GET", path.replace(id, "unknown"), Map.of()), 404);
        protectedResponse(client.send("GET", tenant ? "/mitarbeiter/faelle" : "/mieter/faelle", Map.of()), 403);
        protectedResponse(client.send("HEAD", tenant ? "/mitarbeiter/faelle" : "/mieter/faelle", Map.of()), 403);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/anmelden", "/registrieren"})
    void loginErrorAndRateLimitResponsesKeepProtection(String path) throws Exception {
        Client client = client();
        String csrf = csrf(client.send("GET", path, Map.of()));
        String email = "http-new-" + UUID.randomUUID() + "@example.test";
        protectedResponse(client.post(path + "/code", Map.of("csrf", "wrong", "email", email)), 403);
        protectedResponse(client.post(path + "/code", Map.of("csrf", csrf, "email", "invalid")), 400);
        protectedResponse(client.post(path + "/code", Map.of("csrf", csrf, "email", email)), 200);
        protectedResponse(client.post(path + "/code", Map.of("csrf", csrf, "email", email)), 429);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void loginReturnLocationStaysLocalAndRespectsTheAccountRole(boolean tenant) throws Exception {
        Client client = client();
        String target = "/mitarbeiter/faelle?q=https%3A%2F%2Fexample.invalid&sort=title";
        HttpResponse<String> denied = client.send("GET", target, Map.of());
        protectedResponse(denied, 303);
        assertEquals(CONTEXT + "/anmelden", denied.headers().firstValue("Location").orElseThrow());
        String csrf = csrf(client.send("GET", "/anmelden?returnTo=https://example.invalid", Map.of()));
        HttpResponse<String> verified = login(client, tenant, csrf);
        assertEquals(CONTEXT + (tenant ? "/mieter/faelle" : target), verified.headers().firstValue("Location").orElseThrow());
    }

    private HttpResponse<String> login(Client client, boolean tenant, String csrf) throws Exception {
        // Only OTP throttling time is controlled; HTTP sessions use the real container.
        clock.now.updateAndGet(instant -> instant.plus(Duration.ofHours(1)));
        String email = tenant ? TENANT_EMAIL : STAFF_EMAIL;
        protectedResponse(client.post("/anmelden/code", Map.of("csrf", csrf, "email", email)), 200);
        HttpResponse<String> verified = client.post("/anmelden/pruefen", Map.of("csrf", csrf, "code", mailbox.codes.get(email)));
        protectedResponse(verified, 303);
        return verified;
    }

    private static void protectedResponse(HttpResponse<String> response, int status) {
        assertEquals(status, response.statusCode(), () -> response.uri() + "\n" + response.body());
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals("no-referrer", response.headers().firstValue("Referrer-Policy").orElseThrow());
        assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElseThrow());
        assertEquals(CSP, response.headers().firstValue("Content-Security-Policy").orElseThrow());
    }

    private static void cookieProtection(HttpResponse<String> response) {
        String cookie = response.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("JSESSIONID=")).findFirst().orElseThrow();
        assertTrue(cookie.contains("HttpOnly"), cookie);
        assertTrue(cookie.contains("SameSite=Lax"), cookie);
        assertTrue(cookie.contains("Path=" + CONTEXT), cookie);
        assertFalse(cookie.contains("Domain="), cookie);
    }

    private static String csrf(HttpResponse<String> response) {
        Matcher matcher = CSRF.matcher(response.body());
        assertTrue(matcher.find(), "Rendered page must contain a CSRF token");
        return matcher.group(1);
    }

    private URI base() {
        return URI.create("http://127.0.0.1:" + port + CONTEXT);
    }

    private Client client() {
        Client client = new Client();
        clients.add(client);
        return client;
    }

    private class Client {

        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        private final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies)
                .followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(5)).build();

        private HttpResponse<String> send(String method, String path, Map<String, String> form) throws Exception {
            String body = form.entrySet().stream().map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                    + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
            HttpRequest request = HttpRequest.newBuilder(URI.create(base() + path)).timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .method(method, body.isEmpty() ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build();
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        }

        private HttpResponse<String> post(String path, Map<String, String> form) throws Exception {
            return send("POST", path, form);
        }

        private String sessionId() {
            return cookies.getCookieStore().getCookies().stream().filter(cookie -> cookie.getName().equals("JSESSIONID"))
                    .map(HttpCookie::getValue).findFirst().orElseThrow();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class LoginConfiguration {

        @Bean
        TestMailbox httpTestMailbox() {
            return new TestMailbox();
        }

        @Bean
        LoginClock httpTestClock() {
            return new LoginClock();
        }

        @Bean
        @Primary
        StaffLogin httpTestLogin(DemoAccounts accounts, TestMailbox mailbox, LoginClock clock) {
            return new StaffLogin(accounts, mailbox.codes::put, clock, Runnable::run);
        }
    }

    static class TestMailbox {

        private final Map<String, String> codes = new ConcurrentHashMap<>();
    }

    static class LoginClock extends Clock {

        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-14T08:00:00Z"));

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant(), zone);
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }
}
