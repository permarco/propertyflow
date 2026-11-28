package com.pertegato.propertyflow.caseprocessing.controller;

import com.pertegato.propertyflow.access.web.BrowserResponses;
import com.pertegato.propertyflow.access.web.GuestRequestLimits;
import com.pertegato.propertyflow.caseprocessing.contract.CaseReading;
import com.pertegato.propertyflow.caseprocessing.contract.CaseServiceException;
import com.pertegato.propertyflow.caseprocessing.contract.CaseStatus;
import com.pertegato.propertyflow.caseprocessing.contract.PublicMessage;
import com.pertegato.propertyflow.caseprocessing.contract.TenantCaseView;
import com.pertegato.propertyflow.caseprocessing.contract.Urgency;
import com.pertegato.propertyflow.caseprocessing.contract.UrgencyChange;
import com.pertegato.propertyflow.caseprocessing.contract.UrgencySource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@Profile("propertyflow-backend")
@RequiredArgsConstructor
public class PersonalCaseController {

    private static final Logger LOG = LoggerFactory.getLogger(PersonalCaseController.class);
    private static final String NOT_AVAILABLE = "Diese Fallansicht ist über den verwendeten Link nicht verfügbar.";
    private static final String TECHNICAL_ERROR = "Die Fallansicht ist vorübergehend nicht verfügbar.";
    static final String NOT_ASSESSED = "Noch nicht bewertet";
    static final DateTimeFormatter LOCAL_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm")
            .withZone(ZoneId.of("Europe/Zurich"));

    private final CaseReading reading;
    private final GuestRequestLimits limits;

    @GetMapping("/mieter/fall/zugang/{token}")
    String read(@PathVariable String token, HttpServletRequest request, HttpServletResponse response, Model model) {
        BrowserResponses.protect(response);
        if (!limits.allowPersonalLink(request.getRemoteAddr())) {
            return error(response, model, HttpStatus.TOO_MANY_REQUESTS, "Bitte warten Sie kurz und öffnen Sie den Fall-Link erneut.");
        }
        try {
            TenantCaseView view = reading.readByToken(token);
            populate(view, request, model);
            return "tenant/case";
        } catch (CaseServiceException exception) {
            return switch (exception.code()) {
                case INVALID_INPUT, CASE_NOT_AVAILABLE, FORBIDDEN, UNAUTHENTICATED ->
                        error(response, model, HttpStatus.NOT_FOUND, NOT_AVAILABLE);
                default -> error(response, model, HttpStatus.INTERNAL_SERVER_ERROR, TECHNICAL_ERROR);
            };
        } catch (RuntimeException exception) {
            LOG.error("Personal case read failed ({})", exception.getClass().getSimpleName());
            return error(response, model, HttpStatus.INTERNAL_SERVER_ERROR, TECHNICAL_ERROR);
        }
    }

    static void populate(TenantCaseView view, HttpServletRequest request, Model model) {
            model.addAttribute("caseId", view.state().caseId());
            model.addAttribute("title", view.title());
            model.addAttribute("originalUnit", view.originalUnit());
            model.addAttribute("assignedUnit", view.assignedUnit().orElse(null));
            model.addAttribute("maskedContact", view.maskedContact());
            model.addAttribute("status", statusLabel(view.state().status()));
            model.addAttribute("urgency", view.state().urgency().map(PersonalCaseController::urgencyLabel).orElse(NOT_ASSESSED));
            model.addAttribute("urgencyCss", view.state().urgency().map(PersonalCaseController::urgencyCss).orElse("urgency-0"));
            model.addAttribute("urgencySource", view.urgencySource().map(PersonalCaseController::urgencySourceLabel).orElse(""));
            model.addAttribute("history", history(view));
            model.addAttribute("messageCsrf", TenantMessageController.csrf(request.getSession()));
            model.addAttribute("messageOperation", UUID.randomUUID().toString());
            model.addAttribute("closed", view.state().status() == CaseStatus.CLOSED);
    }

    private String error(HttpServletResponse response, Model model, HttpStatus status, String message) {
        model.asMap().clear();
        model.addAttribute("error", message);
        response.setStatus(status.value());
        return "tenant/error";
    }

    private static List<HistoryEntry> history(TenantCaseView view) {
        List<HistoryEvent> events = new ArrayList<>();
        for (PublicMessage message : view.messages()) {
            String role = switch (message.author()) {
                case TENANT -> "Mieter";
                case STAFF -> "Immobilienverwaltung";
                case SYSTEM -> "System";
            };
            events.add(new HistoryEvent("message", message.id(), role, message.text(), message.occurredAt(), answerLabel(message), null));
        }
        for (UrgencyChange change : view.urgencyHistory()) {
            events.add(new HistoryEvent("urgency", change.id(), urgencySourceLabel(change.source()), "", change.occurredAt(), "",
                    HistoryUrgency.from(change.previous(), change.current())));
        }
        events.sort(Comparator.comparing(HistoryEvent::occurredAt)
                .thenComparing(HistoryEvent::type)
                .thenComparing(HistoryEvent::id));
        return events.stream()
                .map(event -> new HistoryEntry(event.role(), event.text(), LOCAL_TIME.format(event.occurredAt()),
                        event.answerLabel(), event.urgency()))
                .toList();
    }

    static String statusLabel(CaseStatus status) {
        return switch (status) {
            case RECEIVED -> "Eingegangen";
            case UNDER_CLARIFICATION -> "In Abklärung";
            case STAFF_REVIEW_REQUIRED -> "Mitarbeiterprüfung erforderlich";
            case IN_PROGRESS -> "In Bearbeitung";
            case WAITING_FOR_TENANT -> "Wartet auf Mieterantwort";
            case WAITING_FOR_EXTERNAL -> "Wartet auf externe Rückmeldung";
            case WAITING_FOR_FOLLOW_UP -> "Wartet auf Wiedervorlage";
            case CLOSED -> "Abgeschlossen";
        };
    }

    static String urgencyLabel(Urgency urgency) {
        return switch (urgency) {
            case CRITICAL -> "Kritisch";
            case HIGH -> "Hoch";
            case NORMAL -> "Normal";
            case LOW -> "Niedrig";
        };
    }

    static String urgencyCss(Urgency urgency) {
        return switch (urgency) {
            case CRITICAL -> "urgency-1";
            case HIGH -> "urgency-2";
            case NORMAL -> "urgency-3";
            case LOW -> "urgency-4";
        };
    }

    static String urgencySourceLabel(UrgencySource source) {
        return switch (source) {
            case STAFF -> "Immobilienverwaltung";
            case SYSTEM -> "System";
        };
    }

    static String answerLabel(PublicMessage message) {
        String label = switch (message.answerResolution() == null ? "" : message.answerResolution()) {
            case "MANUAL" -> "Antwortbedarf manuell aufgehoben";
            case "CASE_CLOSED" -> "Antwortbedarf durch Fallabschluss beendet";
            default -> answerLabel(message.answerState());
        };
        return message.answerResolvedAt() == null ? label : label + " · " + LOCAL_TIME.format(message.answerResolvedAt());
    }

    static String answerLabel(PublicMessage.AnswerState state) {
        return switch (state) {
            case NONE -> "";
            case OPEN -> "Offener Antwortbedarf";
            case ANSWERED -> "Rückfrage beantwortet";
            case CANCELLED -> "Antwortbedarf aufgehoben";
        };
    }

    private record HistoryEvent(String type, String id, String role, String text, Instant occurredAt, String answerLabel,
            HistoryUrgency urgency) {
    }

    public record HistoryEntry(String role, String text, String timestamp, String answerLabel, HistoryUrgency urgency) {
    }

    public record HistoryUrgency(String previousLabel, String previousCss, String currentLabel, String currentCss) {

        static HistoryUrgency from(Optional<Urgency> previous, Urgency current) {
            return new HistoryUrgency(
                    previous.map(PersonalCaseController::urgencyLabel).orElse(NOT_ASSESSED),
                    previous.map(PersonalCaseController::urgencyCss).orElse("urgency-0"),
                    urgencyLabel(current), urgencyCss(current));
        }
    }
}
