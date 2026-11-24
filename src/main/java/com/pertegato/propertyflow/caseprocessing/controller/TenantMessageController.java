package com.pertegato.propertyflow.caseprocessing.controller;

import com.pertegato.propertyflow.access.web.BrowserResponses;
import com.pertegato.propertyflow.access.web.GuestRequestLimits;
import com.pertegato.propertyflow.caseprocessing.contract.CaseActionException;
import com.pertegato.propertyflow.caseprocessing.contract.TenantMessageSubmission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.WebUtils;

@Controller
@Profile("propertyflow-backend & !frontend-demo")
@RequiredArgsConstructor
public class TenantMessageController {

    private static final String CSRF = "propertyflow.tenant-message.csrf";
    private final TenantMessageSubmission messages;
    private final GuestRequestLimits limits;

    static String csrf(HttpSession session) {
        synchronized (WebUtils.getSessionMutex(session)) {
            if (session.getAttribute(CSRF) == null) {
                session.setAttribute(CSRF, UUID.randomUUID().toString());
            }
            return (String) session.getAttribute(CSRF);
        }
    }

    @PostMapping({"/mieter/fall/zugang/{token}/nachrichten", "/mieter/fall/zugang/{token}"})
    Object send(@PathVariable String token, @RequestParam Map<String, String> fields,
            HttpServletRequest request, HttpServletResponse response, Model model) {
        BrowserResponses.protect(response);
        HttpSession session = request.isRequestedSessionIdFromCookie() && !request.isRequestedSessionIdFromURL() ? request.getSession(false) : null;
        if (session == null || fields.get("csrf") == null || !fields.get("csrf").equals(session.getAttribute(CSRF))) {
            return ResponseEntity.status(403).body(Map.of("code", "SESSION_REQUIRED"));
        }
        if (!limits.allowSubmission(request.getRemoteAddr())) {
            return ResponseEntity.status(429).body(Map.of("code", "TRY_LATER"));
        }
        try {
            if (!fields.keySet().stream().allMatch(key -> key.equals("csrf") || key.equals("operation") || key.equals("text"))) {
                throw new CaseActionException("INVALID_MESSAGE");
            }
            messages.send(token, fields.get("operation"), fields.get("text"));
            BrowserResponses.seeOther(request, response, "/mieter/fall/zugang/" + token);
            return null;
        } catch (CaseActionException exception) {
            int status = switch (exception.code()) {
                case "CASE_NOT_AVAILABLE" -> 404;
                case "CASE_CLOSED", "IDEMPOTENCY_CONFLICT", "PROCESS_CHANGED", "PROCESS_NOT_READY" -> 409;
                default -> 400;
            };
            if (status == 404 || "CASE_CLOSED".equals(exception.code())) {
                response.setStatus(status);
                model.addAttribute("error", status == 404 ? "Dieser Fall ist nicht verfügbar." : "Dieser Fall ist abgeschlossen.");
                return "tenant/error";
            }
            return retryForm(status, fields, response, model);
        } catch (IllegalArgumentException exception) {
            return retryForm(400, fields, response, model);
        } catch (RuntimeException exception) {
            return retryForm(500, fields, response, model);
        }
    }

    private String retryForm(int status, Map<String, String> fields, HttpServletResponse response, Model model) {
        response.setStatus(status);
        model.addAttribute("messageText", fields.getOrDefault("text", ""));
        model.addAttribute("messageOperation", fields.getOrDefault("operation", ""));
        model.addAttribute("messageCsrf", fields.getOrDefault("csrf", ""));
        model.addAttribute("error", status == 400
                ? "Bitte prüfen Sie die Nachricht: höchstens 2’000 Zeichen und eine gültige Absende-ID."
                : "Die Übergabe konnte nicht bestätigt werden. Ihr Text und dieselbe Absende-ID bleiben für einen erneuten Versuch erhalten.");
        return "tenant/message-error";
    }
}
