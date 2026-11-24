package com.pertegato.propertyflow.caseprocessing.controller;

import com.pertegato.propertyflow.access.contract.TenantAccountAccess;
import com.pertegato.propertyflow.access.web.BrowserResponses;
import com.pertegato.propertyflow.access.web.GuestRequestLimits;
import com.pertegato.propertyflow.access.web.StaffWebSession;
import com.pertegato.propertyflow.access.web.TenantWebSession;
import com.pertegato.propertyflow.caseprocessing.contract.CaseActionException;
import com.pertegato.propertyflow.caseprocessing.contract.CaseServiceException;
import com.pertegato.propertyflow.caseprocessing.service.PersistentCaseService;
import com.pertegato.propertyflow.caseprocessing.service.TenantMessageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@Profile("propertyflow-backend & !frontend-demo")
@RequiredArgsConstructor
public class TenantAccountCaseController {
    private final PersistentCaseService cases;
    private final TenantMessageService messages;
    private final TenantAccountAccess accounts;
    private final GuestRequestLimits limits;

    @Value("${propertyflow.mcp.backend.enabled:false}")
    private boolean connectedAppsAvailable;

    @ModelAttribute
    void protect(HttpServletResponse response) {
        BrowserResponses.protect(response);
    }

    @GetMapping("/mieter/faelle")
    String list(HttpServletRequest request, HttpServletResponse response, Model model) {
        UUID account = TenantWebSession.account(request);
        if (accounts.verifiedEmail(account).isEmpty()) {
            BrowserResponses.seeOther(request, response, "/mieter/anmelden");
            return null;
        }
        model.addAttribute("cases", cases.listForAccount(account).stream().map(view -> new AccountRow(
                view.state().caseId(), view.title(), view.originalUnit(), PersonalCaseController.statusLabel(view.state().status()))).toList());
        model.addAttribute("csrf", request.getSession(false).getAttribute(StaffWebSession.CSRF));
        model.addAttribute("connectedAppsAvailable", connectedAppsAvailable);
        return "tenant/account-cases";
    }

    @GetMapping("/mieter/faelle/{caseId}")
    String read(@PathVariable String caseId, HttpServletRequest request, HttpServletResponse response, Model model) {
        try {
            PersonalCaseController.populate(cases.readForAccount(TenantWebSession.account(request), caseId), request, model);
            return "tenant/case";
        } catch (CaseServiceException exception) {
            boolean unavailable = switch (exception.code()) {
                case CASE_NOT_AVAILABLE, UNAUTHENTICATED, FORBIDDEN, INVALID_INPUT -> true;
                default -> false;
            };
            response.setStatus(unavailable ? 404 : 503);
            model.addAttribute("error", unavailable ? "Dieser Fall ist für Ihr Konto nicht verfügbar. Bitte prüfen Sie Ihre Anmeldung."
                    : "Die Fallansicht ist vorübergehend nicht verfügbar.");
            return "tenant/error";
        }
    }

    @PostMapping({"/mieter/faelle/{caseId}", "/mieter/faelle/{caseId}/nachrichten"})
    String send(@PathVariable String caseId, @RequestParam Map<String, String> fields,
            HttpServletRequest request, HttpServletResponse response, Model model) {
        HttpSession session = StaffWebSession.cookieSession(request);
        if (session == null || !TenantMessageController.csrf(session).equals(fields.get("csrf"))) {
            response.setStatus(403);
            model.addAttribute("error", "Bitte melden Sie sich erneut an und öffnen Sie den Fall.");
            return "tenant/error";
        }
        if (!limits.allowSubmission(request.getRemoteAddr())) {
            response.setStatus(429);
            model.addAttribute("error", "Bitte warten Sie kurz vor dem nächsten Versuch.");
            return "tenant/error";
        }
        try {
            messages.sendForAccount(TenantWebSession.account(request), caseId, fields.get("operation"), fields.get("text"));
            BrowserResponses.seeOther(request, response, "/mieter/faelle/" + caseId);
            return null;
        } catch (CaseActionException exception) {
            response.setStatus("CASE_NOT_AVAILABLE".equals(exception.code()) ? 404 : 409);
            model.addAttribute("error", "Die Nachricht wurde nicht bestätigt. Prüfen Sie Fallzugriff und Bearbeitungsstand.");
            return "tenant/error";
        }
    }

    public record AccountRow(String id, String title, String unit, String status) {
    }
}
