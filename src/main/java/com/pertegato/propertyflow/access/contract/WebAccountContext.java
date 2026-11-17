package com.pertegato.propertyflow.access.contract;

import java.util.Objects;
import java.util.UUID;

/**
 * Account reference from an authenticated web session, never from request fields.
 */
public record WebAccountContext(UUID accountId) {
    public WebAccountContext {
        Objects.requireNonNull(accountId, "accountId");
    }
}
