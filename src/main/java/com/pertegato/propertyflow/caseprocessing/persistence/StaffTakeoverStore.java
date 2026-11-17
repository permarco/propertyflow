package com.pertegato.propertyflow.caseprocessing.persistence;

import com.pertegato.propertyflow.caseprocessing.contract.CaseActionException;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseDataStore.Command;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.CaseState;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("propertyflow-backend & !frontend-demo")
public class StaffTakeoverStore {

    private final JdbcTemplate database;

    public StaffTakeoverStore(JdbcTemplate database) {
        this.database = database;
    }

    public void prepare(Command command, CaseState state) {
        if (!"UNDER_CLARIFICATION".equals(state.status())) {
            throw new CaseActionException("CASE_CHANGED");
        }
        int inserted = database.update("""
                INSERT INTO pf_staff_takeover (id, case_id, leading_instance_key, automatic_call_ref)
                SELECT ?, case_id, leading_instance_key, call_ref FROM pf_automatic_entry
                WHERE case_id = ? AND leading_instance_key = ? AND current_entry AND child_instance_key IS NOT NULL
                """, command.id(), state.id(), command.leader());
        if (inserted != 1) {
            throw new CaseActionException("CASE_CHANGED");
        }
    }

    public boolean apply(Command command, CaseState state) {
        if (!"UNDER_CLARIFICATION".equals(state.status()) || !Boolean.TRUE.equals(database.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM pf_staff_takeover t JOIN pf_automatic_entry a ON a.call_ref = t.automatic_call_ref
                    WHERE t.id = ? AND a.current_entry AND a.leading_instance_key = ?)
                """, Boolean.class, command.id(), command.leader()))) {
            return false;
        }
        List<UUID> prepared = database.query("""
                SELECT s.id FROM pf_staff_continuation s JOIN pf_staff_takeover t ON t.automatic_call_ref = s.automatic_call_ref
                WHERE t.id = ? AND NOT s.superseded
                """, (row, index) -> row.getObject(1, UUID.class), command.id());
        boolean review = !prepared.isEmpty() || pending(state.id(), command.leader());
        UUID continuation = prepared.isEmpty() ? UUID.randomUUID() : prepared.getFirst();
        String target = review ? "STAFF_REVIEW_REQUIRED" : "IN_PROGRESS";
        database.update("UPDATE pf_staff_continuation SET superseded = TRUE WHERE case_id = ? AND id <> ? AND NOT superseded",
                state.id(), continuation);
        database.update("""
                INSERT INTO pf_staff_continuation (id, case_id, leading_instance_key, automatic_call_ref, case_version,
                    origin, entry_kind, review_applied)
                SELECT ?, case_id, leading_instance_key, automatic_call_ref, ?, 'TAKEOVER', ?, TRUE
                    FROM pf_staff_takeover WHERE id = ?
                ON CONFLICT (id) DO UPDATE SET origin = 'TAKEOVER', entry_kind = 'REVIEW', review_applied = TRUE
                """, continuation, state.version() + 1, review ? "REVIEW" : "CONTINUE", command.id());
        database.update("UPDATE pf_automatic_entry SET effects_blocked = TRUE WHERE case_id = ? AND current_entry", state.id());
        database.update("UPDATE pf_case SET status = ?, version = version + 1 WHERE id = ?", target, state.id());
        database.update("""
                UPDATE pf_staff_takeover SET continuation_id = ?, target_status = ?, applied_at = CURRENT_TIMESTAMP WHERE id = ?
                """, continuation, target, command.id());
        confirm(state.id(), command.leader(), continuation);
        UUID event = UUID.randomUUID();
        database.update("""
                INSERT INTO pf_case_status_event (id, case_id, takeover_id, from_status, to_status, case_version)
                VALUES (?, ?, ?, ?, ?, ?)
                """, event, state.id(), command.id(), state.status(), target, state.version() + 1);
        mail(state.id(), event);
        return true;
    }

    public boolean pending(String caseId, long leader) {
        return Boolean.TRUE.equals(database.queryForObject("""
                SELECT urgency = 'CRITICAL' OR EXISTS (SELECT 1 FROM pf_case_review_signal WHERE case_id = ?
                    AND (leading_instance_key = ? OR leading_instance_key IS NULL) AND state <> 'NOTE'
                    AND considered_decision_id IS NULL AND considered_wait_command_id IS NULL AND considered_direct_command_id IS NULL)
                FROM pf_case WHERE id = ?
                """, Boolean.class, caseId, leader, caseId));
    }

    public void confirm(String caseId, long leader, UUID continuation) {
        database.update("""
                UPDATE pf_case_review_signal SET state = 'CONFIRMED', leading_instance_key = ?, continuation_id = ?
                WHERE case_id = ? AND (leading_instance_key = ? OR leading_instance_key IS NULL) AND state <> 'NOTE'
                    AND considered_decision_id IS NULL AND considered_wait_command_id IS NULL AND considered_direct_command_id IS NULL
                """, leader, continuation, caseId, leader);
    }

    public void upgrade(CaseState state, StaffReviewStore.Continuation continuation) {
        if (!("TAKEOVER".equals(continuation.origin()) || "ANSWER_CLEARED".equals(continuation.origin())) || continuation.childKey() != null
                || !"CONTINUE".equals(continuation.entryKind()) || !"IN_PROGRESS".equals(state.status())
                || !pending(state.id(), continuation.leader())) {
            return;
        }
        confirm(state.id(), continuation.leader(), continuation.id());
        database.update("UPDATE pf_staff_continuation SET entry_kind = 'REVIEW', review_applied = FALSE WHERE id = ?", continuation.id());
        // Der registrierende Mitarbeiterworker liest den aktualisierten Eintritt erneut.
        // Die Statusänderung erfolgt anschliessend idempotent in SReviewStatus.
    }

    private void mail(String caseId, UUID event) {
        database.update("""
                INSERT INTO pf_case_mail_order (id, case_id, event_id, access_id, notification_type)
                SELECT ?, case_id, ?, access_id, 'STATUS_CHANGED' FROM pf_acceptance_mail_order WHERE case_id = ?
                """, UUID.randomUUID(), event, caseId);
    }

    public Optional<Delivery> claim() {
        List<UUID> ids = database.query("""
                WITH candidate AS (
                    SELECT t.id FROM pf_staff_takeover t JOIN pf_case_process p ON p.case_id = t.case_id
                    JOIN pf_staff_continuation s ON s.id = t.continuation_id JOIN pf_case c ON c.id = t.case_id
                    WHERE t.applied_at IS NOT NULL AND NOT t.delivered AND NOT s.superseded
                        AND p.process_instance_key = t.leading_instance_key AND c.status <> 'CLOSED'
                        AND t.next_attempt_at <= CURRENT_TIMESTAMP ORDER BY t.applied_at LIMIT 1 FOR UPDATE OF t SKIP LOCKED
                ) UPDATE pf_staff_takeover t SET next_attempt_at = CURRENT_TIMESTAMP + INTERVAL '5 seconds'
                    FROM candidate c WHERE t.id = c.id RETURNING t.id
                """, (row, index) -> row.getObject(1, UUID.class));
        return ids.stream().findFirst().map(this::delivery);
    }

    public Delivery delivery(UUID id) {
        return database.queryForObject("""
                SELECT t.*, p.tenant_id, coalesce((SELECT r.id::text FROM pf_reply_entry r JOIN pf_automatic_entry a
                    ON a.child_instance_key = r.source_automatic_instance_key WHERE a.call_ref = t.automatic_call_ref
                    ORDER BY r.created_at DESC LIMIT 1), '') AS reply_entry
                FROM pf_staff_takeover t JOIN pf_case_process p ON p.case_id = t.case_id
                    JOIN pf_staff_continuation s ON s.id = t.continuation_id
                WHERE t.id = ? AND p.process_instance_key = t.leading_instance_key AND NOT s.superseded
                """, (row, index) -> new Delivery(id, row.getString("case_id"), row.getLong("leading_instance_key"),
                        row.getString("tenant_id"), row.getObject("automatic_call_ref", UUID.class), row.getString("reply_entry")), id);
    }

    public void delivered(UUID id) {
        database.update("UPDATE pf_staff_takeover SET delivered = TRUE WHERE id = ?", id);
    }

    public record Delivery(UUID id, String caseId, long leader, String tenant, UUID call, String replyEntry) {
    }
}
