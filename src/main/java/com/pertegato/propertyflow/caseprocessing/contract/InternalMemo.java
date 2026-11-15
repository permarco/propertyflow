package com.pertegato.propertyflow.caseprocessing.contract;

import java.time.Instant;
import java.util.Objects;

public record InternalMemo(String id, String text, Instant occurredAt) {
    public InternalMemo {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
