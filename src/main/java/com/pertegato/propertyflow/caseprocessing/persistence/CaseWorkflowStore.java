package com.pertegato.propertyflow.caseprocessing.persistence;

import java.time.Duration;
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
public class CaseWorkflowStore {

    private final JdbcTemplate database;

    public Optional<StartOrder> claim(long definitionKey, String tenantId, Duration lease) {
        UUID leaseId = UUID.randomUUID();
        List<StartOrder> orders = database.query("""
                WITH candidate AS (
                    SELECT id FROM pf_process_start_order
                    WHERE state = 'PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP
                      AND (lease_until IS NULL OR lease_until <= CURRENT_TIMESTAMP)
                    ORDER BY next_attempt_at, created_at, id LIMIT 1 FOR UPDATE SKIP LOCKED
                )
                UPDATE pf_process_start_order o
                SET lease_id = ?, lease_until = CURRENT_TIMESTAMP + (? * INTERVAL '1 millisecond'),
                    attempts = attempts + 1, definition_key = COALESCE(definition_key, ?), tenant_id = COALESCE(tenant_id, ?)
                FROM candidate c WHERE o.id = c.id
                RETURNING o.id, o.case_id, o.definition_key, o.tenant_id, o.lease_id, o.attempts
                """, (row, index) -> new StartOrder(row.getObject("id", UUID.class), row.getString("case_id"),
                        row.getLong("definition_key"), row.getString("tenant_id"), row.getObject("lease_id", UUID.class),
                        row.getInt("attempts")), leaseId, lease.toMillis(), definitionKey, tenantId);
        return orders.stream().findFirst();
    }

    public void finishAttempt(StartOrder order, String result, boolean blocked, Duration delay) {
        // Fencing prevents a late response from overwriting a registration or a newer lease.
        database.update("""
                UPDATE pf_process_start_order SET state = ?, last_result = ?, lease_id = NULL, lease_until = NULL,
                    next_attempt_at = CURRENT_TIMESTAMP + (? * INTERVAL '1 millisecond')
                WHERE id = ? AND lease_id = ? AND state = 'PENDING'
                """, blocked ? "BLOCKED" : "PENDING", result, delay.toMillis(), order.id(), order.leaseId());
    }

    public Optional<CaseState> lockCase(String caseId) {
        return database.query("SELECT id, status, version FROM pf_case WHERE id = ? FOR UPDATE",
                (row, index) -> new CaseState(row.getString("id"), row.getString("status"), row.getLong("version")), caseId)
                .stream().findFirst();
    }

    public Optional<StartIdentity> findStart(UUID orderId, String caseId) {
        return database.query("""
                SELECT definition_key, tenant_id FROM pf_process_start_order WHERE id = ? AND case_id = ?
                """, (row, index) -> new StartIdentity(row.getObject("definition_key", Long.class), row.getString("tenant_id")),
                orderId, caseId).stream().findFirst();
    }

    public Optional<StartIdentity> reopenStart(UUID orderId, String caseId) {
        return database.query("SELECT definition_key, tenant_id FROM pf_case_reopen_order WHERE id = ? AND case_id = ?",
                (row, index) -> new StartIdentity(row.getLong("definition_key"), row.getString("tenant_id")), orderId, caseId).stream().findFirst();
    }

    public Optional<Long> leadingInstance(String caseId) {
        return database.query("SELECT process_instance_key FROM pf_case_process WHERE case_id = ?",
                (row, index) -> row.getLong(1), caseId).stream().findFirst();
    }

    public void register(String caseId, UUID orderId, long instanceKey, long definitionKey, String tenantId) {
        database.update("""
                INSERT INTO pf_case_process (case_id, start_order_id, process_instance_key, definition_key, tenant_id)
                VALUES (?, ?, ?, ?, ?)
                """, caseId, orderId, instanceKey, definitionKey, tenantId);
        database.update("""
                INSERT INTO pf_case_process_history (case_id, start_order_id, process_instance_key, definition_key, tenant_id)
                VALUES (?, ?, ?, ?, ?)
                """, caseId, orderId, instanceKey, definitionKey, tenantId);
        database.update("""
                UPDATE pf_process_start_order SET state = 'CONFIRMED', lease_id = NULL, lease_until = NULL,
                    last_result = 'REGISTERED' WHERE id = ?
                """, orderId);
    }

    public Optional<Entry> entryForCase(String caseId) {
        return database.query("""
                SELECT e.* FROM pf_automatic_entry e JOIN pf_case_process p ON p.case_id = e.case_id
                AND p.process_instance_key = e.leading_instance_key WHERE e.case_id = ? AND e.current_entry
                """, (row, index) -> new Entry(
                row.getObject("id", UUID.class), row.getObject("call_ref", UUID.class), row.getString("case_id"),
                row.getLong("leading_instance_key"), row.getLong("prepare_element_key"), row.getLong("case_version"),
                row.getObject("child_instance_key", Long.class), row.getObject("parent_element_key", Long.class)), caseId)
                .stream().findFirst();
    }

    public Entry createEntry(CaseState state, long leadingKey, long elementKey) {
        Entry entry = new Entry(UUID.randomUUID(), UUID.randomUUID(), state.id(), leadingKey, elementKey, state.version(), null, null);
        database.update("""
                INSERT INTO pf_automatic_entry (id, call_ref, case_id, leading_instance_key, prepare_element_key, case_version)
                VALUES (?, ?, ?, ?, ?, ?)
                """, entry.id(), entry.callRef(), state.id(), leadingKey, elementKey, state.version());
        return entry;
    }

    public String entryKind(UUID entry) {
        return database.queryForObject("SELECT entry_kind FROM pf_automatic_entry WHERE id = ?", String.class, entry);
    }

    public Optional<Entry> questionEntry(UUID decision, String caseId) {
        return entryForCase(caseId).filter(entry -> Boolean.TRUE.equals(database.queryForObject(
                "SELECT reply_decision_id = ? FROM pf_automatic_entry WHERE id = ?", Boolean.class, decision, entry.id())));
    }

    public Entry createQuestionEntry(CaseState state, long leader, long element, UUID decision) {
        return createAutomaticEntry(state, leader, element, decision, "QUESTION");
    }

    public Entry createAutomaticEntry(CaseState state, long leader, long element, UUID decision, String kind) {
        database.update("UPDATE pf_automatic_entry SET current_entry = FALSE, effects_blocked = TRUE WHERE case_id = ? AND current_entry", state.id());
        database.update("UPDATE pf_staff_continuation SET superseded = TRUE WHERE case_id = ? AND NOT superseded", state.id());
        Entry entry = new Entry(UUID.randomUUID(), UUID.randomUUID(), state.id(), leader, element, state.version(), null, null);
        database.update("""
                INSERT INTO pf_automatic_entry (id, call_ref, case_id, leading_instance_key, prepare_element_key, case_version, entry_kind, reply_decision_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, entry.id(), entry.callRef(), state.id(), leader, element, state.version(), kind, decision);
        return entry;
    }

    public boolean questionPhase(CaseState state) {
        return "WAITING_FOR_TENANT".equals(state.status()) && entryForCase(state.id())
                .filter(entry -> ("QUESTION".equals(entryKind(entry.id())) || "CLOSE".equals(entryKind(entry.id())))).isPresent();
    }

    public boolean manualQuestionPhase(CaseState state) {
        return "WAITING_FOR_TENANT".equals(state.status()) && Boolean.TRUE.equals(database.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM pf_clarification_question q JOIN pf_automatic_entry a ON a.call_ref = q.automatic_call_ref
                    WHERE q.case_id = ? AND a.current_entry AND q.leading_instance_key =
                        (SELECT process_instance_key FROM pf_case_process WHERE case_id = ?))
                """, Boolean.class, state.id(), state.id()));
    }

    public boolean replyWaiting(String caseId, long leader) {
        return Boolean.TRUE.equals(database.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM pf_reply_call WHERE case_id = ? AND leading_instance_key = ?
                    AND state IN ('REGISTERING', 'WAITING', 'INTERRUPTED'))
                """, Boolean.class, caseId, leader));
    }

    public void bindChild(UUID entryId, long childKey, long parentElementKey) {
        database.update("UPDATE pf_automatic_entry SET child_instance_key = ?, parent_element_key = ? WHERE id = ?",
                childKey, parentElementKey, entryId);
    }

    public boolean hasInitialTransition(UUID entryId) {
        return Boolean.TRUE.equals(database.queryForObject("SELECT EXISTS (SELECT 1 FROM pf_case_status_event WHERE entry_id = ?)",
                Boolean.class, entryId));
    }

    public void startClarification(CaseState state, Entry entry) {
        UUID eventId = UUID.randomUUID();
        database.update("UPDATE pf_case SET status = 'UNDER_CLARIFICATION', version = version + 1 WHERE id = ?", state.id());
        database.update("""
                INSERT INTO pf_case_status_event (id, case_id, entry_id, from_status, to_status, case_version)
                VALUES (?, ?, ?, 'RECEIVED', 'UNDER_CLARIFICATION', ?)
                """, eventId, state.id(), entry.id(), state.version() + 1);
        // Keep the original obligation reference even if access was revoked meanwhile.
        // The delivery service must resolve and validate the current link when it actually sends.
        UUID accessId = database.queryForObject("SELECT access_id FROM pf_acceptance_mail_order WHERE case_id = ?",
                UUID.class, state.id());
        database.update("""
                INSERT INTO pf_case_mail_order (id, case_id, event_id, access_id, notification_type)
                VALUES (?, ?, ?, ?, 'STATUS_CHANGED')
                """, UUID.randomUUID(), state.id(), eventId, accessId);
    }

    public record StartOrder(UUID id, String caseId, long definitionKey, String tenantId, UUID leaseId, int attempts) {
    }

    public record StartIdentity(Long definitionKey, String tenantId) {
    }

    public record CaseState(String id, String status, long version) {
    }

    public record Entry(UUID id, UUID callRef, String caseId, long leadingKey, long prepareElementKey, long caseVersion,
            Long childKey, Long parentElementKey) {
    }
}
