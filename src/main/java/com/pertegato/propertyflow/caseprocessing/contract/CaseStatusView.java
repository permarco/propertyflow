package com.pertegato.propertyflow.caseprocessing.contract;

import java.util.Objects;
import java.util.Optional;

public record CaseStatusView(String caseId, CaseStatus status, Optional<Urgency> urgency) {
    public CaseStatusView {
        Objects.requireNonNull(caseId, "caseId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(urgency, "urgency");
    }
}
