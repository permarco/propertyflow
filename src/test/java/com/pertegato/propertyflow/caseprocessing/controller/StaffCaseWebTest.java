package com.pertegato.propertyflow.caseprocessing.controller;

import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.access.web.StaffWebSession;
import com.pertegato.propertyflow.caseprocessing.contract.AccountCaseReading;
import com.pertegato.propertyflow.caseprocessing.contract.CaseErrorCode;
import com.pertegato.propertyflow.caseprocessing.contract.CaseRecommendation;
import com.pertegato.propertyflow.caseprocessing.contract.CaseServiceException;
import com.pertegato.propertyflow.caseprocessing.contract.CaseStatus;
import com.pertegato.propertyflow.caseprocessing.contract.CaseStatusView;
import com.pertegato.propertyflow.caseprocessing.contract.InternalChange;
import com.pertegato.propertyflow.caseprocessing.contract.InternalChange.UrgencyDetails;
import com.pertegato.propertyflow.caseprocessing.contract.InternalMemo;
import com.pertegato.propertyflow.caseprocessing.contract.PublicAuthor;
import com.pertegato.propertyflow.caseprocessing.contract.PublicMessage;
import com.pertegato.propertyflow.caseprocessing.contract.StaffCaseListing;
import com.pertegato.propertyflow.caseprocessing.contract.StaffCasePage;
import com.pertegato.propertyflow.caseprocessing.contract.StaffCaseQuery;
import com.pertegato.propertyflow.caseprocessing.contract.StaffCaseSummary;
import com.pertegato.propertyflow.caseprocessing.contract.StaffCaseView;
import com.pertegato.propertyflow.caseprocessing.contract.StaffCaseView.ReviewReason;
import com.pertegato.propertyflow.caseprocessing.contract.TenantCaseView;
import com.pertegato.propertyflow.caseprocessing.contract.Urgency;
import com.pertegato.propertyflow.caseprocessing.contract.UrgencyChange;
import com.pertegato.propertyflow.caseprocessing.contract.UrgencySource;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = StaffCaseWebTest.StaffCaseConfiguration.class, properties = {
        "spring.config.location=optional:classpath:/stage2-case-adapter-test.properties",
        "spring.docker.compose.enabled=false",
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
@ActiveProfiles({"propertyflow-backend", "stage2-case-adapter-test"})
class StaffCaseWebTest {

    private static final String CONTEXT = "/propertyflow";
    private static final String OVERVIEW = "/mitarbeiter/faelle";
    private static final String CASE_ID = "CASE-STAFF-1";
    private static final String DETAIL = OVERVIEW + "/" + CASE_ID;
    private static final String TITLE = "Heizung <kaputt>";
    private static final String UNIT = "Bahnhofstrasse 8";
    private static final String CSRF = "staff-test-csrf";
    private static final Instant RECEIVED_AT = Instant.parse("2026-11-14T08:00:00Z");
    private static final WebAccountContext STAFF = new WebAccountContext(UUID.fromString("90f4ba19-71eb-4f4c-9eca-abd8940a3cab"));

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private StaffCaseListing listing;

    @Autowired
    private AccountCaseReading reading;

    private MockMvc mvc;

    @BeforeEach
    void prepareMvc() {
        reset(listing, reading);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {OVERVIEW, DETAIL})
    void anonymousRequestsCannotReadEitherPage(String path) throws Exception {
        ResultActions partial = mvc.perform(request(path).header("X-PropertyFlow-Request", "partial"));
        protectedResponse(partial, 401)
                .andExpect(header().doesNotExist("Location"))
                .andExpect(content().string(not(containsString(CASE_ID))));
        assertThat(partial.andReturn().getRequest().getSession(false)).isNull();

        MvcResult navigation = protectedResponse(mvc.perform(request(path)), 303)
                .andExpect(header().string("Location", CONTEXT + "/anmelden"))
                .andReturn();
        assertThat(navigation.getRequest().getSession(false).getAttribute(StaffWebSession.RETURN_TO)).isEqualTo(path);
        verifyNoInteractions(listing, reading);
    }

    @Test
    void requestCannotUseAnAccountReferenceFromParametersOrUrlSession() throws Exception {
        MockHttpSession cookielessSession = new MockHttpSession();
        cookielessSession.setAttribute(StaffWebSession.ACCOUNT, STAFF.accountId());
        protectedResponse(mvc.perform(request(DETAIL)
                .header("X-PropertyFlow-Request", "partial")
                .session(cookielessSession)
                .param("accountId", STAFF.accountId().toString())
                .with(servletRequest -> {
                    servletRequest.setRequestedSessionId(cookielessSession.getId());
                    servletRequest.setRequestedSessionIdFromCookie(false);
                    servletRequest.setRequestedSessionIdFromURL(true);
                    return servletRequest;
                })), 401);
        verifyNoInteractions(listing, reading);
    }

    @Test
    void listUsesTrustedSessionAccountAndMapsFilterSortingAndPage() throws Exception {
        StaffCaseQuery query = new StaffCaseQuery("Heizung", "Noch nicht bewertet", true, "title", true, 2);
        StaffCaseSummary row = new StaffCaseSummary(caseState(), TITLE, UNIT, RECEIVED_AT, RECEIVED_AT,
                Optional.of(LocalDate.parse("2026-11-20")));
        when(listing.listForStaff(STAFF, query)).thenReturn(new StaffCasePage(List.of(row), 51, 2, 2));

        protectedResponse(mvc.perform(signedIn(OVERVIEW)
                .param("q", "Heizung")
                .param("urgency", "Noch nicht bewertet")
                .param("closed", "true")
                .param("sort", "title")
                .param("desc", "true")
                .param("page", "2")
                .param("accountId", "ac624e41-ca64-4d1c-98bb-3c2ccc39450a")), 200)
                .andExpect(content().string(containsString("Fallübersicht")))
                .andExpect(content().string(containsString("Heizung &lt;kaputt&gt;")))
                .andExpect(content().string(containsString(CONTEXT + DETAIL)))
                .andExpect(content().string(containsString("Eingegangen")))
                .andExpect(content().string(containsString(CONTEXT + "/abmelden")));
        verify(listing).listForStaff(STAFF, query);
        verifyNoInteractions(reading);
    }

    @Test
    void detailShowsStaffProjectionAndDiscardsExternalReturnTarget() throws Exception {
        TenantCaseView publicView = new TenantCaseView(caseState(), TITLE, "Das Haus ist kalt.", UNIT, Optional.empty(),
                "m***@example.test", RECEIVED_AT, RECEIVED_AT, 2, Optional.empty(),
                List.of(new PublicMessage("message-1", PublicAuthor.TENANT, "Das Haus ist kalt.", RECEIVED_AT)),
                List.of(new UrgencyChange("urgency-1", Optional.empty(), Urgency.NORMAL, UrgencySource.SYSTEM, RECEIVED_AT)));
        StaffCaseView projection = new StaffCaseView(publicView, "mieter@example.test", Optional.empty(),
                List.of(new ReviewReason("Mitarbeiterabschluss", false)),
                List.of(new InternalMemo("memo-1", "Intern <wichtig>", RECEIVED_AT)),
                List.of(new InternalChange("urgency-2", "Dringlichkeit manuell bestätigt: Normal → Niedrig.", STAFF.accountId(),
                                "Marco", RECEIVED_AT, false, new UrgencyDetails(Optional.of(Urgency.NORMAL), Urgency.LOW, "Geprüft")),
                        new InternalChange("system-closure", "Fall automatisch abgeschlossen.", null, "System", RECEIVED_AT, true, null)));
        when(reading.readForStaff(STAFF, CASE_ID)).thenReturn(projection);

        protectedResponse(mvc.perform(signedIn(DETAIL).param("returnTo", "https://external.example.test/")), 200)
                .andExpect(content().string(containsString(CASE_ID)))
                .andExpect(content().string(containsString("Das Haus ist kalt.")))
                .andExpect(content().string(containsString("Intern &lt;wichtig&gt;")))
                .andExpect(content().string(containsString("title=\"Mieter\">👤")))
                .andExpect(content().string(containsString("title=\"Immobilienverwaltung · Marco\">👤")))
                .andExpect(content().string(containsString("title=\"System\">🤖")))
                .andExpect(content().string(not(containsString("Immobilienverwaltung · System"))))
                .andExpect(content().string(containsString("class=\"badge urgency-0\">Noch nicht bewertet")))
                .andExpect(content().string(containsString("class=\"badge urgency-3\">Normal")))
                .andExpect(content().string(containsString("class=\"badge urgency-4\">Niedrig")))
                .andExpect(content().string(containsString(". Geprüft")))
                .andExpect(content().string(containsString("mieter@example.test")))
                .andExpect(content().string(containsString(CONTEXT + "/abmelden")))
                .andExpect(content().string(not(containsString("external.example.test"))))
                .andExpect(content().string(not(containsString(DETAIL + "/aktion\""))))
                .andExpect(content().string(containsString("action=\"" + CONTEXT + DETAIL + "/bearbeitung-fortsetzen\"")))
                .andExpect(content().string(not(containsString(DETAIL + "/fortsetzen\""))))
                .andExpect(content().string(containsString("<summary id=\"staff-review-heading\"><span>Bearbeitung fortsetzen</span></summary>")))
                .andExpect(content().string(containsString("Damit wird keine Kosten- oder Auftragsfreigabe erteilt.")))
                .andExpect(content().string(containsString("Begründung (optional, nur intern)")))
                .andExpect(content().string(containsString("data-command-base=\"" + CONTEXT + DETAIL + "/aktionen\"")))
                .andExpect(content().string(containsString("data-actor=\"" + STAFF.accountId() + "\"")));
        verify(reading).readForStaff(STAFF, CASE_ID);
        verifyNoInteractions(listing);
    }

    @Test
    void closureSuggestionRemainsEscapedStaffAdviceWithItsEarlierBasisVisible() throws Exception {
        TenantCaseView publicView = new TenantCaseView(caseState(), TITLE, "Das Haus ist kalt.", UNIT, Optional.empty(),
                "m***@example.test", RECEIVED_AT, RECEIVED_AT, 2, Optional.empty(), List.of(), List.of());
        CaseRecommendation recommendation = new CaseRecommendation("Verwaltung", "Fall prüfen", List.of(), null, false,
                "Vorgeschlagener Abschlussgrund: Problem behoben.\n<script>window.untrusted=true</script>");
        when(reading.readForStaff(STAFF, CASE_ID)).thenReturn(new StaffCaseView(publicView, "mieter@example.test", Optional.empty(),
                List.of(new ReviewReason("Mitarbeiterentscheidung erforderlich", true)), List.of(), List.of(), Optional.of(recommendation)));
        protectedResponse(mvc.perform(signedIn(DETAIL)), 200)
                .andExpect(content().string(containsString("Abschlussbewertung zur Prüfung")))
                .andExpect(content().string(containsString("title=\"System\">🤖")))
                .andExpect(content().string(containsString("Diese Empfehlung beruht auf einem früheren Fallstand.")))
                .andExpect(content().string(containsString("&lt;script&gt;window.untrusted=true&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("<script>window.untrusted=true</script>"))));
    }

    @ParameterizedTest
    @CsvSource({
            "FORBIDDEN, 403",
            "CASE_NOT_AVAILABLE, 404",
            "STORAGE_UNAVAILABLE, 503",
            "INTERNAL_FAILURE, 500"
    })
    void caseErrorsDoNotExposeCaseIdentifierOrInternalFault(CaseErrorCode error, int expectedStatus) throws Exception {
        when(reading.readForStaff(STAFF, CASE_ID))
                .thenThrow(new CaseServiceException(error, Map.of("internal", "vertrauliche technische Einzelheit")));

        ResultActions response = protectedResponse(mvc.perform(signedIn(DETAIL)), expectedStatus)
                .andExpect(header().doesNotExist("Location"))
                .andExpect(content().string(not(containsString(CASE_ID))))
                .andExpect(content().string(not(containsString("vertrauliche technische Einzelheit"))));
        if (error == CaseErrorCode.FORBIDDEN) {
            response.andExpect(header().string("X-PropertyFlow-Access", "denied"));
        }
    }

    @Test
    void listDeniesRevokedCaseAccessWithoutRedirect() throws Exception {
        StaffCaseQuery defaults = new StaffCaseQuery("", "", false, "default", false, 1);
        when(listing.listForStaff(STAFF, defaults)).thenThrow(new CaseServiceException(CaseErrorCode.FORBIDDEN, Map.of()));

        protectedResponse(mvc.perform(signedIn(OVERVIEW).header("X-PropertyFlow-Request", "partial")), 403)
                .andExpect(header().doesNotExist("Location"))
                .andExpect(header().string("X-PropertyFlow-Access", "denied"))
                .andExpect(content().string(not(containsString("Fallübersicht"))));
    }

    @ParameterizedTest
    @CsvSource({"closed, vielleicht", "desc, absteigend", "page, zwei", "page, 2147483648"})
    void malformedTransportFieldsRejectAfterTheSessionCheck(String field, String malformed) throws Exception {
        protectedResponse(mvc.perform(request(OVERVIEW)
                .header("X-PropertyFlow-Request", "partial")
                .param(field, malformed)), 401);
        protectedResponse(mvc.perform(signedIn(OVERVIEW).param(field, malformed)), 400)
                .andExpect(header().doesNotExist("Location"));
        verifyNoInteractions(listing, reading);
    }

    @Test
    void invalidSearchFromServiceIs400WithNeutralMessage() throws Exception {
        StaffCaseQuery invalid = new StaffCaseQuery("", "private-sentinel", false, "default", false, 1);
        when(listing.listForStaff(STAFF, invalid))
                .thenThrow(new CaseServiceException(CaseErrorCode.INVALID_INPUT, Map.of("urgency", "private-sentinel")));

        protectedResponse(mvc.perform(signedIn(OVERVIEW).param("urgency", "private-sentinel")), 400)
                .andExpect(header().doesNotExist("Location"))
                .andExpect(content().string(not(containsString("private-sentinel"))));
        verify(listing).listForStaff(STAFF, invalid);
        verifyNoInteractions(reading);
    }

    private static MockHttpServletRequestBuilder request(String path) {
        return get(CONTEXT + path).contextPath(CONTEXT);
    }

    private static MockHttpServletRequestBuilder signedIn(String path) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(StaffWebSession.ACCOUNT, STAFF.accountId());
        session.setAttribute(StaffWebSession.CSRF, CSRF);
        return request(path).session(session).with(servletRequest -> {
            servletRequest.setRequestedSessionId(session.getId());
            servletRequest.setRequestedSessionIdFromCookie(true);
            return servletRequest;
        });
    }

    private static ResultActions protectedResponse(ResultActions response, int expectedStatus) throws Exception {
        return response.andExpect(status().is(expectedStatus))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    private static CaseStatusView caseState() {
        return new CaseStatusView(CASE_ID, CaseStatus.RECEIVED, Optional.empty());
    }

    @Configuration(proxyBeanMethods = false)
    @Profile("stage2-case-adapter-test")
    @EnableAutoConfiguration
    @Import(StaffCaseController.class)
    static class StaffCaseConfiguration {

        @Bean
        AccountCaseReading accountCaseReading() {
            return mock(AccountCaseReading.class);
        }

        @Bean
        StaffCaseListing staffCaseListing() {
            return mock(StaffCaseListing.class);
        }
    }
}
