package com.pertegato.propertyflow.caseprocessing.controller;

import com.pertegato.propertyflow.access.contract.StaffAccessDecision;
import com.pertegato.propertyflow.access.contract.StaffAccountAccess;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.access.web.BrowserResponses;
import com.pertegato.propertyflow.access.web.StaffWebSession;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Verknüpft ausschliesslich im lokalen Devprofil Fallansicht und tatsächliche Prozessinstanz.
 */
@Controller
@ControllerAdvice(assignableTypes = StaffCaseController.class)
@Profile("propertyflow-backend & propertyflow-dev")
class DevelopmentController {

    private final StaffAccountAccess accounts;
    private final CaseWorkflowStore workflow;
    private final String camundaWebBase;

    DevelopmentController(StaffAccountAccess accounts, CaseWorkflowStore workflow,
            @Value("${propertyflow.camunda.web-base-url:http://127.0.0.1:8080}") String camundaWebBase) {
        this.accounts = accounts;
        this.workflow = workflow;
        this.camundaWebBase = camundaWebBase.replaceAll("/+$", "");
    }

    @ModelAttribute
    void developmentLinks(Model model, HttpServletResponse response) {
        BrowserResponses.protect(response);
        model.addAttribute("developmentMode", true);
    }

    @GetMapping({"/", "/dev"})
    String home() {
        return "development/start";
    }

    @GetMapping("/mitarbeiter/faelle/{caseId}/prozess")
    ResponseEntity<Void> process(@PathVariable String caseId, HttpServletRequest request) {
        HttpSession session = StaffWebSession.cookieSession(request);
        if (session == null || !(session.getAttribute(StaffWebSession.ACCOUNT) instanceof UUID account)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        StaffAccessDecision decision = accounts.checkStaffAccess(new WebAccountContext(account));
        if (decision != StaffAccessDecision.ALLOWED) {
            return ResponseEntity.status(decision == StaffAccessDecision.UNAUTHENTICATED
                    ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN).build();
        }
        return workflow.leadingInstance(caseId)
                .map(key -> ResponseEntity.status(HttpStatus.SEE_OTHER)
                        .location(URI.create(camundaWebBase + "/operate/processes/" + key))
                        .<Void>build())
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
