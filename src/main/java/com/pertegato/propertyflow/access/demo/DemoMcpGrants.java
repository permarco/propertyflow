package com.pertegato.propertyflow.access.demo;

import com.pertegato.propertyflow.access.demo.DemoAccounts.Account;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("frontend-demo")
@ConditionalOnProperty(name = "propertyflow.mcp.enabled", havingValue = "true")
public class DemoMcpGrants {

    private static final Duration LIFETIME = Duration.ofMinutes(30);
    private static final Pattern TOKEN_FORMAT = Pattern.compile("[A-Za-z0-9_-]{43}");

    private final DemoAccounts accounts;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Grant> grants = new HashMap<>();

    @Autowired
    public DemoMcpGrants(DemoAccounts accounts) {
        this(accounts, Clock.systemUTC());
    }

    DemoMcpGrants(DemoAccounts accounts, Clock clock) {
        this.accounts = accounts;
        this.clock = clock;
    }

    public synchronized String grant(UUID tenantId) {
        if (!isActiveTenant(tenantId)) {
            throw new IllegalArgumentException("An active tenant account is required.");
        }

        byte[] randomBytes = new byte[32];
        random.nextBytes(randomBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String digest = digest(token);

        Instant now = clock.instant();
        grants.values().removeIf(grant -> grant.tenantId().equals(tenantId) || !grant.expiresAt().isAfter(now));
        grants.put(digest, new Grant(tenantId, now.plus(LIFETIME)));
        return token;
    }

    public synchronized boolean hasActiveGrant(UUID tenantId) {
        Instant now = clock.instant();
        return isActiveTenant(tenantId) && grants.values().stream()
                .anyMatch(grant -> grant.tenantId().equals(tenantId) && grant.expiresAt().isAfter(now));
    }

    public synchronized void revoke(UUID tenantId) {
        grants.values().removeIf(grant -> grant.tenantId().equals(tenantId));
    }

    public synchronized Optional<UUID> findTenantId(String token) {
        if (token == null || !TOKEN_FORMAT.matcher(token).matches()) {
            return Optional.empty();
        }

        String digest = digest(token);
        Grant grant = grants.get(digest);
        if (grant == null) {
            return Optional.empty();
        }
        if (!grant.expiresAt().isAfter(clock.instant()) || !isActiveTenant(grant.tenantId())) {
            grants.remove(digest);
            return Optional.empty();
        }
        return Optional.of(grant.tenantId());
    }

    private boolean isActiveTenant(UUID tenantId) {
        return accounts.findActiveById(tenantId).filter(Account::tenant).isPresent();
    }

    private static String digest(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required to store MCP credentials.", exception);
        }
    }

    private record Grant(UUID tenantId, Instant expiresAt) {
    }
}
