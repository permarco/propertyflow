package com.pertegato.propertyflow.access.oauth;

import com.pertegato.propertyflow.access.contract.McpScope;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Einstellungen des vorregistrierten MCP-OAuth-Clients.
 *
 * <p>Die Redirect-URI darf neben HTTPS und {@code 127.0.0.1} auch {@code localhost} verwenden, weil lokale
 * MCP-Clients wie Claude Code diese Form fest vorgeben. Spring Authorization Server vergleicht eine solche
 * URI exakt mit der konfigurierten; ein anderer Port oder Pfad wird abgewiesen.</p>
 *
 * <p>Die Rolle begrenzt die angebotenen und erlaubten Scopes. Clients wie Claude Code fordern alle in den
 * Metadaten genannten Scopes gleichzeitig an; eine Mischung aus Mieter- und Mitarbeiterscopes wird aber
 * abgewiesen. Mit {@code tenant} oder {@code staff} erhält ein solcher Client nur die Scopes einer Rolle.
 * Ohne Angabe bleiben alle Scopes angeboten, und der Client wählt sie selbst.</p>
 */
@ConfigurationProperties("propertyflow.mcp.oauth")
record McpOAuthSettings(URI issuer, String clientId, URI redirectUri, String clientName, String role) {

    static final String DEFAULT_CLIENT_NAME = "ChatGPT";
    static final String ALL_ROLES = "all";
    static final String TENANT_ROLE = "tenant";
    static final String STAFF_ROLE = "staff";

    McpOAuthSettings {
        validateUri(issuer, "issuer", false);
        validateUri(redirectUri, "redirect-uri", true);
        if (issuer.getRawQuery() != null || !issuer.getRawPath().isEmpty()) {
            throw new IllegalArgumentException("propertyflow.mcp.oauth.issuer muss eine Origin ohne Pfad sein.");
        }
        if (clientId == null || clientId.isBlank() || clientId.length() > 100) {
            throw new IllegalArgumentException("propertyflow.mcp.oauth.client-id muss gesetzt sein.");
        }
        if (clientName == null || clientName.isBlank()) {
            clientName = DEFAULT_CLIENT_NAME;
        } else if (clientName.strip().length() > 60) {
            throw new IllegalArgumentException("propertyflow.mcp.oauth.client-name darf höchstens 60 Zeichen haben.");
        } else {
            clientName = clientName.strip();
        }
        role = role == null || role.isBlank() ? ALL_ROLES : role.strip().toLowerCase(Locale.ROOT);
        if (!List.of(ALL_ROLES, TENANT_ROLE, STAFF_ROLE).contains(role)) {
            throw new IllegalArgumentException("propertyflow.mcp.oauth.role muss all, tenant oder staff sein.");
        }
    }

    /**
     * Liefert die Scopes, die dieser Client anfordern darf und die in den Metadaten angeboten werden.
     */
    List<String> scopes() {
        return Arrays.stream(McpScope.values())
                .filter(scope -> ALL_ROLES.equals(role) || scope.staff() == STAFF_ROLE.equals(role))
                .map(McpScope::value)
                .toList();
    }

    private static void validateUri(URI uri, String property, boolean localhostAllowed) {
        if (uri == null || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Ungültige URI für propertyflow.mcp.oauth." + property);
        }
        boolean localTest = "http".equals(uri.getScheme())
                && ("127.0.0.1".equals(uri.getHost()) || localhostAllowed && "localhost".equals(uri.getHost()));
        if (!"https".equals(uri.getScheme()) && !localTest) {
            throw new IllegalArgumentException("propertyflow.mcp.oauth." + property + " erfordert HTTPS oder die lokale Testadresse.");
        }
    }

    URI resourceUri() {
        return issuer.resolve("/mcp");
    }

    URI resourceMetadataUri() {
        return issuer.resolve("/.well-known/oauth-protected-resource/mcp");
    }
}
