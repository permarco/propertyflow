package com.pertegato.propertyflow.access.service;

import com.pertegato.propertyflow.access.contract.AccountAdministration;
import com.pertegato.propertyflow.access.contract.EmailAddress;
import com.pertegato.propertyflow.access.persistence.AccountAdministrationStore.Entry;
import com.pertegato.propertyflow.access.persistence.AccountAdministrationStore;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Profile("propertyflow-backend & !frontend-demo")
@RequiredArgsConstructor
public class AccountAdministrationService implements AccountAdministration {
    private final AccountAdministrationStore accounts;

    public List<Entry> list(UUID actor) {
        requireAdministrator(actor);
        return accounts.list();
    }

    public String developmentEmail() {
        return accounts.initialEmail().orElse("");
    }

    public boolean canManageAccounts(UUID actor) {
        return actor != null && accounts.find(actor).filter(Entry::activeStaff).filter(Entry::accountAdministration).isPresent();
    }

    public boolean canManageCaseAccess(UUID actor) {
        return actor != null && accounts.find(actor).filter(Entry::activeStaff).filter(Entry::caseAccessAdministration).isPresent();
    }

    @Override
    public void requireCaseAccessAdministrator(UUID actor) {
        Entry entry = requireStaff(actor);
        if (!entry.caseAccessAdministration()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private Entry requireStaff(UUID actor) {
        if (actor == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return accounts.find(actor).filter(Entry::activeStaff)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
    }

    private void requireAdministrator(UUID actor) {
        if (!requireStaff(actor).accountAdministration()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    @Transactional
    public UUID create(UUID actor, String name, String email) {
        accounts.lockAdministration();
        requireAdministrator(actor);
        if (name == null || name.isBlank() || name.length() > 150) {
            throw new IllegalArgumentException("Bitte einen Namen mit höchstens 150 Zeichen angeben.");
        }
        UUID target = UUID.randomUUID();
        if (!accounts.create(target, name.strip(), EmailAddress.normalize(email), false, false)) {
            throw new IllegalArgumentException("Für diese Adresse besteht bereits ein Mitarbeiterkonto.");
        }
        accounts.audit(actor, target, "CREATE", "staff=true, accounts=false, caseAccess=false");
        return target;
    }

    @Transactional
    public void revoke(UUID actor, UUID target, String operation, boolean block) {
        accounts.lockAdministration();
        requireAdministrator(actor);
        Entry entry = accounts.find(target).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (operation == null || operation.isBlank() || operation.length() > 80) {
            throw new IllegalArgumentException("Eine gültige Absende-ID ist erforderlich.");
        }
        String action = block ? "BLOCK" : "REMOVE_STAFF_ROLE";
        String previous = accounts.command(actor, target, operation).orElse(null);
        if (previous != null) {
            if (!previous.equals(action)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT);
            }
            return;
        }
        if (entry.activeStaff() && entry.accountAdministration() && accounts.administrators() <= 1) {
            throw new IllegalArgumentException("Das letzte aktive Verwaltungskonto muss erhalten bleiben.");
        }
        if (entry.staffRole() || block && entry.active()) {
            accounts.revoke(target, block);
        }
        accounts.auditCommand(actor, target, action, operation);
    }
}
