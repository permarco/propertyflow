package com.pertegato.propertyflow.caseprocessing.controller;

import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.access.web.BrowserResponses;
import com.pertegato.propertyflow.access.web.StaffWebSession;
import com.pertegato.propertyflow.caseprocessing.contract.CaseActionException;
import com.pertegato.propertyflow.caseprocessing.contract.CaseReopening;
import com.pertegato.propertyflow.caseprocessing.contract.FollowUpCommands;
import com.pertegato.propertyflow.caseprocessing.contract.StaffCaseDataCommands;
import com.pertegato.propertyflow.caseprocessing.contract.StaffCaseDataCommands.CaseDataResult;
import com.pertegato.propertyflow.caseprocessing.contract.StaffMessageSubmission;
import com.pertegato.propertyflow.caseprocessing.contract.StaffTaskDecisions;
import com.pertegato.propertyflow.caseprocessing.contract.Urgency;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.WebUtils;

@RestController
@Profile("propertyflow-backend & !frontend-demo")
@RequestMapping(StaffWebSession.STAFF_LIST + "/{caseId}")
public class StaffCaseDataController {

    private final StaffCaseDataCommands commands;
    private final StaffTaskDecisions decisions;
    private final StaffMessageSubmission messages;
    private final CaseReopening reopening;
    private final FollowUpCommands followUps;

    public StaffCaseDataController(StaffCaseDataCommands commands, StaffTaskDecisions decisions, StaffMessageSubmission messages,
            CaseReopening reopening, FollowUpCommands followUps) {
        this.commands = commands;
        this.decisions = decisions;
        this.messages = messages;
        this.reopening = reopening;
        this.followUps = followUps;
    }

    @ModelAttribute
    void protect(HttpServletResponse response) {
        BrowserResponses.protect(response);
    }

    @GetMapping({"/memos/verfuegbar", "/aktionen/verfuegbar"})
    ResponseEntity<?> available(@PathVariable String caseId, HttpServletRequest request) {
        return invoke(request, null, signedIn -> ResponseEntity.ok(Map.of(
                "available", commands.available(signedIn.context(), caseId), "actor", signedIn.context().accountId(), "csrf", signedIn.csrf())));
    }

    @PostMapping("/memos")
    ResponseEntity<?> submit(@PathVariable String caseId, @RequestParam(defaultValue = "") String operationId,
            @RequestParam(defaultValue = "") String version, @RequestParam(defaultValue = "") String text,
            @RequestParam(defaultValue = "") String csrf, HttpServletRequest request) {
        return invoke(request, csrf, signedIn -> {
            CaseDataResult result = commands.submit(signedIn.context(), caseId, UUID.fromString(operationId), Long.parseLong(version), text);
            return ResponseEntity.status("PENDING".equals(result.state()) ? 202 : 200).body(result);
        });
    }

    @GetMapping("/uebernehmen/verfuegbar")
    ResponseEntity<?> takeoverAvailable(@PathVariable String caseId, HttpServletRequest request) {
        return invoke(request, null, signedIn -> ResponseEntity.ok(Map.of(
                "available", commands.takeoverAvailable(signedIn.context(), caseId), "actor", signedIn.context().accountId(), "csrf", signedIn.csrf())));
    }

    @GetMapping("/abklaerung-abschliessen/verfuegbar")
    ResponseEntity<?> clarificationClosure(@PathVariable String caseId, HttpServletRequest request) {
        return invoke(request, null, signedIn -> ResponseEntity.ok(Map.of(
                "approval", commands.clarificationClosure(signedIn.context(), caseId),
                "actor", signedIn.context().accountId(), "csrf", signedIn.csrf())));
    }

    @PostMapping("/abklaerung-abschliessen")
    ResponseEntity<?> closeClarification(@PathVariable String caseId, @RequestParam(defaultValue = "") String operationId,
            @RequestParam(defaultValue = "") String version, @RequestParam(defaultValue = "") String reviewToken,
            @RequestParam(defaultValue = "") String csrf, HttpServletRequest request) {
        return invoke(request, csrf, signedIn -> {
            if (!Set.of("operationId", "version", "reviewToken", "csrf").containsAll(request.getParameterMap().keySet())) {
                return failure(400, "INVALID_INPUT");
            }
            CaseDataResult result = commands.closeClarification(signedIn.context(), caseId, operationId, Long.parseLong(version), reviewToken);
            return ResponseEntity.status("PENDING".equals(result.state()) ? 202 : 200).body(result);
        });
    }

    @GetMapping("/mieterwarten-abschliessen/verfuegbar")
    ResponseEntity<?> replyClosure(@PathVariable String caseId, HttpServletRequest request) {
        return invoke(request, null, signedIn -> ResponseEntity.ok(Map.of(
                "approval", commands.replyClosure(signedIn.context(), caseId),
                "actor", signedIn.context().accountId(), "csrf", signedIn.csrf())));
    }

    @PostMapping("/mieterwarten-abschliessen")
    ResponseEntity<?> closeReply(@PathVariable String caseId, @RequestParam(defaultValue = "") String operationId,
            @RequestParam(defaultValue = "") String version, @RequestParam(defaultValue = "") String reviewToken,
            @RequestParam(defaultValue = "") String csrf, HttpServletRequest request) {
        return invoke(request, csrf, signedIn -> {
            if (!Set.of("operationId", "version", "reviewToken", "csrf").containsAll(request.getParameterMap().keySet())) {
                return failure(400, "INVALID_INPUT");
            }
            CaseDataResult result = commands.closeReply(signedIn.context(), caseId, operationId, Long.parseLong(version), reviewToken);
            return ResponseEntity.status("PENDING".equals(result.state()) ? 202 : 200).body(result);
        });
    }

    @GetMapping("/direkte-wiedervorlage/verfuegbar")
    ResponseEntity<?> directFollowUp(@PathVariable String caseId, HttpServletRequest request) {
        return invoke(request, null, signedIn -> ResponseEntity.ok(Map.of("approval", commands.directFollowUp(signedIn.context(), caseId),
                "actor", signedIn.context().accountId(), "csrf", signedIn.csrf())));
    }

    @PostMapping("/direkte-wiedervorlage")
    ResponseEntity<?> setDirectFollowUp(@PathVariable String caseId, @RequestParam Map<String, String> fields, HttpServletRequest request) {
        return invoke(request, fields.getOrDefault("csrf", ""), signedIn -> {
            requireFields(fields, Set.of("operationId", "version", "reviewToken", "days", "date", "reason", "csrf"));
            String days = fields.getOrDefault("days", "");
            String date = fields.getOrDefault("date", "");
            CaseDataResult result = commands.setDirectFollowUp(signedIn.context(), caseId, fields.getOrDefault("operationId", ""),
                    Long.parseLong(fields.getOrDefault("version", "")), fields.getOrDefault("reviewToken", ""),
                    days.isBlank() ? null : Integer.valueOf(days), date.isBlank() ? null : LocalDate.parse(date), fields.getOrDefault("reason", ""));
            return ResponseEntity.status("PENDING".equals(result.state()) ? 202 : 200).body(result);
        });
    }

    @PostMapping("/uebernehmen")
    ResponseEntity<?> takeOver(@PathVariable String caseId, @RequestParam(defaultValue = "") String operationId,
            @RequestParam(defaultValue = "") String version, @RequestParam(defaultValue = "") String csrf, HttpServletRequest request) {
        return invoke(request, csrf, signedIn -> {
            if (!Set.of("operationId", "version", "csrf").containsAll(request.getParameterMap().keySet())) {
                return failure(400, "INVALID_INPUT");
            }
            CaseDataResult result = commands.takeOver(signedIn.context(), caseId, operationId, Long.parseLong(version));
            return ResponseEntity.status("PENDING".equals(result.state()) ? 202 : 200).body(result);
        });
    }

    @PostMapping("/antwortbedarf-aufheben")
    ResponseEntity<?> clearAnswer(@PathVariable String caseId, @RequestParam(defaultValue = "") String operationId,
            @RequestParam(defaultValue = "") String version, @RequestParam(defaultValue = "") String messageId,
            @RequestParam(defaultValue = "") String csrf, HttpServletRequest request) {
        return invoke(request, csrf, signedIn -> {
            if (!Set.of("operationId", "version", "messageId", "csrf").containsAll(request.getParameterMap().keySet())) {
                return failure(400, "INVALID_INPUT");
            }
            CaseDataResult result = commands.clearAnswer(signedIn.context(), caseId, operationId, Long.parseLong(version), UUID.fromString(messageId));
            return ResponseEntity.status("PENDING".equals(result.state()) ? 202 : 200).body(result);
        });
    }

    @GetMapping({"/memos/auftraege/{operationId}", "/aktionen/{operationId}"})
    ResponseEntity<?> result(@PathVariable String caseId, @PathVariable String operationId, HttpServletRequest request) {
        return invoke(request, null, signedIn -> ResponseEntity.ok(commands.read(signedIn.context(), caseId, operationId)));
    }

    @PostMapping("/dringlichkeit")
    ResponseEntity<?> urgency(@PathVariable String caseId, @RequestParam(defaultValue = "") String operationId,
            @RequestParam(defaultValue = "") String version, @RequestParam(defaultValue = "") String urgency,
            @RequestParam(defaultValue = "") String reason, @RequestParam(defaultValue = "") String csrf, HttpServletRequest request) {
        return invoke(request, csrf, signedIn -> {
            CaseDataResult result = commands.submitUrgency(signedIn.context(), caseId, UUID.fromString(operationId),
                    Long.parseLong(version), Urgency.valueOf(urgency), reason);
            return ResponseEntity.status("PENDING".equals(result.state()) ? 202 : 200).body(result);
        });
    }

    private ResponseEntity<?> invoke(HttpServletRequest request, String submittedCsrf, Function<SignedIn, ResponseEntity<?>> action) {
        return invoke(request, submittedCsrf, action, 503);
    }

    private ResponseEntity<?> invoke(HttpServletRequest request, String submittedCsrf, Function<SignedIn, ResponseEntity<?>> action,
            int unavailableStatus) {
        try {
            SignedIn signedIn = signedIn(request);
            if (submittedCsrf != null && !MessageDigest.isEqual(submittedCsrf.getBytes(StandardCharsets.UTF_8),
                    signedIn.csrf().getBytes(StandardCharsets.UTF_8))) {
                return failure(403, "INVALID_CSRF");
            }
            return action.apply(signedIn);
        } catch (CaseActionException exception) {
            int status = switch (exception.code()) {
                case "UNAUTHENTICATED" -> 401;
                case "FORBIDDEN" -> 403;
                case "CASE_NOT_AVAILABLE", "COMMAND_NOT_FOUND" -> 404;
                case "INVALID_INPUT", "INVALID_DECISION", "INVALID_REOPEN_REQUEST", "INVALID_FOLLOW_UP_DATE" -> 400;
                case "CASE_CHANGED", "CASE_NOT_ACTIVE", "IDEMPOTENCY_CONFLICT" -> 409;
                default -> unavailableStatus;
            };
            return failure(status, exception.code());
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            return failure(400, "INVALID_INPUT");
        } catch (RuntimeException exception) {
            return failure(unavailableStatus, "TEMPORARILY_UNAVAILABLE");
        }
    }

    @PostMapping("/zuordnung")
    ResponseEntity<?> assignment(@PathVariable String caseId, @RequestParam(defaultValue = "") String operationId,
            @RequestParam(defaultValue = "") String version, @RequestParam(defaultValue = "") String assignment,
            @RequestParam(required = false) String propertyId, @RequestParam(required = false) String unitId,
            @RequestParam(defaultValue = "") String csrf, HttpServletRequest request) {
        return invoke(request, csrf, signedIn -> {
            // This endpoint accepts free text and saved text suggestions, never unverified master-data references.
            if (propertyId != null || unitId != null) {
                throw new CaseActionException("INVALID_INPUT");
            }
            CaseDataResult result = commands.submitAssignment(signedIn.context(), caseId, UUID.fromString(operationId),
                    Long.parseLong(version), assignment);
            return ResponseEntity.status("PENDING".equals(result.state()) ? 202 : 200).body(result);
        });
    }

    @GetMapping({"/zuordnung/vorschlaege", "/objekt-wohnung-zuordnen/vorschlaege"})
    ResponseEntity<?> assignmentSuggestions(@RequestParam(defaultValue = "") String q, HttpServletRequest request) {
        return invoke(request, null, signedIn -> ResponseEntity.ok(commands.assignmentSuggestions(signedIn.context(), q)));
    }

    @GetMapping("/pruefung")
    ResponseEntity<?> review(@PathVariable String caseId, HttpServletRequest request) {
        return invoke(request, null, signedIn -> ResponseEntity.ok(Map.of("task", decisions.current(signedIn.context(), caseId),
                "csrf", signedIn.csrf(), "actor", signedIn.context().accountId())), 500);
    }

    @PostMapping("/fortsetzen")
    ResponseEntity<?> continueWork(@PathVariable String caseId, @RequestParam(defaultValue = "") String operationId,
            @RequestParam(defaultValue = "") String userTaskKey, @RequestParam(defaultValue = "") String version,
            @RequestParam(defaultValue = "") String reviewToken, @RequestParam(defaultValue = "") String reason,
            @RequestParam(defaultValue = "") String csrf, HttpServletRequest request) {
        return invoke(request, csrf, signedIn -> {
            decisions.continueWork(signedIn.context(), caseId, UUID.fromString(operationId), Long.parseLong(userTaskKey),
                    Long.parseLong(version), reviewToken, reason);
            return ResponseEntity.ok(Map.of("state", "CONFIRMED"));
        }, 500);
    }

    @PostMapping("/abschliessen")
    ResponseEntity<?> close(@PathVariable String caseId, @RequestParam(defaultValue = "") String operationId,
            @RequestParam(defaultValue = "") String userTaskKey, @RequestParam(defaultValue = "") String version,
            @RequestParam(defaultValue = "") String reviewToken, @RequestParam(defaultValue = "") String csrf, HttpServletRequest request) {
        return invoke(request, csrf, signedIn -> {
            decisions.closeCase(signedIn.context(), caseId, UUID.fromString(operationId), Long.parseLong(userTaskKey),
                    Long.parseLong(version), reviewToken);
            return ResponseEntity.ok(Map.of("state", "CONFIRMED"));
        }, 500);
    }

    @PostMapping("/wiedervorlage-setzen")
    ResponseEntity<?> followUp(@PathVariable String caseId, @RequestParam Map<String, String> fields, HttpServletRequest request) {
        return invoke(request, fields.getOrDefault("csrf", ""), signedIn -> {
            String days = fields.getOrDefault("days", "");
            String date = fields.getOrDefault("date", "");
            decisions.followUp(signedIn.context(), caseId, UUID.fromString(fields.getOrDefault("operationId", "")),
                    Long.parseLong(fields.getOrDefault("userTaskKey", "")), Long.parseLong(fields.getOrDefault("version", "")),
                    fields.getOrDefault("reviewToken", ""), fields.getOrDefault("reason", ""),
                    days.isBlank() ? null : Integer.valueOf(days), date.isBlank() ? null : LocalDate.parse(date));
            return ResponseEntity.ok().build();
        }, 500);
    }

    @GetMapping("/wiedervorlage/verfuegbar")
    ResponseEntity<?> currentFollowUp(@PathVariable String caseId, HttpServletRequest request) {
        return invoke(request, null, signedIn -> ResponseEntity.ok(Map.of("current", followUps.current(signedIn.context(), caseId),
                "actor", signedIn.context().accountId(), "csrf", signedIn.csrf())), 500);
    }

    @GetMapping("/wiedervorlage/auftraege/{operation}")
    ResponseEntity<?> followUpResult(@PathVariable String caseId, @PathVariable UUID operation, HttpServletRequest request) {
        return invoke(request, null, signedIn -> ResponseEntity.ok(Map.of("state", followUps.result(signedIn.context(), caseId, operation))), 500);
    }

    @PostMapping({"/wiedervorlage-aendern", "/wiedervorlage-loeschen"})
    ResponseEntity<?> changeFollowUp(@PathVariable String caseId, @RequestParam Map<String, String> fields, HttpServletRequest request) {
        return invoke(request, fields.getOrDefault("csrf", ""), signedIn -> {
            requireFields(fields, Set.of("operationId", "waitId", "version", "days", "date", "reason", "csrf"));
            String days = fields.getOrDefault("days", "");
            String date = fields.getOrDefault("date", "");
            String result = followUps.submit(signedIn.context(), caseId, UUID.fromString(fields.getOrDefault("operationId", "")),
                    UUID.fromString(fields.getOrDefault("waitId", "")), Long.parseLong(fields.getOrDefault("version", "")),
                    request.getRequestURI().endsWith("/wiedervorlage-loeschen"), days.isBlank() ? null : Integer.valueOf(days),
                    date.isBlank() ? null : LocalDate.parse(date), fields.getOrDefault("reason", ""));
            return ResponseEntity.status("APPLIED".equals(result) ? 200 : 202).body(Map.of("state", result));
        }, 500);
    }

    @PostMapping("/wiedervorlage-abschliessen")
    ResponseEntity<?> closeFollowUp(@PathVariable String caseId, @RequestParam Map<String, String> fields, HttpServletRequest request) {
        return invoke(request, fields.getOrDefault("csrf", ""), signedIn -> {
            requireFields(fields, Set.of("operationId", "waitId", "version", "reason", "csrf"));
            String result = followUps.close(signedIn.context(), caseId, UUID.fromString(fields.getOrDefault("operationId", "")),
                    UUID.fromString(fields.getOrDefault("waitId", "")), Long.parseLong(fields.getOrDefault("version", "")),
                    fields.getOrDefault("reason", ""));
            return ResponseEntity.status("APPLIED".equals(result) ? 200 : 202).body(Map.of("state", result));
        }, 500);
    }

    @PostMapping("/wiedereroeffnen")
    ResponseEntity<?> reopen(@PathVariable String caseId, @RequestParam Map<String, String> fields, HttpServletRequest request) {
        return invoke(request, fields.getOrDefault("csrf", ""), signedIn -> {
            UUID order = reopening.request(signedIn.context(), caseId, UUID.fromString(fields.getOrDefault("operationId", "")),
                    Long.parseLong(fields.getOrDefault("version", "")));
            return ResponseEntity.accepted().body(Map.of("order", order.toString(), "state", "ACCEPTED"));
        }, 500);
    }

    @PostMapping("/externe-rueckmeldung-abwarten")
    ResponseEntity<?> waitExternal(@PathVariable String caseId, @RequestParam Map<String, String> fields, HttpServletRequest request) {
        return invoke(request, fields.getOrDefault("csrf", ""), signedIn -> {
            decisions.waitExternal(signedIn.context(), caseId, UUID.fromString(fields.getOrDefault("operationId", "")),
                    Long.parseLong(fields.getOrDefault("userTaskKey", "")), Long.parseLong(fields.getOrDefault("version", "")),
                    fields.getOrDefault("reviewToken", ""));
            return ResponseEntity.ok().build();
        }, 500);
    }

    @PostMapping("/internes-memo-speichern")
    ResponseEntity<?> saveMemo(@PathVariable String caseId, @RequestParam Map<String, String> fields, HttpServletRequest request) {
        return canonical(request, fields.getOrDefault("csrf", ""), true, signedIn -> {
            requireFields(fields, Set.of("text", "operation", "csrf"));
            CaseDataResult result = commands.submitMemo(signedIn.context(), caseId, fields.get("operation"), fields.get("text"));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while ("PENDING".equals(result.state()) && System.nanoTime() < deadline) {
                try {
                    Thread.sleep(25);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new CaseActionException("WORKFLOW_UNAVAILABLE");
                }
                result = commands.read(signedIn.context(), caseId, fields.get("operation"));
            }
            requireApplied(result);
            return ResponseEntity.ok().build();
        });
    }

    @PostMapping("/dringlichkeit-aendern")
    ResponseEntity<?> changeUrgency(@PathVariable String caseId, @RequestParam Map<String, String> fields, HttpServletRequest request) {
        return canonical(request, fields.getOrDefault("csrf", ""), false, signedIn -> {
            requireFields(fields, Set.of("urgency", "version", "operation", "csrf", "reason"));
            Urgency urgency = switch (fields.getOrDefault("urgency", "")) {
                case "Kritisch" -> Urgency.CRITICAL;
                case "Hoch" -> Urgency.HIGH;
                case "Normal" -> Urgency.NORMAL;
                case "Niedrig" -> Urgency.LOW;
                default -> throw new CaseActionException("INVALID_INPUT");
            };
            CaseDataResult result = commands.submitUrgency(signedIn.context(), caseId, fields.get("operation"),
                    Long.parseLong(fields.getOrDefault("version", "")), urgency, fields.getOrDefault("reason", ""));
            requireAccepted(result);
            return ResponseEntity.ok().build();
        });
    }

    @PostMapping("/objekt-wohnung-zuordnen")
    ResponseEntity<?> assignUnit(@PathVariable String caseId, @RequestParam Map<String, String> fields, HttpServletRequest request) {
        return canonical(request, fields.getOrDefault("csrf", ""), false, signedIn -> {
            requireFields(fields, Set.of("assignment", "version", "operation", "csrf"));
            CaseDataResult result = commands.submitAssignment(signedIn.context(), caseId, fields.get("operation"),
                    Long.parseLong(fields.getOrDefault("version", "")), fields.get("assignment"));
            requireAccepted(result);
            return ResponseEntity.ok().build();
        });
    }

    private ResponseEntity<?> canonical(HttpServletRequest request, String csrf, boolean communication,
            Function<SignedIn, ResponseEntity<?>> action) {
        ResponseEntity<?> response = invoke(request, csrf, action, 500);
        if (!communication && response.getStatusCode().value() == 409) {
            return ResponseEntity.badRequest().body(response.getBody());
        }
        return response;
    }

    private void requireFields(Map<String, String> fields, Set<String> allowed) {
        if (!allowed.containsAll(fields.keySet())) {
            throw new CaseActionException("INVALID_INPUT");
        }
    }

    private void requireAccepted(CaseDataResult result) {
        if ("REJECTED".equals(result.state())) {
            throw new CaseActionException(result.code());
        }
    }

    private void requireApplied(CaseDataResult result) {
        requireAccepted(result);
        if (!"APPLIED".equals(result.state())) {
            throw new CaseActionException("WORKFLOW_UNAVAILABLE");
        }
    }

    @GetMapping("/nachrichten/verfuegbar")
    ResponseEntity<?> messageCapability(@PathVariable String caseId, HttpServletRequest request) {
        return invoke(request, null, signedIn -> ResponseEntity.ok(Map.of("capability", messages.available(signedIn.context(), caseId),
                "actor", signedIn.context().accountId(), "csrf", signedIn.csrf())), 500);
    }

    @GetMapping("/nachrichten/auftraege/{operation}")
    ResponseEntity<?> messageResult(@PathVariable String caseId, @PathVariable String operation, HttpServletRequest request) {
        return invoke(request, null, signedIn -> ResponseEntity.ok(Map.of("state", messages.result(signedIn.context(), caseId, operation))), 500);
    }

    @PostMapping("/externe-nachricht-veroeffentlichen")
    ResponseEntity<?> publishMessage(@PathVariable String caseId, @RequestParam Map<String, String> fields, HttpServletRequest request) {
        return canonical(request, fields.getOrDefault("csrf", ""), true, signedIn -> {
            requireFields(fields, Set.of("text", "answer", "operation", "csrf"));
            String answer = fields.getOrDefault("answer", "false");
            if (!"true".equals(answer) && !"false".equals(answer)) {
                throw new CaseActionException("INVALID_INPUT");
            }
            String result = messages.send(signedIn.context(), caseId, fields.get("operation"), fields.get("text"), Boolean.parseBoolean(answer));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while ("PENDING".equals(result) && System.nanoTime() < deadline) {
                try {
                    Thread.sleep(25);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new CaseActionException("WORKFLOW_UNAVAILABLE");
                }
                result = messages.result(signedIn.context(), caseId, fields.get("operation"));
            }
            if (!"PUBLISHED".equals(result)) {
                throw new CaseActionException("WORKFLOW_UNAVAILABLE");
            }
            return ResponseEntity.ok().build();
        });
    }

    private SignedIn signedIn(HttpServletRequest request) {
        HttpSession session = StaffWebSession.cookieSession(request);
        if (session == null) {
            throw new CaseActionException("UNAUTHENTICATED");
        }
        synchronized (WebUtils.getSessionMutex(session)) {
            try {
                if (session.getAttribute(StaffWebSession.ACCOUNT) instanceof UUID account
                        && session.getAttribute(StaffWebSession.CSRF) instanceof String csrf && !csrf.isBlank()) {
                    return new SignedIn(new WebAccountContext(account), csrf);
                }
            } catch (IllegalStateException expired) {
                throw new CaseActionException("UNAUTHENTICATED");
            }
        }
        throw new CaseActionException("UNAUTHENTICATED");
    }

    private ResponseEntity<?> failure(int status, String code) {
        return ResponseEntity.status(status).body(Map.of("code", code));
    }

    private record SignedIn(WebAccountContext context, String csrf) {
    }
}
