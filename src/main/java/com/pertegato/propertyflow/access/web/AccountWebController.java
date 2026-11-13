package com.pertegato.propertyflow.access.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@ControllerAdvice
@Profile("frontend-demo")
class AccountWebController {

    private final CurrentAccount accounts;

    AccountWebController(CurrentAccount accounts) {
        this.accounts = accounts;
    }

    @ModelAttribute
    void currentAccount(HttpServletRequest request, Model model) {
        model.addAttribute("currentAccountEmail", accounts.find(request).map(account -> account.email()).orElse(null));
        model.addAttribute("tenantSignedIn", accounts.find(request).map(account -> account.tenant()).orElse(false));
    }

    @PostMapping("/abmelden")
    void logout(@RequestParam(required = false) String csrf, HttpServletRequest request, HttpServletResponse response) {
        StaffWebSession.protectResponse(response);
        HttpSession session = request.getSession(false);
        if (session == null || csrf == null || !csrf.equals(session.getAttribute(StaffWebSession.CSRF))) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            return;
        }
        session.invalidate();
        StaffWebSession.redirect(request, response, "/mieter/fall");
    }
}
