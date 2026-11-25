package com.pertegato.propertyflow.caseprocessing.persistence;

import com.pertegato.propertyflow.caseprocessing.contract.Urgency;
import com.pertegato.propertyflow.caseprocessing.contract.UrgencyChange;
import com.pertegato.propertyflow.caseprocessing.contract.UrgencySource;
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
public class UrgencyStore {

    private final JdbcTemplate database;

    public Current current(String caseId) {
        return database.queryForObject("SELECT urgency, urgency_source, status, version FROM pf_case WHERE id = ? FOR UPDATE",
                (row, index) -> new Current(row.getString("urgency") == null ? null : Urgency.valueOf(row.getString("urgency")),
                        row.getString("urgency_source") == null ? null : UrgencySource.valueOf(row.getString("urgency_source")),
                        row.getString("status"), row.getLong("version")), caseId);
    }

    public void manual(String caseId, UUID commandId, UUID actor, Urgency next, String reason, Current previous,
            boolean publicChange, boolean reviewRequired) {
        UUID event = UUID.randomUUID();
        database.update("UPDATE pf_case SET urgency = ?, urgency_source = 'STAFF', version = version + 1 WHERE id = ?", next.name(), caseId);
        database.update("""
                INSERT INTO pf_urgency_event (id, case_id, command_id, previous_urgency, current_urgency, source, actor_id,
                    internal_reason, public_change) VALUES (?, ?, ?, ?, ?, 'STAFF', ?, ?, ?)
                """, event, caseId, commandId, previous.urgency() == null ? null : previous.urgency().name(), next.name(), actor,
                reason == null || reason.isBlank() ? null : reason, publicChange);
        if (publicChange) {
            database.update("INSERT INTO pf_urgency_mail_order (id, case_id, event_id) VALUES (?, ?, ?)", UUID.randomUUID(), caseId, event);
        }
        if (reviewRequired) {
            database.update("""
                    INSERT INTO pf_case_review_signal (id, case_id, source_type, source_ref, reason, leading_instance_key)
                    VALUES (?, ?, 'MANUAL_URGENCY', ?, 'CRITICAL_URGENCY',
                        (SELECT process_instance_key FROM pf_case_process WHERE case_id = ?)) ON CONFLICT DO NOTHING
                    """, UUID.randomUUID(), caseId, commandId, caseId);
        }
    }

    public void conversation(String caseId, UUID evaluationId, Urgency next) {
        Current previous = current(caseId);
        if (previous.source() == UrgencySource.STAFF || previous.urgency() == next) {
            return;
        }
        UUID event = UUID.randomUUID();
        database.update("UPDATE pf_case SET urgency = ?, urgency_source = 'SYSTEM', version = version + 1 WHERE id = ?", next.name(), caseId);
        database.update("""
                INSERT INTO pf_urgency_event (id, case_id, evaluation_id, previous_urgency, current_urgency, source, public_change)
                VALUES (?, ?, ?, ?, ?, 'SYSTEM', TRUE)
                """, event, caseId, evaluationId, previous.urgency() == null ? null : previous.urgency().name(), next.name());
        database.update("INSERT INTO pf_urgency_mail_order (id, case_id, event_id) VALUES (?, ?, ?)", UUID.randomUUID(), caseId, event);
    }

    public List<UrgencyChange> publicHistory(String caseId) {
        return database.query("""
                SELECT id, previous_urgency, current_urgency, source, occurred_at FROM pf_urgency_event
                WHERE case_id = ? AND public_change ORDER BY occurred_at, id
                """, (row, index) -> new UrgencyChange(row.getObject("id", UUID.class).toString(),
                        Optional.ofNullable(row.getString("previous_urgency")).map(Urgency::valueOf),
                        Urgency.valueOf(row.getString("current_urgency")), UrgencySource.valueOf(row.getString("source")),
                        row.getTimestamp("occurred_at").toInstant()), caseId);
    }

    public record Current(Urgency urgency, UrgencySource source, String status, long version) {
    }
}
