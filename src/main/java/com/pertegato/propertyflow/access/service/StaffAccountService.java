package com.pertegato.propertyflow.access.service;

import com.pertegato.propertyflow.access.contract.EmailAddress;
import com.pertegato.propertyflow.access.contract.StaffAccessDecision;
import com.pertegato.propertyflow.access.contract.StaffAccountAccess;
import com.pertegato.propertyflow.access.contract.StaffAuthenticationErrorCode;
import com.pertegato.propertyflow.access.contract.StaffAuthenticationException;
import com.pertegato.propertyflow.access.contract.StaffDirectory;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.access.persistence.StaffAccountStore.Account;
import com.pertegato.propertyflow.access.persistence.StaffAccountStore;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("propertyflow-backend & !frontend-demo")
@RequiredArgsConstructor
public class StaffAccountService implements StaffAccountAccess, StaffDirectory {

    private final StaffAccountStore accounts;

    @Override
    public StaffAccessDecision checkStaffAccess(WebAccountContext context) {
        return resolve(context)
                .map(account -> account.activeStaff() ? StaffAccessDecision.ALLOWED : StaffAccessDecision.FORBIDDEN)
                .orElse(StaffAccessDecision.UNAUTHENTICATED);
    }

    @Override
    public Map<UUID, String> displayNames(Set<UUID> accountIds) {
        return accounts.displayNames(accountIds);
    }

    Optional<Account> resolve(WebAccountContext context) {
        if (context == null) {
            return Optional.empty();
        }
        try {
            return accounts.findById(context.accountId());
        } catch (DataAccessResourceFailureException | RecoverableDataAccessException | TransientDataAccessException exception) {
            throw new StaffAuthenticationException(StaffAuthenticationErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    Optional<Account> findActiveByEmail(String email) {
        try {
            return accounts.findByEmail(email).filter(Account::activeStaff);
        } catch (DataAccessResourceFailureException | RecoverableDataAccessException | TransientDataAccessException exception) {
            throw new StaffAuthenticationException(StaffAuthenticationErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    @Transactional
    public boolean createInitialAdministrator(String name, String email) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Der Name des ersten Verwaltungskontos ist erforderlich.");
        }
        return accounts.createInitialAdministrator(name.strip(), EmailAddress.normalize(email));
    }
}
