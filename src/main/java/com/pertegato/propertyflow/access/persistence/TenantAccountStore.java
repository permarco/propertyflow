package com.pertegato.propertyflow.access.persistence;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("propertyflow-backend & !frontend-demo")
@RequiredArgsConstructor
public class TenantAccountStore {
    private final JdbcTemplate database;

    public Optional<String> verifiedEmail(UUID id) {
        return database.query("SELECT email FROM pf_tenant_account WHERE id = ? AND active",
                (row, index) -> row.getString(1), id).stream().findFirst();
    }

    public Optional<UUID> activeId(String email) {
        return database.query("SELECT id FROM pf_tenant_account WHERE email = ? AND active",
                (row, index) -> row.getObject(1, UUID.class), email).stream().findFirst();
    }

    public Optional<UUID> registerVerified(String email) {
        database.update("INSERT INTO pf_tenant_account (id, email) VALUES (?, ?) ON CONFLICT (email) DO NOTHING",
                UUID.randomUUID(), email);
        // Eine erneute Registrierung hebt keine Kontosperre auf.
        return activeId(email);
    }
}
