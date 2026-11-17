package com.pertegato.propertyflow.caseprocessing.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("propertyflow-backend & !frontend-demo")
public class CaseAccessAdministrationStore {
    private final JdbcTemplate database;

    public CaseAccessAdministrationStore(JdbcTemplate database) {
        this.database = database;
    }

    public Optional<CaseContact> contact(String caseId, boolean lock) {
        return database.query("SELECT id, contact_email, version FROM pf_case WHERE id = ?" + (lock ? " FOR UPDATE" : ""),
                (row, index) -> new CaseContact(row.getString(1), row.getString(2), row.getLong(3)), caseId).stream().findFirst();
    }

    public boolean allowVerification(String caseId, UUID actor) {
        return database.queryForObject("""
                SELECT count(*) < 5 FROM pf_access_verification
                WHERE (case_id = ? OR actor_id = ?) AND created_at > CURRENT_TIMESTAMP - INTERVAL '1 hour'
                """, Boolean.class, caseId, actor);
    }

    public void saveVerification(UUID id, UUID actor, CaseContact current, String email, String evidence, String digest) {
        database.update("""
                INSERT INTO pf_access_verification
                (id, case_id, actor_id, case_version, previous_email, target_email, identity_evidence, code_digest, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP + INTERVAL '15 minutes')
                """, id, current.id(), actor, current.version(), current.email(), email, evidence, digest);
    }

    public Optional<Verification> lockVerification(UUID id) {
        return database.query("SELECT * FROM pf_access_verification WHERE id = ? FOR UPDATE", (row, index) -> new Verification(
                row.getObject("id", UUID.class), row.getString("case_id"), row.getObject("actor_id", UUID.class),
                row.getLong("case_version"), row.getString("target_email"), row.getString("code_digest"),
                row.getTimestamp("expires_at").toInstant(), row.getInt("attempts"), row.getTimestamp("consumed_at") != null,
                row.getString("operation"), row.getTimestamp("verified_at") != null, row.getString("previous_email")), id).stream().findFirst();
    }

    public void verified(UUID id) {
        database.update("UPDATE pf_access_verification SET verified_at = CURRENT_TIMESTAMP WHERE id = ?", id);
    }

    public void failedAttempt(UUID id) {
        database.update("UPDATE pf_access_verification SET attempts = attempts + 1 WHERE id = ?", id);
    }

    public void replace(Verification proof, String operation, UUID accessId, String hash, String encrypted) {
        database.update("UPDATE pf_case_access SET revoked_at = CURRENT_TIMESTAMP WHERE case_id = ? AND revoked_at IS NULL", proof.caseId());
        database.update("INSERT INTO pf_case_access (id, case_id, token_hash, encrypted_token) VALUES (?, ?, ?, ?)",
                accessId, proof.caseId(), hash, encrypted);
        database.update("UPDATE pf_case SET contact_email = ?, version = version + 1 WHERE id = ?", proof.email(), proof.caseId());
        database.update("UPDATE pf_access_verification SET consumed_at = CURRENT_TIMESTAMP, operation = ? WHERE id = ?", operation, proof.id());
        database.update("INSERT INTO pf_access_mail_order (id, case_id) VALUES (?, ?)", proof.id(), proof.caseId());
        database.update("INSERT INTO pf_access_audit (id, case_id, actor_id, action) VALUES (?, ?, ?, ?)",
                proof.id(), proof.caseId(), proof.actor(), proof.previousEmail().equals(proof.email()) ? "REPLACE_LINK" : "CHANGE_CONTACT");
    }

    public Optional<MailOrder> claimMail() {
        database.update("UPDATE pf_access_mail_order SET state = 'UNCERTAIN' WHERE state = 'SENDING' AND next_attempt_at < CURRENT_TIMESTAMP");
        return database.query("""
                WITH candidate AS (
                    SELECT id FROM pf_access_mail_order WHERE state = 'PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP
                    ORDER BY next_attempt_at LIMIT 1 FOR UPDATE SKIP LOCKED
                ) UPDATE pf_access_mail_order o SET state = 'SENDING', attempts = attempts + 1,
                    next_attempt_at = CURRENT_TIMESTAMP + INTERVAL '1 minute'
                FROM candidate c WHERE o.id = c.id RETURNING o.id, o.case_id
                """, (row, index) -> new MailOrder(row.getObject(1, UUID.class), row.getString(2))).stream().findFirst();
    }

    public void mailResult(UUID id, String state) {
        database.update("UPDATE pf_access_mail_order SET state = ?, next_attempt_at = CURRENT_TIMESTAMP + INTERVAL '1 minute' WHERE id = ?",
                state, id);
    }

    public record CaseContact(String id, String email, long version) {
    }

    public record Verification(UUID id, String caseId, UUID actor, long version, String email, String digest,
            Instant expiresAt, int attempts, boolean consumed, String operation, boolean verified, String previousEmail) {
    }

    public record MailOrder(UUID id, String caseId) {
    }
}
