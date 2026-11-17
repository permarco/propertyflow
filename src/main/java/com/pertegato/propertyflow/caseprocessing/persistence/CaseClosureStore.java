package com.pertegato.propertyflow.caseprocessing.persistence;

import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.CaseState;
import com.pertegato.propertyflow.caseprocessing.persistence.FollowUpCommandStore.Command;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffDecisionStore.Decision;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffReviewStore.Continuation;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("propertyflow-backend & !frontend-demo")
public class CaseClosureStore {

    public static final String STAFF_CLOSURE_REASON = "Vom Mitarbeiter geprüft: keine weitere Bearbeitung erforderlich.";
    private final JdbcTemplate database;

    public CaseClosureStore(JdbcTemplate database) {
        this.database = database;
    }

    public boolean applied(UUID decision) {
        return Boolean.TRUE.equals(database.queryForObject("SELECT EXISTS (SELECT 1 FROM pf_case_closure WHERE id = ?)", Boolean.class, decision));
    }

    public void close(CaseState state, Decision decision, long leader) {
        close(state, decision.id(), decision.actor(), leader, decision.id(), null, null);
        database.update("UPDATE pf_staff_decision SET state = 'APPLIED', completed_at = CURRENT_TIMESTAMP WHERE id = ?", decision.id());
    }

    public void close(CaseState state, Command command, long leader) {
        close(state, command.id(), command.actor(), leader, null, command.id(), null);
        database.update("""
                UPDATE pf_follow_up_command SET state = 'APPLIED', changed = TRUE, applied_at = CURRENT_TIMESTAMP WHERE id = ?
                """, command.id());
    }

    public void close(CaseState state, CaseDataStore.Command command) {
        close(state, command.id(), command.actor(), command.leader(), null, null, command.id());
    }

    private void close(CaseState state, UUID id, UUID actor, long leader, UUID decision, UUID waitCommand, UUID dataCommand) {
        database.update("""
                INSERT INTO pf_case_closure (id, case_id, leading_instance_key, actor_id, source, reason,
                    staff_decision_id, follow_up_command_id, case_data_command_id)
                VALUES (?, ?, ?, ?, 'STAFF', ?, ?, ?, ?)
                """, id, state.id(), leader, actor, STAFF_CLOSURE_REASON, decision, waitCommand, dataCommand);
        complete(state, decision, waitCommand, dataCommand, null);
    }

    public void closeAutomatically(CaseState state, AutomaticClosureStore.Command command) {
        database.update("""
                INSERT INTO pf_case_closure (id, case_id, leading_instance_key, source, reason, automatic_closure_id)
                VALUES (?, ?, ?, 'SYSTEM', ?, ?)
                """, command.id(), state.id(), command.leader(), command.ground().outcome().name(), command.id());
        complete(state, null, null, null, command.id());
        database.update("UPDATE pf_automatic_closure SET state = 'CLOSED' WHERE id = ?", command.id());
    }

    private void complete(CaseState state, UUID decision, UUID waitCommand, UUID dataCommand, UUID automatic) {
        database.update("UPDATE pf_case SET status = 'CLOSED', version = version + 1 WHERE id = ?", state.id());
        database.update("UPDATE pf_case SET follow_up_due_at = NULL WHERE id = ?", state.id());
        database.update("UPDATE pf_case_wait SET state = 'CLOSED', ended_at = CURRENT_TIMESTAMP WHERE case_id = ? AND state = 'ACTIVE'", state.id());
        database.update("""
                UPDATE pf_staff_answer_requirement SET state = 'CANCELLED', resolved_at = CURRENT_TIMESTAMP, resolution_type = 'CASE_CLOSED'
                WHERE case_id = ? AND state = 'OPEN'
                """, state.id());
        database.update("""
                INSERT INTO pf_case_status_event (id, case_id, decision_id, wait_command_id, closure_command_id, automatic_closure_id, from_status, to_status, case_version)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'CLOSED', ?)
                """, UUID.randomUUID(), state.id(), decision, waitCommand, dataCommand, automatic, state.status(), state.version() + 1);
        database.update("UPDATE pf_automatic_entry SET effects_blocked = TRUE WHERE case_id = ?", state.id());
        database.update("UPDATE pf_case_review_signal SET state = 'NOTE' WHERE case_id = ? AND state IN ('PENDING', 'CONFIRMED')", state.id());
    }

    public UUID conflict(CaseState state, Decision decision, Continuation previous) {
        if (decision.conflictContinuation() != null) {
            return decision.conflictContinuation();
        }
        UUID next = conflict(state, decision.id(), previous);
        database.update("""
                UPDATE pf_staff_decision SET state = 'REJECTED', result_code = 'CLOSURE_BASIS_CHANGED', internal_reason = NULL,
                    conflict_continuation_id = ?, completed_at = CURRENT_TIMESTAMP WHERE id = ?
                """, next, decision.id());
        return next;
    }

    public UUID conflict(CaseState state, Command command, Continuation previous) {
        if (command.conflictContinuation() != null) {
            return command.conflictContinuation();
        }
        UUID next = conflict(state, command.id(), previous);
        database.update("""
                UPDATE pf_follow_up_command SET state = 'REJECTED', internal_reason = NULL, conflict_continuation_id = ? WHERE id = ?
                """, next, command.id());
        // Der abgeschlossene Kindprozess besitzt keinen Timer mehr; die neue Prüfung übernimmt den aktuellen Fallstand.
        database.update("UPDATE pf_case_wait SET state = 'CANCELLED', ended_at = CURRENT_TIMESTAMP WHERE id = ? AND state = 'ACTIVE'",
                command.waitId());
        database.update("UPDATE pf_case SET follow_up_due_at = NULL WHERE id = ?", state.id());
        return next;
    }

    private UUID conflict(CaseState state, UUID reference, Continuation previous) {
        UUID next = UUID.randomUUID();
        database.update("UPDATE pf_staff_continuation SET superseded = TRUE WHERE id = ?", previous.id());
        database.update("""
                INSERT INTO pf_staff_continuation (id, case_id, leading_instance_key, automatic_call_ref, case_version, origin)
                VALUES (?, ?, ?, ?, ?, 'CLOSE_CONFLICT')
                """, next, state.id(), previous.leader(), previous.callRef(), state.version());
        database.update("""
                INSERT INTO pf_case_review_signal (id, case_id, source_type, source_ref, reason, leading_instance_key, continuation_id, state)
                VALUES (?, ?, 'STAFF_CLOSURE', ?, 'CLOSURE_BASIS_CHANGED', ?, ?, 'CONFIRMED') ON CONFLICT DO NOTHING
                """, UUID.randomUUID(), state.id(), reference, previous.leader(), next);
        return next;
    }
}
