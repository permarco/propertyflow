package com.pertegato.propertyflow.caseprocessing.demo;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.HttpStatus.CONFLICT;

class DemoCasesConflictTest {
    private final DemoCases cases = new DemoCases();

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void changedSubmissionFieldConflictsAndPreservesOriginalResult(int field) {
        String[] input = {"Titel", "Wohnung", "a@b.ch", "Beschreibung"};
        String token = cases.create("scope", input[0], input[1], input[2], input[3], "operation");
        DemoCases.View original = cases.tenant(token);
        long count = cases.list("", "", true, "default", false, 1).total();
        input[field] = field == 2 ? "other@example.ch" : "Geändert";
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> cases.create("scope", input[0], input[1], input[2], input[3], "operation"));
        assertEquals(CONFLICT, error.getStatusCode());
        assertEquals(original, cases.tenant(token));
        assertEquals(count, cases.list("", "", true, "default", false, 1).total());
        assertEquals(token, cases.create("scope", "Titel", "Wohnung", "a@b.ch", "Beschreibung", "operation"));
    }

    @Test
    void identicalSubmissionWithWhitespaceStillReplaysAfterLaterCaseChanges() {
        String token = cases.create("scope", " Titel ", " Wohnung ", " a@b.ch ", " Beschreibung ", "operation");
        DemoCases.View original = cases.tenant(token);
        cases.change(original.id(), "unit", "Bestätigte Wohnung", "", original.version(), "assignment");
        cases.message(null, token, "Ergänzung", false, false, "message");
        DemoCases.View later = cases.get(original.id());
        assertEquals(token, cases.create("scope", " Titel ", " Wohnung ", " a@b.ch ", " Beschreibung ", "operation"));
        assertEquals(later, cases.get(original.id()));
    }

    static Stream<Arguments> changedFields() {
        return Stream.of("status", "unit", "urgency", "followUp", "clearFollowUp", "clearAnswer", "close", "reopen")
                .flatMap(action -> Stream.of(Arguments.of(action, false), Arguments.of(action, true)));
    }

    @ParameterizedTest
    @MethodSource("changedFields")
    void changedValueOrReasonConflictsForEveryAction(String action, boolean changeReason) {
        String id = "REQ-2026-001";
        String value = switch (action) {
            case "status" -> "In Bearbeitung";
            case "unit" -> "Wohnung A";
            case "urgency" -> "Normal";
            case "followUp" -> "days:3";
            default -> "";
        };
        if (action.equals("clearFollowUp")) {
            cases.change(id, "followUp", "days:3", "", cases.get(id).version(), "prepare");
        } else if (action.equals("reopen")) {
            cases.change(id, "close", "", "", cases.get(id).version(), "prepare");
        } else if (action.equals("clearAnswer")) {
            cases.message(id, null, "Rückfrage", false, true, "prepare");
            value = cases.get(id).entries().getLast().id();
        }
        long version = cases.get(id).version();
        cases.change(id, action, value, "Grund", version, "operation");
        DemoCases.View saved = cases.get(id);
        String changedValue = changeReason ? value : "Anderer Wert";
        String changedReason = changeReason ? "Anderer Grund" : "Grund";
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> cases.change(id, action, changedValue, changedReason, version, "operation"));
        assertEquals(CONFLICT, error.getStatusCode());
        assertEquals(saved, cases.get(id));
        cases.change(id, action, value, "Grund", saved.version(), "operation");
        assertEquals(saved, cases.get(id));
    }

    @Test
    void noOpUrgencyConfirmationAlsoRejectsChangedPayloadOnReplay() {
        String id = "REQ-2026-001";
        cases.change(id, "urgency", "Normal", "", cases.get(id).version(), "initial");
        long version = cases.get(id).version();
        cases.change(id, "urgency", "Normal", "Bestätigt", version, "operation");
        cases.change(id, "urgency", "Hoch", "Neue Erkenntnis", version, "later");
        DemoCases.View later = cases.get(id);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> cases.change(id, "urgency", "Niedrig", "Bestätigt", version, "operation"));
        assertEquals(CONFLICT, error.getStatusCode());
        assertEquals(later, cases.get(id));
        cases.change(id, "urgency", "Normal", "Bestätigt", version, "operation");
        assertEquals(later, cases.get(id));
    }
}
