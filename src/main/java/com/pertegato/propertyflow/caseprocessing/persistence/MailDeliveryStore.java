package com.pertegato.propertyflow.caseprocessing.persistence;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("propertyflow-backend")
@RequiredArgsConstructor
public class MailDeliveryStore {

    private final JdbcTemplate database;

    public boolean messageRequiresAnswer(UUID id) {
        return Boolean.TRUE.equals(database.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM pf_mail_delivery d JOIN pf_staff_answer_requirement q ON q.message_id = d.staff_message_order_id
                    JOIN pf_case c ON c.id = q.case_id WHERE d.id = ? AND q.state = 'OPEN' AND c.status <> 'CLOSED')
                """, Boolean.class, id));
    }

    public void discoverOrders() {
        // The original obligations were committed with intake/status. This restartable projection
        // adds dispatch state without requiring a second write in the original business transaction.
        database.update("""
                INSERT INTO pf_mail_delivery (id, case_id, acceptance_order_id, notification_type)
                SELECT id, case_id, id, 'ACCEPTED' FROM pf_acceptance_mail_order ON CONFLICT DO NOTHING
                """);
        database.update("""
                INSERT INTO pf_mail_delivery (id, case_id, status_order_id, notification_type)
                SELECT id, case_id, id, notification_type FROM pf_case_mail_order ON CONFLICT DO NOTHING
                """);
        database.update("""
                INSERT INTO pf_mail_delivery (id, case_id, urgency_order_id, notification_type)
                SELECT id, case_id, id, 'URGENCY_CHANGED' FROM pf_urgency_mail_order ON CONFLICT DO NOTHING
                """);
        database.update("""
                INSERT INTO pf_mail_delivery (id, case_id, message_order_id, notification_type)
                SELECT id, case_id, id, 'TENANT_MESSAGE' FROM pf_message_mail_order ON CONFLICT DO NOTHING
                """);
        database.update("""
                INSERT INTO pf_mail_delivery (id, case_id, staff_message_order_id, notification_type)
                SELECT id, case_id, id, 'STAFF_MESSAGE' FROM pf_staff_message_mail_order ON CONFLICT DO NOTHING
                """);
    }

    public Optional<MailStart> claimStart(long definitionKey, String tenantId) {
        return database.query("""
                WITH candidate AS (
                    SELECT id FROM pf_mail_delivery WHERE start_state = 'PENDING' AND next_start_at <= CURRENT_TIMESTAMP
                      AND (lease_until IS NULL OR lease_until <= CURRENT_TIMESTAMP)
                    ORDER BY next_start_at, id LIMIT 1 FOR UPDATE SKIP LOCKED
                )
                UPDATE pf_mail_delivery d SET lease_id = ?, lease_until = CURRENT_TIMESTAMP + INTERVAL '2 minutes',
                    definition_key = COALESCE(definition_key, ?), tenant_id = COALESCE(tenant_id, ?), start_attempts = start_attempts + 1
                FROM candidate c WHERE d.id = c.id RETURNING d.id, d.definition_key, d.tenant_id, d.lease_id, d.start_attempts
                """, (row, index) -> new MailStart(row.getObject("id", UUID.class), row.getLong("definition_key"),
                        row.getString("tenant_id"), row.getObject("lease_id", UUID.class), row.getInt("start_attempts")),
                UUID.randomUUID(), definitionKey, tenantId).stream().findFirst();
    }

    public void finishStart(MailStart start, String result, boolean blocked, Duration delay) {
        database.update("""
                UPDATE pf_mail_delivery SET start_state = ?, last_start_result = ?, lease_id = NULL, lease_until = NULL,
                    next_start_at = CURRENT_TIMESTAMP + (? * INTERVAL '1 millisecond')
                WHERE id = ? AND lease_id = ? AND start_state = 'PENDING'
                """, blocked ? "BLOCKED" : "PENDING", result, delay.toMillis(), start.id(), start.leaseId());
    }

    public Delivery lock(UUID id) {
        return database.query("SELECT * FROM pf_mail_delivery WHERE id = ? FOR UPDATE", (row, index) -> new Delivery(
                row.getObject("id", UUID.class), row.getString("case_id"), row.getString("notification_type"), row.getString("state"),
                row.getInt("attempts"), row.getObject("attempt_id", UUID.class),
                row.getTimestamp("attempt_started_at") == null ? null : row.getTimestamp("attempt_started_at").toInstant(),
                row.getObject("definition_key", Long.class), row.getString("tenant_id"), row.getObject("process_instance_key", Long.class)),
                id).stream().findFirst().orElseThrow(() -> new IllegalArgumentException("UNKNOWN_MAIL_ORDER"));
    }

    public void register(UUID id, long instanceKey) {
        database.update("""
                UPDATE pf_mail_delivery SET process_instance_key = ?, start_state = 'CONFIRMED', lease_id = NULL, lease_until = NULL
                WHERE id = ?
                """, instanceKey, id);
    }

    public Optional<Recipient> recipient(String caseId) {
        return database.query("""
                SELECT c.contact_email, a.id, a.encrypted_token FROM pf_case c
                JOIN pf_case_access a ON a.case_id = c.id AND a.revoked_at IS NULL WHERE c.id = ?
                """, (row, index) -> new Recipient(row.getString("contact_email"), row.getObject("id", UUID.class),
                        row.getString("encrypted_token")), caseId).stream().findFirst();
    }

    public String eventStatus(UUID deliveryId) {
        return database.queryForObject("""
                SELECT e.to_status FROM pf_mail_delivery d JOIN pf_case_mail_order o ON o.id = d.status_order_id
                JOIN pf_case_status_event e ON e.id = o.event_id WHERE d.id = ?
                """, String.class, deliveryId);
    }

    public void begin(UUID id, UUID attemptId, String recipient) {
        database.update("""
                UPDATE pf_mail_delivery SET state = 'SENDING', attempts = attempts + 1, attempt_id = ?,
                    attempt_started_at = CURRENT_TIMESTAMP, recipient = ?, error_code = NULL WHERE id = ?
                """, attemptId, recipient, id);
    }

    public String eventUrgency(UUID deliveryId) {
        return database.queryForObject("""
                SELECT e.current_urgency FROM pf_mail_delivery d JOIN pf_urgency_mail_order o ON o.id = d.urgency_order_id
                JOIN pf_urgency_event e ON e.id = o.event_id WHERE d.id = ?
                """, String.class, deliveryId);
    }

    public void result(UUID id, String state, String reason) {
        database.update("""
                UPDATE pf_mail_delivery SET state = ?, error_code = ?,
                    accepted_at = CASE WHEN ? = 'SENT' THEN CURRENT_TIMESTAMP ELSE accepted_at END WHERE id = ?
                """, state, reason, state, id);
        database.update("UPDATE pf_acceptance_mail_order SET state = ? WHERE id = ?", state, id);
        database.update("UPDATE pf_case_mail_order SET state = ? WHERE id = ?", state, id);
        database.update("UPDATE pf_urgency_mail_order SET state = ? WHERE id = ?", state, id);
        database.update("UPDATE pf_message_mail_order SET state = ? WHERE id = ?", state, id);
        database.update("UPDATE pf_staff_message_mail_order SET state = ? WHERE id = ?", state, id);
        if ("SENT".equals(state)) {
            database.update("""
                    UPDATE pf_case_review_signal SET state = 'CONSUMED'
                    WHERE source_type = 'MAIL' AND source_ref = ? AND state = 'PENDING'
                    """, id);
        }
    }

    public List<String> reviewReasons(String caseId) {
        return database.query("""
                SELECT DISTINCT d.state FROM pf_mail_delivery d WHERE d.case_id = ? AND d.state IN ('FAILED', 'UNCERTAIN')
                    AND NOT EXISTS (SELECT 1 FROM pf_case_review_signal s WHERE s.source_type = 'MAIL' AND s.source_ref = d.id
                        AND (s.considered_decision_id IS NOT NULL OR s.considered_wait_command_id IS NOT NULL OR s.considered_direct_command_id IS NOT NULL))
                """,
                (row, index) -> "UNCERTAIN".equals(row.getString(1))
                        ? "E-Mail-Versand klären: Übergabe an den Maildienst unklar." : "E-Mail-Versand klären: Versand nicht erfolgreich.", caseId);
    }

    public void recordProblem(Delivery delivery) {
        String caseStatus = database.queryForObject("SELECT status FROM pf_case WHERE id = ? FOR UPDATE", String.class, delivery.caseId());
        database.update("""
                INSERT INTO pf_case_review_signal (id, case_id, source_type, source_ref, reason, state, leading_instance_key)
                VALUES (?, ?, 'MAIL', ?, 'MAIL_DELIVERY_REQUIRES_REVIEW', ?,
                    (SELECT process_instance_key FROM pf_case_process WHERE case_id = ?)) ON CONFLICT DO NOTHING
                """, UUID.randomUUID(), delivery.caseId(), delivery.id(), "CLOSED".equals(caseStatus) ? "NOTE" : "PENDING", delivery.caseId());
    }

    public void lockCaseForDelivery(UUID id) {
        database.queryForObject("""
                SELECT c.id FROM pf_case c JOIN pf_mail_delivery d ON d.case_id = c.id WHERE d.id = ? FOR UPDATE OF c
                """, String.class, id);
    }

    public record MailStart(UUID id, long definitionKey, String tenantId, UUID leaseId, int attempts) {
    }

    public record Delivery(UUID id, String caseId, String type, String state, int attempts, UUID attemptId, Instant attemptStartedAt,
            Long definitionKey, String tenantId, Long instanceKey) {
    }

    public record Recipient(String email, UUID accessId, String encryptedToken) {
        @Override
        public String toString() {
            return "Mail recipient [redacted]";
        }
    }
}
