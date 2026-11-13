package com.pertegato.propertyflow.access.web;

import com.pertegato.propertyflow.access.demo.DemoAccounts;
import com.pertegato.propertyflow.access.service.StaffLogin;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.WebUtils;

@Controller
@Profile("frontend-demo")
@RequestMapping({StaffWebSession.LOGIN, "/registrieren"})
class StaffLoginController {

    private static final String VIEW = "access/login";
    private static final String INVALID_CODE = "Der Code ist ungültig oder abgelaufen.";
    private static final String INVALID_CSRF = "Die Anfrage konnte nicht bestätigt werden. Bitte laden Sie die Seite neu.";

    private final StaffLogin login;
    private final DemoAccounts accounts;

    StaffLoginController(StaffLogin login, DemoAccounts accounts) {
        this.login = login;
        this.accounts = accounts;
    }

    @ModelAttribute
    void protectResponse(HttpServletRequest request, HttpServletResponse response, Model model) {
        StaffWebSession.protectResponse(response);
        boolean registration = isRegistration(request);
        model.addAttribute("registration", registration);
        model.addAttribute("loginBase", registration ? "/registrieren" : StaffWebSession.LOGIN);
    }

    @GetMapping
    String show(HttpServletRequest request, Model model) {
        HttpSession session = request.getSession();
        synchronized (WebUtils.getSessionMutex(session)) {
            return form(model, session, false, null);
        }
    }

    @PostMapping("/code")
    String requestCode(@RequestParam(required = false) String email, @RequestParam(required = false) String csrf,
            HttpServletRequest request, HttpServletResponse response, Model model) {
        HttpSession session = request.getSession();
        synchronized (WebUtils.getSessionMutex(session)) {
            if (!validCsrf(session, csrf)) {
                response.setStatus(HttpStatus.FORBIDDEN.value());
                return form(model, session, false, INVALID_CSRF);
            }

            String previous = (String) session.getAttribute(StaffWebSession.CODE_EMAIL);
            try {
                String targetEmail = DemoAccounts.normalizeEmail(email == null ? previous : email);
                if (isRegistration(request)) {
                    login.requestRegistrationCode(targetEmail);
                } else {
                    login.requestCode(targetEmail);
                }
                session.setAttribute(StaffWebSession.CODE_EMAIL, targetEmail);
                return form(model, session, true, null);
            } catch (IllegalArgumentException exception) {
                response.setStatus(HttpStatus.BAD_REQUEST.value());
                return form(model, session, false, "Geben Sie eine gültige E-Mail-Adresse ein.");
            } catch (StaffLogin.CodeRateLimitException exception) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                return form(model, session, email == null && previous != null,
                        "Bitte warten Sie, bevor Sie einen neuen Code anfordern.");
            }
        }
    }

    @PostMapping("/pruefen")
    String verifyCode(@RequestParam(required = false) String code, @RequestParam(required = false) String csrf,
            HttpServletRequest request, HttpServletResponse response, Model model) {
        HttpSession session = request.getSession();
        synchronized (WebUtils.getSessionMutex(session)) {
            if (!validCsrf(session, csrf)) {
                response.setStatus(HttpStatus.FORBIDDEN.value());
                return form(model, session, false, INVALID_CSRF);
            }

            String email = (String) session.getAttribute(StaffWebSession.CODE_EMAIL);
            Optional<UUID> verified = email == null ? Optional.empty() : login.verifyCode(email, code);
            if (verified.isEmpty()) {
                response.setStatus(HttpStatus.BAD_REQUEST.value());
                return form(model, session, email != null, INVALID_CODE);
            }

            String target = StaffWebSession.safeTarget((String) session.getAttribute(StaffWebSession.RETURN_TO));
            if (accounts.findActiveById(verified.get()).filter(DemoAccounts.Account::tenant).isPresent()) {
                target = "/mieter/faelle";
            }
            request.changeSessionId();
            session.setAttribute(StaffWebSession.ACCOUNT, verified.get());
            session.setAttribute(StaffWebSession.CSRF, UUID.randomUUID().toString());
            session.removeAttribute(StaffWebSession.CODE_EMAIL);
            session.removeAttribute(StaffWebSession.RETURN_TO);
            StaffWebSession.redirect(request, response, target);
            return null;
        }
    }

    private boolean validCsrf(HttpSession session, String submitted) {
        return submitted != null && submitted.equals(session.getAttribute(StaffWebSession.CSRF));
    }

    private boolean isRegistration(HttpServletRequest request) {
        return request.getRequestURI().startsWith(request.getContextPath() + "/registrieren");
    }

    private String form(Model model, HttpSession session, boolean codeStep, String error) {
        Object csrf = session.getAttribute(StaffWebSession.CSRF);
        if (csrf == null) {
            csrf = UUID.randomUUID().toString();
            session.setAttribute(StaffWebSession.CSRF, csrf);
        }
        model.addAttribute("csrf", csrf);
        model.addAttribute("codeStep", codeStep);
        model.addAttribute("error", error);
        return VIEW;
    }
}
