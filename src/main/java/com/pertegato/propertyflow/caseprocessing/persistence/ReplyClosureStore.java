package com.pertegato.propertyflow.caseprocessing.persistence;

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
@Profile("propertyflow-backend & !frontend-demo")
@RequiredArgsConstructor
public class ReplyClosureStore {

    private final JdbcTemplate database;

    public void prepare(UUID command, UUID call) {
        database.update("INSERT INTO pf_reply_closure (id, reply_call_id) VALUES (?, ?)", command, call);
    }

    public Optional<Intent> find(UUID command) {
        return database.query("SELECT * FROM pf_reply_closure WHERE id = ?", (row, index) -> new Intent(command,
                row.getObject("reply_call_id", UUID.class), row.getBoolean("accepted"),
                row.getObject("conflict_continuation_id", UUID.class)), command).stream().findFirst();
    }

    public void accept(Intent intent) {
        database.update("UPDATE pf_reply_closure SET accepted = TRUE WHERE id = ?", intent.id());
        database.update("UPDATE pf_reply_call SET state = 'CLOSING', needs_review_delivery = FALSE WHERE id = ?", intent.call());
    }

    public Optional<UUID> claim() {
        List<UUID> ids = database.query("""
                WITH candidate AS (
                    SELECT r.id FROM pf_reply_closure r JOIN pf_case_data_command d ON d.id = r.id
                        JOIN pf_case c ON c.id = d.case_id JOIN pf_case_process p ON p.case_id = c.id
                    WHERE r.accepted AND NOT r.delivered AND d.state = 'PENDING' AND c.status = 'WAITING_FOR_TENANT'
                        AND p.process_instance_key = d.leading_instance_key AND r.next_attempt_at <= CURRENT_TIMESTAMP
                    ORDER BY r.next_attempt_at LIMIT 1 FOR UPDATE OF r SKIP LOCKED
                ) UPDATE pf_reply_closure r SET next_attempt_at = CURRENT_TIMESTAMP + INTERVAL '5 seconds'
                FROM candidate c WHERE c.id = r.id RETURNING r.id
                """, (row, index) -> row.getObject(1, UUID.class));
        return ids.stream().findFirst();
    }

    public void delivered(UUID id) {
        database.update("UPDATE pf_reply_closure SET delivered = TRUE WHERE id = ?", id);
    }

    public void completed(UUID id) {
        database.update("UPDATE pf_reply_call SET state = 'RETURNED' WHERE id = (SELECT reply_call_id FROM pf_reply_closure WHERE id = ?)", id);
    }

    public UUID conflict(CaseState state, Command command) {
        UUID continuation = UUID.randomUUID();
        database.update("UPDATE pf_staff_continuation SET superseded = TRUE WHERE case_id = ? AND NOT superseded", state.id());
        database.update("""
                INSERT INTO pf_staff_continuation (id, case_id, leading_instance_key, automatic_call_ref, case_version, origin)
                SELECT ?, case_id, leading_instance_key, call_ref, ?, 'CLOSE_CONFLICT' FROM pf_automatic_entry
                WHERE case_id = ? AND leading_instance_key = ? AND current_entry
                """, continuation, state.version(), state.id(), command.leader());
        database.update("""
                INSERT INTO pf_case_review_signal (id, case_id, source_type, source_ref, reason, leading_instance_key, continuation_id, state)
                VALUES (?, ?, 'STAFF_CLOSURE', ?, 'CLOSURE_BASIS_CHANGED', ?, ?, 'CONFIRMED')
                """, UUID.randomUUID(), state.id(), command.id(), command.leader(), continuation);
        database.update("UPDATE pf_reply_closure SET conflict_continuation_id = ? WHERE id = ?", continuation, command.id());
        database.update("""
                UPDATE pf_case_data_command SET state = 'REJECTED', result_code = 'CLOSURE_BASIS_CHANGED', completed_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, command.id());
        database.update("UPDATE pf_reply_call SET state = 'RETURNED' WHERE id = (SELECT reply_call_id FROM pf_reply_closure WHERE id = ?)", command.id());
        return continuation;
    }

    public record Intent(UUID id, UUID call, boolean accepted, UUID conflict) {
    }
}
