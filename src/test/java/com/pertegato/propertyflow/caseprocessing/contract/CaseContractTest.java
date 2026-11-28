package com.pertegato.propertyflow.caseprocessing.contract;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CaseContractTest {

    private static final Instant RECEIVED_AT = Instant.parse("2026-11-14T10:00:00Z");

    @Test
    void acceptanceKeepsTokenAvailableForRedirectButRedactsItFromDiagnostics() {
        String token = "secret-case-link-used-only-in-test";
        CaseAccepted accepted = new CaseAccepted("REQ-TEST-1", token, false);

        assertEquals(token, accepted.personalAccessToken());
        assertFalse(accepted.toString().contains(token));
    }

    @Test
    void commandDiagnosticsDoNotExposeSubmittedPersonalData() {
        CreateCaseCommand command = new CreateCaseCommand(
                "operation-1", "Private request title", "Private apartment reference",
                "Private free-text description", "private-tenant@example.test");

        String diagnostic = command.toString();

        assertFalse(diagnostic.contains(command.title()));
        assertFalse(diagnostic.contains(command.unit()));
        assertFalse(diagnostic.contains(command.description()));
        assertFalse(diagnostic.contains(command.guestEmail()));
    }

    @Test
    void commandCanCarryAccountSubmissionWithoutGuestEmail() {
        CreateCaseCommand command = assertDoesNotThrow(() -> new CreateCaseCommand(
                "operation-1", "Heizung defekt", "Wohnung 3", "Alle Heizkörper sind kalt.", null));

        assertNull(command.guestEmail());
    }

    @Test
    void tenantViewIsIndependentOfMutableSourceListsAndCannotBeChangedThroughAccessors() {
        PublicMessage message = new PublicMessage("message-1", PublicAuthor.TENANT, "Heizung defekt", RECEIVED_AT);
        UrgencyChange change = new UrgencyChange(
                "urgency-1", Optional.empty(), Urgency.HIGH, UrgencySource.SYSTEM, RECEIVED_AT.plusSeconds(1));
        List<PublicMessage> messages = new ArrayList<>(List.of(message));
        List<UrgencyChange> changes = new ArrayList<>(List.of(change));
        TenantCaseView view = tenantView(messages, changes);

        messages.clear();
        changes.clear();

        assertEquals(List.of(message), view.messages());
        assertEquals(List.of(change), view.urgencyHistory());
        assertThrows(UnsupportedOperationException.class, () -> view.messages().add(message));
        assertThrows(UnsupportedOperationException.class, () -> view.urgencyHistory().add(change));
    }

    @Test
    void staffViewCopiesInternalListsInsteadOfSharingMutableServiceState() {
        InternalMemo memo = new InternalMemo("memo-1", "Hauswart anrufen", RECEIVED_AT);
        StaffCaseView.ReviewReason reason = new StaffCaseView.ReviewReason("Zuordnung offen", true);
        List<StaffCaseView.ReviewReason> reasons = new ArrayList<>(List.of(reason));
        List<InternalMemo> memos = new ArrayList<>(List.of(memo));
        StaffCaseView view = new StaffCaseView(
                tenantView(List.of(), List.of()), "tenant@example.test", Optional.empty(), reasons, memos);

        reasons.clear();
        memos.clear();

        assertEquals(List.of(reason), view.reviewReasons());
        assertEquals(List.of(memo), view.internalMemos());
        assertThrows(UnsupportedOperationException.class, () -> view.reviewReasons().add(reason));
        assertThrows(UnsupportedOperationException.class, () -> view.internalMemos().add(memo));
    }

    @Test
    void serviceErrorPreservesFieldCodesWithoutMutableStateOrDetailedExceptionMessage() {
        Map<String, String> errors = new HashMap<>();
        errors.put("description", "TOO_LONG");
        CaseServiceException exception = new CaseServiceException(CaseErrorCode.INVALID_INPUT, errors);

        errors.put("description", "REQUIRED");
        errors.put("email", "INVALID");

        assertEquals(Map.of("description", "TOO_LONG"), exception.fieldErrors());
        assertEquals(CaseErrorCode.INVALID_INPUT, exception.code());
        assertEquals("INVALID_INPUT", exception.getMessage());
        assertThrows(UnsupportedOperationException.class, () -> exception.fieldErrors().put("title", "REQUIRED"));
    }

    private TenantCaseView tenantView(List<PublicMessage> messages, List<UrgencyChange> changes) {
        return new TenantCaseView(
                new CaseStatusView("REQ-TEST-1", CaseStatus.STAFF_REVIEW_REQUIRED, Optional.of(Urgency.HIGH)),
                "Heizung defekt", "Alle Heizkörper sind kalt.", "Wohnung 3", Optional.empty(), "t***@example.test",
                RECEIVED_AT, RECEIVED_AT, 1, Optional.of(UrgencySource.SYSTEM), messages, changes);
    }
}
