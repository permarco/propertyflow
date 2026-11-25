package com.pertegato.propertyflow.access.oauth;

import jakarta.servlet.Filter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lokale MCP-Clients wie Claude Code melden {@code http://localhost:<Port>/callback}. Nur genau die
 * konfigurierte URI wird angenommen; ein anderer Port, Pfad oder die IP-Schreibweise werden abgewiesen.
 */
@SpringJUnitWebConfig(McpOAuthFlowTest.Web.class)
@ActiveProfiles("propertyflow-backend")
@TestPropertySource(properties = {
    "propertyflow.mcp.backend.enabled=true",
    "propertyflow.mcp.oauth.issuer=http://127.0.0.1:8081",
    "propertyflow.mcp.oauth.client-id=claude-code-test",
    "propertyflow.mcp.oauth.redirect-uri=http://localhost:8787/callback",
    "propertyflow.mcp.oauth.client-name=Claude Code",
    "propertyflow.mcp.oauth.role=tenant"
})
class McpOAuthLocalhostRedirectTest {

    private static final String RESOURCE = "http://127.0.0.1:8081/mcp";
    private static final String VERIFIER = "01234567890123456789012345678901234567890123456789";

    @Autowired
    private WebApplicationContext web;

    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(web).addFilters(web.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    @Test
    void exactLocalhostCallbackPassesValidationAndLeadsToLogin() throws Exception {
        mvc.perform(authorize("http://localhost:8787/callback"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/mieter/anmelden"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:9999/callback", "http://localhost:8787/other", "http://127.0.0.1:8787/callback"})
    void otherCallbacksAreRejected(String callback) throws Exception {
        mvc.perform(authorize(callback)).andExpect(status().isBadRequest());
    }

    @Test
    void tenantRoleOffersAndAcceptsAllTenantScopes() throws Exception {
        mvc.perform(get("/.well-known/oauth-protected-resource/mcp"))
                .andExpect(jsonPath("$.scopes_supported", contains("tenant:cases:read", "tenant:messages:write", "tenant:cases:create")));
        mvc.perform(authorize("http://localhost:8787/callback", "tenant:cases:read tenant:messages:write tenant:cases:create"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/mieter/anmelden"));
    }

    @Test
    void tenantRoleRejectsStaffScopes() throws Exception {
        mvc.perform(authorize("http://localhost:8787/callback", "staff:cases:read"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith("http://localhost:8787/callback?error=invalid_scope")));
    }

    private static MockHttpServletRequestBuilder authorize(String callback) throws Exception {
        return authorize(callback, "tenant:cases:read");
    }

    private static MockHttpServletRequestBuilder authorize(String callback, String scope) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(VERIFIER.getBytes(StandardCharsets.US_ASCII));
        return get("/oauth2/authorize").queryParam("response_type", "code").queryParam("client_id", "claude-code-test")
                .queryParam("redirect_uri", callback).queryParam("scope", scope).queryParam("state", "example-state")
                .queryParam("resource", RESOURCE).queryParam("code_challenge", Base64.getUrlEncoder().withoutPadding().encodeToString(digest))
                .queryParam("code_challenge_method", "S256");
    }
}
