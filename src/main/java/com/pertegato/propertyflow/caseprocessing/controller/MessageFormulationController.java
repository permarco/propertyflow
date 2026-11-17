package com.pertegato.propertyflow.caseprocessing.controller;

import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.access.web.BrowserResponses;
import com.pertegato.propertyflow.access.web.StaffWebSession;
import com.pertegato.propertyflow.caseprocessing.contract.CaseActionException;
import com.pertegato.propertyflow.caseprocessing.service.MessageFormulationService;
import com.pertegato.propertyflow.caseprocessing.service.MessageFormulationService.Attempt;
import com.pertegato.propertyflow.caseprocessing.service.MessageFormulationService.Attempts;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionBindingEvent;
import jakarta.servlet.http.HttpSessionBindingListener;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.util.WebUtils;

@Controller
@Profile("propertyflow-backend & !frontend-demo")
@RequestMapping("/mitarbeiter/faelle/{caseId}/ki-formulierung")
public class MessageFormulationController {

    private static final String ATTEMPTS = "propertyflow.staff.formulationAttempts";
    private final MessageFormulationService service;

    public MessageFormulationController(MessageFormulationService service) {
        this.service = service;
    }

    @PostMapping(produces = "text/event-stream", consumes = "application/x-www-form-urlencoded")
    SseEmitter start(@PathVariable String caseId, @RequestParam MultiValueMap<String, String> fields,
            HttpServletRequest request, HttpServletResponse response) {
        BrowserResponses.protect(response);
        Scope scope = scope(request, fields.getFirst("csrf"));
        fields(fields, Set.of("csrf", "text", "attemptId", "internal"));
        if (!"false".equals(fields.getOrDefault("internal", java.util.List.of("false")).getFirst())) {
            throw new CaseActionException("INVALID_INPUT");
        }
        Attempt attempt = service.prepare(new WebAccountContext(scope.actor()), scope.attempts(), caseId,
                attemptId(fields.getFirst("attemptId")), fields.getFirst("text"));
        SseEmitter emitter = new SseEmitter(0L);
        emitter.onCompletion(() -> attempt.finish("aborted"));
        emitter.onError(error -> attempt.finish("aborted"));
        emitter.onTimeout(() -> attempt.finish("error"));
        service.stream(attempt, fields.getFirst("text"), event -> {
            try {
                emitter.send(SseEmitter.event().name(event.name()).data(event.data()));
                if (!"delta".equals(event.name())) {
                    emitter.complete();
                }
            } catch (IOException disconnected) {
                throw new UncheckedIOException(disconnected);
            }
        });
        return emitter;
    }

    @PostMapping(value = "/{attemptId}/abbrechen", consumes = "application/x-www-form-urlencoded")
    ResponseEntity<Void> cancel(@PathVariable String caseId, @PathVariable String attemptId,
            @RequestParam MultiValueMap<String, String> fields, HttpServletRequest request, HttpServletResponse response) {
        BrowserResponses.protect(response);
        Scope scope = scope(request, fields.getFirst("csrf"));
        fields(fields, Set.of("csrf"));
        service.cancel(new WebAccountContext(scope.actor()), scope.attempts(), caseId, attemptId(attemptId));
        return ResponseEntity.noContent().build();
    }

    private Scope scope(HttpServletRequest request, String csrf) {
        HttpSession session = StaffWebSession.cookieSession(request);
        if (session == null) {
            throw new CaseActionException("UNAUTHENTICATED");
        }
        synchronized (WebUtils.getSessionMutex(session)) {
            try {
                if (!(session.getAttribute(StaffWebSession.ACCOUNT) instanceof UUID actor)) {
                    throw new CaseActionException("UNAUTHENTICATED");
                }
                if (csrf == null || csrf.isBlank() || !csrf.equals(session.getAttribute(StaffWebSession.CSRF))) {
                    throw new CaseActionException("FORBIDDEN");
                }
                if (session.getAttribute(ATTEMPTS) instanceof Scope current && current.actor().equals(actor)) {
                    return current;
                }
                Scope scope = new Scope(actor, new Attempts());
                session.setAttribute(ATTEMPTS, scope);
                return scope;
            } catch (IllegalStateException expired) {
                throw new CaseActionException("UNAUTHENTICATED");
            }
        }
    }

    private void fields(MultiValueMap<String, String> fields, Set<String> allowed) {
        if (!allowed.containsAll(fields.keySet()) || fields.values().stream().anyMatch(values -> values.size() != 1)) {
            throw new CaseActionException("INVALID_INPUT");
        }
    }

    private UUID attemptId(String value) {
        try {
            UUID id = UUID.fromString(value);
            if (!id.toString().equalsIgnoreCase(value)) {
                throw new IllegalArgumentException();
            }
            return id;
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new CaseActionException("INVALID_INPUT");
        }
    }

    @ExceptionHandler(CaseActionException.class)
    ResponseEntity<Void> failure(CaseActionException failure, HttpServletRequest request, HttpServletResponse response) {
        BrowserResponses.protect(response);
        int status = switch (failure.code()) {
            case "UNAUTHENTICATED" -> 401;
            case "FORBIDDEN" -> 403;
            case "INVALID_INPUT" -> 400;
            case "CASE_NOT_AVAILABLE" -> 404;
            case "CASE_NOT_ACTIVE", "IDEMPOTENCY_CONFLICT" -> 409;
            default -> 500;
        };
        if (status == 401) {
            response.setHeader("Location", request.getContextPath() + StaffWebSession.LOGIN);
        }
        return ResponseEntity.status(status).build();
    }

    private record Scope(UUID actor, Attempts attempts) implements HttpSessionBindingListener {

        @Override
        public void valueUnbound(HttpSessionBindingEvent event) {
            attempts.close();
        }
    }
}
