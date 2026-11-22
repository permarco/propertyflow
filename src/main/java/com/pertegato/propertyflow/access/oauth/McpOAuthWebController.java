package com.pertegato.propertyflow.access.oauth;

import com.pertegato.propertyflow.access.contract.McpScope;
import com.pertegato.propertyflow.access.contract.StaffAccessDecision;
import com.pertegato.propertyflow.access.contract.StaffAccountAccess;
import com.pertegato.propertyflow.access.contract.TenantAccountAccess;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.access.web.BrowserResponses;
import com.pertegato.propertyflow.access.web.StaffWebSession;
import com.pertegato.propertyflow.access.web.TenantWebSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

@Controller
@Profile("propertyflow-backend & !frontend-demo")
@ConditionalOnProperty(name = "propertyflow.mcp.backend.enabled", havingValue = "true")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class McpOAuthWebController {

    private static final String CONNECTIONS = "/mieter/verbundene-apps";
    private static final String STAFF_CONNECTIONS = "/mitarbeiter/verbundene-apps";
    private final McpOAuthSettings settings;
    private final McpOAuthDelegations delegations;
    private final TenantAccountAccess accounts;
    private final StaffAccountAccess staffAccounts;

    McpOAuthWebController(McpOAuthSettings settings, McpOAuthDelegations delegations, TenantAccountAccess accounts,
            StaffAccountAccess staffAccounts) {
        this.settings = settings;
        this.delegations = delegations;
        this.accounts = accounts;
        this.staffAccounts = staffAccounts;
    }

    @ResponseBody
    @GetMapping(value = "/.well-known/oauth-protected-resource/mcp", produces = MediaType.APPLICATION_JSON_VALUE)
    Map<String, Object> metadata() {
        return Map.of("resource", settings.resourceUri(), "authorization_servers", List.of(settings.issuer()),
                "scopes_supported", McpScope.valuesForMetadata(), "bearer_methods_supported", List.of("header"));
    }

    @GetMapping(CONNECTIONS)
    String connections(HttpServletRequest request, HttpServletResponse response, Model model) {
        BrowserResponses.protect(response);
        UUID account = TenantWebSession.account(request);
        if (account == null || accounts.verifiedEmail(account).isEmpty()) {
            BrowserResponses.seeOther(request, response, "/mieter/anmelden");
            return null;
        }
        return showConnections(account, false, request, model);
    }

    @GetMapping(STAFF_CONNECTIONS)
    String staffConnections(HttpServletRequest request, HttpServletResponse response, Model model) {
        BrowserResponses.protect(response);
        return showConnections(staffAccount(request, null, false), true, request, model);
    }

    private String showConnections(UUID account, boolean staff, HttpServletRequest request, Model model) {
        model.addAttribute("permissions", delegations.connected(account, staff));
        model.addAttribute("staff", staff);
        model.addAttribute("revokePath", (staff ? STAFF_CONNECTIONS : CONNECTIONS) + "/widerrufen");
        model.addAttribute("csrf", request.getSession(false).getAttribute(StaffWebSession.CSRF));
        return "tenant/connected-apps";
    }

    @PostMapping(CONNECTIONS + "/widerrufen")
    void revoke(@RequestParam(required = false) String csrf, HttpServletRequest request, HttpServletResponse response) {
        BrowserResponses.protect(response);
        UUID account = TenantWebSession.account(request);
        if (account == null || accounts.verifiedEmail(account).isEmpty() || csrf == null
                || !csrf.equals(request.getSession(false).getAttribute(StaffWebSession.CSRF))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        delegations.revoke(account);
        BrowserResponses.seeOther(request, response, CONNECTIONS);
    }

    @PostMapping(STAFF_CONNECTIONS + "/widerrufen")
    void revokeStaff(@RequestParam(required = false) String csrf, HttpServletRequest request, HttpServletResponse response) {
        BrowserResponses.protect(response);
        delegations.revoke(staffAccount(request, csrf, true));
        BrowserResponses.seeOther(request, response, STAFF_CONNECTIONS);
    }

    private UUID staffAccount(HttpServletRequest request, String csrf, boolean write) {
        UUID account = StaffWebSession.administrativeActor(request, csrf, write);
        if (staffAccounts.checkStaffAccess(new WebAccountContext(account)) != StaffAccessDecision.ALLOWED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return account;
    }
}
