package com.pertegato.propertyflow.caseprocessing.contract;

import java.time.Instant;
import java.util.UUID;

/**
 * Confirmed staff audit entry, excluded from tenant and communication projections.
 */
public record InternalChange(String id, String description, UUID actorId, String actorName, Instant occurredAt) {
    @Override
    public String toString() {
        return "Internal change " + id + " [content redacted]";
    }
}
