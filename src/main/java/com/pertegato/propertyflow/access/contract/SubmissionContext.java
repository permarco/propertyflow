package com.pertegato.propertyflow.access.contract;

import java.util.Objects;
import java.util.UUID;

/**
 * Server-created submission identity; services must still verify current access.
 */
public sealed interface SubmissionContext {
    record Guest(UUID sessionScope) implements SubmissionContext {
        public Guest {
            Objects.requireNonNull(sessionScope, "sessionScope");
        }
    }

    record Tenant(UUID accountId) implements SubmissionContext {
        public Tenant {
            Objects.requireNonNull(accountId, "accountId");
        }
    }
}
