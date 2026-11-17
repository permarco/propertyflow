package com.pertegato.propertyflow.caseprocessing.controller;

import com.pertegato.propertyflow.access.contract.StaffAccessDecision;
import com.pertegato.propertyflow.access.contract.StaffAccountAccess;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.access.web.StaffWebSession;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DevelopmentControllerTest {

    private final StaffAccountAccess accounts = mock(StaffAccountAccess.class);
    private final CaseWorkflowStore workflow = mock(CaseWorkflowStore.class);
    private final DevelopmentController controller = new DevelopmentController(accounts, workflow, "http://127.0.0.1:8086/");

    @Test
    void anonymousAccessCannotReadProcessAssignment() {
        assertEquals(HttpStatus.UNAUTHORIZED, controller.process("case", new MockHttpServletRequest()).getStatusCode());
        verifyNoInteractions(workflow);
    }

    @Test
    void tenantCannotReadProcessAssignment() {
        UUID id = UUID.randomUUID();
        when(accounts.checkStaffAccess(new WebAccountContext(id))).thenReturn(StaffAccessDecision.FORBIDDEN);
        assertEquals(HttpStatus.FORBIDDEN, controller.process("case", signedIn(id)).getStatusCode());
        verifyNoInteractions(workflow);
    }

    @Test
    void linkResolvesCurrentLeadingInstanceIncludingAfterReopening() {
        UUID id = UUID.randomUUID();
        when(accounts.checkStaffAccess(new WebAccountContext(id))).thenReturn(StaffAccessDecision.ALLOWED);
        when(workflow.leadingInstance("case")).thenReturn(Optional.of(123L), Optional.of(456L));
        assertEquals(URI.create("http://127.0.0.1:8086/operate/processes/123"),
                controller.process("case", signedIn(id)).getHeaders().getLocation());
        assertEquals(URI.create("http://127.0.0.1:8086/operate/processes/456"),
                controller.process("case", signedIn(id)).getHeaders().getLocation());
    }

    @Test
    void pendingStartHasNoInventedProcessLink() {
        UUID id = UUID.randomUUID();
        when(accounts.checkStaffAccess(new WebAccountContext(id))).thenReturn(StaffAccessDecision.ALLOWED);
        when(workflow.leadingInstance("case")).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, controller.process("case", signedIn(id)).getStatusCode());
    }

    private MockHttpServletRequest signedIn(UUID id) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(StaffWebSession.ACCOUNT, id);
        request.setSession(session);
        request.setRequestedSessionIdFromCookie(true);
        return request;
    }
}
