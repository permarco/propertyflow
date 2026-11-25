package com.pertegato.propertyflow.access.web;

import com.pertegato.propertyflow.access.contract.EmailAddress;
import com.pertegato.propertyflow.access.contract.StaffAuthenticationErrorCode;
import com.pertegato.propertyflow.access.contract.StaffAuthenticationException;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.access.service.TenantOtpService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.WebUtils;

@Controller
@Profile("propertyflow-backend")
class TenantLoginController {

    private static final Logger LOG = LoggerFactory.getLogger(TenantLoginController.class);
    private static final String CODE_EMAIL = "propertyflow.tenant.codeEmail";
    private static final String FLOW_ID = "propertyflow.tenant.loginFlowId";
    private static final String INVALID_CODE = "Der Code ist ungültig oder abgelaufen.";
    private static final String INVALID_CSRF = "Die Anfrage konnte nicht bestätigt werden. Bitte laden Sie die Anmeldeseite neu.";
    private static final String INVALID_EMAIL = "Geben Sie eine gültige E-Mail-Adresse ein.";
    private static final String TECHNICAL_ERROR = "Die Anmeldung ist vorübergehend nicht verfügbar. Bitte versuchen Sie es später erneut.";

    private final TenantOtpService authentication;

    TenantLoginController(TenantOtpService authentication) {
        this.authentication = authentication;
    }

    @ModelAttribute
    void protect(HttpServletRequest request, HttpServletResponse response, Model model) {
        BrowserResponses.protect(response);
        boolean registration = request.getRequestURI().startsWith(request.getContextPath() + "/registrieren");
        model.addAttribute("registration", registration);
        model.addAttribute("tenantLoginUrl", "/mieter/anmelden");
        model.addAttribute("loginBase", registration ? "/registrieren" : "/mieter/anmelden");
    }

    @GetMapping({"/mieter/anmelden", "/registrieren"})
    String show(HttpServletRequest request, Model model) {
        HttpSession session = request.getSession();
        synchronized (WebUtils.getSessionMutex(session)) {
            if (session.getAttribute(StaffWebSession.CSRF) == null) {
                session.setAttribute(StaffWebSession.CSRF, newCsrf());
            }
            return form(model, session, false, null);
        }
    }

    @PostMapping({"/mieter/anmelden/code", "/registrieren/code"})
    String requestCode(@RequestParam(required = false) String email, @RequestParam(required = false) String csrf,
            HttpServletRequest request, HttpServletResponse response, Model model) {
        HttpSession session = StaffWebSession.cookieSession(request);
        if (session == null) {
            return error(response, model, null, false, HttpStatus.FORBIDDEN, INVALID_CSRF);
        }
        synchronized (WebUtils.getSessionMutex(session)) {
            if (!validCsrf(session, csrf)) {
                return error(response, model, null, false, HttpStatus.FORBIDDEN, INVALID_CSRF);
            }
            String previous = (String) session.getAttribute(CODE_EMAIL);
            String normalized;
            try {
                normalized = EmailAddress.normalize(email == null ? previous : email);
            } catch (IllegalArgumentException exception) {
                return error(response, model, session, false, HttpStatus.BAD_REQUEST, INVALID_EMAIL);
            }
            UUID flow = UUID.randomUUID();
            try {
                authentication.requestCode(flow, normalized, request.getRequestURI().startsWith(request.getContextPath() + "/registrieren"));
                session.setAttribute(FLOW_ID, flow);
                session.setAttribute(CODE_EMAIL, normalized);
                return form(model, session, true, null);
            } catch (StaffAuthenticationException exception) {
                return switch (exception.code()) {
                    case INVALID_EMAIL -> error(response, model, session, false, HttpStatus.BAD_REQUEST, INVALID_EMAIL);
                    case RATE_LIMITED -> error(response, model, session, email == null && previous != null,
                            HttpStatus.TOO_MANY_REQUESTS, "Bitte warten Sie, bevor Sie einen neuen Code anfordern.");
                    case SERVICE_UNAVAILABLE -> error(
                            response, model, session, false, HttpStatus.SERVICE_UNAVAILABLE, TECHNICAL_ERROR);
                };
            } catch (RuntimeException exception) {
                return unexpected(response, model, session, false, exception);
            }
        }
    }

    @PostMapping({"/mieter/anmelden/pruefen", "/registrieren/pruefen"})
    String verify(@RequestParam(required = false) String code, @RequestParam(required = false) String csrf,
            HttpServletRequest request, HttpServletResponse response, Model model) {
        HttpSession session = StaffWebSession.cookieSession(request);
        if (session == null) {
            return error(response, model, null, false, HttpStatus.FORBIDDEN, INVALID_CSRF);
        }
        synchronized (WebUtils.getSessionMutex(session)) {
            if (!validCsrf(session, csrf)) {
                return error(response, model, null, false, HttpStatus.FORBIDDEN, INVALID_CSRF);
            }
            if (!(session.getAttribute(FLOW_ID) instanceof UUID flow)) {
                return error(response, model, session, false, HttpStatus.BAD_REQUEST, INVALID_CODE);
            }

            Optional<WebAccountContext> verified;
            try {
                verified = authentication.verifyCode(flow, code);
            } catch (StaffAuthenticationException exception) {
                if (exception.code() == StaffAuthenticationErrorCode.SERVICE_UNAVAILABLE) {
                    return error(response, model, session, true, HttpStatus.SERVICE_UNAVAILABLE, TECHNICAL_ERROR);
                }
                return unexpected(response, model, session, true, exception);
            } catch (RuntimeException exception) {
                return unexpected(response, model, session, true, exception);
            }
            if (verified.isEmpty()) {
                return error(response, model, session, true, HttpStatus.BAD_REQUEST, INVALID_CODE);
            }

            String target = McpConfirmationLogin.takeReturnTarget(session, false, TenantOAuthLogin.takeReturnTarget(session));
            request.changeSessionId();
            session.removeAttribute(StaffWebSession.ACCOUNT);
            session.setAttribute(TenantWebSession.ACCOUNT, verified.orElseThrow().accountId());
            session.setAttribute(StaffWebSession.CSRF, newCsrf());
            session.removeAttribute(FLOW_ID);
            session.removeAttribute(CODE_EMAIL);
            session.removeAttribute(StaffWebSession.RETURN_TO);
            BrowserResponses.seeOther(request, response, target);
            return null;
        }
    }

    private static boolean validCsrf(HttpSession session, String supplied) {
        try {
            return supplied != null && supplied.equals(session.getAttribute(StaffWebSession.CSRF));
        } catch (IllegalStateException exception) {
            return false;
        }
    }

    private static String newCsrf() {
        return UUID.randomUUID().toString();
    }

    private static String form(Model model, HttpSession session, boolean codeStep, String message) {
        model.addAttribute("codeStep", codeStep);
        model.addAttribute("csrf", session == null ? null : session.getAttribute(StaffWebSession.CSRF));
        model.addAttribute("error", message);
        return "access/tenant-login";
    }

    private static String error(HttpServletResponse response, Model model, HttpSession session, boolean codeStep,
            HttpStatus status, String message) {
        response.setStatus(status.value());
        return form(model, session, codeStep, message);
    }

    private static String unexpected(HttpServletResponse response, Model model, HttpSession session, boolean codeStep,
            RuntimeException exception) {
        LOG.error("Staff authentication failed ({})", exception.getClass().getSimpleName());
        return error(response, model, session, codeStep, HttpStatus.INTERNAL_SERVER_ERROR, TECHNICAL_ERROR);
    }
}
