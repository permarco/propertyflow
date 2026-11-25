package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.access.contract.StaffAccessDecision;
import com.pertegato.propertyflow.access.contract.StaffAccountAccess;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.caseprocessing.contract.CaseActionException;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseOperationsStore;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("propertyflow-backend")
@RequiredArgsConstructor
public class CaseOperationsService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.of("Europe/Zurich"));
    private final StaffAccountAccess access;
    private final CaseOperationsStore store;

    public Overview read(WebAccountContext context) {
        StaffAccessDecision decision = access.checkStaffAccess(context);
        if (decision != StaffAccessDecision.ALLOWED) {
            throw new CaseActionException(decision == StaffAccessDecision.UNAUTHENTICATED ? "UNAUTHENTICATED" : "FORBIDDEN");
        }
        List<CaseOperationsStore.Entry> entries = store.attention();
        List<Row> rows = entries.stream().limit(100).map(entry -> new Row(entry.id(), entry.caseId(), kind(entry.kind()),
                state(entry.state()), guidance(entry.state()), DATE.format(entry.since()))).toList();
        return new Overview(rows, entries.size() > 100);
    }

    private String kind(String value) {
        return switch (value) {
            case "PROCESS_START" -> "Fallprozess starten";
            case "CASE_MAIL" -> "Fallbenachrichtigung";
            case "ACCESS_MAIL" -> "Neuen Fall-Link zustellen";
            case "CASE_COMMAND" -> "Fallangaben bearbeiten";
            case "STAFF_MESSAGE" -> "Mitarbeiternachricht veröffentlichen";
            case "FOLLOW_UP" -> "Wiedervorlage bearbeiten";
            case "STAFF_DECISION" -> "Mitarbeiterentscheidung übernehmen";
            case "REOPEN" -> "Fall wiedereröffnen";
            default -> "Bearbeitungsauftrag";
        };
    }

    private String state(String value) {
        return switch (value) {
            case "BLOCKED" -> "Blockiert";
            case "FAILED" -> "Fehlgeschlagen";
            case "UNCERTAIN" -> "Zustellung ungewiss";
            case "REJECTED" -> "Abgewiesen";
            default -> "Bestätigung seit über fünf Minuten offen";
        };
    }

    private String guidance(String value) {
        return switch (value) {
            case "UNCERTAIN", "SENDING" -> "Zustellung zuerst im Mailsystem prüfen. Eine erneute Sendung könnte doppelt zugestellt werden.";
            case "REJECTED" -> "Aktuellen Fallstand prüfen. Dieser Auftrag wurde nicht ausgeführt; eine neue Aktion benötigt eine neue Freigabe.";
            case "FAILED" -> "Zustellproblem im Mailsystem prüfen. Der gespeicherte Fall bleibt erhalten.";
            case "BLOCKED" -> "Prozesskonfiguration und Camunda prüfen. Der Auftrag wird derzeit nicht automatisch fortgesetzt.";
            default -> "Fall und Prozessstand prüfen. Aus der Wartezeit allein lässt sich kein Fehlschlag ableiten.";
        };
    }

    public record Overview(List<Row> rows, boolean more) {
    }

    public record Row(UUID reference, String caseId, String kind, String state, String guidance, String since) {
    }
}
