package com.pertegato.propertyflow.caseprocessing.persistence;

import com.pertegato.propertyflow.caseprocessing.contract.StaffDecisionAction;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.CaseState;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@Profile("propertyflow-backend & !frontend-demo")
public class StaffDecisionStore {

    private static final RowMapper<Decision> DECISION = (row, index) -> new Decision(row.getObject("id", UUID.class),
            row.getObject("operation_id", UUID.class), row.getString("case_id"), row.getObject("continuation_id", UUID.class),
            row.getObject("actor_id", UUID.class), row.getLong("user_task_key"), row.getLong("expected_version"),
            row.getString("review_fingerprint"), row.getString("state"), StaffDecisionAction.valueOf(row.getString("action")),
            row.getString("task_element"), row.getString("internal_reason"), row.getObject("conflict_continuation_id", UUID.class));
    private final JdbcTemplate database;

    public StaffDecisionStore(JdbcTemplate database) {
        this.database = database;
    }

    public String reviewFingerprint(String caseId, long leader) {
        return database.queryForObject("""
                SELECT md5(coalesce(string_agg(id::text, ',' ORDER BY id), '')) FROM pf_case_review_signal
                WHERE case_id = ? AND (leading_instance_key = ? OR leading_instance_key IS NULL)
                """, String.class, caseId, leader);
    }

    public Optional<Decision> find(String caseId, UUID actor, UUID operation) {
        return database.query("SELECT * FROM pf_staff_decision WHERE case_id = ? AND actor_id = ? AND operation_id = ?",
                DECISION, caseId, actor, operation).stream().findFirst();
    }

    public Decision get(UUID id) {
        return database.query("SELECT * FROM pf_staff_decision WHERE id = ?", DECISION, id).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("UNKNOWN_STAFF_DECISION"));
    }

    public Decision prepare(String caseId, UUID continuation, UUID actor, UUID operation, long taskKey, long version, String reviewToken,
            StaffDecisionAction action, String element, String reason) {
        UUID id = UUID.randomUUID();
        database.update("""
                INSERT INTO pf_staff_decision (id, operation_id, case_id, continuation_id, actor_id, user_task_key,
                    expected_version, review_fingerprint, action, task_element, internal_reason) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, operation, caseId, continuation, actor, taskKey, version, reviewToken, action.name(), element, reason);
        return get(id);
    }

    public void questionApplied(UUID id) {
        database.update("UPDATE pf_staff_decision SET state = 'APPLIED', completed_at = CURRENT_TIMESTAMP WHERE id = ?", id);
    }

    public void confirmed(UUID id) {
        database.update("UPDATE pf_staff_decision SET state = 'CONFIRMED' WHERE id = ? AND state = 'PREPARED'", id);
    }

    public void followUpRequest(UUID decisionId, Instant dueAt) {
        database.update("INSERT INTO pf_staff_wait_request (decision_id, due_at) VALUES (?, ?)", decisionId, Timestamp.from(dueAt));
    }

    public void reject(UUID id, String reason) {
        database.update("""
                UPDATE pf_staff_decision SET state = 'REJECTED', result_code = ?, internal_reason = NULL,
                    completed_at = CURRENT_TIMESTAMP WHERE id = ? AND state <> 'APPLIED'
                """, reason, id);
    }

    public boolean followUpResume(UUID command, String caseId, long leader, long child) {
        return Boolean.TRUE.equals(database.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM pf_follow_up_command f JOIN pf_case_wait w ON w.id = f.wait_id
                    WHERE f.id = ? AND f.case_id = ? AND w.leading_instance_key = ? AND w.staff_instance_key = ?
                        AND f.action = 'DELETE' AND f.state = 'APPLIED')
                """, Boolean.class, command, caseId, leader, child));
    }

    public void progress(CaseState state, UUID decisionId) {
        UUID event = UUID.randomUUID();
        database.update("UPDATE pf_case_wait SET state = 'CANCELLED', ended_at = CURRENT_TIMESTAMP WHERE case_id = ? AND state = 'ACTIVE'", state.id());
        database.update("UPDATE pf_case SET status = 'IN_PROGRESS', version = version + 1 WHERE id = ?", state.id());
        database.update("""
                INSERT INTO pf_case_status_event (id, case_id, decision_id, from_status, to_status, case_version)
                VALUES (?, ?, ?, ?, 'IN_PROGRESS', ?)
                """, event, state.id(), decisionId, state.status(), state.version() + 1);
        database.update("""
                INSERT INTO pf_case_mail_order (id, case_id, event_id, access_id, notification_type)
                SELECT ?, case_id, ?, access_id, 'STATUS_CHANGED' FROM pf_acceptance_mail_order WHERE case_id = ?
                """, UUID.randomUUID(), event, state.id());
        database.update("UPDATE pf_staff_decision SET state = 'APPLIED', completed_at = CURRENT_TIMESTAMP WHERE id = ?", decisionId);
        Decision decision = get(decisionId);
        McpTransitionAudit.applied(database, state.id(), decision.actor(), decision.operation());
        database.update("""
                UPDATE pf_case_review_signal SET state = 'CONSUMED', considered_decision_id = ?
                WHERE case_id = ? AND considered_decision_id IS NULL AND considered_wait_command_id IS NULL AND considered_direct_command_id IS NULL
                    AND leading_instance_key = (SELECT process_instance_key FROM pf_case_process WHERE case_id = ?)
                """, decisionId, state.id(), state.id());
    }

    public record Decision(UUID id, UUID operation, String caseId, UUID continuation, UUID actor, long taskKey,
            long version, String reviewToken, String state, StaffDecisionAction action, String element, String reason, UUID conflictContinuation) {
        @Override
        public String toString() {
            return "Staff decision " + id + " [reason redacted]";
        }
    }
}
