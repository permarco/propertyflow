package com.pertegato.propertyflow.caseprocessing.persistence;

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
public class TenantReplyStore {

    private final JdbcTemplate database;

    public Source source(UUID entry, String caseId, long leader) {
        return database.queryForObject("""
                SELECT coalesce(source_staff_instance_key, source_automatic_instance_key, source_interrupted_automatic_instance_key) AS instance,
                    source_staff_instance_key IS NULL AS automatic, source_interrupted_automatic_instance_key IS NOT NULL AS interrupted
                FROM pf_reply_entry WHERE id = ? AND case_id = ? AND leading_instance_key = ?
                """, (row, index) -> new Source(row.getLong("instance"), row.getBoolean("automatic"), row.getBoolean("interrupted")), entry, caseId, leader);
    }

    public record Source(long instance, boolean automatic, boolean interrupted) {
    }

    public Optional<Call> child(long child) {
        return find("r.child_instance_key = ?", child).stream().findFirst();
    }

    public Call get(UUID id) {
        return find("r.id = ?", id).stream().findFirst().orElseThrow();
    }

    private List<Call> find(String condition, Object... arguments) {
        return database.query("""
                SELECT r.*, e.consumed_version FROM pf_reply_call r JOIN pf_reply_entry e ON e.id = r.entry_id WHERE
                """ + condition + " ORDER BY r.parent_element_key DESC", (row, index) -> new Call(row.getObject("id", UUID.class), row.getObject("entry_id", UUID.class),
                        row.getString("case_id"), row.getLong("leading_instance_key"), row.getLong("child_instance_key"),
                        row.getLong("parent_element_key"), row.getString("tenant_id"), row.getString("state"), row.getLong("consumed_version")), arguments);
    }

    public Call register(CaseState state, UUID entry, long leader, long child, long parent, String tenant) {
        UUID id = UUID.randomUUID();
        database.update("""
                INSERT INTO pf_reply_call (id, entry_id, case_id, leading_instance_key, child_instance_key, parent_element_key, tenant_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, entry, state.id(), leader, child, parent, tenant);
        if (!"WAITING_FOR_TENANT".equals(state.status())) {
            UUID event = UUID.randomUUID();
            database.update("UPDATE pf_case SET status = 'WAITING_FOR_TENANT', version = version + 1 WHERE id = ?", state.id());
            database.update("""
                    INSERT INTO pf_case_status_event (id, case_id, reply_entry_id, from_status, to_status, case_version)
                    VALUES (?, ?, ?, ?, 'WAITING_FOR_TENANT', ?)
                    """, event, state.id(), entry, state.status(), state.version() + 1);
            database.update("""
                    INSERT INTO pf_case_mail_order (id, case_id, event_id, access_id, notification_type)
                    SELECT ?, case_id, ?, access_id, 'STATUS_CHANGED' FROM pf_acceptance_mail_order WHERE case_id = ?
                    """, UUID.randomUUID(), event, state.id());
        }
        return get(id);
    }

    public void ready(UUID call) {
        database.update("UPDATE pf_reply_call SET state = 'WAITING' WHERE id = ? AND state = 'REGISTERING'", call);
    }

    public void reviewDelivered(UUID call) {
        database.update("UPDATE pf_reply_call SET needs_review_delivery = FALSE WHERE id = ?", call);
    }

    public Optional<Call> active(String caseId, long leader) {
        return find("r.case_id = ? AND r.leading_instance_key = ? AND r.state IN ('REGISTERING', 'WAITING', 'INTERRUPTED')",
                caseId, leader).stream().findFirst();
    }

    public boolean closing(String caseId, long leader) {
        return !find("r.case_id = ? AND r.leading_instance_key = ? AND r.state = 'CLOSING'", caseId, leader).isEmpty();
    }

    public Optional<Delivery> claim() {
        List<UUID> ids = database.query("""
                WITH candidate AS (
                    SELECT r.id FROM pf_reply_call r JOIN pf_case c ON c.id = r.case_id
                    JOIN pf_case_process p ON p.case_id = r.case_id
                    WHERE (r.state = 'WAITING' OR r.state = 'INTERRUPTED' AND r.needs_review_delivery) AND c.status = 'WAITING_FOR_TENANT' AND p.process_instance_key = r.leading_instance_key
                        AND r.next_attempt_at <= CURRENT_TIMESTAMP
                    ORDER BY r.next_attempt_at LIMIT 1 FOR UPDATE OF r SKIP LOCKED
                ) UPDATE pf_reply_call r SET next_attempt_at = CURRENT_TIMESTAMP + INTERVAL '5 seconds'
                  FROM candidate c WHERE r.id = c.id RETURNING r.id
                """, (row, index) -> row.getObject("id", UUID.class));
        return ids.stream().findFirst().map(id -> {
            Call call = get(id);
            long version = database.queryForObject("""
                    SELECT coalesce(max(case_version), 0) FROM pf_evaluation_delivery WHERE case_id = ? AND leading_instance_key = ?
                    """, Long.class, call.caseId(), call.leader());
            return new Delivery(call, version, "INTERRUPTED".equals(call.state()) || reviewPending(call.caseId(), call.leader()));
        });
    }

    public boolean reviewPending(String caseId, long leader) {
        return Boolean.TRUE.equals(database.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM pf_case_review_signal WHERE case_id = ? AND leading_instance_key = ?
                    AND state IN ('PENDING', 'CONFIRMED') AND considered_decision_id IS NULL AND considered_wait_command_id IS NULL
                        AND considered_direct_command_id IS NULL)
                """, Boolean.class, caseId, leader));
    }

    public Optional<Decision> decision(UUID call) {
        return database.query("SELECT * FROM pf_reply_decision WHERE call_id = ? ORDER BY occurred_at LIMIT 1",
                (row, index) -> new Decision(row.getObject("id", UUID.class), row.getString("outcome"),
                        row.getObject("continuation_id", UUID.class)), call).stream().findFirst();
    }

    public Decision decide(Call call, CaseState state, UUID evaluation, boolean review) {
        return decide(call, state, evaluation, review ? "REVIEW" : "WAIT");
    }

    public Decision decide(Call call, CaseState state, UUID evaluation, String outcome) {
        boolean review = "REVIEW".equals(outcome);
        UUID continuation = null;
        if (review) {
            continuation = UUID.randomUUID();
            UUID automatic = database.queryForObject("SELECT call_ref FROM pf_automatic_entry WHERE case_id = ? AND leading_instance_key = ? AND current_entry",
                    UUID.class, state.id(), call.leader());
            database.update("UPDATE pf_staff_continuation SET superseded = TRUE WHERE case_id = ? AND NOT superseded", state.id());
            database.update("""
                    INSERT INTO pf_staff_continuation (id, case_id, leading_instance_key, automatic_call_ref, case_version, origin)
                    VALUES (?, ?, ?, ?, ?, 'REPLY_REVIEW')
                    """, continuation, state.id(), call.leader(), automatic, state.version());
            database.update("""
                    UPDATE pf_case_review_signal SET state = 'CONFIRMED', continuation_id = ?
                    WHERE case_id = ? AND leading_instance_key = ? AND state IN ('PENDING', 'CONFIRMED')
                        AND considered_decision_id IS NULL AND considered_wait_command_id IS NULL AND considered_direct_command_id IS NULL
                    """, continuation, state.id(), call.leader());
        }
        UUID id = UUID.randomUUID();
        database.update("""
                INSERT INTO pf_reply_decision (id, call_id, evaluation_id, outcome, case_version, continuation_id)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, call.id(), evaluation, outcome, state.version(), continuation);
        database.update("UPDATE pf_reply_call SET state = ?, needs_review_delivery = ? WHERE id = ?", review ? "INTERRUPTED" : "RETURNED", review, call.id());
        database.update("UPDATE pf_reply_entry SET consumed_version = greatest(consumed_version, ?) WHERE id = ?", state.version(), call.entry());
        return new Decision(id, outcome, continuation);
    }

    public Call questionSource(UUID decision, String caseId, long leader) {
        return automaticSource(decision, caseId, leader, "QUESTION");
    }

    public Call automaticSource(UUID decision, String caseId, long leader, String kind) {
        List<UUID> calls = database.query("""
                SELECT r.id FROM pf_reply_call r JOIN pf_reply_decision d ON d.call_id = r.id
                WHERE d.id = ? AND r.case_id = ? AND r.leading_instance_key = ? AND d.outcome = ? AND r.state = 'RETURNED'
                    AND NOT EXISTS (SELECT 1 FROM pf_reply_call newer WHERE newer.case_id = r.case_id
                        AND newer.leading_instance_key = r.leading_instance_key AND newer.parent_element_key > r.parent_element_key)
                """, (row, index) -> row.getObject(1, UUID.class), decision, caseId, leader, kind);
        if (calls.size() != 1) {
            throw new IllegalArgumentException("INVALID_QUESTION_REPLY_SOURCE");
        }
        return get(calls.getFirst());
    }

    public record Call(UUID id, UUID entry, String caseId, long leader, long child, long parent, String tenant, String state, long consumed) {
    }

    public record Delivery(Call call, long version, boolean review) {
    }

    public record Decision(UUID id, String outcome, UUID continuation) {
    }
}
