package com.pertegato.propertyflow.caseprocessing.contract;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Bestätigter interner Verlaufseintrag aus der Fallbearbeitung.
 */
public record InternalChange(String id, String description, UUID actorId, String actorName, Instant occurredAt,
        boolean automatic, UrgencyDetails urgency) {

    @Override
    public String toString() {
        return "Internal change " + id + " [content redacted]";
    }

    public record UrgencyDetails(Optional<Urgency> previous, Urgency current, String reason) {
    }
}
