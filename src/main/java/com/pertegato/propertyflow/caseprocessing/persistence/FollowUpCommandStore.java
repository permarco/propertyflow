package com.pertegato.propertyflow.caseprocessing.persistence;

import com.pertegato.propertyflow.caseprocessing.persistence.CaseWaitStore.Wait;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.CaseState;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@Profile("propertyflow-backend & !frontend-demo")
@RequiredArgsConstructor
public class FollowUpCommandStore {

    private static final RowMapper<Command> COMMAND = (row, index) -> new Command(row.getObject("id", UUID.class),
            row.getString("case_id"), row.getObject("wait_id", UUID.class), row.getObject("actor_id", UUID.class),
            row.getObject("operation_id", UUID.class), row.getLong("expected_version"), row.getString("review_fingerprint"),
            row.getString("action"), row.getString("request_input"), row.getString("internal_reason"),
            row.getTimestamp("due_at") == null ? null : row.getTimestamp("due_at").toInstant(), row.getString("state"),
            row.getObject("conflict_continuation_id", UUID.class));
    private final JdbcTemplate database;

    public Optional<Command> find(String caseId, UUID actor, UUID operation) {
        return database.query("SELECT * FROM pf_follow_up_command WHERE case_id = ? AND actor_id = ? AND operation_id = ?",
                COMMAND, caseId, actor, operation).stream().findFirst();
    }

    public Command get(UUID id) {
        return find(id).orElseThrow();
    }

    public Optional<Command> find(UUID id) {
        return database.query("SELECT * FROM pf_follow_up_command WHERE id = ?", COMMAND, id).stream().findFirst();
    }

    public void prepareClosure(UUID id) {
        database.update("UPDATE pf_follow_up_command SET state = 'APPLYING' WHERE id = ? AND state = 'PENDING' AND action = 'CLOSE'", id);
    }

    public boolean pending(String caseId) {
        return Boolean.TRUE.equals(database.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pf_follow_up_command WHERE case_id = ? AND state IN ('PENDING', 'APPLYING'))",
                Boolean.class, caseId));
    }

    public boolean interruptPending(Wait wait) {
        return Boolean.TRUE.equals(database.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM pf_wait_contact WHERE wait_id = ?)
                    OR EXISTS (SELECT 1 FROM pf_case_review_signal WHERE case_id = ? AND leading_instance_key = ?
                        AND state IN ('PENDING', 'CONFIRMED') AND considered_decision_id IS NULL AND considered_wait_command_id IS NULL
                            AND considered_direct_command_id IS NULL)
                """, Boolean.class, wait.id(), wait.caseId(), wait.leader()));
    }

    public Command prepare(CaseState state, Wait wait, UUID actor, UUID operation, String fingerprint,
            String action, String input, String reason, Instant due) {
        UUID id = UUID.randomUUID();
        database.update("""
                INSERT INTO pf_follow_up_command (id, case_id, wait_id, actor_id, operation_id, expected_version,
                    review_fingerprint, action, request_input, internal_reason, due_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, state.id(), wait.id(), actor, operation, state.version(), fingerprint, action, input, reason,
                due == null ? null : Timestamp.from(due));
        return get(id);
    }

    public boolean attempt(UUID id) {
        return database.update("""
                UPDATE pf_follow_up_command SET correlation_attempted = TRUE
                WHERE id = ? AND state = 'PENDING' AND NOT correlation_attempted
                """, id) == 1;
    }

    public void reject(UUID id) {
        database.update("""
                UPDATE pf_follow_up_command SET state = 'REJECTED', internal_reason = NULL
                WHERE id = ? AND state = 'PENDING'
                """, id);
    }

    public void unchanged(UUID id) {
        database.update("UPDATE pf_follow_up_command SET state = 'APPLYING', internal_reason = NULL WHERE id = ?", id);
    }

    public void apply(Command command, CaseState state, Wait previous) {
        boolean delete = "DELETE".equals(command.action());
        database.update("UPDATE pf_case_wait SET state = ?, ended_at = CURRENT_TIMESTAMP WHERE id = ?",
                delete ? "CANCELLED" : "REPLACED", previous.id());
        if (!delete) {
            database.update("""
                    INSERT INTO pf_case_wait (id, case_id, leading_instance_key, staff_instance_key, child_instance_key,
                        parent_element_key, kind, due_at, change_command_id)
                    VALUES (?, ?, ?, ?, ?, ?, 'FOLLOW_UP', ?, ?)
                    """, command.id(), state.id(), previous.leader(), previous.staff(), previous.child(), previous.parent(),
                    Timestamp.from(command.due()), command.id());
        }
        String phase = delete ? "IN_PROGRESS" : "WAITING_FOR_FOLLOW_UP";
        database.update("UPDATE pf_case SET status = ?, follow_up_due_at = ?, version = version + 1 WHERE id = ?",
                phase, delete ? null : Timestamp.from(command.due()), state.id());
        UUID event = UUID.randomUUID();
        database.update("""
                INSERT INTO pf_case_status_event (id, case_id, wait_command_id, from_status, to_status, case_version)
                VALUES (?, ?, ?, ?, ?, ?)
                """, event, state.id(), command.id(), state.status(), phase, state.version() + 1);
        database.update("""
                INSERT INTO pf_case_mail_order (id, case_id, event_id, access_id, notification_type)
                SELECT ?, case_id, ?, access_id, 'STATUS_CHANGED' FROM pf_acceptance_mail_order WHERE case_id = ?
                """, UUID.randomUUID(), event, state.id());
        database.update("""
                UPDATE pf_case_review_signal SET state = 'CONSUMED', considered_wait_command_id = ?
                WHERE case_id = ? AND leading_instance_key = ? AND considered_decision_id IS NULL AND considered_wait_command_id IS NULL
                    AND considered_direct_command_id IS NULL
                """, command.id(), state.id(), previous.leader());
        // Keep delivery blocked until the new epoch is also confirmed in the staff scope.
        database.update("UPDATE pf_follow_up_command SET state = 'APPLYING', changed = TRUE, applied_at = CURRENT_TIMESTAMP WHERE id = ?", command.id());
    }

    public void applied(UUID id) {
        database.update("UPDATE pf_follow_up_command SET state = 'APPLIED' WHERE id = ? AND state = 'APPLYING'", id);
    }

    public record Command(UUID id, String caseId, UUID waitId, UUID actor, UUID operation, long version,
            String fingerprint, String action, String input, String reason, Instant due, String state, UUID conflictContinuation) {
        @Override
        public String toString() {
            return "Follow-up command " + id + " [internal content redacted]";
        }
    }
}
