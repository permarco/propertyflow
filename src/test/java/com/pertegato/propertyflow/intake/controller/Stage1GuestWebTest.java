package com.pertegato.propertyflow.intake.controller;

import com.pertegato.propertyflow.access.contract.SubmissionContext.Guest;
import com.pertegato.propertyflow.access.web.GuestRequestLimits;
import com.pertegato.propertyflow.caseprocessing.contract.CaseAccepted;
import com.pertegato.propertyflow.caseprocessing.contract.CaseErrorCode;
import com.pertegato.propertyflow.caseprocessing.contract.CaseReading;
import com.pertegato.propertyflow.caseprocessing.contract.CaseServiceException;
import com.pertegato.propertyflow.caseprocessing.contract.CaseStatus;
import com.pertegato.propertyflow.caseprocessing.contract.CaseStatusView;
import com.pertegato.propertyflow.caseprocessing.contract.CreateCaseCommand;
import com.pertegato.propertyflow.caseprocessing.contract.PublicAuthor;
import com.pertegato.propertyflow.caseprocessing.contract.PublicMessage;
import com.pertegato.propertyflow.caseprocessing.contract.TenantCaseView;
import com.pertegato.propertyflow.caseprocessing.contract.Urgency;
import com.pertegato.propertyflow.caseprocessing.contract.UrgencyChange;
import com.pertegato.propertyflow.caseprocessing.contract.UrgencySource;
import com.pertegato.propertyflow.caseprocessing.controller.PersonalCaseController;
import com.pertegato.propertyflow.intake.contract.CaseSubmission;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(classes = Stage1GuestWebTest.AdapterConfiguration.class, properties = {
        "spring.config.location=optional:classpath:/stage1-adapter-test.properties",
        "spring.docker.compose.enabled=false",
        "server.servlet.context-path=/stage1",
        "server.servlet.session.tracking-modes=cookie",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "camunda.client.enabled=false",
        "management.health.camunda.enabled=false",
        "spring.ai.model.chat=none",
        "spring.ai.model.embedding=none",
        "spring.ai.model.image=none",
        "spring.ai.model.audio.transcription=none",
        "spring.ai.model.audio.speech=none",
        "spring.ai.model.moderation=none"
})
@ActiveProfiles({"propertyflow-backend", "stage1-adapter-test"})
class Stage1GuestWebTest {

    private static final String CONTEXT = "/stage1";
    private static final String FORM = "/mieter/fall";
    private static final String LINK = FORM + "/zugang/";
    private static final String TOKEN = "synthetic-case-token";
    private static final String EMAIL = "tenant@example.test";
    private static final String DESCRIPTION = "Erste Zeile\r\nZweite Zeile";

    @Autowired
    WebApplicationContext context;

    @Autowired
    CaseSubmission submission;

    @Autowired
    CaseReading reading;

    @Autowired
    GuestRequestLimits limits;

    private MockMvc mvc;

    @BeforeEach
    void resetPorts() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        reset(submission, reading, limits);
        when(limits.allowSubmission(any())).thenReturn(true);
        when(limits.allowPersonalLink(any())).thenReturn(true);
    }

    @Test
    void guestFormSubmitsWithServerSideScopeAndRedirectsWithoutBody() throws Exception {
        when(submission.submit(any(), any())).thenReturn(new CaseAccepted("CASE-1", TOKEN, false));

        OpenForm first = newForm();
        verifyNoInteractions(submission, reading);

        Guest initialScope = new Guest(UUID.randomUUID());
        ArgumentCaptor<Guest> guest = ArgumentCaptor.forClass(Guest.class);
        first.fields().put("sessionScope", initialScope.sessionScope().toString());
        first.fields().put("accountId", initialScope.sessionScope().toString());
        MockHttpServletResponse accepted = submit(first.session(), first.fields());
        protectedResponse(accepted, 303);
        assertEquals(CONTEXT + LINK + TOKEN, accepted.getHeader("Location"));
        assertTrue(body(accepted).isEmpty());

        CreateCaseCommand expected = new CreateCaseCommand(
                first.fields().get("operation"), "Heizung <kalt>", "Wohnung 2", DESCRIPTION, EMAIL);
        verify(submission).submit(guest.capture(), eq(expected));
        assertNotEquals(initialScope, guest.getValue());
        Guest actualScope = guest.getValue();

        when(submission.submit(actualScope, expected)).thenReturn(new CaseAccepted("CASE-1", TOKEN, true));
        MockHttpServletResponse replay = submit(first.session(), first.fields());
        protectedResponse(replay, 303);
        assertEquals(accepted.getHeader("Location"), replay.getHeader("Location"));

        OpenForm other = newForm();
        other.fields().put("operation", first.fields().get("operation"));
        submit(other.session(), other.fields());
        ArgumentCaptor<Guest> allGuests = ArgumentCaptor.forClass(Guest.class);
        verify(submission, times(3)).submit(allGuests.capture(), eq(expected));
        assertEquals(actualScope, allGuests.getAllValues().get(1));
        assertNotEquals(actualScope, allGuests.getValue());
        verifyNoInteractions(reading);
    }

    @Test
    void rejectsMissingOrForeignCsrfAndUnsupportedAccountWithoutCallingService() throws Exception {
        OpenForm original = newForm();
        protectedResponse(submit(null, original.fields()), 403);
        protectedResponse(submit(newForm().session(), original.fields()), 403);
        protectedResponse(submit(original.session(), withField(original.fields(), "csrf", "another-csrf")), 403);
        Map<String, String> missingCsrf = new LinkedHashMap<>(original.fields());
        missingCsrf.remove("csrf");
        protectedResponse(submit(original.session(), missingCsrf), 403);
        protectedResponse(submit(original.session(), withField(original.fields(), "accountSubmission", "true")), 401);
        verifyNoInteractions(submission, reading);
    }

    @ParameterizedTest
    @CsvSource({
            "INVALID_INPUT,400", "UNAUTHENTICATED,401", "FORBIDDEN,403", "IDEMPOTENCY_CONFLICT,409",
            "REPLAY_ACCESS_UNAVAILABLE,409", "STORAGE_UNAVAILABLE,503", "INTERNAL_FAILURE,500"
    })
    void mapsFailureToProtectedHtmlAndRetainsDraft(CaseErrorCode error, int status) throws Exception {
        when(submission.submit(any(), any())).thenThrow(new CaseServiceException(error, Map.of("title", "TOO_LONG")));

        OpenForm form = newForm();
        MockHttpServletResponse response = submit(form.session(), form.fields());
        protectedResponse(response, status);
        String html = body(response);
        assertFalse(response.containsHeader("Location"));
        assertEquals(form.fields().get("operation"), formValue(html, "operation"));
        assertTrue(html.contains("Heizung &lt;kalt&gt;"));
        assertTrue(html.contains("Wohnung 2"));
        assertTrue(html.contains("Zweite Zeile"));
        assertFalse(html.contains(error.name()));
        if (error == CaseErrorCode.INVALID_INPUT) {
            assertTrue(html.contains("Die Eingabe ist zu lang."));
        }
        if (error == CaseErrorCode.REPLAY_ACCESS_UNAVAILABLE) {
            assertTrue(html.contains("Der bisherige Zugang ist nicht mehr verfügbar."));
        }
    }

    @Test
    void readsPersonalLinkWithoutSessionAndRendersOnlyEscapedPublicData() throws Exception {
        Instant receipt = Instant.parse("2026-11-14T14:00:00Z");
        when(reading.readByToken(TOKEN)).thenReturn(new TenantCaseView(
                new CaseStatusView("CASE-1", CaseStatus.RECEIVED, Optional.of(Urgency.HIGH)),
                "Heizung <kalt>", "Ursprünglicher Text", "Wohnung 2", Optional.empty(), "t***@example.test",
                receipt, receipt, 1, Optional.of(UrgencySource.SYSTEM),
                List.of(new PublicMessage("message-1", PublicAuthor.TENANT, "<script>Ursprünglicher Text</script>", receipt)),
                List.of(new UrgencyChange("urgency-1", Optional.empty(), Urgency.HIGH, UrgencySource.SYSTEM, receipt.plusSeconds(5)))));

        MvcResult result = request(LINK + TOKEN);
        MockHttpServletResponse response = result.getResponse();
        protectedResponse(response, 200);
        assertTrue(result.getRequest().getSession(false) == null);
        String html = body(response);
        assertTrue(html.contains("CASE-1"));
        assertTrue(html.contains("Heizung &lt;kalt&gt;"));
        assertTrue(html.contains("Eingegangen"));
        assertTrue(html.contains("Dringlichkeit: Noch nicht bewertet → Hoch"));
        assertTrue(html.contains("14.11.2026, 15:00"));
        assertTrue(html.contains("&lt;script&gt;Ursprünglicher Text&lt;/script&gt;"));
        assertFalse(html.contains("<script>"));
        assertFalse(html.contains("method=\"post\""));
        assertFalse(html.contains(TOKEN));
        assertFalse(html.contains(EMAIL));
        verify(reading).readByToken(TOKEN);
        verifyNoInteractions(submission);
    }

    @ParameterizedTest
    @CsvSource({"CASE_NOT_AVAILABLE,404", "INVALID_INPUT,404", "STORAGE_UNAVAILABLE,500", "INTERNAL_FAILURE,500"})
    void tokenFailuresAreNeutralAndCarryProtection(CaseErrorCode code, int status) throws Exception {
        when(reading.readByToken(TOKEN)).thenThrow(new CaseServiceException(code, Map.of()));

        MockHttpServletResponse response = request(LINK + TOKEN).getResponse();
        protectedResponse(response, status);
        String html = body(response);
        assertFalse(html.contains(TOKEN));
        assertFalse(html.contains(code.name()));
        assertFalse(html.contains("case-head"));
        verify(reading).readByToken(TOKEN);
    }

    @Test
    void rateLimitedRequestsReturn429BeforeCallingTheServices() throws Exception {
        when(limits.allowSubmission(any())).thenReturn(false);
        when(limits.allowPersonalLink(any())).thenReturn(false);

        OpenForm form = newForm();
        MockHttpServletResponse rejected = submit(form.session(), form.fields());
        protectedResponse(rejected, 429);
        assertEquals(form.fields().get("operation"), formValue(body(rejected), "operation"));
        assertFalse(rejected.containsHeader("Location"));
        protectedResponse(request(LINK + TOKEN).getResponse(), 429);
        verifyNoInteractions(submission, reading);
    }

    private OpenForm newForm() throws Exception {
        MvcResult opened = request(FORM);
        protectedResponse(opened.getResponse(), 200);
        String html = body(opened.getResponse());
        assertTrue(html.contains("action=\"" + CONTEXT + FORM + "\""));
        assertTrue(html.contains("maxlength=\"2000\""));
        Map<String, String> fields = new LinkedHashMap<>(Map.of(
                "csrf", formValue(html, "csrf"), "operation", formValue(html, "operation"),
                "title", "Heizung <kalt>", "unit", "Wohnung 2", "description", DESCRIPTION, "email", EMAIL));
        return new OpenForm((MockHttpSession) opened.getRequest().getSession(false), fields);
    }

    private static String formValue(String html, String name) {
        Matcher field = Pattern.compile("name=\"" + name + "\"[^>]*value=\"([^\"]+)\"").matcher(html);
        assertTrue(field.find(), "Required hidden field missing: " + name);
        return field.group(1);
    }

    private MvcResult request(String path) throws Exception {
        return mvc.perform(get(CONTEXT + path).contextPath(CONTEXT)).andReturn();
    }

    private MockHttpServletResponse submit(MockHttpSession session, Map<String, String> fields) throws Exception {
        MockHttpServletRequestBuilder request = post(CONTEXT + FORM).contextPath(CONTEXT)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED);
        fields.forEach(request::param);
        if (session != null) {
            request.session(session).with(servletRequest -> {
                servletRequest.setRequestedSessionId(session.getId());
                servletRequest.setRequestedSessionIdFromCookie(true);
                return servletRequest;
            });
        }
        return mvc.perform(request).andReturn().getResponse();
    }

    private static Map<String, String> withField(Map<String, String> form, String key, String value) {
        Map<String, String> modified = new LinkedHashMap<>(form);
        modified.put(key, value);
        return modified;
    }

    private static String body(MockHttpServletResponse response) throws UnsupportedEncodingException {
        return response.getContentAsString(StandardCharsets.UTF_8);
    }

    private static void protectedResponse(MockHttpServletResponse response, int status) {
        assertEquals(status, response.getStatus());
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertEquals("no-referrer", response.getHeader("Referrer-Policy"));
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
    }

    private record OpenForm(MockHttpSession session, Map<String, String> fields) {
    }

    @Configuration(proxyBeanMethods = false)
    @Profile("stage1-adapter-test")
    @EnableAutoConfiguration
    @Import({GuestCaseIntakeController.class, PersonalCaseController.class})
    static class AdapterConfiguration {

        @Bean
        CaseSubmission caseSubmission() {
            return mock(CaseSubmission.class);
        }

        @Bean
        CaseReading caseReading() {
            return mock(CaseReading.class);
        }

        @Bean
        GuestRequestLimits guestRequestLimits() {
            return mock(GuestRequestLimits.class);
        }
    }
}
