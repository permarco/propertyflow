package com.pertegato.propertyflow.access.web;

import com.pertegato.propertyflow.access.demo.DemoAccounts.Account;
import com.pertegato.propertyflow.access.demo.DemoMcpGrants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.WebUtils;

@Controller
@Profile("frontend-demo")
@ConditionalOnProperty(name = "propertyflow.mcp.enabled", havingValue = "true")
@RequestMapping(DemoMcpAccessController.PATH)
class DemoMcpAccessController {

    static final String PATH = "/mcp-zugriff";
    private static final String VIEW = "access/mcp-grant";

    private final CurrentAccount accounts;
    private final DemoMcpGrants grants;

    DemoMcpAccessController(CurrentAccount accounts, DemoMcpGrants grants) {
        this.accounts = accounts;
        this.grants = grants;
    }

    @ModelAttribute
    void protectResponse(HttpServletResponse response) {
        StaffWebSession.protectResponse(response);
    }

    @GetMapping
    String show(HttpServletRequest request, Model model) {
        Account tenant = accounts.requireTenant(request);
        HttpSession session = request.getSession(false);
        synchronized (WebUtils.getSessionMutex(session)) {
            if (session.getAttribute(StaffWebSession.CSRF) == null) {
                session.setAttribute(StaffWebSession.CSRF, UUID.randomUUID().toString());
            }
            return form(model, session, tenant.id());
        }
    }

    @PostMapping("/freigeben")
    String grant(@RequestParam(required = false) String csrf, @RequestParam(required = false) String consent,
            HttpServletRequest request, HttpServletResponse response, Model model) {
        Account tenant = accounts.requireTenant(request);
        HttpSession session = request.getSession(false);
        requireCsrf(session, csrf);
        if (!"true".equals(consent)) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            model.addAttribute("error", "Bitte bestätigen Sie die Lesefreigabe für den lokalen MCP-Client.");
        } else {
            model.addAttribute("token", grants.grant(tenant.id()));
        }
        return form(model, session, tenant.id());
    }

    @PostMapping("/widerrufen")
    void revoke(@RequestParam(required = false) String csrf, HttpServletRequest request, HttpServletResponse response) {
        Account tenant = accounts.requireTenant(request);
        requireCsrf(request.getSession(false), csrf);
        grants.revoke(tenant.id());
        StaffWebSession.redirect(request, response, PATH);
    }

    private void requireCsrf(HttpSession session, String value) {
        if (session == null || value == null || !value.equals(session.getAttribute(StaffWebSession.CSRF))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Die Anfrage konnte nicht bestätigt werden. Bitte laden Sie die Seite neu.");
        }
    }

    private String form(Model model, HttpSession session, UUID tenantId) {
        model.addAttribute("csrf", session.getAttribute(StaffWebSession.CSRF));
        model.addAttribute("hasGrant", grants.hasActiveGrant(tenantId));
        model.addAttribute("staff", false);
        return VIEW;
    }
}
