package com.pertegato.propertyflow.access.web;

import com.pertegato.propertyflow.access.service.AccountAdministrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
public class AccountAdministrationController {
    private final AccountAdministrationService accounts;

    public AccountAdministrationController(AccountAdministrationService accounts) {
        this.accounts = accounts;
    }

    @ModelAttribute
    void protect(HttpServletResponse response) {
        BrowserResponses.protect(response);
    }

    @GetMapping("/mitarbeiter/konten")
    String list(HttpServletRequest request, Model model) {
        model.addAttribute("operation", UUID.randomUUID());
        model.addAttribute("accounts", accounts.list(StaffWebSession.administrativeActor(request, null, false)));
        model.addAttribute("csrf", request.getSession(false).getAttribute(StaffWebSession.CSRF));
        return "access/accounts";
    }

    @PostMapping("/mitarbeiter/konten")
    String create(@RequestParam String csrf, @RequestParam String name, @RequestParam String email,
            @RequestParam Map<String, String> fields,
            HttpServletRequest request, HttpServletResponse response, Model model) {
        UUID actor = StaffWebSession.administrativeActor(request, csrf, true);
        try {
            if (!Set.of("csrf", "name", "email").containsAll(fields.keySet())) {
                throw new IllegalArgumentException("Das Formular enthält unzulässige Felder.");
            }
            accounts.create(actor, name, email);
            return list(request, model);
        } catch (IllegalArgumentException exception) {
            response.setStatus(400);
            model.addAttribute("error", exception.getMessage());
            return list(request, model);
        }
    }

    @PostMapping("/mitarbeiter/konten/{userId}/zugriff-entziehen")
    String revoke(@PathVariable UUID userId, @RequestParam String csrf, @RequestParam String operation,
            @RequestParam String action, @RequestParam Map<String, String> fields, HttpServletRequest request,
            HttpServletResponse response, Model model) {
        try {
            UUID actor = StaffWebSession.administrativeActor(request, csrf, true);
            if (!Set.of("csrf", "operation", "action").containsAll(fields.keySet())
                    || !Set.of("konto-sperren", "rolle-entziehen").contains(action)) {
                throw new IllegalArgumentException("Die Verwaltungsaktion ist ungültig.");
            }
            accounts.revoke(actor, userId, operation, "konto-sperren".equals(action));
            if (actor.equals(userId)) {
                request.getSession(false).invalidate();
                return "access/revoked";
            }
            return list(request, model);
        } catch (IllegalArgumentException exception) {
            response.setStatus(409);
            model.addAttribute("error", exception.getMessage());
            return list(request, model);
        }
    }
}
