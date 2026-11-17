package com.pertegato.propertyflow.access.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@Profile("propertyflow-backend & !frontend-demo")
public class AccountAdministrationStore {
    private static final RowMapper<Entry> ROW = (row, index) -> new Entry(row.getObject("id", UUID.class),
            row.getString("name"), row.getString("email"), row.getBoolean("active"), row.getBoolean("staff_role"),
            row.getBoolean("account_administration"), row.getBoolean("case_access_administration"), row.getLong("version"));
    private final JdbcTemplate database;

    public AccountAdministrationStore(JdbcTemplate database) {
        this.database = database;
    }

    public void lockAdministration() {
        // Gemeinsame Sperre verhindert den parallelen Entzug der letzten Verwaltungsrechte.
        database.queryForObject("SELECT id FROM pf_staff_setup WHERE id = 1 FOR UPDATE", Integer.class);
    }

    public Optional<Entry> find(UUID id) {
        return database.query("SELECT * FROM pf_staff_account WHERE id = ?", ROW, id).stream().findFirst();
    }

    public List<Entry> list() {
        return database.query("SELECT * FROM pf_staff_account ORDER BY name, id", ROW);
    }

    public Optional<String> initialEmail() {
        return database.query("SELECT email FROM pf_staff_account a JOIN pf_staff_setup s ON s.account_id = a.id",
                (row, index) -> row.getString(1)).stream().findFirst();
    }

    public boolean create(UUID id, String name, String email, boolean administrator, boolean accessAdministrator) {
        return database.update("""
                INSERT INTO pf_staff_account (id, name, email, active, staff_role, account_administration, case_access_administration)
                VALUES (?, ?, ?, true, true, ?, ?) ON CONFLICT (email) DO NOTHING
                """, id, name, email, administrator, accessAdministrator) == 1;
    }

    public long administrators() {
        return database.queryForObject("SELECT count(*) FROM pf_staff_account WHERE active AND staff_role AND account_administration", Long.class);
    }

    public void revoke(UUID id, boolean block) {
        database.update("""
                UPDATE pf_staff_account SET active = CASE WHEN ? THEN false ELSE active END,
                    staff_role = false, account_administration = false, case_access_administration = false, version = version + 1 WHERE id = ?
                """, block, id);
    }

    public void audit(UUID actor, UUID target, String action, String detail) {
        database.update("INSERT INTO pf_account_audit (id, actor_id, target_id, action, detail) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), actor, target, action, detail);
    }

    public Optional<String> command(UUID actor, UUID target, String operation) {
        return database.query("SELECT action FROM pf_account_audit WHERE actor_id = ? AND target_id = ? AND operation = ?",
                (row, index) -> row.getString(1), actor, target, operation).stream().findFirst();
    }

    public void auditCommand(UUID actor, UUID target, String action, String operation) {
        database.update("INSERT INTO pf_account_audit (id, actor_id, target_id, action, detail, operation) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), actor, target, action, "Mitarbeiterrechte entzogen", operation);
    }

    public record Entry(UUID id, String name, String email, boolean active, boolean staffRole,
            boolean accountAdministration, boolean caseAccessAdministration, long version) {
        public boolean activeStaff() {
            return active && staffRole;
        }
    }
}
