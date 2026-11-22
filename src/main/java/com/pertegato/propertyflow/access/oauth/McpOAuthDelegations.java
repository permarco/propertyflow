package com.pertegato.propertyflow.access.oauth;

import com.pertegato.propertyflow.access.contract.McpAccountContext;
import com.pertegato.propertyflow.access.contract.McpScope;
import com.pertegato.propertyflow.access.contract.McpTenantAuthorization;
import com.pertegato.propertyflow.access.contract.StaffAccessDecision;
import com.pertegato.propertyflow.access.contract.StaffAccountAccess;
import com.pertegato.propertyflow.access.contract.TenantAccountAccess;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("propertyflow-backend & !frontend-demo")
@ConditionalOnProperty(name = "propertyflow.mcp.backend.enabled", havingValue = "true")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class McpOAuthDelegations implements McpTenantAuthorization {

    private final McpOAuthSettings settings;
    private final OAuth2AuthorizationService authorizations;
    private final OAuth2AuthorizationConsentService consents;
    private final TenantAccountAccess accounts;
    private final StaffAccountAccess staff;
    private final JdbcOperations database;

    McpOAuthDelegations(McpOAuthSettings settings, OAuth2AuthorizationService authorizations,
            OAuth2AuthorizationConsentService consents, TenantAccountAccess accounts, StaffAccountAccess staff, JdbcOperations database) {
        this.settings = settings;
        this.authorizations = authorizations;
        this.consents = consents;
        this.accounts = accounts;
        this.staff = staff;
        this.database = database;
    }

    @Override
    public Optional<McpAccountContext> authenticate(String token) {
        OAuth2Authorization authorization = authorizations.findByToken(token, OAuth2TokenType.ACCESS_TOKEN);
        if (!usable(authorization)) {
            return Optional.empty();
        }
        try {
            UUID account = UUID.fromString(authorization.getPrincipalName());
            UUID delegation = UUID.fromString(authorization.getId());
            return Arrays.stream(McpScope.values()).anyMatch(scope -> permitted(authorization, account, scope))
                    ? Optional.of(new McpAccountContext(account, delegation, settings.clientId()))
                    : Optional.empty();
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    @Override
    public boolean allows(McpAccountContext context, McpScope scope) {
        OAuth2Authorization authorization = authorizations.findById(context.delegationId().toString());
        return usable(authorization) && settings.clientId().equals(context.clientId())
                && context.accountId().toString().equals(authorization.getPrincipalName())
                && permitted(authorization, context.accountId(), scope);
    }

    @Override
    @Transactional
    public boolean lockDelegation(McpAccountContext context, McpScope scope) {
        return !database.queryForList("SELECT id FROM oauth2_authorization WHERE id = ? FOR UPDATE", String.class,
                context.delegationId().toString()).isEmpty() && allows(context, scope);
    }

    private boolean permitted(OAuth2Authorization authorization, UUID account, McpScope scope) {
        if (!authorization.getAccessToken().getToken().getScopes().contains(scope.value())) {
            return false;
        }
        return scope.staff()
                ? staff.checkStaffAccess(new WebAccountContext(account)) == StaffAccessDecision.ALLOWED
                : accounts.verifiedEmail(account).isPresent();
    }

    @Override
    public URI resourceMetadataUri() {
        return settings.resourceMetadataUri();
    }

    @Override
    public URI resourceUri() {
        return settings.resourceUri();
    }

    List<String> connected(UUID account, boolean staffAccount) {
        OAuth2AuthorizationConsent consent = consents.findById(settings.clientId(), account.toString());
        return consent == null ? List.of() : Arrays.stream(McpScope.values())
                .filter(scope -> scope.staff() == staffAccount && consent.getScopes().contains(scope.value()))
                .map(McpScope::label)
                .toList();
    }

    @Transactional
    public void revoke(UUID account) {
        String principal = account.toString();
        database.update("DELETE FROM oauth2_authorization WHERE principal_name = ? AND registered_client_id = ?",
                principal, settings.clientId());
        OAuth2AuthorizationConsent consent = consents.findById(settings.clientId(), principal);
        if (consent != null) {
            consents.remove(consent);
        }
    }

    private boolean usable(OAuth2Authorization authorization) {
        if (authorization == null || !settings.clientId().equals(authorization.getRegisteredClientId())) {
            return false;
        }
        OAuth2Authorization.Token<OAuth2AccessToken> token = authorization.getAccessToken();
        return token != null && token.isActive()
                && token.getClaims() != null && List.of(settings.resourceUri().toASCIIString()).equals(token.getClaims().get("aud"));
    }
}
