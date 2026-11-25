package com.pertegato.propertyflow.access.oauth;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class McpOAuthSettingsTest {

    private static final URI ISSUER = URI.create("http://127.0.0.1:8081");

    @ParameterizedTest
    @ValueSource(strings = {"https://client.example.test/callback", "http://127.0.0.1:8787/callback", "http://localhost:8787/callback"})
    void acceptsHttpsAndLocalRedirects(String redirect) {
        McpOAuthSettings settings = new McpOAuthSettings(ISSUER, "client", URI.create(redirect), null, null);
        assertEquals(URI.create(redirect), settings.redirectUri());
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://client.example.test/callback", "http://localhost.example.test/callback", "http://localhost:8787/callback#x"})
    void rejectsOtherPlainHttpRedirects(String redirect) {
        assertThrows(IllegalArgumentException.class, () -> new McpOAuthSettings(ISSUER, "client", URI.create(redirect), null, null));
    }

    @Test
    void issuerMustNotUseLocalhost() {
        assertThrows(IllegalArgumentException.class, () -> new McpOAuthSettings(URI.create("http://localhost:8081"), "client",
                URI.create("http://localhost:8787/callback"), null, null));
    }

    @Test
    void roleLimitsOfferedScopes() {
        URI redirect = URI.create("http://localhost:8787/callback");
        assertEquals(6, new McpOAuthSettings(ISSUER, "client", redirect, null, null).scopes().size());
        assertEquals(List.of("tenant:cases:read", "tenant:messages:write", "tenant:cases:create"),
                new McpOAuthSettings(ISSUER, "client", redirect, null, " Tenant ").scopes());
        assertEquals(List.of("staff:cases:read", "staff:messages:write", "staff:cases:transition"),
                new McpOAuthSettings(ISSUER, "client", redirect, null, "staff").scopes());
        assertThrows(IllegalArgumentException.class, () -> new McpOAuthSettings(ISSUER, "client", redirect, null, "admin"));
    }

    @Test
    void clientNameDefaultsToChatGptAndCanBeConfigured() {
        URI redirect = URI.create("http://localhost:8787/callback");
        assertEquals("ChatGPT", new McpOAuthSettings(ISSUER, "client", redirect, " ", null).clientName());
        assertEquals("Claude Code", new McpOAuthSettings(ISSUER, "client", redirect, " Claude Code ", null).clientName());
        assertThrows(IllegalArgumentException.class, () -> new McpOAuthSettings(ISSUER, "client", redirect, "x".repeat(61), null));
    }
}
