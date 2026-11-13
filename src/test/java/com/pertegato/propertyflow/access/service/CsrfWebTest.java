package com.pertegato.propertyflow.access.service;

import com.pertegato.propertyflow.access.demo.DemoAccounts;
import com.pertegato.propertyflow.caseprocessing.demo.DemoCases;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "propertyflow.staff.bootstrap.email=csrf-staff@example.test")
@ActiveProfiles("frontend-demo")
@Import(CsrfWebTest.LoginConfiguration.class)
class CsrfWebTest {

    private static final String CSRF = "csrf";
    private static final String ACCOUNT = "propertyflow.staff.accountId";
    private static final String STAFF_EMAIL = "csrf-staff@example.test";
    private static final String INTAKE = "/mieter/fall";

    @Autowired
    WebApplicationContext context;
    @Autowired
    DemoAccounts accounts;
    @Autowired
    DemoCases cases;
    @Autowired
    TestMailbox mailbox;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        mailbox.codes.clear();
    }

    static Stream<Arguments> intakeRoles() {
        return matrix("guest", "tenant", "staff");
    }

    static Stream<Arguments> messageRoutes() {
        return matrix("token", "tenant", "staff");
    }

    static Stream<Arguments> actions() {
        return matrix("status", "unit", "urgency", "followUp", "clearFollowUp", "clearAnswer", "close", "reopen");
    }

    static Stream<Arguments> loginPaths() {
        return matrix("/anmelden", "/registrieren");
    }

    static Stream<Arguments> accountRoles() {
        return matrix("tenant", "staff");
    }

    private static Stream<Arguments> matrix(String... variants) {
        return Stream.of(variants).flatMap(variant -> Stream.of("missing", "wrong", "foreign")
                .map(token -> Arguments.of(variant, token)));
    }

    @ParameterizedTest
    @MethodSource("intakeRoles")
    void rejectedIntakeDoesNotCreateCaseOrConsumeOperation(String role, String tokenKind) throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        MockHttpSession session = session(role, email);
        String operation = UUID.randomUUID().toString();
        int count = cases.list("", "", true, "default", false, 1).total();
        mvc.perform(intake(session, email, operation, "Abgewiesen", badToken(tokenKind)))
                .andExpect(status().isForbidden());
        assertEquals(count, cases.list("", "", true, "default", false, 1).total());

        String location = mvc.perform(intake(session, email, operation, "Korrigiert", csrf(session)))
                .andExpect(status().isSeeOther())
                .andReturn().getResponse().getHeader("Location");
        assertEquals(count + 1, cases.list("", "", true, "default", false, 1).total());
        assertEquals("Korrigiert", cases.tenant(location.substring(location.lastIndexOf('/') + 1)).title());
    }

    @ParameterizedTest
    @MethodSource("messageRoutes")
    void rejectedMessagePreservesEntireCaseAndAllowsCorrectedRetry(String route, String tokenKind) throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        MockHttpSession session = session(route.equals("token") ? "guest" : route, email);
        String token = createCase(email);
        DemoCases.View before = cases.get(cases.tenant(token).id());
        String path = switch (route) {
            case "token" -> "/mieter/fall/zugang/" + token;
            case "tenant" -> "/mieter/faelle/" + before.id();
            default -> "/mitarbeiter/faelle/" + before.id();
        } + "/nachrichten";
        String operation = UUID.randomUUID().toString();
        mvc.perform(withCsrf(post(path)
                .session(session), badToken(tokenKind))
                        .param("operation", operation)
                        .param("text", "Abgewiesen"))
                .andExpect(status().isForbidden());
        assertEquals(before, cases.get(before.id()));
        mvc.perform(withCsrf(post(path)
                .session(session), csrf(session))
                        .param("operation", operation)
                        .param("text", "Korrigiert"))
                .andExpect(status().isSeeOther());
        DemoCases.View after = cases.get(before.id());
        assertEquals(before.version() + 1, after.version());
        assertEquals(before.entries().size() + 1, after.entries().size());
        assertEquals("Korrigiert", after.entries().getLast().text());
    }

    @ParameterizedTest
    @MethodSource("actions")
    void rejectedActionPreservesEntireCaseAndAllowsCorrectedRetry(String action, String tokenKind) throws Exception {
        MockHttpSession session = session("staff", STAFF_EMAIL);
        String id = cases.tenant(createCase("case-" + UUID.randomUUID() + "@example.test")).id();
        String value = switch (action) {
            case "status" -> "In Bearbeitung";
            case "unit" -> "Wohnung A";
            case "urgency" -> "Hoch";
            case "followUp" -> "days:3";
            default -> "";
        };
        if (action.equals("clearFollowUp")) {
            cases.change(id, "followUp", "days:3", "", cases.get(id).version(), "prepare");
        } else if (action.equals("reopen")) {
            cases.change(id, "close", "", "", cases.get(id).version(), "prepare");
        } else if (action.equals("clearAnswer")) {
            cases.message(id, null, "Rückfrage", false, true, "prepare");
            value = cases.get(id).entries().getLast().id();
        }
        DemoCases.View before = cases.get(id);
        String operation = UUID.randomUUID().toString();
        String path = "/mitarbeiter/faelle/" + id + "/aktion";
        mvc.perform(withCsrf(post(path)
                .session(session), badToken(tokenKind))
                        .param("action", action)
                        .param("value", value)
                        .param("reason", "Abgewiesen")
                        .param("version", Long.toString(before.version()))
                        .param("operation", operation))
                .andExpect(status().isForbidden());
        assertEquals(before, cases.get(id));
        mvc.perform(withCsrf(post(path)
                .session(session), csrf(session))
                        .param("action", action)
                        .param("value", value)
                        .param("reason", "Korrigiert")
                        .param("version", Long.toString(before.version()))
                        .param("operation", operation))
                .andExpect(status().isSeeOther());
        assertEquals(before.version() + 1, cases.get(id).version());
        assertEquals(before.entries().size() + 1, cases.get(id).entries().size());
    }

    @ParameterizedTest
    @MethodSource("loginPaths")
    void rejectedCodeRequestSendsNothingAndDoesNotConsumeRateLimit(String path, String tokenKind) throws Exception {
        String email = prepareAccount(path);
        MockHttpSession session = session("guest", email);
        mvc.perform(withCsrf(post(path + "/code")
                .session(session), badToken(tokenKind))
                .param("email", email))
                .andExpect(status().isForbidden());
        assertTrue(mailbox.codes.isEmpty());
        assertNull(session.getAttribute(ACCOUNT));
        assertEquals(path.equals("/anmelden"), accounts.findActiveByEmail(email).isPresent());
        requestCode(path, session, email);
        assertEquals(1, mailbox.codes.size());
        verifyCode(path, session, email);
    }

    @ParameterizedTest
    @MethodSource("loginPaths")
    void rejectedVerificationDoesNotConsumeCodeOrActivateSession(String path, String tokenKind) throws Exception {
        String email = prepareAccount(path);
        MockHttpSession session = session("guest", email);
        requestCode(path, session, email);
        String sessionId = session.getId();
        String originalCsrf = csrf(session);
        mvc.perform(withCsrf(post(path + "/pruefen")
                .session(session), badToken(tokenKind))
                        .param("code", mailbox.codes.get(email)))
                .andExpect(status().isForbidden());
        assertNull(session.getAttribute(ACCOUNT));
        assertEquals(sessionId, session.getId());
        assertEquals(originalCsrf, csrf(session));
        assertEquals(path.equals("/anmelden"), accounts.findActiveByEmail(email).isPresent());
        verifyCode(path, session, email);
    }

    @ParameterizedTest
    @MethodSource("accountRoles")
    void rejectedLogoutPreservesAccessAndValidLogoutInvalidatesSession(String role, String tokenKind) throws Exception {
        MockHttpSession session = session(role, UUID.randomUUID() + "@example.test");
        Object account = session.getAttribute(ACCOUNT);
        mvc.perform(withCsrf(post("/abmelden")
                .session(session), badToken(tokenKind)))
                .andExpect(status().isForbidden());
        assertFalse(session.isInvalid());
        assertEquals(account, session.getAttribute(ACCOUNT));
        mvc.perform(get(role.equals("staff") ? "/mitarbeiter/faelle" : "/mieter/faelle")
                .session(session))
                .andExpect(status().isOk());
        mvc.perform(withCsrf(post("/abmelden")
                .session(session), csrf(session)))
                .andExpect(status().isSeeOther());
        assertTrue(session.isInvalid());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/anmelden", "/registrieren"})
    void csrfFromBeforeRealLoginCannotBeUsedForWriting(String path) throws Exception {
        String email = prepareAccount(path);
        MockHttpSession session = session("guest", email);
        String oldCsrf = csrf(session);
        requestCode(path, session, email);
        verifyCode(path, session, email);
        assertNotEquals(oldCsrf, csrf(session));
        String operation = UUID.randomUUID().toString();
        int count = cases.tenantCases(email).size();
        mvc.perform(intake(session, email, operation, "Alter Token", oldCsrf))
                .andExpect(status().isForbidden());
        assertEquals(count, cases.tenantCases(email).size());
        mvc.perform(intake(session, email, operation, "Neuer Token", csrf(session)))
                .andExpect(status().isSeeOther());
        assertEquals(count + 1, cases.tenantCases(email).size());
    }

    private MockHttpSession session(String role, String email) throws Exception {
        MockHttpSession session = (MockHttpSession) mvc.perform(get(INTAKE))
                .andReturn().getRequest().getSession(false);
        if (role.equals("staff")) {
            session.setAttribute(ACCOUNT, accounts.findActiveByEmail(STAFF_EMAIL).orElseThrow().id());
        } else if (role.equals("tenant")) {
            session.setAttribute(ACCOUNT, accounts.activateVerifiedTenant(email).id());
        }
        return session;
    }

    private String badToken(String kind) throws Exception {
        return switch (kind) {
            case "missing" -> null;
            case "wrong" -> "invalid-csrf";
            case "foreign" -> csrf(session("guest", "unused@example.test"));
            default -> throw new IllegalArgumentException(kind);
        };
    }

    private String csrf(MockHttpSession session) {
        return session.getAttribute(CSRF).toString();
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request, String csrf) {
        if (csrf != null) {
            request.param(CSRF, csrf);
        }
        return request;
    }

    private MockHttpServletRequestBuilder intake(MockHttpSession session, String email, String operation, String title, String csrf) {
        return withCsrf(post(INTAKE)
                .session(session), csrf)
                .param("email", email)
                .param("title", title)
                .param("unit", "Wohnung 3")
                .param("description", "Heizung kalt")
                .param("operation", operation);
    }

    private String createCase(String email) {
        return cases.create(UUID.randomUUID().toString(), "CSRF-Test", "Wohnung 3", email, "Heizung kalt", "create");
    }

    private String prepareAccount(String path) {
        String email = UUID.randomUUID() + "@example.test";
        if (path.equals("/anmelden")) {
            accounts.activateVerifiedTenant(email);
        }
        return email;
    }

    private void requestCode(String path, MockHttpSession session, String email) throws Exception {
        mvc.perform(withCsrf(post(path + "/code")
                .session(session), csrf(session))
                .param("email", email))
                .andExpect(status().isOk());
    }

    private void verifyCode(String path, MockHttpSession session, String email) throws Exception {
        mvc.perform(withCsrf(post(path + "/pruefen")
                .session(session), csrf(session))
                .param("code", mailbox.codes.get(email)))
                .andExpect(status().isSeeOther());
        assertEquals(accounts.findActiveByEmail(email).orElseThrow().id(), session.getAttribute(ACCOUNT));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class LoginConfiguration {

        @Bean
        TestMailbox csrfTestMailbox() {
            return new TestMailbox();
        }

        @Bean
        @Primary
        StaffLogin csrfTestLogin(DemoAccounts accounts, TestMailbox mailbox) {
            return new StaffLogin(accounts, mailbox.codes::put, Clock.systemUTC(), Runnable::run);
        }
    }

    static class TestMailbox {
        private final Map<String, String> codes = new HashMap<>();
    }
}
