package com.pertegato.propertyflow.caseprocessing.contract;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Staff-only projection; this complete value must not be exposed through tenant or MCP reads.
 */
public record StaffCaseView(
        TenantCaseView publicView,
        String contactEmail,
        Optional<LocalDate> followUpDate,
        List<String> reviewReasons,
        List<InternalMemo> internalMemos) {

    public StaffCaseView {
        Objects.requireNonNull(publicView, "publicView");
        Objects.requireNonNull(contactEmail, "contactEmail");
        Objects.requireNonNull(followUpDate, "followUpDate");
        reviewReasons = List.copyOf(reviewReasons);
        internalMemos = List.copyOf(internalMemos);
    }
}
