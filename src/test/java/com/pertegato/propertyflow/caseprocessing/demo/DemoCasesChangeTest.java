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
        assertEquals(before.review(), event.consideredReview());
        assertEquals("", after.review());
        assertFalse(event.at().isBefore(start));
        assertFalse(event.at().isAfter(Instant.now()));
        assertEquals(before.contact(), after.contact());

        var tenant = cases.tenant("demo-aktiver-fall");
        assertEquals("In Bearbeitung", tenant.status());
        var visibleEvent = tenant.entries().stream().filter(e -> e.id().equals(event.id())).findFirst().orElseThrow();
        assertEquals("", visibleEvent.reason());
        assertEquals("", visibleEvent.consideredReview());
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

    @Test
    void consideredReviewSurvivesLaterActionsAndDoesNotBlockNewQuestion() {
        var original = cases.get(ID);
        cases.change(ID, "status", "In Bearbeitung", "", original.version(), "consider");
        var decision = cases.get(ID).entries().getLast();
        cases.message(ID, null, "Welche Räume sind betroffen?", false, true, "question");
        assertEquals("Wartet auf Mieterantwort", cases.get(ID).status());
        cases.change(ID, "close", "", "", cases.get(ID).version(), "close");
        cases.change(ID, "reopen", "", "", cases.get(ID).version(), "reopen");
        assertEquals("In Bearbeitung", cases.get(ID).status());
        assertEquals(decision, cases.get(ID).entries().stream().filter(e -> e.id().equals(decision.id())).findFirst().orElseThrow());
        assertTrue(cases.tenant("demo-aktiver-fall").entries().stream().allMatch(e -> e.consideredReview().isEmpty()));
        cases.change(ID, "status", "In Bearbeitung", "", cases.get(ID).version(), "consider-again");
        assertEquals(1, cases.get(ID).entries().stream().filter(e -> !e.consideredReview().isEmpty()).count());
    }

    @Test
    void answerHistoryPreservesManualAndClosureEndingsAcrossReopening() {
        cases.message(ID, null, "Frage eins", false, true, "q1");
        String first = cases.get(ID).entries().getLast().id();
        cases.message(ID, null, "Frage zwei", false, true, "q2");
        String second = cases.get(ID).entries().getLast().id();
        cases.change(ID, "clearAnswer", first, "", cases.get(ID).version(), "end-first");
        var endedFirst = cases.get(ID).entries().stream().filter(e -> e.id().equals(first)).findFirst().orElseThrow();
        assertTrue(endedFirst.originallyAnswerRequired());
        assertFalse(endedFirst.answerRequired());
        assertEquals("Manuell", endedFirst.answerEndedBy());
        assertNotNull(endedFirst.answerEndedAt());
        assertTrue(cases.get(ID).entries().getLast().text().contains(first));
        cases.change(ID, "close", "", "", cases.get(ID).version(), "close-questions");
        var closed = cases.get(ID);
        cases.change(ID, "close", "", "", closed.version(), "close-questions");
        assertEquals(closed, cases.get(ID));
        cases.change(ID, "reopen", "", "", closed.version(), "reopen-questions");
        var firstAfter = cases.get(ID).entries().stream().filter(e -> e.id().equals(first)).findFirst().orElseThrow();
        var secondAfter = cases.get(ID).entries().stream().filter(e -> e.id().equals(second)).findFirst().orElseThrow();
        assertEquals(endedFirst, firstAfter);
        assertTrue(secondAfter.originallyAnswerRequired());
        assertFalse(secondAfter.answerRequired());
        assertEquals("Fallabschluss", secondAfter.answerEndedBy());
        assertNotNull(secondAfter.answerEndedAt());
        assertTrue(cases.get(ID).entries().stream().noneMatch(DemoCases.Entry::answerRequired));
        var tenantSecond = cases.tenant("demo-aktiver-fall").entries().stream().filter(e -> e.id().equals(second)).findFirst().orElseThrow();
        assertEquals(secondAfter.answerEndedAt(), tenantSecond.answerEndedAt());
        assertTrue(tenantSecond.originallyAnswerRequired());
    }

    @Test
    void followUpDecisionPreservesReviewAndQuestionsWithoutBlockingLaterStatus() {
        String review = cases.get(ID).review();
        cases.message(ID, null, "Ist es noch kalt?", false, true, "follow-question");
        var question = cases.get(ID).entries().getLast();
        cases.change(ID, "followUp", "days:3", "Nachkontrolle", cases.get(ID).version(), "schedule");
        var scheduled = cases.get(ID);
        assertEquals("Wartet auf Wiedervorlage", scheduled.status());
        assertEquals("", scheduled.review());
        assertEquals(review, scheduled.entries().getLast().consideredReview());
        assertEquals("Nachkontrolle", scheduled.entries().getLast().reason());
        assertTrue(scheduled.entries().contains(question));
        cases.change(ID, "followUp", "days:4", "", scheduled.version(), "reschedule");
        assertEquals("", cases.get(ID).entries().getLast().consideredReview());
        cases.change(ID, "clearFollowUp", "", "", cases.get(ID).version(), "resume");
        assertEquals("In Bearbeitung", cases.get(ID).status());
        assertTrue(cases.get(ID).entries().contains(question));
        cases.message(ID, null, "Bitte noch die Temperatur nennen.", false, true, "follow-question-two");
        assertEquals("Wartet auf Mieterantwort", cases.get(ID).status());
        assertTrue(cases.tenant("demo-aktiver-fall").entries().stream().allMatch(e -> e.consideredReview().isEmpty()));
    }

    @Test
    void invalidOrConflictingFollowUpDoesNotConsumeReview() {
        var before = cases.get(ID);
        assertThrows(ResponseStatusException.class, () ->
                cases.change(ID, "followUp", "days:-1", "", before.version(), "invalid-date"));
        assertEquals(before, cases.get(ID));
        assertThrows(ResponseStatusException.class, () ->
                cases.change(ID, "followUp", "days:3", "", before.version() - 1, "stale-date"));
        assertEquals(before, cases.get(ID));
        cases.change(ID, "followUp", "days:3", "", before.version(), "valid-date");
        var after = cases.get(ID);
        cases.change(ID, "followUp", "days:3", "", before.version(), "valid-date");
        assertEquals(after, cases.get(ID));
    }

    @Test
    void deletingFixtureFollowUpPreservesOldDateReasonAndTimestampInternally() {
        String id = "REQ-2026-007";
        var before = cases.get(id);
        assertNotNull(before.followUp());
        Instant start = Instant.now();
        cases.change(id, "clearFollowUp", "", "Früher weiterbearbeiten", before.version(), "delete-fixture");
        var after = cases.get(id);
        var event = after.entries().getLast();
        assertEquals("Wiedervorlage gelöscht: bisher " +
                before.followUp().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")) +
                " → keine Wiedervorlage", event.text());
        assertNull(after.followUp());
        assertEquals("In Bearbeitung", after.status());
        assertEquals("Früher weiterbearbeiten", event.reason());
        assertTrue(event.internal());
        assertTrue(event.event());
        assertFalse(event.at().isBefore(start));
        assertFalse(event.at().isAfter(Instant.now()));
        assertEquals(before.entries(), after.entries().subList(0, before.entries().size()));
        cases.change(id, "clearFollowUp", "", "Früher weiterbearbeiten", before.version(), "delete-fixture");
        assertEquals(after, cases.get(id));
        assertThrows(ResponseStatusException.class, () ->
                cases.change(id, "clearFollowUp", "", "", after.version(), "delete-missing"));
        assertEquals(after, cases.get(id));
    }

    @Test
    void deletingFollowUpWithoutReasonStaysHiddenFromTenant() {
        cases.change(ID, "followUp", "days:3", "", cases.get(ID).version(), "set-for-delete");
        cases.change(ID, "clearFollowUp", "", "", cases.get(ID).version(), "delete-without-reason");
        var event = cases.get(ID).entries().getLast();
        assertEquals("", event.reason());
        assertTrue(cases.tenant("demo-aktiver-fall").entries().stream().noneMatch(e -> e.id().equals(event.id())));
    }

    @Test
    void unchangedSystemUrgencyIsConfirmedInternallyAndManualRepeatIsNoOp() {
        var before = cases.get(ID);
        assertEquals("Hoch", before.urgency());
        assertEquals("System", before.source());
        var tenantHistory = cases.tenant("demo-aktiver-fall").entries();
        cases.change(ID, "urgency", "Hoch", "Bewertung geprüft", before.version(), "confirm-urgency");
        var confirmed = cases.get(ID);
        var event = confirmed.entries().getLast();
        assertEquals("Hoch", confirmed.urgency());
        assertEquals("Immobilienverwaltung", confirmed.source());
        assertEquals(before.version() + 1, confirmed.version());
        assertEquals(before.entries().size() + 1, confirmed.entries().size());
        assertTrue(event.internal());
        assertEquals("Dringlichkeit manuell bestätigt: Hoch · Quelle: System → Immobilienverwaltung", event.text());
        assertEquals("Bewertung geprüft", event.reason());
        assertEquals(-1, event.previousUrgency());
        assertEquals(tenantHistory, cases.tenant("demo-aktiver-fall").entries());
        cases.change(ID, "urgency", "Hoch", "Kein weiterer Änderungsnachweis", confirmed.version(), "same-manual");
        assertEquals(confirmed, cases.get(ID));
        cases.change(ID, "urgency", "Normal", "", confirmed.version(), "real-change");
        var changed = cases.get(ID);
        assertFalse(changed.entries().getLast().internal());
        assertEquals("Dringlichkeit: Hoch → Normal", changed.entries().getLast().text());
        cases.change(ID, "urgency", "Hoch", "Kein weiterer Änderungsnachweis", confirmed.version(), "same-manual");
        assertEquals(changed, cases.get(ID));
    }

    @Test
    void unchangedUrgencyStillChecksVersionAndClosedState() {
        var before = cases.get(ID);
        assertThrows(ResponseStatusException.class, () ->
                cases.change(ID, "urgency", "Hoch", "", before.version() - 1, "stale-confirmation"));
        assertEquals(before, cases.get(ID));
        cases.change(ID, "urgency", "Hoch", "", before.version(), "confirm-before-close");
        cases.change(ID, "close", "", "", cases.get(ID).version(), "close-confirmed");
        var closed = cases.get(ID);
        assertThrows(ResponseStatusException.class, () ->
                cases.change(ID, "urgency", "Hoch", "", closed.version(), "closed-confirmation"));
        assertEquals(closed, cases.get(ID));
    }
}
