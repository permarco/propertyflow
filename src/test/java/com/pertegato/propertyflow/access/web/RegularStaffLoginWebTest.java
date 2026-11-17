package com.pertegato.propertyflow.access.web;

import com.pertegato.propertyflow.access.contract.StaffAuthentication;
import com.pertegato.propertyflow.access.contract.StaffAuthenticationErrorCode;
import com.pertegato.propertyflow.access.contract.StaffAuthenticationException;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RegularStaffLoginWebTest {

    private static final String EMAIL = "staff@example.test";
    private static final String CSRF = "session-csrf";
    private static final String CODE = "123456";
    private final StaffAuthentication authentication = mock(StaffAuthentication.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new RegularStaffLoginController(authentication))
            .setViewResolvers(new InternalResourceViewResolver("/views/", ".html"))
            .build();

    @Test
    void csrfMustBelongToTheCookieSession() throws Exception {
        mvc.perform(form("/anmelden/code", session()).param("email", EMAIL).param("csrf", "foreign"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/anmelden/pruefen").param("csrf", CSRF).param("code", CODE))
                .andExpect(status().isForbidden());
        verifyNoInteractions(authentication);
    }

    @Test
    void serverBindsTheCodeToItsOwnFlowAndRotatesTheSession() throws Exception {
        MockHttpSession session = session();
        String oldSessionId = session.getId();
        String target = "/mitarbeiter/faelle/CASE-1?returnTo=%2Fmitarbeiter%2Ffaelle";
        session.setAttribute(StaffWebSession.RETURN_TO, target);
        UUID clientFlow = UUID.randomUUID();
        mvc.perform(form("/anmelden/code", session).param("email", EMAIL).param("csrf", CSRF)
                        .param("loginFlowId", clientFlow.toString()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("codeStep", true));
        ArgumentCaptor<UUID> requested = ArgumentCaptor.forClass(UUID.class);
        verify(authentication).requestCode(requested.capture(), eq(EMAIL));
        assertThat(requested.getValue()).isNotEqualTo(clientFlow);
        UUID account = UUID.randomUUID();
        when(authentication.verifyCode(requested.getValue(), CODE)).thenReturn(Optional.of(new WebAccountContext(account)));

        mvc.perform(form("/anmelden/pruefen", session).param("csrf", CSRF).param("code", CODE)
                        .param("loginFlowId", clientFlow.toString()).param("returnTo", "https://example.test"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", target));

        assertThat(session.getId()).isNotEqualTo(oldSessionId);
        assertThat(session.getAttribute(StaffWebSession.ACCOUNT)).isEqualTo(account);
        String renewedCsrf = (String) session.getAttribute(StaffWebSession.CSRF);
        assertThat(renewedCsrf).isNotEqualTo(CSRF);
        mvc.perform(form("/anmelden/pruefen", session).param("csrf", renewedCsrf).param("code", CODE))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 503})
    void technicalErrorsInBothStepsPreserveUnauthenticatedSession(int expected) throws Exception {
        MockHttpSession session = session();
        RuntimeException failure = expected == 503
                ? new StaffAuthenticationException(StaffAuthenticationErrorCode.SERVICE_UNAVAILABLE)
                : new IllegalStateException("private diagnostic");
        doThrow(failure).doNothing().when(authentication).requestCode(any(), eq(EMAIL));
        var request = mvc.perform(form("/anmelden/code", session).param("email", EMAIL).param("csrf", CSRF))
                .andExpect(status().is(expected))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andReturn();
        String message = (String) request.getModelAndView().getModel().get("error");
        assertThat(message).contains("vorübergehend").doesNotContain("diagnostic");
        mvc.perform(form("/anmelden/code", session).param("email", EMAIL).param("csrf", CSRF))
                .andExpect(status().isOk());
        when(authentication.verifyCode(any(), eq(CODE))).thenThrow(failure);
        mvc.perform(form("/anmelden/pruefen", session).param("csrf", CSRF).param("code", CODE))
                .andExpect(status().is(expected))
                .andExpect(model().attribute("error", message));
        assertThat(session.getAttribute(StaffWebSession.ACCOUNT)).isNull();
        assertThat(session.getAttribute(StaffWebSession.CSRF)).isEqualTo(CSRF);
    }

    private static MockHttpSession session() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(StaffWebSession.CSRF, CSRF);
        return session;
    }

    private static MockHttpServletRequestBuilder form(String path, MockHttpSession session) {
        return post(path).session(session).with(request -> {
            request.setRequestedSessionId(session.getId());
            request.setRequestedSessionIdFromCookie(true);
            return request;
        });
    }
}
