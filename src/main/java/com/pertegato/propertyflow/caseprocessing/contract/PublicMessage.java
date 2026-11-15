package com.pertegato.propertyflow.caseprocessing.contract;

import java.time.Instant;
import java.util.Objects;

public record PublicMessage(String id, PublicAuthor author, String text, Instant occurredAt) {
    public PublicMessage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(author, "author");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
