package com.pertegato.propertyflow.access.web;

import com.pertegato.propertyflow.access.demo.DemoAccounts;
import com.pertegato.propertyflow.access.service.StaffCodeDelivery;
import com.pertegato.propertyflow.caseprocessing.demo.DemoCases;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "propertyflow.staff.bootstrap.email=" + StaffAccessWebTest.STAFF_EMAIL,
        "propertyflow.tenant.demo.email=" + StaffAccessWebTest.TENANT_EMAIL
})
@ActiveProfiles("frontend-demo")
@Import(StaffAccessWebTest.MailConfiguration.class)
class StaffAccessWebTest {
    static final String STAFF_EMAIL = "web-access@example.test";
    static final String TENANT_EMAIL = "tenant-access@example.test";
    private static final String LIST = "/mitarbeiter/faelle";
    private static final String LOGIN = "/anmelden";
    private static final String REQUEST_CODE = LOGIN + "/code";
    private static final String VERIFY_CODE = LOGIN + "/pruefen";
    private static final String NOTICE = "Wenn für diese Adresse ein berechtigtes Konto besteht, senden wir einen Einmalcode.";

    @Autowired
    WebApplicationContext context;

    @Autowired
    DemoAccounts staffAccounts;

    @Autowired
    TestMailbox mailbox;

    @Autowired
    DemoCases cases;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void regularWebDemoLoadsConfiguredAccountWithoutDatabase() {
        assertTrue(staffAccounts.findActiveByEmail(STAFF_EMAIL).isPresent());
        assertTrue(context.getBeansOfType(DataSource.class).isEmpty());
    }

    @Test
    void staffPagesAndWritesRequireAuthenticationWhileTenantRoutesStayPublic() throws Exception {
        mvc.perform(get(LIST))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl(LOGIN))
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get(LIST + "/REQ-2026-001").header("X-PropertyFlow-Request", "partial"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
        mvc.perform(post(LIST + "/REQ-2026-001/aktion"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(LIST + "/REQ-2026-001/nachrichten"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/mieter/fall"))
                .andExpect(status().isOk());
        mvc.perform(get("/mieter/fall/zugang/demo-aktiver-fall"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Hauswart telefonisch"))));
    }

    @Test
    void validOtpRotatesSessionAndCsrfAndRestoresStaffPage() throws Exception {
        String target = LIST + "?q=Heizung";
        MvcResult navigation = mvc.perform(get(target)).andExpect(status().isSeeOther()).andReturn();
        MockHttpSession session = (MockHttpSession) navigation.getRequest().getSession(false);
        assertNotNull(session);
        mvc.perform(get(LOGIN).session(session)).andExpect(status().isOk());
        String originalId = session.getId();
        String originalCsrf = session.getAttribute(StaffWebSession.CSRF).toString();

        mvc.perform(post(REQUEST_CODE).session(session).param("email", STAFF_EMAIL))
                .andExpect(status().isForbidden());
        mvc.perform(post(REQUEST_CODE).session(session).param("email", STAFF_EMAIL).param("csrf", originalCsrf))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(NOTICE)));
        TestMail mail = mailbox.delivered.poll(5, TimeUnit.SECONDS);
        assertNotNull(mail, "OTP must be handed to the mail adapter");
        assertEquals(STAFF_EMAIL, mail.recipient());

        mvc.perform(post(VERIFY_CODE).session(session).param("code", mail.code()).param("csrf", "incorrect"))
                .andExpect(status().isForbidden());
        mvc.perform(post(VERIFY_CODE).session(session).param("code", "invalid").param("csrf", originalCsrf))
                .andExpect(status().isBadRequest());
        mvc.perform(post(VERIFY_CODE).session(session).param("code", mail.code()).param("csrf", originalCsrf))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl(target));
        assertNotEquals(originalId, session.getId());
        String currentCsrf = session.getAttribute(StaffWebSession.CSRF).toString();
        assertNotEquals(originalCsrf, currentCsrf);
        mvc.perform(get(target).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Fallübersicht")));
        mvc.perform(post(VERIFY_CODE).session(session).param("code", mail.code()).param("csrf", currentCsrf))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sessionForUnknownAccountDoesNotAuthorizeStaffAccess() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(StaffWebSession.ACCOUNT, UUID.randomUUID());
        mvc.perform(get(LIST).session(session))
                .andExpect(status().isForbidden())
                .andExpect(content().string(not(containsString("Fallübersicht"))));
    }

    @Test
    void unknownAddressGetsNeutralNoticeWithoutAuthenticatedSession() throws Exception {
        MvcResult login = mvc.perform(get(LOGIN)).andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertNotNull(session);
        String csrf = session.getAttribute(StaffWebSession.CSRF).toString();
        mvc.perform(post(REQUEST_CODE).session(session).param("email", "unknown@example.test").param("csrf", csrf))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(NOTICE)));
        mvc.perform(post(VERIFY_CODE).session(session).param("code", "000000").param("csrf", csrf))
                .andExpect(status().isBadRequest());
        mvc.perform(get(LIST).session(session))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl(LOGIN));
    }

    @Test
    void accountHeaderUsesSessionIdentityAndLogoutInvalidatesIt() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(StaffWebSession.ACCOUNT, staffAccounts.findActiveByEmail(STAFF_EMAIL).orElseThrow().id());
        session.setAttribute(StaffWebSession.CSRF, "logout-test-csrf");
        mvc.perform(get("/mieter/fall/zugang/demo-aktiver-fall").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(STAFF_EMAIL)))
                .andExpect(content().string(containsString("Abmelden")));
        mvc.perform(get("/abmelden").session(session)).andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/abmelden").session(session).param("csrf", "wrong"))
                .andExpect(status().isForbidden());
        mvc.perform(get(LIST).session(session)).andExpect(status().isOk());
        mvc.perform(post("/abmelden").session(session).param("csrf", "logout-test-csrf"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/mieter/fall"));
        assertTrue(session.isInvalid());
        mvc.perform(get(LIST)).andExpect(status().isSeeOther());
        mvc.perform(get("/mieter/fall/zugang/demo-aktiver-fall"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(STAFF_EMAIL))))
                .andExpect(content().string(not(containsString("Abmelden"))));
    }

    @Test
    void tenantOtpOpensOverviewWithoutGrantingStaffRights() throws Exception {
        MvcResult page = mvc.perform(get(LOGIN)).andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) page.getRequest().getSession(false);
        String csrf = session.getAttribute(StaffWebSession.CSRF).toString();
        mvc.perform(post(REQUEST_CODE).session(session).param("email", TENANT_EMAIL).param("csrf", csrf))
                .andExpect(status().isOk());
        TestMail mail = mailbox.delivered.poll(5, TimeUnit.SECONDS);
        assertNotNull(mail);
        assertEquals(TENANT_EMAIL, mail.recipient());
        mvc.perform(post(VERIFY_CODE).session(session).param("code", mail.code()).param("csrf", csrf))
                .andExpect(status().isSeeOther()).andExpect(redirectedUrl("/mieter/faelle"));
        mvc.perform(get(LIST).session(session)).andExpect(status().isForbidden());
        mvc.perform(post(LIST + "/REQ-2026-001/aktion").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/mieter/fall").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(TENANT_EMAIL)))
                .andExpect(content().string(not(containsString("name=\"email\""))));
    }

    @Test
    void tenantSubmissionUsesAccountAddressEvenWhenRequestTriesToOverrideIt() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(StaffWebSession.ACCOUNT, staffAccounts.findActiveByEmail(TENANT_EMAIL).orElseThrow().id());
        session.setAttribute(StaffWebSession.CSRF, "tenant-csrf");
        for (String submittedEmail : new String[] {"", "another@example.test"}) {
            MvcResult created = mvc.perform(post("/mieter/fall").session(session)
                            .param("csrf", "tenant-csrf").param("operation", UUID.randomUUID().toString())
                            .param("title", "Heizung").param("unit", "Wohnung 2").param("description", "Heizung kalt")
                            .param("accountSubmission", "false").param("email", submittedEmail))
                    .andExpect(status().isSeeOther()).andReturn();
            String location = created.getResponse().getHeader("Location");
            String token = location.substring(location.lastIndexOf('/') + 1);
            assertEquals(TENANT_EMAIL, cases.tenant(token).email());
        }
    }

    @Test
    void expiredTenantSessionPreservesInputAndGuestSubmissionStillNeedsEmail() throws Exception {
        mvc.perform(post("/mieter/fall").param("csrf", "expired-csrf").param("accountSubmission", "true")
                        .param("operation", UUID.randomUUID().toString()).param("title", "Erhaltene Eingabe")
                        .param("unit", "Wohnung 2").param("description", "Heizung kalt"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString("Erhaltene Eingabe")))
                .andExpect(content().string(containsString("name=\"email\"")))
                .andExpect(content().string(containsString("Ihre Anmeldung ist nicht mehr gültig")));
        MvcResult form = mvc.perform(get("/mieter/fall")).andReturn();
        MockHttpSession session = (MockHttpSession) form.getRequest().getSession(false);
        mvc.perform(post("/mieter/fall").session(session)
                        .param("csrf", session.getAttribute(StaffWebSession.CSRF).toString())
                        .param("operation", UUID.randomUUID().toString()).param("title", "Heizung")
                        .param("unit", "Wohnung 2").param("description", "Heizung kalt"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submissionOperationsAreIsolatedBetweenAccountsAndGuestSessions() throws Exception {
        String operation = UUID.randomUUID().toString();
        MockHttpSession first = submissionSession();
        first.setAttribute(StaffWebSession.ACCOUNT, staffAccounts.findActiveByEmail(TENANT_EMAIL).orElseThrow().id());
        MockHttpSession second = submissionSession();
        second.setAttribute(StaffWebSession.ACCOUNT,
                staffAccounts.activateVerifiedTenant("isolation-" + UUID.randomUUID() + "@example.test").id());
        MockHttpSession guest = submissionSession();
        MockHttpSession otherGuest = submissionSession();
        long before = cases.list("", "", true, "default", false, 1).total();

        String firstToken = submitCase(first, operation, "Fall A");
        DemoCases.View original = cases.tenant(firstToken);
        String secondToken = submitCase(second, operation, "Fall B");
        String guestToken = submitCase(guest, operation, "Gastfall");
        String otherGuestToken = submitCase(otherGuest, operation, "Anderer Gastfall");

        assertEquals(4, Stream.of(firstToken, secondToken, guestToken, otherGuestToken).distinct().count());
        assertEquals(original, cases.tenant(firstToken));
        assertEquals("Fall B", cases.tenant(secondToken).title());
        assertEquals("Gastfall", cases.tenant(guestToken).title());
        assertEquals("Anderer Gastfall", cases.tenant(otherGuestToken).title());
        assertEquals(firstToken, submitCase(first, operation, "Fall A"));
        assertEquals(secondToken, submitCase(second, operation, "Fall B"));
        assertEquals(guestToken, submitCase(guest, operation, "Gastfall"));
        assertEquals(otherGuestToken, submitCase(otherGuest, operation, "Anderer Gastfall"));
        assertEquals(before + 4, cases.list("", "", true, "default", false, 1).total());
        mvc.perform(get("/mieter/faelle/" + original.id()).session(second))
                .andExpect(status().isNotFound());

        MockHttpSession sameAccount = submissionSession();
        sameAccount.setAttribute(StaffWebSession.ACCOUNT, first.getAttribute(StaffWebSession.ACCOUNT));
        assertEquals(firstToken, submitCase(sameAccount, operation, "Fall A"));
    }

    @Test
    void tenantAccountCannotPublishStaffMessagesAndLeavesCaseUnchanged() throws Exception {
        MockHttpSession session = submissionSession();
        session.setAttribute(StaffWebSession.ACCOUNT, staffAccounts.findActiveByEmail(TENANT_EMAIL).orElseThrow().id());
        String token = submitCase(session, UUID.randomUUID().toString(), "Zugriffstest");
        DemoCases.View before = cases.get(cases.tenant(token).id());
        mvc.perform(post(LIST + "/" + before.id() + "/nachrichten").session(session)
                        .param("csrf", session.getAttribute(StaffWebSession.CSRF).toString())
                        .param("operation", UUID.randomUUID().toString()).param("text", "Nicht speichern"))
                .andExpect(status().isForbidden());
        assertEquals(before, cases.get(before.id()));
    }

    @Test
    void anonymousTokenCannotSetStaffFlagsButCanSendAnExternalMessage() throws Exception {
        MockHttpSession session = submissionSession();
        String token = submitCase(session, UUID.randomUUID().toString(), "Gastzugriff");
        DemoCases.View before = cases.get(cases.tenant(token).id());
        String operation = UUID.randomUUID().toString();
        String route = "/mieter/fall/zugang/" + token + "/nachrichten";
        for (String flag : new String[] {"internal", "answer"}) {
            mvc.perform(post(route).session(session)
                            .param("csrf", session.getAttribute(StaffWebSession.CSRF).toString())
                            .param("operation", operation).param("text", "Meine Nachricht").param(flag, "true"))
                    .andExpect(status().isBadRequest());
            assertEquals(before, cases.get(before.id()));
        }
        mvc.perform(post(route).session(session)
                        .param("csrf", session.getAttribute(StaffWebSession.CSRF).toString())
                        .param("operation", operation).param("text", "Meine Nachricht"))
                .andExpect(status().isSeeOther());
        DemoCases.Entry message = cases.get(before.id()).entries().getLast();
        assertEquals("Mieter", message.role());
        assertEquals(false, message.internal());
        assertEquals(false, message.answerRequired());
        assertEquals(before.entries().size() + 1, cases.get(before.id()).entries().size());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void overlongTenantMessageKeepsDraftAndAllowsCorrectedRetry(boolean useAccount) throws Exception {
        MockHttpSession session = submissionSession();
        if (useAccount) {
            session.setAttribute(StaffWebSession.ACCOUNT, staffAccounts.findActiveByEmail(TENANT_EMAIL).orElseThrow().id());
        }
        String token = submitCase(session, UUID.randomUUID().toString(), "Nachrichtenlänge");
        DemoCases.View before = cases.get(cases.tenant(token).id());
        String caseUrl = useAccount ? "/mieter/faelle/" + before.id() : "/mieter/fall/zugang/" + token;
        String route = caseUrl + "/nachrichten";
        String csrf = session.getAttribute(StaffWebSession.CSRF).toString();
        String operation = UUID.randomUUID().toString();
        String accepted = "m".repeat(1997) + "\n\uD83D\uDE80";
        String draft = " " + accepted.replace("\n", "\r\n");

        mvc.perform(post(route).session(session).param("csrf", csrf).param("operation", operation).param("text", draft))
                .andExpect(status().isBadRequest())
                .andExpect(model().attribute("draft", draft))
                .andExpect(model().attribute("messageMaxLength", 2000))
                .andExpect(content().string(containsString("Die Nachricht darf höchstens 2’000 Zeichen enthalten.")));
        assertEquals(before, cases.get(before.id()));

        mvc.perform(post(route).session(session).param("csrf", csrf).param("operation", operation)
                        .param("text", accepted.replace("\n", "\r\n")))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl(caseUrl));
        DemoCases.View saved = cases.get(before.id());
        assertEquals(before.entries().size() + 1, saved.entries().size());
        assertEquals(accepted, saved.entries().getLast().text());
    }

    @Test
    void accountMessageRequiresAuthenticationEvenWithValidGuestCsrf() throws Exception {
        MockHttpSession session = submissionSession();
        DemoCases.View before = cases.get("REQ-2026-001");
        String route = "/mieter/faelle/" + before.id() + "/nachrichten";
        mvc.perform(post(route).session(session)
                        .param("csrf", session.getAttribute(StaffWebSession.CSRF).toString())
                        .param("operation", UUID.randomUUID().toString()).param("text", "Nicht speichern"))
                .andExpect(status().isUnauthorized());
        assertEquals(before, cases.get(before.id()));
        mvc.perform(post(route).param("csrf", "expired-session-token")
                        .param("operation", UUID.randomUUID().toString()).param("text", "Nicht speichern"))
                .andExpect(status().isForbidden());
        assertEquals(before, cases.get(before.id()));
    }

    @Test
    void directHttpSubmissionRejectsOversizedInputWithoutConsumingOperation() throws Exception {
        MockHttpSession session = submissionSession();
        String operation = UUID.randomUUID().toString();
        long before = cases.list("", "", true, "default", false, 1).total();
        mvc.perform(post("/mieter/fall").session(session)
                        .param("csrf", session.getAttribute(StaffWebSession.CSRF).toString())
                        .param("operation", operation).param("title", "x".repeat(151)).param("unit", "Wohnung")
                        .param("description", "Beschreibung").param("email", TENANT_EMAIL))
                .andExpect(status().isBadRequest());
        assertEquals(before, cases.list("", "", true, "default", false, 1).total());
        String token = submitCase(session, operation, "Korrigierte Eingabe");
        assertEquals("Korrigierte Eingabe", cases.tenant(token).title());
        assertEquals(before + 1, cases.list("", "", true, "default", false, 1).total());
    }

    @ParameterizedTest
    @ValueSource(strings = {"m\u0131eter@example.test", "\u212Aonto@example.test"})
    void directHttpSubmissionRejectsUnicodeEmailAndAllowsCorrectedRetry(String address) throws Exception {
        MockHttpSession session = submissionSession();
        String operation = UUID.randomUUID().toString();
        int before = cases.list("", "", true, "default", false, 1).total();

        mvc.perform(post("/mieter/fall").session(session)
                        .param("csrf", session.getAttribute(StaffWebSession.CSRF).toString())
                        .param("operation", operation)
                        .param("title", "E-Mail-Prüfung")
                        .param("unit", "Wohnung 2")
                        .param("description", "Beschreibung")
                        .param("email", address))
                .andExpect(status().isBadRequest());
        assertEquals(before, cases.list("", "", true, "default", false, 1).total());

        String token = submitCase(session, operation, "E-Mail-Prüfung");
        assertEquals(TENANT_EMAIL, cases.tenant(token).email());
        assertEquals(before + 1, cases.list("", "", true, "default", false, 1).total());
    }

    @Test
    void verifiedTenantCannotListReadOrWriteLegacyCaseWithDotlessI() throws Exception {
        String suffix = "-" + UUID.randomUUID() + "@example.test";
        String verifiedAddress = "mieter" + suffix;
        String ownToken = cases.create("test-scope", "Eigenes Anliegen", "Wohnung 1", " " + verifiedAddress.toUpperCase(Locale.ROOT) + " ",
                "Eigene Beschreibung", UUID.randomUUID().toString());
        String legacyToken = cases.create("test-scope", "Fremdes Anliegen", "Wohnung 2", "placeholder" + suffix,
                "Fremde Beschreibung", UUID.randomUUID().toString());
        String ownId = cases.tenant(ownToken).id();
        String legacyId = cases.tenant(legacyToken).id();
        // Seed an address from before the shared input validation was introduced.
        Object legacyCase = ReflectionTestUtils.invokeMethod(cases, "data", legacyId);
        assertNotNull(legacyCase);
        ReflectionTestUtils.setField(legacyCase, "email", "m\u0131eter" + suffix);
        DemoCases.View before = cases.get(legacyId);

        MockHttpSession session = submissionSession();
        session.setAttribute(StaffWebSession.ACCOUNT, staffAccounts.activateVerifiedTenant(verifiedAddress).id());

        assertEquals(verifiedAddress, cases.tenant(ownToken).email());
        mvc.perform(get("/mieter/faelle").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(ownId)))
                .andExpect(content().string(not(containsString(legacyId))));
        mvc.perform(get("/mieter/faelle/" + ownId).session(session))
                .andExpect(status().isOk());
        mvc.perform(get("/mieter/faelle/" + legacyId).session(session))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("Fremde Beschreibung"))));
        mvc.perform(post("/mieter/faelle/" + legacyId + "/nachrichten").session(session)
                        .param("csrf", session.getAttribute(StaffWebSession.CSRF).toString())
                        .param("operation", UUID.randomUUID().toString())
                        .param("text", "Unberechtigter Schreibversuch"))
                .andExpect(status().isNotFound());
        assertEquals(before, cases.get(legacyId));
        mvc.perform(get("/mieter/fall/zugang/" + legacyToken))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Fremde Beschreibung")));
    }

    private MockHttpSession submissionSession() throws Exception {
        MvcResult form = mvc.perform(get("/mieter/fall")).andExpect(status().isOk()).andReturn();
        return (MockHttpSession) form.getRequest().getSession(false);
    }

    private String submitCase(MockHttpSession session, String operation, String title) throws Exception {
        MvcResult created = mvc.perform(post("/mieter/fall").session(session)
                        .param("csrf", session.getAttribute(StaffWebSession.CSRF).toString())
                        .param("operation", operation).param("title", title).param("unit", "Wohnung 2")
                        .param("description", "Beschreibung für " + title).param("email", TENANT_EMAIL)
                        .param("accountSubmission", "false").param("submissionScope", "forged-scope"))
                .andExpect(status().isSeeOther()).andReturn();
        String location = created.getResponse().getHeader("Location");
        assertNotNull(location);
        return location.substring(location.lastIndexOf('/') + 1);
    }

    @Test
    void selfRegistrationVerifiesAddressAndProtectsExistingCasesAndMessages() throws Exception {
        String email = "registered-" + UUID.randomUUID() + "@example.test";
        String firstToken = cases.create("test-scope", "Erstes eigenes Anliegen", "Wohnung 1", email.toUpperCase(),
                "Kalt", UUID.randomUUID().toString());
        String secondToken = cases.create("test-scope", "Zweites eigenes Anliegen", "Wohnung 2", email, "Nass", UUID.randomUUID().toString());
        String firstId = cases.tenant(firstToken).id();
        String secondId = cases.tenant(secondToken).id();
        cases.message(firstId, null, "Geheime interne Notiz", true, false, UUID.randomUUID().toString());
        String foreignId = cases.tenant(cases.create("test-scope", "Fremdes Anliegen", "Wohnung 3", "foreign@example.test",
                "Privat", UUID.randomUUID().toString())).id();

        MvcResult page = mvc.perform(get("/registrieren")).andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) page.getRequest().getSession(false);
        String csrf = session.getAttribute(StaffWebSession.CSRF).toString();
        String originalSession = session.getId();
        mvc.perform(post("/registrieren/code").session(session).param("email", email)).andExpect(status().isForbidden());
        mvc.perform(post("/registrieren/code").session(session).param("email", "invalid").param("csrf", csrf))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/registrieren/code").session(session).param("email", email).param("csrf", csrf))
                .andExpect(status().isOk());
        TestMail mail = mailbox.delivered.poll(5, TimeUnit.SECONDS);
        assertNotNull(mail);
        assertEquals(email, mail.recipient());
        assertTrue(staffAccounts.findActiveByEmail(email).isEmpty());
        mvc.perform(get("/mieter/faelle").session(session)).andExpect(status().isUnauthorized());
        mvc.perform(get("/mieter/faelle/" + firstId).session(session)).andExpect(status().isUnauthorized());
        mvc.perform(post("/registrieren/pruefen").session(session).param("code", "invalid").param("csrf", csrf))
                .andExpect(status().isBadRequest());
        assertTrue(staffAccounts.findActiveByEmail(email).isEmpty());
        mvc.perform(post("/registrieren/pruefen").session(session).param("code", mail.code()).param("csrf", csrf))
                .andExpect(status().isSeeOther()).andExpect(redirectedUrl("/mieter/faelle"));
        assertNotEquals(originalSession, session.getId());
        String newCsrf = session.getAttribute(StaffWebSession.CSRF).toString();
        assertNotEquals(csrf, newCsrf);
        mvc.perform(get("/mieter/faelle").session(session).param("email", "foreign@example.test"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(firstId)))
                .andExpect(content().string(containsString(secondId)))
                .andExpect(content().string(not(containsString(foreignId))));
        mvc.perform(get("/mieter/faelle/" + firstId).session(session))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString("Geheime interne Notiz"))));
        mvc.perform(get("/mieter/faelle/" + foreignId).session(session)).andExpect(status().isNotFound());
        mvc.perform(post("/mieter/faelle/" + foreignId + "/nachrichten").session(session)
                        .param("csrf", newCsrf).param("text", "Unberechtigt").param("operation", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/mieter/faelle/" + firstId + "/nachrichten").session(session)
                        .param("csrf", "wrong").param("text", "Falsch").param("operation", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/mieter/faelle/" + firstId + "/nachrichten").session(session)
                        .param("csrf", newCsrf).param("text", "Intern").param("internal", "true")
                        .param("operation", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/mieter/faelle/" + firstId + "/nachrichten").session(session)
                        .param("csrf", newCsrf).param("text", "Meine Ergänzung").param("operation", UUID.randomUUID().toString()))
                .andExpect(status().isSeeOther());
        assertEquals("Mieter", cases.get(firstId).entries().getLast().role());
        cases.change(firstId, "close", "", "", cases.get(firstId).version(), UUID.randomUUID().toString());
        mvc.perform(post("/mieter/faelle/" + firstId + "/nachrichten").session(session)
                        .param("csrf", newCsrf).param("text", "Nach Abschluss").param("operation", UUID.randomUUID().toString()))
                .andExpect(status().isConflict());
        mvc.perform(get(LIST).session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/abmelden").session(session).param("csrf", newCsrf)).andExpect(status().isSeeOther());
        mvc.perform(get("/mieter/faelle/" + firstId)).andExpect(status().isUnauthorized());
        mvc.perform(get("/mieter/fall/zugang/" + firstToken)).andExpect(status().isOk());
    }

    @Test
    void registrationWithoutCasesShowsAnEmptyOverviewAndCreatesNoCase() throws Exception {
        String email = "empty-" + UUID.randomUUID() + "@example.test";
        MvcResult page = mvc.perform(get("/registrieren")).andReturn();
        MockHttpSession session = (MockHttpSession) page.getRequest().getSession(false);
        String csrf = session.getAttribute(StaffWebSession.CSRF).toString();
        mvc.perform(post("/registrieren/code").session(session).param("email", email).param("csrf", csrf))
                .andExpect(status().isOk());
        TestMail mail = mailbox.delivered.poll(5, TimeUnit.SECONDS);
        assertNotNull(mail);
        mvc.perform(post("/registrieren/pruefen").session(session).param("code", mail.code()).param("csrf", csrf))
                .andExpect(status().isSeeOther());
        mvc.perform(get("/mieter/faelle").session(session))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Sie haben noch keine Fälle.")));
        assertTrue(cases.tenantCases(email).isEmpty());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MailConfiguration {
        @Bean
        @Primary
        TestMailbox staffTestMailbox() {
            return new TestMailbox();
        }
    }

    static class TestMailbox implements StaffCodeDelivery {
        private final BlockingQueue<TestMail> delivered = new LinkedBlockingQueue<>();

        @Override
        public void send(String recipient, String code) {
            delivered.add(new TestMail(recipient, code));
        }
    }

    record TestMail(String recipient, String code) {
    }
}
