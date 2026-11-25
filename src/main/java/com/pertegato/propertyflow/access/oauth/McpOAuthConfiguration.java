package com.pertegato.propertyflow.access.oauth;

import com.pertegato.propertyflow.access.contract.StaffAccessDecision;
import com.pertegato.propertyflow.access.contract.StaffAccountAccess;
import com.pertegato.propertyflow.access.contract.TenantAccountAccess;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.access.web.StaffWebSession;
import com.pertegato.propertyflow.access.web.TenantOAuthLogin;
import com.pertegato.propertyflow.access.web.TenantWebSession;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.OAuth2AccessTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenClaimsContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration(proxyBeanMethods = false)
@Profile("propertyflow-backend")
@ConditionalOnProperty(name = "propertyflow.mcp.backend.enabled", havingValue = "true")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(McpOAuthSettings.class)
@EnableWebSecurity
class McpOAuthConfiguration {

    @Bean
    RegisteredClientRepository mcpRegisteredClients(McpOAuthSettings settings) {
        RegisteredClient client = RegisteredClient.withId(settings.clientId())
                .clientId(settings.clientId())
                .clientName(settings.clientName())
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(settings.redirectUri().toASCIIString())
                .scopes(scopes -> scopes.addAll(settings.scopes()))
                .clientSettings(ClientSettings.builder().requireProofKey(true).requireAuthorizationConsent(true).build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenFormat(OAuth2TokenFormat.REFERENCE)
                        .accessTokenTimeToLive(Duration.ofMinutes(15))
                        .authorizationCodeTimeToLive(Duration.ofMinutes(3))
                        .build())
                .build();
        return new InMemoryRegisteredClientRepository(client);
    }

    @Bean
    @DependsOn("flywayInitializer")
    OAuth2AuthorizationService mcpAuthorizations(JdbcOperations database, RegisteredClientRepository clients) {
        return new JdbcOAuth2AuthorizationService(database, clients);
    }

    @Bean
    OAuth2AuthorizationConsentService mcpConsents(JdbcOperations database, RegisteredClientRepository clients) {
        return new JdbcOAuth2AuthorizationConsentService(database, clients);
    }

    @Bean
    SecurityFilterChain mcpOAuthEndpoints(HttpSecurity http, McpOAuthSettings settings, TenantAccountAccess accounts,
            StaffAccountAccess staffAccounts) throws Exception {
        OAuth2AuthorizationServerConfigurer oauth = new OAuth2AuthorizationServerConfigurer();
        OAuth2AccessTokenGenerator tokens = new OAuth2AccessTokenGenerator();
        OAuth2TokenCustomizer<OAuth2TokenClaimsContext> audience = context ->
                context.getClaims().audience(Collections.singletonList(settings.resourceUri().toASCIIString()));
        tokens.setAccessTokenCustomizer(audience);
        http.securityMatcher(oauth.getEndpointsMatcher())
                .with(oauth, server -> server
                        .authorizationServerSettings(AuthorizationServerSettings.builder().issuer(settings.issuer().toASCIIString()).build())
                        .authorizationServerMetadataEndpoint(endpoint -> endpoint.authorizationServerMetadataCustomizer(metadata -> metadata
                                .tokenEndpointAuthenticationMethods(methods -> {
                                    methods.clear();
                                    methods.add(ClientAuthenticationMethod.NONE.getValue());
                                })
                                .scopes(scopes -> scopes.addAll(settings.scopes()))))
                        .tokenGenerator(tokens))
                .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
                .requestCache(cache -> cache.disable())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint((request, response, failure) ->
                        TenantOAuthLogin.requireLogin(request, response, staffScope(request))))
                .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                .addFilterBefore(new ExistingAccountSession(accounts, staffAccounts), CsrfFilter.class)
                .addFilterBefore(new McpResourceBinding(settings), CsrfFilter.class);
        return http.build();
    }

    private static boolean staffScope(HttpServletRequest request) {
        return scopes(request).stream().anyMatch(scope -> scope.startsWith("staff:"));
    }

    private static List<String> scopes(HttpServletRequest request) {
        String[] values = request.getParameterValues("scope");
        return values == null ? List.of() : Arrays.stream(values).flatMap(value -> Arrays.stream(value.split(" "))).toList();
    }

    private static final class ExistingAccountSession extends OncePerRequestFilter {

        private final TenantAccountAccess accounts;
        private final StaffAccountAccess staffAccounts;

        private ExistingAccountSession(TenantAccountAccess accounts, StaffAccountAccess staffAccounts) {
            this.accounts = accounts;
            this.staffAccounts = staffAccounts;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            if ((request.getContextPath() + TenantOAuthLogin.AUTHORIZE).equals(request.getRequestURI())) {
                List<String> requested = scopes(request);
                boolean staff = staffScope(request);
                if (("GET".equals(request.getMethod()) && requested.isEmpty())
                        || staff && requested.stream().anyMatch(scope -> scope.startsWith("tenant:"))) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "invalid_scope");
                    return;
                }
                UUID tenant = TenantWebSession.account(request);
                boolean staffSession = staff || requested.isEmpty() && tenant == null;
                UUID id = staffSession ? staffAccount(request) : tenant;
                if (id != null && (staffSession || accounts.verifiedEmail(id).isPresent())) {
                    SecurityContextHolder.getContext().setAuthentication(
                            UsernamePasswordAuthenticationToken.authenticated(id.toString(), null, AuthorityUtils.NO_AUTHORITIES));
                }
            }
            chain.doFilter(request, response);
        }

        private UUID staffAccount(HttpServletRequest request) {
            HttpSession session = StaffWebSession.cookieSession(request);
            if (session != null && session.getAttribute(StaffWebSession.ACCOUNT) instanceof UUID account
                    && staffAccounts.checkStaffAccess(new WebAccountContext(account)) == StaffAccessDecision.ALLOWED) {
                return account;
            }
            return null;
        }
    }

    private static final class McpResourceBinding extends OncePerRequestFilter {

        private final String resource;

        private McpResourceBinding(McpOAuthSettings settings) {
            this.resource = settings.resourceUri().toASCIIString();
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String[] resources = request.getParameterValues("resource");
            if (resources != null && (resources.length != 1 || !resource.equals(resources[0]))) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.setContentType("application/json");
                response.setHeader("Cache-Control", "no-store");
                response.getWriter().write("{\"error\":\"invalid_target\"}");
                return;
            }
            chain.doFilter(request, response);
        }
    }
}
