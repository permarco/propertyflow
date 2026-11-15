package com.pertegato.propertyflow.access.contract;

import java.util.Objects;
import java.util.UUID;

/**
 * References from verified MCP credentials; current account and delegation remain subject to service checks.
 */
public record McpAccountContext(UUID accountId, UUID delegationId, String clientId) {
    public McpAccountContext {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(delegationId, "delegationId");
        Objects.requireNonNull(clientId, "clientId");
    }
}
