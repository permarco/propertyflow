package com.pertegato.propertyflow.access.demo;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("frontend-demo")
public class DemoAccounts {
    private static final Pattern EMAIL_ADDRESS = Pattern.compile(
            "[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");
    private final Optional<Account> demoAccount;
    private final Optional<Account> tenantAccount;
    private final ConcurrentMap<String, Account> registeredTenants = new ConcurrentHashMap<>();

    public DemoAccounts(String email) {
        this(email, "");
    }

    @Autowired
    public DemoAccounts(@Value("${propertyflow.staff.bootstrap.email:}") String email,
            @Value("${propertyflow.tenant.demo.email:}") String tenantEmail) {
        demoAccount = email.isBlank() ? Optional.empty() : Optional.of(new Account(UUID.randomUUID(), normalizeEmail(email)));
        tenantAccount = tenantEmail.isBlank() ? Optional.empty()
                : Optional.of(new Account(UUID.randomUUID(), normalizeEmail(tenantEmail), true));
        if (demoAccount.isPresent() && tenantAccount.isPresent()
                && demoAccount.get().email().equals(tenantAccount.get().email())) {
            throw new IllegalArgumentException("Demo-Mieter und Mitarbeiter benötigen unterschiedliche Adressen.");
        }
    }

    public Optional<Account> findActiveByEmail(String email) {
        String normalizedEmail = normalizeEmail(email);
        return allAccounts()
                .filter(account -> account.email().equals(normalizedEmail)).findFirst();
    }

    public Optional<Account> findActiveById(UUID id) {
        return allAccounts()
                .filter(account -> account.id().equals(id)).findFirst();
    }

    public Account activateVerifiedTenant(String email) {
        String normalizedEmail = normalizeEmail(email);
        return findActiveByEmail(normalizedEmail).orElseGet(() -> registeredTenants.computeIfAbsent(
                normalizedEmail, address -> new Account(UUID.randomUUID(), address, true)));
    }

    private Stream<Account> allAccounts() {
        return Stream.concat(Stream.concat(demoAccount.stream(), tenantAccount.stream()), registeredTenants.values().stream());
    }

    public static String normalizeEmail(String email) {
        String normalized = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        if (normalized.length() > 254 || !EMAIL_ADDRESS.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Eine gültige E-Mail-Adresse ist erforderlich.");
        }

        String localPart = normalized.substring(0, normalized.indexOf('@'));
        if (localPart.length() > 64 || localPart.startsWith(".") || localPart.endsWith(".") || localPart.contains("..")) {
            throw new IllegalArgumentException("Eine gültige E-Mail-Adresse ist erforderlich.");
        }
        return normalized;
    }

    public record Account(UUID id, String email, boolean tenant) {
        public Account(UUID id, String email) {
            this(id, email, false);
        }
    }
}
