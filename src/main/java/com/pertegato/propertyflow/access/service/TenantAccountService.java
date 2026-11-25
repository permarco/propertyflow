package com.pertegato.propertyflow.access.service;

import com.pertegato.propertyflow.access.contract.EmailAddress;
import com.pertegato.propertyflow.access.contract.TenantAccountAccess;
import com.pertegato.propertyflow.access.persistence.TenantAccountStore;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("propertyflow-backend")
@RequiredArgsConstructor
public class TenantAccountService implements TenantAccountAccess {
    private final TenantAccountStore accounts;

    @Override
    public Optional<String> verifiedEmail(UUID id) {
        return id == null ? Optional.empty() : accounts.verifiedEmail(id);
    }

    public boolean exists(String email) {
        return accounts.activeId(email).isPresent();
    }

    @Transactional
    public Optional<UUID> authenticate(String email, boolean registration) {
        String address = EmailAddress.normalize(email);
        return registration ? accounts.registerVerified(address) : accounts.activeId(address);
    }
}
