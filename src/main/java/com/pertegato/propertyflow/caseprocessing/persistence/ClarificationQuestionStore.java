package com.pertegato.propertyflow.caseprocessing.persistence;

import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.CaseState;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffMessageStore.Command;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("propertyflow-backend & !frontend-demo")
public class ClarificationQuestionStore {

    private final JdbcTemplate database;

    public ClarificationQuestionStore(JdbcTemplate database) {
        this.database = database;
    }

    public boolean pending(String caseId, long leader) {
        return Boolean.TRUE.equals(database.queryForObject("""
                SELECT urgency = 'CRITICAL' OR EXISTS (SELECT 1 FROM pf_case_review_signal WHERE case_id = ?
                    AND (leading_instance_key = ? OR leading_instance_key IS NULL) AND state <> 'NOTE'
                    AND considered_decision_id IS NULL AND considered_wait_command_id IS NULL AND considered_direct_command_id IS NULL)
                FROM pf_case WHERE id = ?
                """, Boolean.class, caseId, leader, caseId));
    }

    public void published(Command command, CaseState state) {
        database.update("""
                INSERT INTO pf_reply_entry (id, case_id, leading_instance_key, source_interrupted_automatic_instance_key,
                    consumed_version, staff_message_id)
                SELECT ?, case_id, leading_instance_key, child_instance_key, ?, ? FROM pf_automatic_entry WHERE call_ref = ? AND current_entry
                """, command.id(), state.version() + 1, command.id(), command.automatic());
        database.update("""
                INSERT INTO pf_clarification_question (id, case_id, leading_instance_key, automatic_call_ref) VALUES (?, ?, ?, ?)
                """, command.id(), state.id(), command.leader(), command.automatic());
        database.update("UPDATE pf_automatic_entry SET effects_blocked = TRUE WHERE call_ref = ?", command.automatic());
        database.update("UPDATE pf_case SET status = 'WAITING_FOR_TENANT' WHERE id = ?", state.id());
        UUID event = UUID.randomUUID();
        database.update("""
                INSERT INTO pf_case_status_event (id, case_id, reply_entry_id, from_status, to_status, case_version)
                VALUES (?, ?, ?, ?, 'WAITING_FOR_TENANT', ?)
                """, event, state.id(), command.id(), state.status(), state.version() + 1);
        database.update("""
                INSERT INTO pf_case_mail_order (id, case_id, event_id, access_id, notification_type)
                SELECT ?, case_id, ?, access_id, 'STATUS_CHANGED' FROM pf_acceptance_mail_order WHERE case_id = ?
                """, UUID.randomUUID(), event, state.id());
    }

    public Optional<UUID> forCall(String caseId, long leader, UUID call) {
        return database.query("SELECT id FROM pf_clarification_question WHERE case_id = ? AND leading_instance_key = ? AND automatic_call_ref = ?",
                (row, index) -> row.getObject(1, UUID.class), caseId, leader, call).stream().findFirst();
    }

    public boolean published(UUID id) {
        return Boolean.TRUE.equals(database.queryForObject("SELECT EXISTS (SELECT 1 FROM pf_clarification_question WHERE id = ?)", Boolean.class, id));
    }

    public void confirm(String caseId, long leader, UUID continuation) {
        database.update("""
                UPDATE pf_case_review_signal SET state = 'CONFIRMED', leading_instance_key = ?, continuation_id = ?
                WHERE case_id = ? AND (leading_instance_key = ? OR leading_instance_key IS NULL) AND state <> 'NOTE'
                    AND considered_decision_id IS NULL AND considered_wait_command_id IS NULL AND considered_direct_command_id IS NULL
                """, leader, continuation, caseId, leader);
    }

    public Optional<Delivery> claim() {
        List<UUID> ids = database.query("""
                WITH candidate AS (
                    SELECT q.id FROM pf_clarification_question q JOIN pf_case_process p ON p.case_id = q.case_id
                    JOIN pf_automatic_entry a ON a.call_ref = q.automatic_call_ref JOIN pf_case c ON c.id = q.case_id
                    WHERE NOT q.delivered AND a.current_entry AND p.process_instance_key = q.leading_instance_key
                        AND c.status <> 'CLOSED' AND q.next_attempt_at <= CURRENT_TIMESTAMP
                    ORDER BY q.next_attempt_at LIMIT 1 FOR UPDATE OF q SKIP LOCKED
                ) UPDATE pf_clarification_question q SET next_attempt_at = CURRENT_TIMESTAMP + INTERVAL '5 seconds'
                    FROM candidate c WHERE q.id = c.id RETURNING q.id
                """, (row, index) -> row.getObject(1, UUID.class));
        return ids.stream().findFirst().flatMap(this::delivery);
    }

    public Optional<Delivery> delivery(UUID id) {
        return database.query("""
                SELECT q.*, p.tenant_id, coalesce((SELECT r.id::text FROM pf_reply_entry r
                    WHERE r.source_automatic_instance_key = a.child_instance_key ORDER BY r.created_at DESC LIMIT 1), '') AS previous_reply
                FROM pf_clarification_question q JOIN pf_case_process p ON p.case_id = q.case_id
                    JOIN pf_automatic_entry a ON a.call_ref = q.automatic_call_ref
                WHERE q.id = ? AND a.current_entry AND p.process_instance_key = q.leading_instance_key
                """, (row, index) -> new Delivery(id, row.getLong("leading_instance_key"), row.getString("tenant_id"),
                        row.getObject("automatic_call_ref", UUID.class), row.getString("previous_reply")), id).stream().findFirst();
    }

    public void delivered(UUID id) {
        database.update("UPDATE pf_clarification_question SET delivered = TRUE WHERE id = ?", id);
    }

    public record Delivery(UUID id, long leader, String tenant, UUID call, String previousReply) {
    }
}
