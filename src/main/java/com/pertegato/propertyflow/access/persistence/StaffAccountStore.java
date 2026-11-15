package com.pertegato.propertyflow.access.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@Profile("propertyflow-backend & !frontend-demo")
public class StaffAccountStore {

    private static final RowMapper<Account> ACCOUNT = (row, rowNumber) -> new Account(
            row.getObject("id", UUID.class), row.getString("email"), row.getBoolean("active"), row.getBoolean("staff_role"));

    private final JdbcTemplate database;

    public StaffAccountStore(JdbcTemplate database) {
        this.database = database;
    }

    public Optional<Account> findByEmail(String email) {
        return database.query("select id, email, active, staff_role from pf_staff_account where email = ?", ACCOUNT, email)
                .stream()
                .findFirst();
    }

    public Optional<Account> findById(UUID accountId) {
        return database.query("select id, email, active, staff_role from pf_staff_account where id = ?", ACCOUNT, accountId)
                .stream()
                .findFirst();
    }

    public boolean createInitialAdministrator(String name, String email) {
        UUID existing = database.queryForObject("select account_id from pf_staff_setup where id = 1 for update", UUID.class);
        if (existing != null) {
            return false;
        }
        UUID accountId = UUID.randomUUID();
        database.update(
                "insert into pf_staff_account (id, name, email, active, staff_role, account_administration) values (?, ?, ?, true, true, true)",
                accountId, name, email);
        database.update("update pf_staff_setup set account_id = ?, completed_at = current_timestamp where id = 1", accountId);
        return true;
    }

    public record Account(UUID id, String email, boolean active, boolean staffRole) {
        public boolean activeStaff() {
            return active && staffRole;
        }
    }
}
