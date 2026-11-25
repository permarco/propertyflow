package com.pertegato.propertyflow.caseprocessing.persistence;

import com.pertegato.propertyflow.caseprocessing.contract.CaseActionException;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseDataStore.Command;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.CaseState;
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
public class AnswerRequirementStore {

    private final JdbcTemplate database;

    public void prepare(Command command, CaseState state) {
        if (Boolean.TRUE.equals(database.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM pf_reply_call WHERE case_id = ? AND leading_instance_key = ? AND state = 'CLOSING')
                """, Boolean.class, state.id(), command.leader()))) {
            throw new CaseActionException("CASE_CHANGED");
        }
        if (!open(command.caseId(), UUID.fromString(command.text()))) {
            throw new CaseActionException("CASE_CHANGED");
        }
        UUID call = currentReply(command.caseId(), command.leader()).orElse(null);
        if ("WAITING_FOR_TENANT".equals(state.status()) && call == null && !Boolean.TRUE.equals(database.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM pf_automatic_entry WHERE case_id = ? AND leading_instance_key = ?
                    AND current_entry AND (entry_kind = 'QUESTION' OR EXISTS (SELECT 1 FROM pf_clarification_question q
                        WHERE q.automatic_call_ref = pf_automatic_entry.call_ref)))
                """, Boolean.class, state.id(), command.leader()))) {
            throw new CaseActionException("CASE_CHANGED");
        }
        database.update("""
                INSERT INTO pf_answer_clear_intent (command_id, reply_call_id, automatic_call_ref)
                SELECT ?, ?, call_ref FROM pf_automatic_entry WHERE case_id = ? AND leading_instance_key = ? AND current_entry
                """, command.id(), call, command.caseId(), command.leader());
    }

    private boolean open(String caseId, UUID message) {
        return Boolean.TRUE.equals(database.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM pf_staff_answer_requirement WHERE case_id = ? AND message_id = ? AND state = 'OPEN')
                """, Boolean.class, caseId, message));
    }

    private Optional<UUID> currentReply(String caseId, long leader) {
        return database.query("""
                SELECT id FROM pf_reply_call WHERE case_id = ? AND leading_instance_key = ? AND state = 'WAITING'
                """, (row, index) -> row.getObject("id", UUID.class), caseId, leader).stream().findFirst();
    }

    public boolean apply(Command command, CaseState state) {
        UUID message = UUID.fromString(command.text());
        UUID expectedCall = database.queryForObject("SELECT reply_call_id FROM pf_answer_clear_intent WHERE command_id = ?",
                UUID.class, command.id());
        if (!open(state.id(), message) || !java.util.Objects.equals(expectedCall, currentReply(state.id(), command.leader()).orElse(null))) {
            return false;
        }
        database.update("""
                UPDATE pf_staff_answer_requirement SET state = 'CANCELLED', resolution_type = 'MANUAL', resolved_at = CURRENT_TIMESTAMP
                WHERE message_id = ? AND state = 'OPEN'
                """, message);
        database.update("UPDATE pf_case SET version = version + 1, analysis_version = analysis_version + 1 WHERE id = ?", state.id());
        boolean remaining = Boolean.TRUE.equals(database.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pf_staff_answer_requirement WHERE case_id = ? AND state = 'OPEN')", Boolean.class, state.id()));
        boolean continueFlow = !remaining && ("WAITING_FOR_TENANT".equals(state.status()) || "UNDER_CLARIFICATION".equals(state.status()));
        UUID continuation = null;
        String target = null;
        if (continueFlow) {
            boolean review = Boolean.TRUE.equals(database.queryForObject("""
                    SELECT urgency = 'CRITICAL' OR EXISTS (SELECT 1 FROM pf_case_review_signal WHERE case_id = ?
                        AND (leading_instance_key = ? OR leading_instance_key IS NULL) AND state <> 'NOTE'
                        AND considered_decision_id IS NULL AND considered_wait_command_id IS NULL AND considered_direct_command_id IS NULL)
                    FROM pf_case WHERE id = ?
                    """, Boolean.class, state.id(), command.leader(), state.id()));
            target = review ? "STAFF_REVIEW_REQUIRED" : "IN_PROGRESS";
            continuation = UUID.randomUUID();
            database.update("UPDATE pf_staff_continuation SET superseded = TRUE WHERE case_id = ? AND NOT superseded", state.id());
            database.update("""
                    INSERT INTO pf_staff_continuation (id, case_id, leading_instance_key, automatic_call_ref, case_version,
                        origin, entry_kind, review_applied)
                    SELECT ?, ?, ?, automatic_call_ref, ?, 'ANSWER_CLEARED', ?, TRUE FROM pf_answer_clear_intent WHERE command_id = ?
                    """, continuation, state.id(), command.leader(), state.version() + 1, review ? "REVIEW" : "CONTINUE", command.id());
            database.update("""
                    UPDATE pf_case_review_signal SET state = 'CONFIRMED', continuation_id = ?, leading_instance_key = ?
                    WHERE case_id = ? AND (leading_instance_key = ? OR leading_instance_key IS NULL) AND state <> 'NOTE'
                        AND considered_decision_id IS NULL AND considered_wait_command_id IS NULL AND considered_direct_command_id IS NULL
                    """, continuation, command.leader(), state.id(), command.leader());
            database.update("UPDATE pf_case SET status = ? WHERE id = ?", target, state.id());
            database.update("UPDATE pf_automatic_entry SET effects_blocked = TRUE WHERE case_id = ? AND leading_instance_key = ?",
                    state.id(), command.leader());
            if (expectedCall != null) {
                database.update("UPDATE pf_reply_call SET state = 'INTERRUPTED', needs_review_delivery = FALSE WHERE id = ?", expectedCall);
            }
        }
        database.update("""
                INSERT INTO pf_answer_cancellation (id, case_id, message_id, actor_id, leading_instance_key, continuation_id, target_status)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, command.id(), state.id(), message, command.actor(), command.leader(), continuation, target);
        if (target != null) {
            UUID event = UUID.randomUUID();
            database.update("""
                    INSERT INTO pf_case_status_event (id, case_id, answer_cancellation_id, from_status, to_status, case_version)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, event, state.id(), command.id(), state.status(), target, state.version() + 1);
            database.update("""
                    INSERT INTO pf_case_mail_order (id, case_id, event_id, access_id, notification_type)
                    SELECT ?, case_id, ?, access_id, 'STATUS_CHANGED' FROM pf_acceptance_mail_order WHERE case_id = ?
                    """, UUID.randomUUID(), event, state.id());
        }
        return true;
    }

    public Optional<Delivery> claim() {
        List<UUID> ids = database.query("""
                WITH candidate AS (
                    SELECT a.id FROM pf_answer_cancellation a JOIN pf_case_process p ON p.case_id = a.case_id
                    JOIN pf_staff_continuation s ON s.id = a.continuation_id JOIN pf_case c ON c.id = a.case_id
                    WHERE NOT a.delivered AND NOT s.superseded AND p.process_instance_key = a.leading_instance_key
                        AND c.status <> 'CLOSED' AND a.next_attempt_at <= CURRENT_TIMESTAMP
                    ORDER BY a.occurred_at LIMIT 1 FOR UPDATE OF a SKIP LOCKED
                ) UPDATE pf_answer_cancellation a SET next_attempt_at = CURRENT_TIMESTAMP + INTERVAL '5 seconds'
                FROM candidate c WHERE a.id = c.id RETURNING a.id
                """, (row, index) -> row.getObject("id", UUID.class));
        return ids.stream().findFirst().map(this::delivery);
    }

    public Delivery delivery(UUID id) {
        return database.queryForObject("""
                SELECT a.*, i.reply_call_id, i.automatic_call_ref, p.tenant_id
                FROM pf_answer_cancellation a JOIN pf_answer_clear_intent i ON i.command_id = a.id
                JOIN pf_case_process p ON p.case_id = a.case_id WHERE a.id = ?
                """, (row, index) -> new Delivery(id, row.getString("case_id"), row.getLong("leading_instance_key"), row.getString("tenant_id"),
                        row.getObject("reply_call_id", UUID.class), row.getObject("automatic_call_ref", UUID.class),
                        row.getObject("continuation_id", UUID.class)), id);
    }

    public void delivered(UUID id) {
        database.update("UPDATE pf_answer_cancellation SET delivered = TRUE WHERE id = ?", id);
    }

    public boolean current(Delivery delivery) {
        return Boolean.TRUE.equals(database.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM pf_answer_cancellation a JOIN pf_staff_continuation s ON s.id = a.continuation_id
                    JOIN pf_case c ON c.id = a.case_id JOIN pf_case_process p ON p.case_id = a.case_id
                    WHERE a.id = ? AND NOT s.superseded AND c.status = a.target_status AND p.process_instance_key = a.leading_instance_key)
                """, Boolean.class, delivery.id()));
    }

    public Optional<UUID> automaticReplyEntry(Delivery delivery) {
        return database.query("""
                SELECT r.id FROM pf_reply_entry r JOIN pf_automatic_entry a ON a.child_instance_key = coalesce(r.source_automatic_instance_key, r.source_interrupted_automatic_instance_key)
                WHERE r.case_id = ? AND r.leading_instance_key = ? AND a.call_ref = ? ORDER BY r.created_at DESC, r.id DESC LIMIT 1
                """, (row, index) -> row.getObject("id", UUID.class), delivery.caseId(), delivery.leader(), delivery.automaticCall())
                .stream().findFirst();
    }

    public record Delivery(UUID id, String caseId, long leader, String tenant, UUID replyCall, UUID automaticCall, UUID continuation) {
    }
}
