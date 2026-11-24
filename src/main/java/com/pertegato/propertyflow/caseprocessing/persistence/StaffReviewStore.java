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
@Profile("propertyflow-backend & !frontend-demo")
@RequiredArgsConstructor
public class StaffReviewStore {

    private final JdbcTemplate database;

    public Optional<SignalDelivery> claimSignal() {
        return database.query("""
                WITH candidate AS (
                    SELECT s.id, p.process_instance_key, p.tenant_id FROM pf_case_review_signal s
                    JOIN pf_case_process p ON p.case_id = s.case_id JOIN pf_case c ON c.id = s.case_id
                    WHERE s.state = 'PENDING' AND s.next_attempt_at <= CURRENT_TIMESTAMP AND c.status <> 'CLOSED'
                      AND (s.leading_instance_key IS NULL OR s.leading_instance_key = p.process_instance_key)
                    ORDER BY s.next_attempt_at, s.id LIMIT 1 FOR UPDATE OF s SKIP LOCKED
                )
                UPDATE pf_case_review_signal s SET leading_instance_key = c.process_instance_key,
                    next_attempt_at = CURRENT_TIMESTAMP + INTERVAL '30 seconds'
                FROM candidate c WHERE s.id = c.id RETURNING s.id, s.case_id, c.process_instance_key, c.tenant_id
                """, (row, index) -> new SignalDelivery(row.getObject("id", UUID.class), row.getString("case_id"),
                        row.getLong("process_instance_key"), row.getString("tenant_id"))).stream().findFirst();
    }

    public Signal signal(UUID id, String caseId) {
        return database.query("SELECT * FROM pf_case_review_signal WHERE id = ? AND case_id = ? FOR UPDATE",
                (row, index) -> new Signal(row.getObject("id", UUID.class), row.getString("state"),
                        row.getObject("leading_instance_key", Long.class), row.getObject("continuation_id", UUID.class)), id, caseId)
                .stream().findFirst().orElseThrow(() -> new IllegalArgumentException("UNKNOWN_REVIEW_SIGNAL"));
    }

    public Continuation prepare(CaseState state, long leader, UUID callRef) {
        database.update("""
                INSERT INTO pf_staff_continuation (id, case_id, leading_instance_key, automatic_call_ref, case_version)
                VALUES (?, ?, ?, ?, ?) ON CONFLICT (case_id, automatic_call_ref) WHERE NOT superseded DO NOTHING
                """, UUID.randomUUID(), state.id(), leader, callRef, state.version());
        return forCall(state.id(), callRef).orElseThrow();
    }

    public Optional<Continuation> forCall(String caseId, UUID callRef) {
        return find("case_id = ? AND automatic_call_ref = ? AND NOT superseded", caseId, callRef).stream().findFirst();
    }

    public Continuation continuation(UUID id, String caseId) {
        return find("id = ? AND case_id = ?", id, caseId).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("UNKNOWN_STAFF_CONTINUATION"));
    }

    public Optional<Continuation> forChild(String caseId, long child) {
        return find("case_id = ? AND child_instance_key = ?", caseId, child).stream().findFirst();
    }

    private List<Continuation> find(String condition, Object... arguments) {
        return database.query("SELECT * FROM pf_staff_continuation WHERE " + condition,
                (row, index) -> new Continuation(row.getObject("id", UUID.class), row.getString("case_id"),
                        row.getLong("leading_instance_key"), row.getObject("automatic_call_ref", UUID.class),
                        row.getLong("case_version"), row.getObject("claim_element_key", Long.class),
                        row.getObject("child_instance_key", Long.class), row.getObject("parent_element_key", Long.class),
                        row.getBoolean("review_applied"), row.getString("origin"), row.getString("entry_kind")), arguments);
    }

    public void confirm(UUID signalId, UUID continuationId, UUID callRef) {
        database.update("UPDATE pf_automatic_entry SET effects_blocked = TRUE WHERE call_ref = ?", callRef);
        database.update("UPDATE pf_case_review_signal SET state = 'CONFIRMED', continuation_id = ? WHERE id = ?",
                continuationId, signalId);
    }

    public boolean blocked(UUID callRef) {
        return Boolean.TRUE.equals(database.queryForObject("SELECT effects_blocked FROM pf_automatic_entry WHERE call_ref = ?",
                Boolean.class, callRef));
    }

    public List<String> reasons(String caseId) {
        return database.query("""
                SELECT DISTINCT reason FROM pf_case_review_signal WHERE case_id = ? AND source_type <> 'MAIL' AND considered_decision_id IS NULL AND considered_wait_command_id IS NULL
                    AND considered_direct_command_id IS NULL
                UNION SELECT 'STAFF_DECISION_STALE' FROM pf_staff_decision
                WHERE case_id = ? AND state = 'REJECTED' AND result_code = 'DECISION_BASIS_CHANGED'
                UNION SELECT CASE WHEN source = 'STAFF' THEN 'STAFF_CLOSED' ELSE 'SYSTEM_CLOSED' END FROM pf_case_closure WHERE case_id = ?
                """,
                (row, index) -> switch (row.getString("reason")) {
                    case "CRITICAL_URGENCY" -> "Kritische Dringlichkeit: menschliche Prüfung erforderlich.";
                    case "ANALYSIS_FAILED" -> "Automatische Analyse nicht verfügbar oder ungültig: menschliche Prüfung erforderlich.";
                    case "HUMAN_DECISION_REQUIRED" -> "Die aktuelle Auswertung erfordert eine menschliche Entscheidung.";
                    case "ALL_QUESTIONS_ANSWERED" -> "Alle zuvor offenen Rückfragen sind beantwortet: Vorgehen prüfen.";
                    case "STAFF_DECISION_STALE" -> "Fallstand seit der bestätigten Entscheidung geändert: erneute Prüfung erforderlich.";
                    case "CLOSURE_BASIS_CHANGED" -> "Fallstand seit Abschlussbestätigung geändert: erneute Prüfung erforderlich.";
                    case "STAFF_CLOSED" -> CaseClosureStore.STAFF_CLOSURE_REASON;
                    case "SYSTEM_CLOSED" -> "Systemabschluss mit gespeicherter Abschlussgrundlage.";
                    case "INVALID_CLOSURE_MESSAGE" -> "Automatische Abschlussmitteilung inhaltlich unzulässig: menschliche Prüfung erforderlich.";
                    default -> "Fachlicher Prüfbedarf liegt vor.";
                }, caseId, caseId, caseId);
    }

    public void questionConflict(String caseId, long leader, UUID continuation, UUID command) {
        database.update("""
                INSERT INTO pf_case_review_signal (id, case_id, source_type, source_ref, reason, leading_instance_key, continuation_id, state)
                VALUES (?, ?, 'STAFF_QUESTION', ?, 'STAFF_QUESTION_STALE', ?, ?, 'CONFIRMED') ON CONFLICT DO NOTHING
                """, UUID.randomUUID(), caseId, command, leader, continuation);
    }

    public void claim(UUID id, long elementKey) {
        database.update("UPDATE pf_staff_continuation SET claim_element_key = ? WHERE id = ?", elementKey, id);
    }

    public void bind(UUID id, long childKey, long parentElementKey) {
        database.update("UPDATE pf_staff_continuation SET child_instance_key = ?, parent_element_key = ? WHERE id = ?",
                childKey, parentElementKey, id);
    }

    public void note(UUID signalId) {
        database.update("UPDATE pf_case_review_signal SET state = 'NOTE' WHERE id = ?", signalId);
    }

    public void consume(UUID signalId) {
        database.update("UPDATE pf_case_review_signal SET state = 'CONSUMED' WHERE id = ?", signalId);
    }

    public void transferPending(UUID previous, Continuation current) {
        database.update("""
                UPDATE pf_case_review_signal SET continuation_id = ?, leading_instance_key = ?, state = 'PENDING',
                    next_attempt_at = CURRENT_TIMESTAMP
                WHERE continuation_id = ? AND continuation_id <> ? AND state <> 'NOTE'
                    AND considered_decision_id IS NULL AND considered_wait_command_id IS NULL AND considered_direct_command_id IS NULL
                """, current.id(), current.leader(), previous, current.id());
    }

    public void transferPendingToProcess(UUID previous, long leader) {
        database.update("""
                UPDATE pf_case_review_signal SET continuation_id = NULL, leading_instance_key = ?, state = 'PENDING',
                    next_attempt_at = CURRENT_TIMESTAMP
                WHERE continuation_id = ? AND state <> 'NOTE' AND considered_decision_id IS NULL
                    AND considered_wait_command_id IS NULL AND considered_direct_command_id IS NULL
                """, leader, previous);
    }

    public boolean waitMutationPending(String caseId) {
        return Boolean.TRUE.equals(database.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pf_follow_up_command WHERE case_id = ? AND state = 'APPLYING')",
                Boolean.class, caseId));
    }

    public Optional<UUID> currentConversationFailure(String caseId, long leader) {
        return database.query("""
                SELECT s.id FROM pf_case_review_signal s JOIN pf_conversation_evaluation e ON e.id = s.source_ref
                JOIN pf_case c ON c.id = e.case_id
                WHERE s.case_id = ? AND s.leading_instance_key = ? AND s.source_type = 'CONVERSATION'
                    AND s.reason = 'ANALYSIS_FAILED' AND e.state = 'APPLIED' AND e.applied_version = c.version
                    AND e.analysis_version = c.analysis_version
                """, (row, index) -> row.getObject("id", UUID.class), caseId, leader).stream().findFirst();
    }

    public Optional<UUID> phaseReference(String caseId, long child, String phase) {
        if ("IN_PROGRESS".equals(phase)) {
            return database.query("""
                    SELECT id FROM (
                        SELECT d.id, d.completed_at AS occurred_at FROM pf_staff_decision d
                        JOIN pf_staff_continuation s ON s.id = d.continuation_id
                        WHERE d.case_id = ? AND s.child_instance_key = ? AND d.action = 'CONTINUE' AND d.state = 'APPLIED'
                        UNION ALL SELECT r.id, r.applied_at FROM pf_case_reopen_order r
                        JOIN pf_staff_continuation s ON s.id = r.continuation_id
                        WHERE r.case_id = ? AND s.child_instance_key = ? AND r.state = 'APPLIED' AND r.target_status = 'IN_PROGRESS'
                        UNION ALL SELECT f.id, f.applied_at FROM pf_follow_up_command f JOIN pf_case_wait w ON w.id = f.wait_id
                        WHERE f.case_id = ? AND w.staff_instance_key = ? AND f.action = 'DELETE' AND f.state = 'APPLIED'
                        UNION ALL SELECT a.id, a.occurred_at FROM pf_answer_cancellation a
                        JOIN pf_staff_continuation s ON s.id = a.continuation_id
                        WHERE a.case_id = ? AND s.child_instance_key = ? AND a.target_status = 'IN_PROGRESS'
                        UNION ALL SELECT t.id, t.applied_at FROM pf_staff_takeover t
                        JOIN pf_staff_continuation s ON s.id = t.continuation_id
                        WHERE t.case_id = ? AND s.child_instance_key = ? AND t.target_status = 'IN_PROGRESS'
                    ) changes ORDER BY occurred_at DESC LIMIT 1
                    """, (row, index) -> row.getObject("id", UUID.class), caseId, child, caseId, child, caseId, child, caseId, child, caseId, child).stream().findFirst();
        }
        return database.query("SELECT id FROM pf_case_wait WHERE case_id = ? AND staff_instance_key = ? AND state = 'ACTIVE'",
                (row, index) -> row.getObject("id", UUID.class), caseId, child).stream().findFirst();
    }

    public void interruptReview(CaseState state, Continuation continuation) {
        List<UUID> signals = database.query("""
                SELECT id FROM pf_case_review_signal WHERE continuation_id = ? AND state = 'CONFIRMED' AND considered_decision_id IS NULL AND considered_wait_command_id IS NULL
                    AND considered_direct_command_id IS NULL
                ORDER BY created_at, id
                """, (row, index) -> row.getObject("id", UUID.class), continuation.id());
        if (signals.isEmpty()) {
            throw new IllegalStateException("CURRENT_REVIEW_SIGNAL_MISSING");
        }
        if (!"STAFF_REVIEW_REQUIRED".equals(state.status())) {
            UUID event = UUID.randomUUID();
            database.update("UPDATE pf_case SET status = 'STAFF_REVIEW_REQUIRED', version = version + 1 WHERE id = ?", state.id());
            database.update("UPDATE pf_case_wait SET state = 'REVIEW', ended_at = CURRENT_TIMESTAMP WHERE case_id = ? AND state = 'ACTIVE'", state.id());
            database.update("""
                    INSERT INTO pf_case_status_event (id, case_id, review_signal_id, from_status, to_status, case_version)
                    VALUES (?, ?, ?, ?, 'STAFF_REVIEW_REQUIRED', ?)
                    """, event, state.id(), signals.getFirst(), state.status(), state.version() + 1);
            database.update("""
                    INSERT INTO pf_case_mail_order (id, case_id, event_id, access_id, notification_type)
                    SELECT ?, case_id, ?, access_id, 'STATUS_CHANGED' FROM pf_acceptance_mail_order WHERE case_id = ?
                    """, UUID.randomUUID(), event, state.id());
        }
        database.update("UPDATE pf_case_review_signal SET state = 'CONSUMED' WHERE continuation_id = ? AND state = 'CONFIRMED'", continuation.id());
    }

    public void startReview(CaseState state, Continuation continuation) {
        UUID eventId = UUID.randomUUID();
        if (!"STAFF_REVIEW_REQUIRED".equals(state.status())) {
            database.update("UPDATE pf_case SET status = 'STAFF_REVIEW_REQUIRED', version = version + 1 WHERE id = ?", state.id());
            database.update("""
                INSERT INTO pf_case_status_event (id, case_id, continuation_id, from_status, to_status, case_version)
                VALUES (?, ?, ?, ?, 'STAFF_REVIEW_REQUIRED', ?)
                """, eventId, state.id(), continuation.id(), state.status(), state.version() + 1);
            database.update("""
                INSERT INTO pf_case_mail_order (id, case_id, event_id, access_id, notification_type)
                SELECT ?, case_id, ?, access_id, 'STATUS_CHANGED' FROM pf_acceptance_mail_order WHERE case_id = ?
                """, UUID.randomUUID(), eventId, state.id());
        }
        database.update("UPDATE pf_staff_continuation SET review_applied = TRUE WHERE id = ?", continuation.id());
        database.update("UPDATE pf_case_review_signal SET state = 'CONSUMED' WHERE continuation_id = ? AND state = 'CONFIRMED'",
                continuation.id());
    }

    public record SignalDelivery(UUID id, String caseId, long leader, String tenant) {
    }

    public record Signal(UUID id, String state, Long leader, UUID continuationId) {
    }

    public record Continuation(UUID id, String caseId, long leader, UUID callRef, long caseVersion, Long claimElementKey,
            Long childKey, Long parentElementKey, boolean reviewApplied, String origin, String entryKind) {
    }
}
