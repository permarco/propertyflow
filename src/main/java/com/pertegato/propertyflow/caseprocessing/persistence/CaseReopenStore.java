package com.pertegato.propertyflow.caseprocessing.persistence;

import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.CaseState;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("propertyflow-backend & !frontend-demo")
public class CaseReopenStore {

    private final JdbcTemplate database;

    public CaseReopenStore(JdbcTemplate database) {
        this.database = database;
    }

    public Optional<Order> find(String caseId, UUID actor, UUID operation) {
        return select("case_id = ? AND actor_id = ? AND operation_id = ?", caseId, actor, operation).stream().findFirst();
    }

    public Optional<Order> get(UUID id) {
        return select("id = ?", id).stream().findFirst();
    }

    public Optional<Order> current(String caseId, long previous) {
        return select("case_id = ? AND previous_instance_key = ? AND state <> 'REJECTED'", caseId, previous).stream().findFirst();
    }

    private List<Order> select(String condition, Object... args) {
        return database.query("SELECT * FROM pf_case_reopen_order WHERE " + condition, (row, index) -> new Order(
                row.getObject("id", UUID.class), row.getString("case_id"), row.getObject("actor_id", UUID.class), row.getLong("previous_instance_key"),
                row.getLong("expected_version"), row.getLong("definition_key"), row.getString("tenant_id"), row.getString("state"),
                row.getObject("new_instance_key", Long.class), row.getObject("continuation_id", UUID.class), row.getString("target_status")), args);
    }

    public Order request(CaseState state, UUID actor, UUID operation) {
        UUID id = UUID.randomUUID();
        database.update("""
                INSERT INTO pf_case_reopen_order (id, case_id, actor_id, operation_id, previous_instance_key, expected_version, definition_key, tenant_id)
                SELECT ?, case_id, ?, ?, process_instance_key, ?, definition_key, tenant_id FROM pf_case_process WHERE case_id = ?
                """, id, actor, operation, state.version(), state.id());
        return get(id).orElseThrow();
    }

    public Optional<Order> claim() {
        List<UUID> ids = database.query("""
                WITH candidate AS (SELECT id FROM pf_case_reopen_order WHERE state = 'PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP
                    ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED)
                UPDATE pf_case_reopen_order o SET next_attempt_at = CURRENT_TIMESTAMP + INTERVAL '2 minutes'
                FROM candidate c WHERE o.id = c.id RETURNING o.id
                """, (row, index) -> row.getObject("id", UUID.class));
        return ids.stream().findFirst().flatMap(this::get);
    }

    public void blocked(UUID id) {
        database.update("UPDATE pf_case_reopen_order SET state = 'BLOCKED' WHERE id = ? AND state = 'PENDING'", id);
    }

    public void reject(UUID id) {
        database.update("UPDATE pf_case_reopen_order SET state = 'REJECTED' WHERE id = ? AND state = 'PENDING'", id);
    }

    public boolean closedWithEvidence(String caseId, long leader) {
        return Boolean.TRUE.equals(database.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pf_case_closure WHERE case_id = ? AND leading_instance_key = ?)", Boolean.class, caseId, leader));
    }

    public String tenant(String caseId) {
        return database.queryForObject("SELECT tenant_id FROM pf_case_process WHERE case_id = ?", String.class, caseId);
    }

    public Order activate(CaseState state, Order order, long leader, long element) {
        boolean review = Boolean.TRUE.equals(database.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM pf_case_review_signal WHERE case_id = ? AND state IN ('PENDING', 'CONFIRMED'))
                    OR EXISTS (SELECT 1 FROM pf_case WHERE id = ? AND urgency = 'CRITICAL')
                """, Boolean.class, state.id(), state.id()));
        String target = review ? "STAFF_REVIEW_REQUIRED" : "IN_PROGRESS";
        UUID continuation = UUID.randomUUID();
        UUID entry = UUID.randomUUID();
        UUID call = UUID.randomUUID();
        database.update("""
                UPDATE pf_case_process SET process_instance_key = ?, definition_key = ?, tenant_id = ?, start_order_id = NULL,
                    reopen_order_id = ?, registered_at = CURRENT_TIMESTAMP WHERE case_id = ?
                """, leader, order.definition(), order.tenant(), order.id(), state.id());
        database.update("""
                INSERT INTO pf_case_process_history (process_instance_key, case_id, definition_key, tenant_id, reopen_order_id)
                VALUES (?, ?, ?, ?, ?)
                """, leader, state.id(), order.definition(), order.tenant(), order.id());
        database.update("UPDATE pf_case SET status = ?, follow_up_due_at = NULL, version = version + 1 WHERE id = ?", target, state.id());
        // A new staff continuation owns this generation; no automatic child is created or revived.
        database.update("""
                INSERT INTO pf_automatic_entry (id, call_ref, case_id, leading_instance_key, prepare_element_key, case_version, entry_kind, effects_blocked)
                VALUES (?, ?, ?, ?, ?, ?, 'REOPEN', TRUE)
                """, entry, call, state.id(), leader, element, state.version() + 1);
        database.update("""
                INSERT INTO pf_staff_continuation (id, case_id, leading_instance_key, automatic_call_ref, case_version, origin, entry_kind, review_applied)
                VALUES (?, ?, ?, ?, ?, 'REOPEN', ?, TRUE)
                """, continuation, state.id(), leader, call, state.version() + 1, review ? "REVIEW" : "CONTINUE");
        UUID event = UUID.randomUUID();
        database.update("""
                INSERT INTO pf_case_status_event (id, case_id, reopen_order_id, from_status, to_status, case_version)
                VALUES (?, ?, ?, 'CLOSED', ?, ?)
                """, event, state.id(), order.id(), target, state.version() + 1);
        database.update("""
                INSERT INTO pf_case_mail_order (id, case_id, event_id, access_id, notification_type)
                SELECT ?, case_id, ?, access_id, 'STATUS_CHANGED' FROM pf_acceptance_mail_order WHERE case_id = ?
                """, UUID.randomUUID(), event, state.id());
        database.update("""
                UPDATE pf_case_reopen_order SET state = 'APPLIED', applied_at = CURRENT_TIMESTAMP, new_instance_key = ?,
                    continuation_id = ?, target_status = ? WHERE id = ?
                """, leader, continuation, target, order.id());
        return get(order.id()).orElseThrow();
    }

    public record Order(UUID id, String caseId, UUID actor, long previous, long version, long definition, String tenant,
            String state, Long leader, UUID continuation, String target) {
    }
}
