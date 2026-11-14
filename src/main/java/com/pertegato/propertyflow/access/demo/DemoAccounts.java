package com.pertegato.propertyflow.access.demo;

import com.pertegato.propertyflow.access.contract.EmailAddress;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("frontend-demo")
public class DemoAccounts {
    private final Optional<Account> demoAccount;
    private final Optional<Account> tenantAccount;
    private final ConcurrentMap<String, Account> registeredTenants = new ConcurrentHashMap<>();

    public DemoAccounts(String email) {
        this(email, "");
    }

    @Autowired
    public DemoAccounts(@Value("${propertyflow.staff.bootstrap.email:}") String email,
            @Value("${propertyflow.tenant.demo.email:}") String tenantEmail) {
        demoAccount = email.isBlank() ? Optional.empty() : Optional.of(new Account(UUID.randomUUID(), EmailAddress.normalize(email)));
        tenantAccount = tenantEmail.isBlank() ? Optional.empty()
                : Optional.of(new Account(UUID.randomUUID(), EmailAddress.normalize(tenantEmail), true));
        if (demoAccount.isPresent() && tenantAccount.isPresent()
                && demoAccount.get().email().equals(tenantAccount.get().email())) {
            throw new IllegalArgumentException("Demo-Mieter und Mitarbeiter benötigen unterschiedliche Adressen.");
        }
    }

    public Optional<Account> findActiveByEmail(String email) {
        String normalizedEmail = EmailAddress.normalize(email);
        return allAccounts()
                .filter(account -> account.email().equals(normalizedEmail)).findFirst();
    }

    public Optional<Account> findActiveById(UUID id) {
        return allAccounts()
                .filter(account -> account.id().equals(id)).findFirst();
    }

    public Account activateVerifiedTenant(String email) {
        String normalizedEmail = EmailAddress.normalize(email);
        return findActiveByEmail(normalizedEmail).orElseGet(() -> registeredTenants.computeIfAbsent(
                normalizedEmail, address -> new Account(UUID.randomUUID(), address, true)));
    }

    private Stream<Account> allAccounts() {
        return Stream.concat(Stream.concat(demoAccount.stream(), tenantAccount.stream()), registeredTenants.values().stream());
    }

    public record Account(UUID id, String email, boolean tenant) {
        public Account(UUID id, String email) {
            this(id, email, false);
        }
    }
}
