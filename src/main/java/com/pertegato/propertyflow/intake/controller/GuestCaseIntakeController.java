package com.pertegato.propertyflow.intake.controller;

import com.pertegato.propertyflow.access.contract.SubmissionContext.Guest;
import com.pertegato.propertyflow.access.contract.SubmissionContext;
import com.pertegato.propertyflow.access.contract.TenantAccountAccess;
import com.pertegato.propertyflow.access.web.BrowserResponses;
import com.pertegato.propertyflow.access.web.GuestRequestLimits;
import com.pertegato.propertyflow.access.web.TenantWebSession;
import com.pertegato.propertyflow.caseprocessing.contract.CaseAccepted;
import com.pertegato.propertyflow.caseprocessing.contract.CaseServiceException;
import com.pertegato.propertyflow.caseprocessing.contract.CreateCaseCommand;
import com.pertegato.propertyflow.intake.contract.CaseDescription;
import com.pertegato.propertyflow.intake.contract.CaseSubmission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
@Profile("propertyflow-backend & !frontend-demo")
@RequestMapping("/mieter/fall")
@RequiredArgsConstructor
public class GuestCaseIntakeController {

    private static final Logger LOG = LoggerFactory.getLogger(GuestCaseIntakeController.class);
    private static final String CSRF_ATTRIBUTE = GuestCaseIntakeController.class.getName() + ".csrf";
    private static final String SCOPE_ATTRIBUTE = GuestCaseIntakeController.class.getName() + ".scope";
    private static final String FORM_VIEW = "tenant/intake";
    private static final String TECHNICAL_ERROR = "Das Anliegen konnte nicht bestätigt werden. Bitte versuchen Sie es erneut.";
    private static final String SIGN_IN_REQUIRED = "Dieses Formular wurde aus einem Mieterkonto abgesendet. "
            + "Öffnen Sie das Gastformular, um ohne Anmeldung einzureichen.";
    private static final Map<String, String> FIELD_LABELS = Map.of(
            "title", "Betreff",
            "unit", "Objekt / Wohnung",
            "email", "E-Mail-Adresse",
            "description", "Beschreibung",
            "operation", "Absende-ID");

    private final CaseSubmission submission;
    private final GuestRequestLimits limits;
    private final TenantAccountAccess accounts;

    @ModelAttribute
    void protect(HttpServletResponse response) {
        BrowserResponses.protect(response);
    }

    @GetMapping
    String form(HttpServletRequest request, Model model) {
        String email = accounts.verifiedEmail(TenantWebSession.account(request)).orElse("");
        model.addAttribute("accountSubmission", !email.isEmpty());
        model.addAttribute("accountEmail", email);
        IntakeForm blank = new IntakeForm("", "", email, "", UUID.randomUUID().toString());
        return render(model, blank, csrfForForm(request.getSession()), null, Map.of());
    }

    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    String submit(@RequestParam Map<String, String> fields, HttpServletRequest request, HttpServletResponse response, Model model) {
        String email = accounts.verifiedEmail(TenantWebSession.account(request)).orElse("");
        model.addAttribute("accountSubmission", !email.isEmpty());
        model.addAttribute("accountEmail", email);
        IntakeForm form = new IntakeForm(
                fields.get("title"), fields.get("unit"), email.isEmpty() ? fields.get("email") : email,
                fields.get("description"), fields.get("operation"));
        HttpSession session = request.getSession(false);
        if (session == null || !request.isRequestedSessionIdFromCookie()
                || !(session.getAttribute(CSRF_ATTRIBUTE) instanceof String csrf)
                || !csrf.equals(fields.get("csrf"))) {
            return reject(response, model, form, null, HttpStatus.FORBIDDEN,
                    "Die Anfrage konnte nicht bestätigt werden. Öffnen Sie ein neues Formular.", Map.of());
        }
        String accountSubmission = fields.get("accountSubmission");
        if ("true".equals(accountSubmission) && accounts.verifiedEmail(TenantWebSession.account(request)).isEmpty()) {
            return reject(response, model, form, null, HttpStatus.UNAUTHORIZED, SIGN_IN_REQUIRED, Map.of());
        }
        if (accountSubmission != null && !"false".equals(accountSubmission) && !"true".equals(accountSubmission)) {
            return reject(response, model, form, csrf, HttpStatus.BAD_REQUEST, "Die Formularanfrage ist ungültig.", Map.of());
        }
        if (!limits.allowSubmission(request.getRemoteAddr())) {
            return reject(response, model, form, csrf, HttpStatus.TOO_MANY_REQUESTS, "Bitte warten Sie kurz und versuchen Sie es erneut.", Map.of());
        }

        try {
            UUID accountId = TenantWebSession.account(request);
            SubmissionContext context = accounts.verifiedEmail(accountId).isPresent()
                    ? new SubmissionContext.Tenant(accountId) : new Guest(guestScope(session));
            CaseAccepted accepted = submission.submit(context, form.toCommand());
            String path = UriComponentsBuilder.fromPath("/mieter/fall/zugang/{token}")
                    .encode()
                    .buildAndExpand(accepted.personalAccessToken())
                    .toUriString();
            BrowserResponses.seeOther(request, response, context instanceof SubmissionContext.Tenant ? "/mieter/faelle/" + accepted.caseId() : path);
            return null;
        } catch (CaseServiceException exception) {
            return switch (exception.code()) {
                case INVALID_INPUT -> reject(response, model, form, csrf, HttpStatus.BAD_REQUEST,
                        "Bitte prüfen Sie Ihre Eingaben.", fieldMessages(exception.fieldErrors()));
                case UNAUTHENTICATED -> reject(response, model, form, null, HttpStatus.UNAUTHORIZED, SIGN_IN_REQUIRED, Map.of());
                case FORBIDDEN -> reject(response, model, form, null, HttpStatus.FORBIDDEN, "Die Anfrage wurde abgelehnt.", Map.of());
                case IDEMPOTENCY_CONFLICT -> reject(response, model, form, csrf, HttpStatus.CONFLICT,
                        "Diese Absende-ID wurde bereits mit anderen Angaben verwendet.", Map.of());
                case REPLAY_ACCESS_UNAVAILABLE -> reject(response, model, form, csrf, HttpStatus.CONFLICT,
                        "Das Anliegen wurde bereits angenommen. Der bisherige Zugang ist nicht mehr verfügbar.", Map.of());
                case STORAGE_UNAVAILABLE -> reject(response, model, form, csrf, HttpStatus.SERVICE_UNAVAILABLE, TECHNICAL_ERROR, Map.of());
                case CASE_NOT_AVAILABLE, INTERNAL_FAILURE -> reject(
                        response, model, form, csrf, HttpStatus.INTERNAL_SERVER_ERROR, TECHNICAL_ERROR, Map.of());
            };
        } catch (RuntimeException exception) {
            LOG.error("Guest case submission failed ({})", exception.getClass().getSimpleName());
            return reject(response, model, form, csrf, HttpStatus.INTERNAL_SERVER_ERROR, TECHNICAL_ERROR, Map.of());
        }
    }

    private String reject(HttpServletResponse response, Model model, IntakeForm form, String csrf,
            HttpStatus status, String error, Map<String, String> fieldErrors) {
        response.setStatus(status.value());
        return render(model, form, csrf, error, fieldErrors);
    }

    private String csrfForForm(HttpSession session) {
        synchronized (session) {
            if (session.getAttribute(CSRF_ATTRIBUTE) instanceof String existing) {
                return existing;
            }
            String csrf = UUID.randomUUID().toString();
            session.setAttribute(CSRF_ATTRIBUTE, csrf);
            return csrf;
        }
    }

    private String render(Model model, IntakeForm form, String csrf, String error, Map<String, String> fieldErrors) {
        model.addAttribute("form", form);
        model.addAttribute("csrf", csrf);
        model.addAttribute("descriptionMaxLength", CaseDescription.MAX_LENGTH);
        model.addAttribute("error", error);
        model.addAttribute("fieldErrors", fieldErrors);
        return FORM_VIEW;
    }

    private UUID guestScope(HttpSession session) {
        synchronized (session) {
            if (session.getAttribute(SCOPE_ATTRIBUTE) instanceof UUID scope) {
                return scope;
            }
            UUID scope = UUID.randomUUID();
            session.setAttribute(SCOPE_ATTRIBUTE, scope);
            return scope;
        }
    }

    private Map<String, String> fieldMessages(Map<String, String> errors) {
        Map<String, String> messages = new LinkedHashMap<>();
        FIELD_LABELS.forEach((field, label) -> {
            String code = errors.get(field);
            if (code != null) {
                String message = switch (code) {
                    case "REQUIRED" -> "Bitte ausfüllen.";
                    case "TOO_LONG" -> "Die Eingabe ist zu lang.";
                    default -> "Bitte prüfen.";
                };
                messages.put(field, label + " · " + message);
            }
        });
        return messages;
    }

    public record IntakeForm(String title, String unit, String email, String description, String operation) {
        CreateCaseCommand toCommand() {
            return new CreateCaseCommand(operation, title, unit, description, email);
        }
    }
}
