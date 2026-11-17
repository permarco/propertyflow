package com.pertegato.propertyflow.caseprocessing.controller;

import com.pertegato.propertyflow.access.contract.StaffAccessDecision;
import com.pertegato.propertyflow.access.contract.StaffAccountAccess;
import com.pertegato.propertyflow.access.web.StaffWebSession;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseOperationsStore;
import com.pertegato.propertyflow.caseprocessing.service.CaseOperationsService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CaseOperationsWebTest {

    private final StaffAccountAccess access = mock(StaffAccountAccess.class);
    private final CaseOperationsStore store = mock(CaseOperationsStore.class);
    private MockMvc mvc;

    @BeforeEach
    void prepare() {
        mvc = MockMvcBuilders.standaloneSetup(new CaseOperationsController(new CaseOperationsService(access, store))).build();
        when(access.checkStaffAccess(any())).thenReturn(StaffAccessDecision.ALLOWED);
        when(store.attention()).thenReturn(List.of());
    }

    @Test
    void anonymousAndRevokedStaffCannotReadAndNoWriteRouteExists() throws Exception {
        mvc.perform(get("/mitarbeiter/betrieb")).andExpect(status().isSeeOther()).andExpect(header().string("Location", "/anmelden"));
        when(access.checkStaffAccess(any())).thenReturn(StaffAccessDecision.FORBIDDEN);
        mvc.perform(signed()).andExpect(status().isForbidden()).andExpect(model().attributeDoesNotExist("overview"));
        mvc.perform(post("/mitarbeiter/betrieb")).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(store);
    }

    @Test
    void boundedOverviewShowsGuidanceAndProtectsResponse() throws Exception {
        when(store.attention()).thenReturn(IntStream.range(0, 101).mapToObj(index -> new CaseOperationsStore.Entry(
                "CASE_MAIL", UUID.randomUUID(), "CASE-A", "UNCERTAIN", Instant.parse("2026-11-16T10:00:00Z"))).toList());
        CaseOperationsService.Overview overview = (CaseOperationsService.Overview) mvc.perform(signed())
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andReturn().getModelAndView().getModel().get("overview");
        assertThat(overview.more()).isTrue();
        assertThat(overview.rows()).hasSize(100);
        assertThat(overview.rows().getFirst().state()).isEqualTo("Zustellung ungewiss");
        assertThat(overview.rows().getFirst().guidance()).contains("doppelt");
    }

    @Test
    void databaseFailureIsNotPresentedAsEmptyHealthyOverview() throws Exception {
        when(store.attention()).thenThrow(new DataAccessResourceFailureException("private diagnostic"));
        mvc.perform(signed()).andExpect(status().isServiceUnavailable()).andExpect(model().attributeDoesNotExist("overview"))
                .andExpect(model().attribute("error", "Der Auftragsstand konnte nicht geladen werden. Bitte versuchen Sie es später erneut."));
    }

    private MockHttpServletRequestBuilder signed() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(StaffWebSession.ACCOUNT, UUID.randomUUID());
        session.setAttribute(StaffWebSession.CSRF, "csrf");
        return get("/mitarbeiter/betrieb").session(session).with(request -> {
            request.setRequestedSessionId(session.getId());
            request.setRequestedSessionIdFromCookie(true);
            return request;
        });
    }
}
