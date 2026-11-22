package com.pertegato.propertyflow.caseprocessing.controller;

import com.pertegato.propertyflow.access.web.BrowserResponses;
import com.pertegato.propertyflow.access.web.McpConfirmationLogin;
import com.pertegato.propertyflow.access.web.StaffWebSession;
import com.pertegato.propertyflow.access.web.TenantWebSession;
import com.pertegato.propertyflow.caseprocessing.contract.CaseActionException;
import com.pertegato.propertyflow.caseprocessing.contract.McpMessageConfirmations;
import com.pertegato.propertyflow.caseprocessing.contract.McpMessageConfirmations.View;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
@Profile("propertyflow-backend & !frontend-demo")
@ConditionalOnProperty(name = "propertyflow.mcp.backend.enabled", havingValue = "true")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class McpMessageConfirmationController {

    private final McpMessageConfirmations confirmations;

    McpMessageConfirmationController(McpMessageConfirmations confirmations) {
        this.confirmations = confirmations;
    }

    @GetMapping({McpConfirmationLogin.TENANT_PATH + "{operation}", McpConfirmationLogin.STAFF_PATH + "{operation}"})
    String show(@PathVariable UUID operation, HttpServletRequest request, HttpServletResponse response, Model model) {
        BrowserResponses.protect(response);
        boolean staff = staff(request);
        UUID account = account(request, staff);
        if (account == null) {
            McpConfirmationLogin.requireLogin(request, response, staff, operation);
            return null;
        }
        try {
            View view = confirmations.view(account, staff, operation);
            model.addAttribute("draft", view);
            model.addAttribute("staff", staff);
            model.addAttribute("csrf", request.getSession(false).getAttribute(StaffWebSession.CSRF));
            model.addAttribute("target", path(staff, operation));
            return "mcp/confirm-message";
        } catch (CaseActionException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    @PostMapping({McpConfirmationLogin.TENANT_PATH + "{operation}", McpConfirmationLogin.STAFF_PATH + "{operation}"})
    void decide(@PathVariable UUID operation, @RequestParam String decision, @RequestParam String csrf,
            HttpServletRequest request, HttpServletResponse response) {
        BrowserResponses.protect(response);
        boolean staff = staff(request);
        UUID account = account(request, staff);
        if (account == null || csrf.isEmpty() || !csrf.equals(request.getSession(false).getAttribute(StaffWebSession.CSRF))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (!"send".equals(decision) && !"cancel".equals(decision)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        try {
            confirmations.decide(account, staff, operation, "send".equals(decision));
            BrowserResponses.seeOther(request, response, path(staff, operation));
        } catch (CaseActionException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private static boolean staff(HttpServletRequest request) {
        return request.getRequestURI().startsWith(request.getContextPath() + McpConfirmationLogin.STAFF_PATH);
    }

    private static String path(boolean staff, UUID operation) {
        return (staff ? McpConfirmationLogin.STAFF_PATH : McpConfirmationLogin.TENANT_PATH) + operation;
    }

    private static UUID account(HttpServletRequest request, boolean staff) {
        if (!staff) {
            return TenantWebSession.account(request);
        }
        HttpSession session = StaffWebSession.cookieSession(request);
        return session != null && session.getAttribute(StaffWebSession.ACCOUNT) instanceof UUID id ? id : null;
    }
}
