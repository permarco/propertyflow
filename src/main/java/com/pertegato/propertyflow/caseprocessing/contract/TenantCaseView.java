package com.pertegato.propertyflow.caseprocessing.contract;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable public projection; it must never contain internal memos, reasons or raw contact addresses.
 */
public record TenantCaseView(
        CaseStatusView state,
        String title,
        String description,
        String originalUnit,
        Optional<String> assignedUnit,
        String maskedContact,
        Instant receivedAt,
        Instant lastTenantContactAt,
        long version,
        Optional<UrgencySource> urgencySource,
        List<PublicMessage> messages,
        List<UrgencyChange> urgencyHistory) {

    public TenantCaseView {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(originalUnit, "originalUnit");
        Objects.requireNonNull(assignedUnit, "assignedUnit");
        Objects.requireNonNull(maskedContact, "maskedContact");
        Objects.requireNonNull(receivedAt, "receivedAt");
        Objects.requireNonNull(lastTenantContactAt, "lastTenantContactAt");
        Objects.requireNonNull(urgencySource, "urgencySource");
        messages = List.copyOf(messages);
        urgencyHistory = List.copyOf(urgencyHistory);
    }
}
