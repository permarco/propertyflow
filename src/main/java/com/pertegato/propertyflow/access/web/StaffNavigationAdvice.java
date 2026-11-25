package com.pertegato.propertyflow.access.web;

import com.pertegato.propertyflow.access.service.AccountAdministrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(basePackages = "com.pertegato.propertyflow.caseprocessing.controller")
@Profile("propertyflow-backend")
public class StaffNavigationAdvice {
    private final AccountAdministrationService accounts;
    private final boolean connectedAppsAvailable;

    public StaffNavigationAdvice(AccountAdministrationService accounts,
            @Value("${propertyflow.mcp.backend.enabled:false}") boolean connectedAppsAvailable) {
        this.accounts = accounts;
        this.connectedAppsAvailable = connectedAppsAvailable;
    }

    @ModelAttribute
    void permissions(HttpServletRequest request, Model model) {
        HttpSession session = StaffWebSession.cookieSession(request);
        UUID actor = session != null && session.getAttribute(StaffWebSession.ACCOUNT) instanceof UUID id ? id : null;
        model.addAttribute("canManageAccounts", accounts.canManageAccounts(actor));
        model.addAttribute("canManageCaseAccess", accounts.canManageCaseAccess(actor));
        model.addAttribute("connectedAppsAvailable", connectedAppsAvailable);
    }
}
