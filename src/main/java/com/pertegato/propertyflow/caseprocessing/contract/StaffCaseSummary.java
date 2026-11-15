package com.pertegato.propertyflow.caseprocessing.contract;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

public record StaffCaseSummary(CaseStatusView state, String title, String unit, Instant receivedAt, Instant lastTenantContactAt,
        Optional<LocalDate> followUp) {
}
