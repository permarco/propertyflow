package com.pertegato.propertyflow.caseprocessing.persistence;

import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.CaseState;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffDecisionStore.Decision;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("propertyflow-backend & !frontend-demo")
@RequiredArgsConstructor
public class CaseWaitStore {

    private final JdbcTemplate database;

    public String scopeKey(long child) {
        return database.queryForObject("SELECT id::text FROM pf_case_wait WHERE child_instance_key = ? AND (staff_decision_id IS NOT NULL OR direct_command_id IS NOT NULL)",
                String.class, child);
    }

    public Optional<Wait> active(String caseId) {
        return database.query("SELECT id FROM pf_case_wait WHERE case_id = ? AND state = 'ACTIVE'",
                (row, index) -> row.getObject("id", UUID.class), caseId).stream().findFirst().flatMap(this::get);
    }

    public Optional<Wait> get(UUID id) {
        return database.query("SELECT * FROM pf_case_wait WHERE id = ?", (row, index) -> new Wait(row.getObject("id", UUID.class),
                row.getString("case_id"), row.getLong("leading_instance_key"), row.getLong("staff_instance_key"),
                row.getObject("child_instance_key", Long.class), row.getObject("parent_element_key", Long.class),
                row.getString("kind"), row.getTimestamp("due_at") == null ? null : row.getTimestamp("due_at").toInstant(),
                row.getString("state")), id).stream().findFirst();
    }

    public Optional<UUID> contact(UUID messageId) {
        return database.query("SELECT wait_id FROM pf_wait_contact WHERE message_id = ?",
                (row, index) -> row.getObject("wait_id", UUID.class), messageId).stream().findFirst();
    }

    public Optional<Contact> claimContact() {
        return database.query("""
                WITH candidate AS (
                    SELECT c.message_id, w.id AS wait_id, w.staff_instance_key, w.leading_instance_key, p.tenant_id
                    FROM pf_wait_contact c JOIN pf_case_wait w ON w.id = c.wait_id
                    JOIN pf_case k ON k.id = w.case_id JOIN pf_case_process p ON p.case_id = w.case_id
                    WHERE w.staff_instance_key IS NOT NULL AND NOT c.delivered AND c.next_attempt_at <= CURRENT_TIMESTAMP AND w.state = 'ACTIVE'
                        AND k.status = 'WAITING_FOR_FOLLOW_UP' AND p.process_instance_key = w.leading_instance_key
                        AND NOT EXISTS (SELECT 1 FROM pf_follow_up_command f WHERE f.case_id = w.case_id AND f.state = 'APPLYING')
                    ORDER BY c.next_attempt_at LIMIT 1 FOR UPDATE OF c SKIP LOCKED
                )
                UPDATE pf_wait_contact c SET next_attempt_at = CURRENT_TIMESTAMP + INTERVAL '10 seconds'
                FROM candidate q WHERE c.message_id = q.message_id
                RETURNING c.message_id, q.wait_id, q.staff_instance_key, q.leading_instance_key, q.tenant_id
                """, (row, index) -> new Contact(row.getObject("message_id", UUID.class), row.getObject("wait_id", UUID.class),
                        row.getLong("staff_instance_key"), row.getLong("leading_instance_key"), row.getString("tenant_id"))).stream().findFirst();
    }

    public void delivered(UUID messageId) {
        database.update("UPDATE pf_wait_contact SET delivered = TRUE WHERE message_id = ?", messageId);
    }

    public Instant requestedDate(UUID decisionId) {
        return database.queryForObject("SELECT due_at FROM pf_staff_wait_request WHERE decision_id = ?",
                (row, index) -> row.getTimestamp("due_at").toInstant(), decisionId);
    }

    public Wait begin(CaseState state, Decision decision, long leader, long staff, Long child, Long parent, Instant dueAt) {
        String kind = dueAt == null ? "EXTERNAL" : "FOLLOW_UP";
        String nextStatus = dueAt == null ? "WAITING_FOR_EXTERNAL" : "WAITING_FOR_FOLLOW_UP";
        database.update("UPDATE pf_case_wait SET state = 'REPLACED', ended_at = CURRENT_TIMESTAMP WHERE case_id = ? AND state = 'ACTIVE'", state.id());
        database.update("""
                INSERT INTO pf_case_wait (id, case_id, leading_instance_key, staff_instance_key, child_instance_key, parent_element_key, kind, due_at, staff_decision_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, decision.id(), state.id(), leader, staff, child, parent, kind, dueAt == null ? null : Timestamp.from(dueAt), decision.id());
        database.update("UPDATE pf_case SET follow_up_due_at = ? WHERE id = ?", dueAt == null ? null : Timestamp.from(dueAt), state.id());
        transition(state, decision.id(), null, nextStatus);
        database.update("UPDATE pf_staff_decision SET state = 'APPLIED', completed_at = CURRENT_TIMESTAMP WHERE id = ?", decision.id());
        // A conscious wait accounts for the reviewed reasons, while keeping their historical records.
        database.update("""
                UPDATE pf_case_review_signal SET state = 'CONSUMED', considered_decision_id = ?
                WHERE case_id = ? AND leading_instance_key = ? AND considered_decision_id IS NULL AND considered_wait_command_id IS NULL
                    AND considered_direct_command_id IS NULL
                """, decision.id(), state.id(), leader);
        return get(decision.id()).orElseThrow();
    }

    public void resolve(CaseState state, Wait wait, String cause, String nextStatus) {
        database.update("UPDATE pf_case_wait SET state = ?, ended_at = CURRENT_TIMESTAMP WHERE id = ? AND state = 'ACTIVE'", cause, wait.id());
        database.update("UPDATE pf_case SET follow_up_due_at = NULL WHERE id = ?", state.id());
        database.update("""
                UPDATE pf_follow_up_command SET state = 'REJECTED', internal_reason = NULL
                WHERE wait_id = ? AND (state = 'PENDING' OR (state = 'APPLYING' AND action = 'CLOSE'))
                """, wait.id());
        database.update("INSERT INTO pf_wait_resolution (id, case_id, cause) VALUES (?, ?, ?) ON CONFLICT DO NOTHING", wait.id(), state.id(), cause);
        transition(state, null, wait.id(), nextStatus);
    }

    private void transition(CaseState state, UUID decision, UUID resolution, String nextStatus) {
        UUID event = UUID.randomUUID();
        database.update("UPDATE pf_case SET status = ?, version = version + 1 WHERE id = ?", nextStatus, state.id());
        database.update("""
                INSERT INTO pf_case_status_event (id, case_id, decision_id, wait_resolution_id, from_status, to_status, case_version)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, event, state.id(), decision, resolution, state.status(), nextStatus, state.version() + 1);
        database.update("""
                INSERT INTO pf_case_mail_order (id, case_id, event_id, access_id, notification_type)
                SELECT ?, case_id, ?, access_id, 'STATUS_CHANGED' FROM pf_acceptance_mail_order WHERE case_id = ?
                """, UUID.randomUUID(), event, state.id());
    }

    public record Wait(UUID id, String caseId, long leader, long staff, Long child, Long parent, String kind, Instant dueAt, String state) {
    }

    public record Contact(UUID messageId, UUID waitId, long staff, long leader, String tenant) {
    }
}
