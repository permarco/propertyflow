package com.pertegato.propertyflow.access.web;

import com.pertegato.propertyflow.access.demo.DemoAccounts;
import com.pertegato.propertyflow.access.demo.DemoAccounts.Account;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@Profile("frontend-demo")
public class CurrentAccount {

    private final DemoAccounts accounts;

    public CurrentAccount(DemoAccounts accounts) {
        this.accounts = accounts;
    }

    public Optional<Account> find(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(StaffWebSession.ACCOUNT) instanceof UUID id) {
            return accounts.findActiveById(id);
        }
        return Optional.empty();
    }

    public String submissionEmail(HttpServletRequest request, String guestEmail, boolean accountSubmission) {
        Optional<Account> tenant = find(request).filter(Account::tenant);
        if (accountSubmission && tenant.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Ihre Anmeldung ist nicht mehr gültig. Melden Sie sich erneut an oder geben Sie Ihre E-Mail-Adresse ein.");
        }
        HttpSession session = request.getSession(false);
        if (session == null || request.getParameter("csrf") == null
                || !request.getParameter("csrf").equals(session.getAttribute(StaffWebSession.CSRF))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Die Anfrage konnte nicht bestätigt werden. Bitte prüfen Sie Ihre Eingaben und senden Sie erneut.");
        }
        return tenant.map(Account::email).orElse(guestEmail);
    }

    public Account requireTenant(HttpServletRequest request) {
        Account account = find(request).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Bitte melden Sie sich an, um Ihre Fälle zu sehen."));
        if (!account.tenant()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Dieser Zugang benötigt ein Mieterkonto.");
        }
        return account;
    }
}
