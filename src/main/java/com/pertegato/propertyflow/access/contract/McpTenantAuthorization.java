package com.pertegato.propertyflow.access.contract;

import java.net.URI;
import java.util.Optional;

/**
 * Zugriff auf die vom OAuth-Dienst geprüfte Delegation für Mieter und Mitarbeitende.
 */
public interface McpTenantAuthorization {

    Optional<McpAccountContext> authenticate(String token);

    boolean allows(McpAccountContext context, McpScope scope);

    /**
     * Sperrt die Delegation bis zum Ende der Transaktion des bestätigten Vorgangs.
     */
    boolean lockDelegation(McpAccountContext context, McpScope scope);

    URI resourceMetadataUri();

    URI resourceUri();
}
