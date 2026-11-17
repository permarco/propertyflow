package com.pertegato.propertyflow.caseprocessing.persistence;

import com.pertegato.propertyflow.caseprocessing.contract.PublicAuthor;
import com.pertegato.propertyflow.caseprocessing.contract.PublicMessage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@Profile("propertyflow-backend & !frontend-demo")
public class TenantMessageStore {

    private static final RowMapper<Message> MESSAGE = (row, index) -> new Message(row.getObject("id", UUID.class),
            row.getString("case_id"), row.getObject("access_id", UUID.class), row.getString("body"),
            row.getLong("leading_instance_key"), row.getString("tenant_id"), row.getString("state"), row.getBoolean("correlated"),
            row.getObject("child_instance_key", Long.class), row.getObject("parent_element_key", Long.class));
    private final JdbcTemplate database;

    public TenantMessageStore(JdbcTemplate database) {
        this.database = database;
    }

    public Optional<Access> access(String hash, boolean lock) {
        return database.query("SELECT id, case_id FROM pf_case_access WHERE token_hash = ? AND revoked_at IS NULL"
                        + (lock ? " FOR UPDATE" : ""),
                (row, index) -> new Access(row.getObject("id", UUID.class), row.getString("case_id")), hash).stream().findFirst();
    }

    public Optional<Access> accountAccess(String caseId, String email, boolean lock) {
        return database.query("""
                SELECT a.id, a.case_id FROM pf_case_access a JOIN pf_case c ON c.id = a.case_id
                WHERE c.id = ? AND c.contact_email = ? AND a.revoked_at IS NULL
                """ + (lock ? " FOR UPDATE OF a, c" : ""),
                (row, index) -> new Access(row.getObject("id", UUID.class), row.getString("case_id")), caseId, email).stream().findFirst();
    }

    public void recordActor(UUID messageId, UUID accountId) {
        database.update("UPDATE pf_tenant_message SET actor_account_id = ? WHERE id = ?", accountId, messageId);
    }

    public Optional<Message> find(Access access, String operation) {
        return database.query("SELECT * FROM pf_tenant_message WHERE case_id = ? AND operation_id = ?",
                MESSAGE, access.caseId(), operation).stream().findFirst();
    }

    public Message get(UUID id) {
        return database.query("SELECT * FROM pf_tenant_message WHERE id = ?", MESSAGE, id).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("UNKNOWN_TENANT_MESSAGE"));
    }

    public Message save(Access access, String operation, String text, long leader, String tenant) {
        UUID id = UUID.randomUUID();
        database.update("""
                UPDATE pf_case SET version = version + 1, analysis_version = analysis_version + 1,
                    last_tenant_contact_at = CURRENT_TIMESTAMP WHERE id = ?
                """, access.caseId());
        database.update("""
                INSERT INTO pf_tenant_message (id, case_id, access_id, operation_id, body, leading_instance_key, tenant_id, analysis_version)
                SELECT ?, id, ?, ?, ?, ?, ?, analysis_version FROM pf_case WHERE id = ?
                """, id, access.id(), operation, text, leader, tenant, access.caseId());
        database.update("INSERT INTO pf_message_mail_order (id, case_id) VALUES (?, ?)", id, access.caseId());
        database.update("""
                INSERT INTO pf_wait_contact (message_id, wait_id)
                SELECT ?, id FROM pf_case_wait WHERE case_id = ? AND kind = 'FOLLOW_UP' AND state = 'ACTIVE'
                """, id, access.caseId());
        return get(id);
    }

    public void correlated(UUID id) {
        database.update("UPDATE pf_tenant_message SET correlated = TRUE WHERE id = ?", id);
    }

    public Optional<Message> claimDelivery() {
        return database.query("""
                WITH candidate AS (
                    SELECT m.id FROM pf_tenant_message m JOIN pf_case c ON c.id = m.case_id
                    JOIN pf_case_process p ON p.case_id = m.case_id AND p.process_instance_key = m.leading_instance_key
                    WHERE m.state = 'PENDING' AND c.status <> 'CLOSED' AND m.next_delivery_at <= CURRENT_TIMESTAMP
                    ORDER BY m.next_delivery_at, m.id LIMIT 1 FOR UPDATE OF m SKIP LOCKED
                )
                UPDATE pf_tenant_message m SET next_delivery_at = CURRENT_TIMESTAMP + INTERVAL '30 seconds'
                FROM candidate c WHERE m.id = c.id RETURNING m.*
                """, MESSAGE).stream().findFirst();
    }

    public void receive(UUID id, long child, long parent) {
        database.update("""
                UPDATE pf_tenant_message SET state = 'RECEIVED', correlated = TRUE, child_instance_key = ?, parent_element_key = ? WHERE id = ?
                """, child, parent, id);
    }

    public void supersede(UUID id) {
        database.update("UPDATE pf_tenant_message SET state = 'SUPERSEDED' WHERE id = ?", id);
    }

    public List<PublicMessage> history(String caseId) {
        return database.query("""
                SELECT id, body, occurred_at FROM pf_tenant_message
                WHERE case_id = ? AND author = 'TENANT' AND visibility = 'EXTERNAL' AND published ORDER BY occurred_at, id
                """, (row, index) -> new PublicMessage(row.getString("id"), PublicAuthor.TENANT,
                        row.getString("body"), row.getTimestamp("occurred_at").toInstant()), caseId);
    }

    public record Access(UUID id, String caseId) {
    }

    public record Message(UUID id, String caseId, UUID accessId, String text, long leader, String tenant, String state,
            boolean correlated, Long child, Long parentElement) {
        @Override
        public String toString() {
            return "Tenant message " + id + " [content redacted]";
        }
    }
}
