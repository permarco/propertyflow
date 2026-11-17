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
        List<InternalMemo> internalMemos,
        List<InternalChange> internalChanges,
        Optional<CaseRecommendation> recommendation) {

    public StaffCaseView(TenantCaseView publicView, String contactEmail, Optional<LocalDate> followUpDate,
            List<String> reviewReasons, List<InternalMemo> internalMemos, List<InternalChange> internalChanges) {
        this(publicView, contactEmail, followUpDate, reviewReasons, internalMemos, internalChanges, Optional.empty());
    }

    public StaffCaseView(TenantCaseView publicView, String contactEmail, Optional<LocalDate> followUpDate,
            List<String> reviewReasons, List<InternalMemo> internalMemos) {
        this(publicView, contactEmail, followUpDate, reviewReasons, internalMemos, List.of());
    }

    public StaffCaseView {
        Objects.requireNonNull(publicView, "publicView");
        Objects.requireNonNull(contactEmail, "contactEmail");
        Objects.requireNonNull(followUpDate, "followUpDate");
        reviewReasons = List.copyOf(reviewReasons);
        internalMemos = List.copyOf(internalMemos);
        internalChanges = List.copyOf(internalChanges);
        Objects.requireNonNull(recommendation, "recommendation");
    }
}
