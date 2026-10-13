package com.pertegato.propertyflow.caseprocessing.demo;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.HttpStatus.*;

/** Unit tests for manual status changes under FALL-06 and FD-AK-21. */
class DemoCasesChangeTest {
    private final DemoCases cases = new DemoCases();
    private static final String ID = "REQ-2026-001";

    @ParameterizedTest
    @ValueSource(strings = {"", "Intern: weiteres Vorgehen geprüft"})
    void manualStatusChangeStoresOneEventWithOptionalReason(String reason) {
        var before = cases.get(ID);
        assertEquals("Mitarbeiterprüfung erforderlich", before.status());
        assertNull(before.followUp());
        Instant start = Instant.now();

        cases.change(ID, "status", "In Bearbeitung", reason, before.version(), "manual-status");

        var after = cases.get(ID);
        var event = after.entries().getLast();
        assertEquals("In Bearbeitung", after.status());
        assertEquals(before.version() + 1, after.version());
        assertEquals(before.entries().size() + 1, after.entries().size());
        assertEquals(before.entries(), after.entries().subList(0, before.entries().size()));
        assertTrue(event.event());
        assertEquals("Status: Mitarbeiterprüfung erforderlich → In Bearbeitung", event.text());
        assertEquals("Immobilienverwaltung", event.role());
        assertEquals(reason, event.reason());
        assertFalse(event.at().isBefore(start));
        assertFalse(event.at().isAfter(Instant.now()));
        assertEquals(before.contact(), after.contact());

        var tenant = cases.tenant("demo-aktiver-fall");
        assertEquals("In Bearbeitung", tenant.status());
        var visibleEvent = tenant.entries().stream().filter(e -> e.id().equals(event.id())).findFirst().orElseThrow();
        assertEquals("", visibleEvent.reason());
        if (!reason.isEmpty()) {
            assertTrue(tenant.entries().stream().noneMatch(e -> e.text().contains(reason) || e.reason().contains(reason)));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"Unbekannt", "Abgeschlossen", "Wartet auf Wiedervorlage", "Wartet auf Mieterantwort"})
    void invalidStatusOrMissingPrerequisiteLeavesEntireCaseUnchanged(String target) {
        var before = cases.get(ID);
        assertNull(before.followUp());
        assertTrue(before.entries().stream().noneMatch(DemoCases.Entry::answerRequired));

        var error = assertThrows(ResponseStatusException.class, () ->
                cases.change(ID, "status", target, "Nicht speichern", before.version(), "invalid-status"));

        assertEquals(BAD_REQUEST, error.getStatusCode());
        assertEquals(before, cases.get(ID));
    }

    @Test
    void closedCaseCannotBeReopenedByNormalStatusChange() {
        var before = cases.get("REQ-2026-009");
        assertTrue(before.closed());

        var error = assertThrows(ResponseStatusException.class, () ->
                cases.change(before.id(), "status", "In Bearbeitung", "Nicht speichern", before.version(), "closed-status"));

        assertEquals(CONFLICT, error.getStatusCode());
        assertEquals(before, cases.get(before.id()));
    }

    @Test
    void staleStatusChangeDoesNotOverwriteNewerDecisionOrStoreReason() {
        var original = cases.get(ID);
        cases.change(ID, "status", "In Bearbeitung", "Geprüft", original.version(), "first-decision");
        var current = cases.get(ID);

        var error = assertThrows(ResponseStatusException.class, () ->
                cases.change(ID, "status", "Mitarbeiterprüfung erforderlich", "Veraltete Entscheidung",
                        original.version(), "stale-decision"));

        assertEquals(CONFLICT, error.getStatusCode());
        assertEquals(current, cases.get(ID));
    }

    @Test
    void replayOfSuccessfulStatusChangeHasNoAdditionalEffectEvenAfterLaterDecision() {
        var before = cases.get(ID);
        cases.change(ID, "status", "In Bearbeitung", "Geprüft", before.version(), "retry-status");
        var firstResult = cases.get(ID);

        assertDoesNotThrow(() -> cases.change(ID, "status", "In Bearbeitung", "Geprüft", before.version(), "retry-status"));
        assertEquals(firstResult, cases.get(ID));

        cases.change(ID, "status", "Mitarbeiterprüfung erforderlich", "Neue Erkenntnis", firstResult.version(), "later-decision");
        var laterResult = cases.get(ID);
        assertDoesNotThrow(() -> cases.change(ID, "status", "In Bearbeitung", "Geprüft", before.version(), "retry-status"));
        assertEquals(laterResult, cases.get(ID));
    }
}
