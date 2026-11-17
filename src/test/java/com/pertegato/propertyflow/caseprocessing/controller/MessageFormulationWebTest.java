package com.pertegato.propertyflow.caseprocessing.controller;

import com.pertegato.propertyflow.access.contract.StaffAccessDecision;
import com.pertegato.propertyflow.access.contract.StaffAccountAccess;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.access.web.StaffWebSession;
import com.pertegato.propertyflow.caseprocessing.contract.StaffCaseDataCommands;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseRepository;
import com.pertegato.propertyflow.caseprocessing.service.MessageFormulationModel;
import com.pertegato.propertyflow.caseprocessing.service.MessageFormulationService;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MessageFormulationWebTest {

    private static final String PATH = "/mitarbeiter/faelle/CASE-A/ki-formulierung";
    private final StaffAccountAccess access = mock(StaffAccountAccess.class);
    private final StaffCaseDataCommands commands = mock(StaffCaseDataCommands.class);
    private final CaseRepository cases = mock(CaseRepository.class);
    private final MessageFormulationModel model = mock(MessageFormulationModel.class);
    private MockMvc mvc;
    private MockHttpSession session;

    @BeforeEach
    void prepare() {
        when(access.checkStaffAccess(any())).thenReturn(StaffAccessDecision.ALLOWED);
        when(cases.existsById(anyString())).thenReturn(true);
        when(commands.available(any(), anyString())).thenReturn(true);
        when(model.formulate(anyString())).thenReturn(Flux.just("Guten Tag,\n", "<script>Text</script>"));
        MessageFormulationService service = new MessageFormulationService(access, cases, commands, model, Duration.ofSeconds(3));
        mvc = MockMvcBuilders.standaloneSetup(new MessageFormulationController(service)).build();
        session = new MockHttpSession();
        session.setAttribute(StaffWebSession.ACCOUNT, UUID.randomUUID());
        session.setAttribute(StaffWebSession.CSRF, "csrf");
    }

    @Test
    void streamsJsonEventsWithProtectedHeadersAndRejectsReplay() throws Exception {
        UUID id = UUID.randomUUID();
        MvcResult started = mvc.perform(start(id)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer")).andReturn();
        started.getAsyncResult(5000);
        mvc.perform(asyncDispatch(started)).andExpect(content().string(containsString("event:delta")))
                .andExpect(content().string(containsString("event:complete")));
        assertThat(started.getResponse().getContentAsString()).contains("Guten Tag,\\n", "<script>Text</script>");
        mvc.perform(start(id)).andExpect(status().isConflict());
    }

    @Test
    void earlyCancelIsIdempotentAndSessionScoped() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(signed(PATH + "/" + id + "/abbrechen")).andExpect(status().isNoContent());
        mvc.perform(signed(PATH + "/" + id + "/abbrechen")).andExpect(status().isNoContent());
        mvc.perform(start(id)).andExpect(status().isConflict());
        verifyNoInteractions(model);
    }

    @ParameterizedTest
    @CsvSource({"internal,true", "attemptId,invalid", "actorId,forged", "text,''"})
    void malformedFormDoesNotCallProvider(String field, String value) throws Exception {
        MockHttpServletRequestBuilder request = start(UUID.randomUUID());
        request.with(input -> {
            input.setParameter(field, value);
            return input;
        });
        mvc.perform(request).andExpect(status().isBadRequest());
        verifyNoInteractions(model);
    }

    @Test
    void duplicateParametersAreRejected() throws Exception {
        mvc.perform(start(UUID.randomUUID()).param("text", "second")).andExpect(status().isBadRequest());
        verifyNoInteractions(model);
    }

    @Test
    void anonymousTenantUrlSessionAndCsrfAreRejected() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isUnauthorized()).andExpect(header().string("Location", "/anmelden"));
        mvc.perform(start(UUID.randomUUID()).with(input -> {
            input.setRequestedSessionIdFromCookie(false);
            input.setRequestedSessionIdFromURL(true);
            return input;
        })).andExpect(status().isUnauthorized());
        mvc.perform(start(UUID.randomUUID()).with(input -> {
            input.setParameter("csrf", "wrong");
            return input;
        })).andExpect(status().isForbidden());
        session.removeAttribute(StaffWebSession.ACCOUNT);
        session.setAttribute("propertyflow.tenant.accountId", UUID.randomUUID());
        mvc.perform(start(UUID.randomUUID())).andExpect(status().isUnauthorized());
        verifyNoInteractions(model);
    }

    @Test
    void missingCaseAndRevokedRoleAndClosedCaseAreRejected() throws Exception {
        when(cases.existsById("CASE-A")).thenReturn(false);
        mvc.perform(start(UUID.randomUUID())).andExpect(status().isNotFound());
        when(cases.existsById("CASE-A")).thenReturn(true);
        when(access.checkStaffAccess(any(WebAccountContext.class))).thenReturn(StaffAccessDecision.FORBIDDEN);
        mvc.perform(start(UUID.randomUUID())).andExpect(status().isForbidden());
        when(access.checkStaffAccess(any(WebAccountContext.class))).thenReturn(StaffAccessDecision.ALLOWED);
        when(commands.available(any(), anyString())).thenReturn(false);
        mvc.perform(start(UUID.randomUUID())).andExpect(status().isConflict());
        verifyNoInteractions(model);
    }

    @Test
    void logoutCancelsRunningProvider() throws Exception {
        CountDownLatch subscribed = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);
        when(model.formulate(anyString())).thenReturn(Flux.<String>never()
                .doOnSubscribe(subscription -> subscribed.countDown()).doOnCancel(cancelled::countDown));
        MvcResult started = mvc.perform(start(UUID.randomUUID())).andReturn();
        assertThat(subscribed.await(2, TimeUnit.SECONDS)).isTrue();
        session.invalidate();
        assertThat(cancelled.await(2, TimeUnit.SECONDS)).isTrue();
        started.getAsyncResult(5000);
        mvc.perform(asyncDispatch(started)).andExpect(content().string(containsString("event:aborted")));
    }

    private MockHttpServletRequestBuilder start(UUID id) {
        return signed(PATH).param("text", "Techniker kommt Dienstag.").param("attemptId", id.toString());
    }

    private MockHttpServletRequestBuilder signed(String path) {
        return post(path).contentType(MediaType.APPLICATION_FORM_URLENCODED).session(session).param("csrf", "csrf")
                .with(request -> {
                    request.setRequestedSessionId(session.getId());
                    request.setRequestedSessionIdFromCookie(true);
                    return request;
                });
    }
}
