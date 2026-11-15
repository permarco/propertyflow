package com.pertegato.propertyflow.caseprocessing.contract;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record UrgencyChange(String id, Optional<Urgency> previous, Urgency current, UrgencySource source, Instant occurredAt) {
    public UrgencyChange {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
