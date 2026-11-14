package com.pertegato.propertyflow.access.service;

import com.pertegato.propertyflow.access.demo.DemoAccounts;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("frontend-demo")
@Import(LoginRecoveryWebTest.LoginConfiguration.class)
class LoginRecoveryWebTest {

    @Autowired
    WebApplicationContext context;

    @Autowired
    DemoAccounts accounts;

    @Autowired
    MutableClock clock;

    @Autowired
    TestMailbox mailbox;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/anmelden", "/registrieren"})
    void deniedResendPreservesTheVerificationFormAndTheStillValidCode(String path) throws Exception {
        String email = "cooldown-" + UUID.randomUUID() + "@example.test";
        if (path.equals("/anmelden")) {
            accounts.activateVerifiedTenant(email);
        }
        MvcResult form = mvc.perform(get(path)).andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) form.getRequest().getSession(false);
        String csrf = session.getAttribute("csrf").toString();
        mvc.perform(post(path + "/code").session(session).param("csrf", csrf).param("email", email))
                .andExpect(status().isOk());
        String validCode = mailbox.code.get();
        clock.advance(Duration.ofSeconds(59));
        mvc.perform(post(path + "/code").session(session).param("csrf", csrf))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().string(containsString("Bitte warten")))
                .andExpect(content().string(containsString("id=\"login-code\"")));
        assertEquals(validCode, mailbox.code.get());
        mvc.perform(get("/mieter/faelle").session(session)).andExpect(status().isUnauthorized());
        mvc.perform(post(path + "/pruefen").session(session).param("csrf", csrf).param("code", validCode))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/mieter/faelle"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/anmelden", "/registrieren"})
    void expiredCodeRendersAnErrorWithoutAuthenticationAndNewCodeRecovers(String path) throws Exception {
        String email = "expiry-" + UUID.randomUUID() + "@example.test";
        boolean registration = path.equals("/registrieren");
        if (!registration) {
            accounts.activateVerifiedTenant(email);
        }
        MvcResult form = mvc.perform(get(path)).andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) form.getRequest().getSession(false);
        String csrf = session.getAttribute("csrf").toString();
        mvc.perform(post(path + "/code").session(session).param("csrf", csrf).param("email", email))
                .andExpect(status().isOk());
        String expiredCode = mailbox.code.get();
        clock.advance(Duration.ofMinutes(10));

        mvc.perform(post(path + "/pruefen").session(session).param("csrf", csrf).param("code", expiredCode))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Der Code ist ungültig oder abgelaufen.")))
                .andExpect(content().string(containsString("id=\"login-code\"")))
                .andExpect(content().string(containsString("aria-describedby=\"login-error\"")))
                .andExpect(content().string(containsString("Neuen Code anfordern")));
        mvc.perform(get("/mieter/faelle").session(session)).andExpect(status().isUnauthorized());
        assertEquals(!registration, accounts.findActiveByEmail(email).isPresent());

        mvc.perform(post(path + "/code").session(session).param("csrf", csrf))
                .andExpect(status().isOk());
        mvc.perform(post(path + "/pruefen").session(session).param("csrf", csrf).param("code", mailbox.code.get()))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/mieter/faelle"));
        mvc.perform(get("/mieter/faelle").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(email)));
        assertTrue(accounts.findActiveByEmail(email).orElseThrow().tenant());
    }

    // Keep the production service and MVC/template path; only time and mail delivery
    // are controlled here. This configuration is never part of the application JAR.
    @TestConfiguration(proxyBeanMethods = false)
    static class LoginConfiguration {

        @Bean
        MutableClock loginTestClock() {
            return new MutableClock();
        }

        @Bean
        TestMailbox loginTestMailbox() {
            return new TestMailbox();
        }

        @Bean
        @Primary
        StaffLogin expiryTestLogin(DemoAccounts accounts, TestMailbox mailbox, MutableClock clock) {
            return new StaffLogin(accounts, (email, code) -> mailbox.code.set(code), clock, Runnable::run);
        }
    }

    static class TestMailbox {

        private final AtomicReference<String> code = new AtomicReference<>();
    }

    static class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-11-13T08:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        @Override
        public Instant instant() {
            return now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }
    }
}
