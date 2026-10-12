package com.pertegato.propertyflow.caseprocessing.demo;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class DemoCasesTest {
    @Test void searchFindsWordsAcrossFieldsAndMemosWithPunctuationIgnored() {
        DemoCases cases = new DemoCases();
        var page = cases.list("REQ2026001 HEIZ, Hauswart!", "", false, "default", false, 1);
        assertEquals(1, page.total());
        assertEquals("REQ-2026-001", page.items().getFirst().id());
        assertEquals(0, cases.list("Heiz nichtvorhanden", "", false, "default", false, 1).total());
        assertEquals(50, cases.list("", "", true, "default", false, 1).items().size());
        assertEquals(6, cases.list("", "", true, "default", false, 2).items().size());
        assertEquals("Noch nicht bewertet", cases.list("", "", true, "default", false, 1).items().getFirst().urgency());
    }
    @Test void tenantNeverReceivesInternalMemosOrReasons() {
        DemoCases cases = new DemoCases();
        var c = cases.get("REQ-2026-001");
        cases.change(c.id(), "urgency", "Normal", "Geheime Kostenfreigabe", c.version(), "urgency");
        var tenant = cases.tenant("demo-aktiver-fall");
        assertTrue(tenant.entries().stream().noneMatch(e -> e.internal() || !e.reason().isEmpty() || e.text().contains("Hauswart")));
        assertEquals("Normal", tenant.urgency());
        assertThrows(ResponseStatusException.class, () -> cases.tenant("REQ-2026-001"));
    }
    @Test void writesAreIdempotentAndClosedCasesRejectMessagesAndChanges() {
        DemoCases cases = new DemoCases();
        var c = cases.get("REQ-2026-001");
        cases.message(c.id(), null, "Test", true, false, "message");
        cases.message(c.id(), null, "Test", true, false, "message");
        assertEquals(c.entries().size() + 1, cases.get(c.id()).entries().size());
        assertThrows(ResponseStatusException.class, () -> cases.change(c.id(), "urgency", "Normal", "", c.version(), "stale"));
        cases.change(c.id(), "close", "", "", cases.get(c.id()).version(), "close");
        assertThrows(ResponseStatusException.class, () -> cases.message(c.id(), null, "Test", true, false, "new-message"));
        assertThrows(ResponseStatusException.class, () -> cases.message(null, "demo-aktiver-fall", "Test", false, false, "tenant"));
        cases.change(c.id(), "reopen", "", "", cases.get(c.id()).version(), "reopen");
        cases.message(null, "demo-aktiver-fall", "Wieder offen", false, false, "tenant");
    }
    @Test void creationValidatesAndDeduplicatesWithoutChangingOriginalData() {
        DemoCases cases = new DemoCases();
        assertThrows(ResponseStatusException.class, () -> cases.create(" ", "Wohnung", "a@b.ch", "Test", "bad"));
        String token = cases.create("<script>alert(1)</script>", "Wohnung", "a@b.ch", "Test", "create");
        assertEquals(token, cases.create("Anders", "Anders", "a@b.ch", "Anders", "create"));
        assertEquals("<script>alert(1)</script>", cases.tenant(token).title());
        cases.message(null, token, "Zusatz", false, false, "extra");
        assertEquals("Test", cases.tenant(token).description());
    }

    @Test void actionsDoNotConsumeMessageOperationAndFollowupSortKeepsMissingDatesLast() {
        DemoCases cases = new DemoCases();
        String id = "REQ-2026-001";
        cases.change(id, "status", "In Bearbeitung", "", cases.get(id).version(), "shared-form-id");
        int count = cases.get(id).entries().size();
        cases.message(id, null, "Nachricht nach Statusänderung", false, false, "shared-form-id");
        assertEquals(count + 1, cases.get(id).entries().size());
        cases.change(id, "followUp", "days:3", "Interne Planung", cases.get(id).version(), "followup");
        assertEquals("Wartet auf Wiedervorlage", cases.get(id).status());
        assertNotNull(cases.list("", "", true, "followUp", true, 1).items().getFirst().followUp());
        assertNull(cases.list("", "", true, "followUp", true, 2).items().getLast().followUp());
        cases.change(id, "clearFollowUp", "", "", cases.get(id).version(), "clear");
        assertNull(cases.get(id).followUp());
        assertEquals("In Bearbeitung", cases.get(id).status());
    }

    @Test void assignmentAcceptsFreeTextAndRejectsInvalidInput() {
        DemoCases cases = new DemoCases();
        String id = "REQ-2026-001";
        String original = cases.get(id).originalUnit();
        cases.change(id, "unit", "  Neue Liegenschaft, Wohnung 12  ", "", cases.get(id).version(), "free-unit");
        assertEquals("Neue Liegenschaft, Wohnung 12", cases.get(id).unit());
        assertEquals(original, cases.get(id).originalUnit());
        long version = cases.get(id).version();
        assertThrows(ResponseStatusException.class, () -> cases.change(id, "unit", "   ", "", version, "empty-unit"));
        assertThrows(ResponseStatusException.class, () -> cases.change(id, "unit", "x".repeat(201), "", version, "long-unit"));
        assertEquals(version, cases.get(id).version());
    }
}
