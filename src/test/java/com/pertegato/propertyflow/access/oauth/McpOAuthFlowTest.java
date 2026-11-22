package com.pertegato.propertyflow.access.oauth;

import com.pertegato.propertyflow.access.contract.McpAccountContext;
import com.pertegato.propertyflow.access.contract.McpScope;
import com.pertegato.propertyflow.access.contract.McpTenantAuthorization;
import com.pertegato.propertyflow.access.contract.StaffAccessDecision;
import com.pertegato.propertyflow.access.contract.StaffAccountAccess;
import com.pertegato.propertyflow.access.contract.TenantAccountAccess;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.access.service.TenantOtpService;
import com.pertegato.propertyflow.access.web.StaffWebSession;
import com.pertegato.propertyflow.access.web.TenantWebSession;
import com.pertegato.propertyflow.caseprocessing.contract.CaseStatus;
import com.pertegato.propertyflow.caseprocessing.contract.CaseStatusView;
import com.pertegato.propertyflow.caseprocessing.contract.McpCaseReading;
import com.pertegato.propertyflow.caseprocessing.contract.McpMessageConfirmations;
import jakarta.servlet.Filter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(McpOAuthFlowTest.Web.class)
@ActiveProfiles("propertyflow-backend")
@TestPropertySource(properties = {
    "propertyflow.mcp.backend.enabled=true",
    "propertyflow.mcp.oauth.issuer=https://pf.example.test",
    "propertyflow.mcp.oauth.client-id=chat-test",
    "propertyflow.mcp.oauth.redirect-uri=https://client.example.test/callback"
})
class McpOAuthFlowTest {

    private static final String RESOURCE = "https://pf.example.test/mcp";
    private static final String CALLBACK = "https://client.example.test/callback";
    private static final String CLIENT = "chat-test";
    private static final String SCOPE = "tenant:cases:read";
    private static final String VERIFIER = "01234567890123456789012345678901234567890123456789";
    private static final Pattern CONSENT_STATE = Pattern.compile("name=\"state\"\\s+value=\"([^\"]+)\"");

    @Autowired
    private WebApplicationContext web;

    @Autowired
    private TenantAccountAccess accounts;

    @Autowired
    private StaffAccountAccess staff;

    @Autowired
    private McpTenantAuthorization authorization;

    @Autowired
    private TenantOtpService otp;

    @Autowired
    private McpCaseReading cases;

    @Test
    void authorizationCodeRequiresConsentAndPkceAndCanBeRevoked() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(web)
                .addFilters(web.getBean("springSecurityFilterChain", Filter.class))
                .build();
        mvc.perform(get("/.well-known/oauth-protected-resource/mcp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resource").value(RESOURCE));
        mvc.perform(get("/.well-known/oauth-authorization-server"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").value("https://pf.example.test"))
                .andExpect(jsonPath("$.token_endpoint_auth_methods_supported[0]").value("none"))
                .andExpect(jsonPath("$.scopes_supported").isArray());
        MvcResult loginRequired = mvc.perform(authorize(RESOURCE)).andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/mieter/anmelden")).andReturn();
        mvc.perform(authorize("https://other.example.test/mcp"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_target"));

        UUID tenant = UUID.randomUUID();
        when(accounts.verifiedEmail(tenant)).thenReturn(Optional.of("tenant@example.test"));
        MockHttpSession session = (MockHttpSession) loginRequired.getRequest().getSession(false);
        mvc.perform(withSession(get("/mieter/anmelden"), session)).andExpect(status().isOk());
        String loginCsrf = (String) session.getAttribute(StaffWebSession.CSRF);
        mvc.perform(withSession(post("/mieter/anmelden/code").param("email", "tenant@example.test").param("csrf", loginCsrf), session))
                .andExpect(status().isOk());
        when(otp.verifyCode(any(UUID.class), eq("123456"))).thenReturn(Optional.of(new WebAccountContext(tenant)));
        MvcResult loggedIn = mvc.perform(withSession(post("/mieter/anmelden/pruefen").param("code", "123456")
                        .param("csrf", loginCsrf), session))
                .andExpect(status().isSeeOther()).andReturn();
        assertTrue(loggedIn.getResponse().getHeader(HttpHeaders.LOCATION).startsWith("/oauth2/authorize?"));
        assertEquals(tenant, session.getAttribute(TenantWebSession.ACCOUNT));
        String accountCsrf = (String) session.getAttribute(StaffWebSession.CSRF);
        String html = mvc.perform(withSession(authorize(RESOURCE), session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Matcher form = CONSENT_STATE.matcher(html);
        assertTrue(form.find(), "Die Einwilligungsseite muss eine Vorgangsreferenz liefern.");

        MvcResult allowed = mvc.perform(withSession(post("/oauth2/authorize")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("client_id", CLIENT).param("state", form.group(1)).param("scope", SCOPE), session))
                .andExpect(status().is3xxRedirection()).andReturn();
        URI location = URI.create(allowed.getResponse().getHeader(HttpHeaders.LOCATION));
        String code = UriComponentsBuilder.fromUri(location).build().getQueryParams().getFirst("code");
        assertEquals("client.example.test", location.getHost());
        assertEquals("example-state", UriComponentsBuilder.fromUri(location).build().getQueryParams().getFirst("state"));

        mvc.perform(exchange(code, "wrong-verifier", RESOURCE)).andExpect(status().isBadRequest());
        mvc.perform(exchange(code, VERIFIER, "https://other.example.test/mcp")).andExpect(status().isBadRequest());
        String granted = mvc.perform(exchange(code, VERIFIER, RESOURCE)).andExpect(status().isOk())
                .andExpect(jsonPath("$.token_type").value("Bearer")).andReturn().getResponse().getContentAsString();
        String token = JsonMapper.shared().readTree(granted).get("access_token").asString();
        McpAccountContext context = authorization.authenticate(token).orElseThrow();
        assertEquals(tenant, context.accountId());
        assertTrue(authorization.allows(context, McpScope.TENANT_CASES));
        when(cases.readStatusForMcp(context, "case-a"))
                .thenReturn(new CaseStatusView("case-a", CaseStatus.RECEIVED, Optional.empty()));
        mvc.perform(callFallstand(token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.structuredContent.caseId").value("case-a"));

        mvc.perform(withSession(post("/mieter/verbundene-apps/widerrufen").param("csrf", "invalid"), session))
                .andExpect(status().isForbidden());
        assertTrue(authorization.allows(context, McpScope.TENANT_CASES));
        mvc.perform(withSession(post("/mieter/verbundene-apps/widerrufen").param("csrf", accountCsrf), session))
                .andExpect(status().isSeeOther());
        assertTrue(authorization.authenticate(token).isEmpty());
        assertFalse(authorization.allows(context, McpScope.TENANT_CASES));
        mvc.perform(callFallstand(token)).andExpect(status().isUnauthorized());
        mvc.perform(exchange(code, VERIFIER, RESOURCE)).andExpect(status().isBadRequest());
        String renewed = mvc.perform(withSession(authorize(RESOURCE), session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(CONSENT_STATE.matcher(renewed).find());
    }

    private static MockHttpServletRequestBuilder authorize(String resource) throws Exception {
        return authorize(resource, SCOPE);
    }

    @Test
    void staffScopesRequireStaffSessionAndAreLostWhenRoleIsRemoved() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(web)
                .addFilters(web.getBean("springSecurityFilterChain", Filter.class))
                .build();
        String scope = McpScope.STAFF_CASES.value();
        mvc.perform(authorize(RESOURCE, scope)).andExpect(redirectedUrl("/anmelden"));
        mvc.perform(authorize(RESOURCE, SCOPE + " " + scope)).andExpect(status().isBadRequest());

        UUID account = UUID.randomUUID();
        WebAccountContext actor = new WebAccountContext(account);
        when(staff.checkStaffAccess(actor)).thenReturn(StaffAccessDecision.ALLOWED);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(StaffWebSession.ACCOUNT, account);
        String html = mvc.perform(withSession(authorize(RESOURCE, scope), session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Matcher form = CONSENT_STATE.matcher(html);
        assertTrue(form.find());
        MvcResult allowed = mvc.perform(withSession(post("/oauth2/authorize")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("client_id", CLIENT).param("state", form.group(1)).param("scope", scope), session))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String code = UriComponentsBuilder.fromUriString(allowed.getResponse().getHeader(HttpHeaders.LOCATION))
                .build().getQueryParams().getFirst("code");
        String granted = mvc.perform(exchange(code, VERIFIER, RESOURCE)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = JsonMapper.shared().readTree(granted).get("access_token").asString();
        McpAccountContext context = authorization.authenticate(token).orElseThrow();
        assertEquals(account, context.accountId());
        assertTrue(authorization.allows(context, McpScope.STAFF_CASES));
        assertFalse(authorization.allows(context, McpScope.TENANT_CASES));
        when(staff.checkStaffAccess(actor)).thenReturn(StaffAccessDecision.FORBIDDEN);
        assertTrue(authorization.authenticate(token).isEmpty());
        assertFalse(authorization.allows(context, McpScope.STAFF_CASES));
    }

    private static MockHttpServletRequestBuilder authorize(String resource, String scope) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(VERIFIER.getBytes(StandardCharsets.US_ASCII));
        return get("/oauth2/authorize").queryParam("response_type", "code").queryParam("client_id", CLIENT)
                .queryParam("redirect_uri", CALLBACK).queryParam("scope", scope).queryParam("state", "example-state")
                .queryParam("resource", resource).queryParam("code_challenge", Base64.getUrlEncoder().withoutPadding().encodeToString(digest))
                .queryParam("code_challenge_method", "S256");
    }

    private static MockHttpServletRequestBuilder exchange(String code, String verifier, String resource) {
        return post("/oauth2/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code").param("code", code).param("redirect_uri", CALLBACK)
                .param("client_id", CLIENT).param("code_verifier", verifier).param("resource", resource);
    }

    private static MockHttpServletRequestBuilder withSession(MockHttpServletRequestBuilder request, MockHttpSession session) {
        return request.session(session).with(servlet -> {
            servlet.setRequestedSessionIdFromCookie(true);
            return servlet;
        });
    }

    private static MockHttpServletRequestBuilder callFallstand(String token) {
        return post("/mcp").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .header("MCP-Protocol-Version", "2025-11-25").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content("""
                        {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{
                          "name":"fallstand_abrufen","arguments":{"caseId":"case-a"}}}
                        """);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import({McpOAuthConfiguration.class, McpOAuthDelegations.class, McpOAuthWebController.class})
    @ComponentScan(basePackages = {"com.pertegato.propertyflow.access.web", "com.pertegato.propertyflow.caseprocessing.mcp"},
            useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*\\.(TenantLoginController|BackendMcpConfiguration)"))
    static class Web {

        @Bean
        JdbcTemplate flywayInitializer() throws Exception {
            DriverManagerDataSource database = new DriverManagerDataSource(
                    "jdbc:h2:mem:mcp-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
            String sql = new ClassPathResource("db/backend-migration/updates/V9__mcp_oauth.sql")
                    .getContentAsString(StandardCharsets.UTF_8).replace("timestamptz", "timestamp with time zone");
            new ResourceDatabasePopulator(new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8))).execute(database);
            return new JdbcTemplate(database);
        }

        @Bean
        TenantAccountAccess accounts() {
            return mock(TenantAccountAccess.class);
        }

        @Bean
        TenantOtpService otp() {
            return mock(TenantOtpService.class);
        }

        @Bean
        McpCaseReading cases() {
            return mock(McpCaseReading.class);
        }

        @Bean
        McpMessageConfirmations confirmations() {
            return mock(McpMessageConfirmations.class);
        }

        @Bean
        StaffAccountAccess staffAccounts() {
            return mock(StaffAccountAccess.class);
        }
    }
}
