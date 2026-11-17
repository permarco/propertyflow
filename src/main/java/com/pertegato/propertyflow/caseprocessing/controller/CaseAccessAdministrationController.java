package com.pertegato.propertyflow.caseprocessing.controller;

import com.pertegato.propertyflow.access.contract.AccountAdministration;
import com.pertegato.propertyflow.access.web.BrowserResponses;
import com.pertegato.propertyflow.access.web.StaffWebSession;
import com.pertegato.propertyflow.caseprocessing.service.CaseAccessAdministrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
@Profile("propertyflow-backend & !frontend-demo")
public class CaseAccessAdministrationController {
    private final CaseAccessAdministrationService service;
    private final AccountAdministration accounts;

    public CaseAccessAdministrationController(CaseAccessAdministrationService service, AccountAdministration accounts) {
        this.service = service;
        this.accounts = accounts;
    }

    @ModelAttribute
    void protect(HttpServletResponse response) {
        BrowserResponses.protect(response);
    }

    @GetMapping("/mitarbeiter/fallzugaenge")
    String view(@RequestParam(required = false) String caseId, HttpServletRequest request, Model model) {
        UUID actor = StaffWebSession.administrativeActor(request, null, false);
        accounts.requireCaseAccessAdministrator(actor);
        if (caseId != null && !caseId.isBlank()) {
            model.addAttribute("contact", service.contact(actor, caseId));
        }
        model.addAttribute("csrf", request.getSession(false).getAttribute(StaffWebSession.CSRF));
        return "access/case-access";
    }

    @PostMapping("/mitarbeiter/fallzugaenge/{id}/pruefung")
    String verify(@PathVariable String id, @RequestParam String csrf, @RequestParam long version,
            @RequestParam String email, @RequestParam String evidence, HttpServletRequest request,
            HttpServletResponse response, Model model) {
        UUID actor = StaffWebSession.administrativeActor(request, csrf, true);
        try {
            UUID proof = service.requestVerification(actor, id, version, email, evidence);
            model.addAttribute("verificationId", proof);
            model.addAttribute("operation", UUID.randomUUID());
            model.addAttribute("email", email.strip().toLowerCase(Locale.ROOT));
            model.addAttribute("version", version);
        } catch (IllegalArgumentException exception) {
            response.setStatus(400);
            model.addAttribute("error", exception.getMessage());
        }
        return view(id, request, model);
    }

    @PostMapping("/mitarbeiter/fallzugaenge/{id}/anwenden")
    String replace(@PathVariable String id, @RequestParam String csrf, @RequestParam long version,
            @RequestParam UUID verificationId, @RequestParam String code, @RequestParam String operation,
            @RequestParam String email, HttpServletRequest request, HttpServletResponse response, Model model) {
        try {
            service.verifyAndReplace(StaffWebSession.administrativeActor(request, csrf, true), id, version, verificationId, code, operation, email);
            model.addAttribute("success", "Der bisherige Link ist gesperrt. Der neue Link wird an die bestätigte Adresse versendet.");
        } catch (IllegalArgumentException exception) {
            response.setStatus(400);
            model.addAttribute("error", exception.getMessage());
            model.addAttribute("verificationId", verificationId);
            model.addAttribute("operation", operation);
            model.addAttribute("email", email);
            model.addAttribute("version", version);
        }
        return view(id, request, model);
    }
    @PostMapping({"/mitarbeiter/fallzugaenge/{id}/link-ersetzen", "/mitarbeiter/fallzugaenge/{id}/kontaktadresse-aendern"})
    void command(@PathVariable String id, @RequestParam Map<String, String> fields,
            HttpServletRequest request, HttpServletResponse response) {
        UUID actor = StaffWebSession.administrativeActor(request, fields.get("csrf"), true);
        boolean change = request.getRequestURI().endsWith("/kontaktadresse-aendern");
        Set<String> allowed = change ? Set.of("csrf", "version", "verificationId", "operation", "email")
                : Set.of("csrf", "version", "verificationId", "operation");
        if (!allowed.containsAll(fields.keySet())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        try {
            service.replace(actor, id, Long.parseLong(fields.get("version")), UUID.fromString(fields.get("verificationId")),
                    fields.get("operation"), fields.get("email"), change);
            response.setStatus(204);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/mitarbeiter/fallzugaenge/{id}/pruefung/{proofId}/bestaetigen")
    void confirm(@PathVariable String id, @PathVariable UUID proofId, @RequestParam String csrf, @RequestParam String code,
            HttpServletRequest request, HttpServletResponse response) {
        try {
            service.verify(StaffWebSession.administrativeActor(request, csrf, true), id, proofId, code);
            response.setStatus(204);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }

}
