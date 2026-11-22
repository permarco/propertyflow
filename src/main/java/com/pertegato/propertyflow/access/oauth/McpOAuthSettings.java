package com.pertegato.propertyflow.access.oauth;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("propertyflow.mcp.oauth")
record McpOAuthSettings(URI issuer, String clientId, URI redirectUri) {

    McpOAuthSettings {
        validateUri(issuer, "issuer");
        validateUri(redirectUri, "redirect-uri");
        if (issuer.getRawQuery() != null || !issuer.getRawPath().isEmpty()) {
            throw new IllegalArgumentException("propertyflow.mcp.oauth.issuer muss eine Origin ohne Pfad sein.");
        }
        if (clientId == null || clientId.isBlank() || clientId.length() > 100) {
            throw new IllegalArgumentException("propertyflow.mcp.oauth.client-id muss gesetzt sein.");
        }
    }

    private static void validateUri(URI uri, String property) {
        if (uri == null || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Ungültige URI für propertyflow.mcp.oauth." + property);
        }
        if (!"https".equals(uri.getScheme()) && !("http".equals(uri.getScheme()) && "127.0.0.1".equals(uri.getHost()))) {
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
