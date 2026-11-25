package com.pertegato.propertyflow.caseprocessing.controller;

import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.access.web.StaffWebSession;
import com.pertegato.propertyflow.caseprocessing.contract.CaseReopening;
import com.pertegato.propertyflow.caseprocessing.contract.FollowUpCommands;
import com.pertegato.propertyflow.caseprocessing.contract.StaffCaseDataCommands;
import com.pertegato.propertyflow.caseprocessing.contract.StaffCaseDataCommands.CaseDataResult;
import com.pertegato.propertyflow.caseprocessing.contract.StaffMessageSubmission;
import com.pertegato.propertyflow.caseprocessing.contract.StaffTaskDecisions;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prüft, dass Abschluss, Wiedereröffnung und Aufhebung des Antwortbedarfs erst nach dem
 * gespeicherten Fallstand Erfolg melden (UC-014, UC-015, UC-028).
 */
class StaffCaseDataWaitTest {

    private static final String CASE_ID = "CASE-1";
    private static final String BASE = "/mitarbeiter/faelle/" + CASE_ID;
    private static final String CSRF = "csrf-test";

    private final UUID actor = UUID.randomUUID();
    private final UUID operation = UUID.randomUUID();
    private final UUID order = UUID.randomUUID();
    private final UUID message = UUID.randomUUID();

    private StaffCaseDataCommands commands;
    private StaffTaskDecisions decisions;
    private CaseReopening reopening;
    private MockMvc browser;

    @BeforeEach
    void setUp() {
        commands = mock(StaffCaseDataCommands.class);
        decisions = mock(StaffTaskDecisions.class);
        reopening = mock(CaseReopening.class);
        StaffCaseDataController controller = new StaffCaseDataController(commands, decisions, mock(StaffMessageSubmission.class), reopening,
                mock(FollowUpCommands.class));
        browser = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void closeConfirmsOnlyAfterWorkerStoredClosure() throws Exception {
        when(decisions.decisionState(any(WebAccountContext.class), eq(CASE_ID), eq(operation))).thenReturn("CONFIRMED", "CONFIRMED", "APPLIED");

        browser.perform(close())
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    void closeRejectedByWorkerReportsChangedCase() throws Exception {
        when(decisions.decisionState(any(WebAccountContext.class), eq(CASE_ID), eq(operation))).thenReturn("REJECTED");

        browser.perform(close())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CASE_CHANGED"));
    }

    @Test
    void closeWithoutStoredResultWithinWaitReportsUnknownOutcome() throws Exception {
        when(decisions.decisionState(any(WebAccountContext.class), eq(CASE_ID), eq(operation))).thenReturn("CONFIRMED");

        browser.perform(close())
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("WORKFLOW_UNAVAILABLE"));
    }

    @Test
    void reopenConfirmsOnlyAfterNewInstanceStoredActiveStatus() throws Exception {
        when(reopening.request(any(WebAccountContext.class), eq(CASE_ID), eq(operation), eq(3L))).thenReturn(order);
        when(reopening.orderState(any(WebAccountContext.class), eq(CASE_ID), eq(order))).thenReturn("PENDING", "APPLIED");

        browser.perform(reopen())
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    void reopenRejectedOrBlockedOrderIsNotReportedAsSuccess() throws Exception {
        when(reopening.request(any(WebAccountContext.class), eq(CASE_ID), eq(operation), eq(3L))).thenReturn(order);
        when(reopening.orderState(any(WebAccountContext.class), eq(CASE_ID), eq(order))).thenReturn("REJECTED");
        browser.perform(reopen())
                .andExpect(status().isConflict());

        when(reopening.orderState(any(WebAccountContext.class), eq(CASE_ID), eq(order))).thenReturn("BLOCKED");
        browser.perform(reopen())
                .andExpect(status().isInternalServerError());
    }

    @Test
    void clearAnswerConfirmsOnlyAfterStoredCancellation() throws Exception {
        when(commands.clearAnswer(any(WebAccountContext.class), eq(CASE_ID), eq("clear"), anyLong(), eq(message)))
                .thenReturn(new CaseDataResult("clear", "PENDING", null, null));
        when(commands.read(any(WebAccountContext.class), eq(CASE_ID), anyString()))
                .thenReturn(new CaseDataResult("clear", "PENDING", null, null), new CaseDataResult("clear", "APPLIED", null, 4L));

        browser.perform(clearAnswer())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("APPLIED"));
    }

    @Test
    void clearAnswerRejectedByWorkerReportsItsCode() throws Exception {
        when(commands.clearAnswer(any(WebAccountContext.class), eq(CASE_ID), eq("clear"), anyLong(), eq(message)))
                .thenReturn(new CaseDataResult("clear", "PENDING", null, null));
        when(commands.read(any(WebAccountContext.class), eq(CASE_ID), anyString()))
                .thenReturn(new CaseDataResult("clear", "REJECTED", "CASE_CHANGED", null));

        browser.perform(clearAnswer())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CASE_CHANGED"));
    }

    private MockHttpServletRequestBuilder close() {
        return signed(post(BASE + "/abschliessen"))
                .param("operationId", operation.toString())
                .param("userTaskKey", "42")
                .param("version", "3")
                .param("reviewToken", "review")
                .param("csrf", CSRF);
    }

    private MockHttpServletRequestBuilder reopen() {
        return signed(post(BASE + "/wiedereroeffnen"))
                .param("operationId", operation.toString())
                .param("version", "3")
                .param("csrf", CSRF);
    }

    private MockHttpServletRequestBuilder clearAnswer() {
        return signed(post(BASE + "/antwortbedarf-aufheben"))
                .param("operationId", "clear")
                .param("version", "3")
                .param("messageId", message.toString())
                .param("csrf", CSRF);
    }

    private MockHttpServletRequestBuilder signed(MockHttpServletRequestBuilder request) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(StaffWebSession.ACCOUNT, actor);
        session.setAttribute(StaffWebSession.CSRF, CSRF);
        return request.session(session).with(servlet -> {
            servlet.setRequestedSessionId(session.getId());
            servlet.setRequestedSessionIdFromCookie(true);
            return servlet;
        });
    }
}
