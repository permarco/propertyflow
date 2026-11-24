package com.pertegato.propertyflow.caseprocessing.persistence;

import com.pertegato.propertyflow.caseprocessing.contract.CaseActionException;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseDataStore.Command;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.CaseState;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffReviewStore.Continuation;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("propertyflow-backend & !frontend-demo")
@RequiredArgsConstructor
public class DirectFollowUpStore {

    private final JdbcTemplate database;
    private final CaseWaitStore waits;
    private final StaffTakeoverStore takeovers;

    public boolean available(CaseState state, long leader) {
        return Boolean.TRUE.equals(database.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM pf_automatic_entry a WHERE a.case_id = ? AND a.leading_instance_key = ?
                    AND a.current_entry AND a.child_instance_key IS NOT NULL
                    AND (? <> 'UNDER_CLARIFICATION' OR NOT EXISTS (SELECT 1 FROM pf_staff_continuation s WHERE s.automatic_call_ref = a.call_ref
                        AND NOT s.superseded AND s.child_instance_key IS NOT NULL)))
                    AND (? = 'UNDER_CLARIFICATION' OR (? = 'WAITING_FOR_TENANT'
                        AND EXISTS (SELECT 1 FROM pf_reply_call WHERE case_id = ? AND leading_instance_key = ? AND state = 'WAITING')))
                """, Boolean.class, state.id(), leader, state.status(), state.status(), state.status(), state.id(), leader));
    }

    public void prepare(Command command, CaseState state, Instant due, String fingerprint) {
        if (!available(state, command.leader())) {
            throw new CaseActionException("CASE_CHANGED");
        }
        database.update("""
                INSERT INTO pf_direct_follow_up (id, automatic_call_ref, reply_call_id, due_at, review_fingerprint)
                SELECT ?, call_ref, (SELECT id FROM pf_reply_call WHERE case_id = ? AND leading_instance_key = ? AND state = 'WAITING'), ?, ?
                FROM pf_automatic_entry WHERE case_id = ? AND leading_instance_key = ? AND current_entry
                """, command.id(), state.id(), command.leader(), Timestamp.from(due), fingerprint, state.id(), command.leader());
    }

    public boolean apply(Command command, CaseState state, String fingerprint) {
        Intent intent = get(command.id());
        if (!available(state, command.leader()) || !intent.fingerprint().equals(fingerprint)
                || !Boolean.TRUE.equals(database.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM pf_automatic_entry WHERE call_ref = ? AND current_entry)
                        AND (?::uuid IS NULL OR EXISTS (SELECT 1 FROM pf_reply_call WHERE id = ? AND state = 'WAITING'))
                    """, Boolean.class, intent.call(), intent.reply(), intent.reply()))) {
            return false;
        }
        List<UUID> existing = database.query("""
                SELECT id FROM pf_staff_continuation WHERE automatic_call_ref = ? AND NOT superseded AND child_instance_key IS NULL
                """, (row, index) -> row.getObject(1, UUID.class), intent.call());
        UUID continuation = existing.isEmpty() ? UUID.randomUUID() : existing.getFirst();
        database.update("UPDATE pf_staff_continuation SET superseded = TRUE WHERE case_id = ? AND id <> ? AND NOT superseded",
                state.id(), continuation);
        database.update("""
                INSERT INTO pf_staff_continuation (id, case_id, leading_instance_key, automatic_call_ref, case_version, origin, entry_kind, review_applied)
                VALUES (?, ?, ?, ?, ?, 'DIRECT_FOLLOW_UP', 'FOLLOW_UP', TRUE)
                ON CONFLICT (id) DO UPDATE SET origin = 'DIRECT_FOLLOW_UP', entry_kind = 'FOLLOW_UP', review_applied = TRUE
                """, continuation, state.id(), command.leader(), intent.call(), state.version() + 1);
        database.update("UPDATE pf_direct_follow_up SET continuation_id = ? WHERE id = ?", continuation, command.id());
        database.update("UPDATE pf_automatic_entry SET effects_blocked = TRUE WHERE call_ref = ?", intent.call());
        database.update("UPDATE pf_reply_call SET state = 'INTERRUPTED', needs_review_delivery = FALSE WHERE id = ?", intent.reply());
        database.update("""
                INSERT INTO pf_case_wait (id, case_id, leading_instance_key, kind, due_at, direct_command_id)
                VALUES (?, ?, ?, 'FOLLOW_UP', ?, ?)
                """, command.id(), state.id(), command.leader(), Timestamp.from(intent.due()), command.id());
        database.update("""
                UPDATE pf_case_review_signal SET state = 'CONSUMED', leading_instance_key = ?, considered_direct_command_id = ?
                WHERE case_id = ? AND (leading_instance_key = ? OR leading_instance_key IS NULL)
                    AND considered_decision_id IS NULL AND considered_wait_command_id IS NULL AND considered_direct_command_id IS NULL
                """, command.leader(), command.id(), state.id(), command.leader());
        database.update("UPDATE pf_case SET status = 'WAITING_FOR_FOLLOW_UP', follow_up_due_at = ?, version = version + 1 WHERE id = ?",
                Timestamp.from(intent.due()), state.id());
        UUID event = UUID.randomUUID();
        database.update("""
                INSERT INTO pf_case_status_event (id, case_id, direct_command_id, from_status, to_status, case_version)
                VALUES (?, ?, ?, ?, 'WAITING_FOR_FOLLOW_UP', ?)
                """, event, state.id(), command.id(), state.status(), state.version() + 1);
        database.update("""
                INSERT INTO pf_case_mail_order (id, case_id, event_id, access_id, notification_type)
                SELECT ?, case_id, ?, access_id, 'STATUS_CHANGED' FROM pf_acceptance_mail_order WHERE case_id = ?
                """, UUID.randomUUID(), event, state.id());
        return true;
    }

    public Optional<UUID> forContinuation(UUID continuation) {
        return database.query("SELECT id FROM pf_direct_follow_up WHERE continuation_id = ?",
                (row, index) -> row.getObject(1, UUID.class), continuation).stream().findFirst();
    }

    public boolean direct(UUID id) {
        return Boolean.TRUE.equals(database.queryForObject("SELECT EXISTS (SELECT 1 FROM pf_direct_follow_up WHERE id = ?)", Boolean.class, id));
    }

    public void register(CaseState state, Continuation continuation, long staff) {
        UUID id = forContinuation(continuation.id()).orElse(null);
        if (id == null) {
            return;
        }
        database.update("UPDATE pf_case_wait SET staff_instance_key = ? WHERE direct_command_id = ? AND staff_instance_key IS NULL", staff, id);
        CaseWaitStore.Wait wait = waits.get(id).orElseThrow();
        if (!"ACTIVE".equals(wait.state())) {
            return;
        }
        boolean contact = Boolean.TRUE.equals(database.queryForObject("SELECT EXISTS (SELECT 1 FROM pf_wait_contact WHERE wait_id = ?)",
                Boolean.class, id));
        boolean review = Boolean.TRUE.equals(database.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM pf_case_review_signal WHERE case_id = ? AND (leading_instance_key = ? OR leading_instance_key IS NULL)
                    AND state <> 'NOTE' AND considered_decision_id IS NULL AND considered_wait_command_id IS NULL
                        AND considered_direct_command_id IS NULL)
                """, Boolean.class, state.id(), continuation.leader()));
        if (contact || review) {
            waits.resolve(state, wait, contact ? "CONTACT" : "REVIEW", "STAFF_REVIEW_REQUIRED");
            takeovers.confirm(state.id(), continuation.leader(), continuation.id());
            database.update("UPDATE pf_staff_continuation SET entry_kind = 'REVIEW' WHERE id = ?", continuation.id());
        }
    }

    public CaseWaitStore.Wait bind(UUID id, String caseId, long leader, long staff, long child, long parent) {
        CaseWaitStore.Wait wait = waits.get(id).orElseThrow();
        if (!wait.caseId().equals(caseId) || wait.leader() != leader || wait.staff() != staff
                || wait.child() != null && (wait.child() != child || wait.parent() != parent)) {
            throw new IllegalArgumentException("INVALID_DIRECT_WAIT_CONTEXT");
        }
        database.update("UPDATE pf_case_wait SET child_instance_key = ?, parent_element_key = ? WHERE id = ? AND child_instance_key IS NULL",
                child, parent, id);
        return waits.get(id).orElseThrow();
    }

    public Intent get(UUID id) {
        return database.queryForObject("SELECT * FROM pf_direct_follow_up WHERE id = ?",
                (row, index) -> new Intent(id, row.getObject("automatic_call_ref", UUID.class), row.getObject("reply_call_id", UUID.class),
                        row.getTimestamp("due_at").toInstant(), row.getString("review_fingerprint"), row.getObject("continuation_id", UUID.class)), id);
    }

    public Optional<UUID> claim() {
        return database.query("""
                WITH candidate AS (SELECT d.id FROM pf_direct_follow_up d JOIN pf_case_data_command c ON c.id = d.id
                    JOIN pf_case_process p ON p.case_id = c.case_id JOIN pf_case k ON k.id = c.case_id
                    WHERE c.state = 'APPLIED' AND p.process_instance_key = c.leading_instance_key AND k.status <> 'CLOSED'
                        AND NOT d.delivered AND d.next_attempt_at <= CURRENT_TIMESTAMP
                    ORDER BY d.next_attempt_at LIMIT 1 FOR UPDATE OF d SKIP LOCKED)
                UPDATE pf_direct_follow_up d SET next_attempt_at = CURRENT_TIMESTAMP + INTERVAL '5 seconds'
                    FROM candidate q WHERE d.id = q.id RETURNING d.id
                """, (row, index) -> row.getObject(1, UUID.class)).stream().findFirst();
    }

    public void delivered(UUID id) {
        database.update("UPDATE pf_direct_follow_up SET delivered = TRUE WHERE id = ?", id);
    }

    public String previousReplyEntry(UUID call) {
        return database.query("""
                SELECT r.id::text FROM pf_reply_entry r JOIN pf_automatic_entry a ON a.child_instance_key = r.source_automatic_instance_key
                WHERE a.call_ref = ? ORDER BY r.created_at DESC LIMIT 1
                """, (row, index) -> row.getString(1), call).stream().findFirst().orElse("");
    }

    public record Intent(UUID id, UUID call, UUID reply, Instant due, String fingerprint, UUID continuation) {
    }
}
