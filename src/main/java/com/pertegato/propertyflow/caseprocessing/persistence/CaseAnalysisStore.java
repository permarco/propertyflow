package com.pertegato.propertyflow.caseprocessing.persistence;

import com.pertegato.propertyflow.caseprocessing.contract.Urgency;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("propertyflow-backend & !frontend-demo")
public class CaseAnalysisStore {

    private final JdbcTemplate database;

    public CaseAnalysisStore(JdbcTemplate database) {
        this.database = database;
    }

    public Input input(String caseId) {
        Input original = database.queryForObject("SELECT description, initial_message_id, analysis_version FROM pf_case WHERE id = ?",
                (row, index) -> new Input(row.getString("description"), row.getObject("initial_message_id", UUID.class),
                        row.getLong("analysis_version")), caseId);
        List<Input> additions = database.query("""
                SELECT body, id, analysis_version, 'Mieternachricht' AS origin FROM pf_tenant_message
                WHERE case_id = ? AND visibility = 'EXTERNAL' AND published
                UNION ALL
                SELECT body, id, analysis_version, 'Nachricht der Immobilienverwaltung' AS origin FROM pf_staff_message
                WHERE case_id = ? AND visibility = 'EXTERNAL' AND published
                ORDER BY analysis_version, id
                """, (row, index) -> new Input(row.getString("origin") + ":\n" + row.getString("body"),
                        row.getObject("id", UUID.class), row.getLong("analysis_version")), caseId, caseId);
        if (additions.isEmpty()) {
            return original;
        }
        StringBuilder text = new StringBuilder(original.text());
        for (Input message : additions) {
            text.append("\n\n").append(message.text());
        }
        // Never silently truncate new information. The bounded model contract can reject an oversized history.
        return new Input(text.toString(), additions.getLast().messageId(), original.version());
    }

    public Optional<Analysis> find(UUID callRef, long inputVersion) {
        return database.query("SELECT * FROM pf_case_analysis WHERE call_ref = ? AND input_version = ?",
                (row, index) -> new Analysis(row.getObject("id", UUID.class), row.getObject("attempt_id", UUID.class),
                        row.getString("state"), row.getTimestamp("started_at").toInstant(), row.getLong("input_version"),
                        row.getString("system_urgency"), row.getString("error_code")), callRef, inputVersion).stream().findFirst();
    }

    public Analysis begin(String caseId, UUID callRef, Input input, String promptVersion, long jobKey) {
        UUID id = UUID.randomUUID();
        UUID attempt = UUID.randomUUID();
        database.update("""
                INSERT INTO pf_case_analysis (id, case_id, call_ref, source_message_id, input_version, attempt_id, state, prompt_version, job_key)
                VALUES (?, ?, ?, ?, ?, ?, 'RUNNING', ?, ?)
                ON CONFLICT (call_ref, input_version) DO UPDATE
                SET attempt_id = EXCLUDED.attempt_id, started_at = CURRENT_TIMESTAMP, state = 'RUNNING', job_key = EXCLUDED.job_key,
                    completed_at = NULL, system_urgency = NULL, error_code = NULL
                """, id, caseId, callRef, input.messageId(), input.version(), attempt, promptVersion, jobKey);
        return find(callRef, input.version()).orElseThrow();
    }

    public void finish(UUID id, String state, Urgency urgency, String error) {
        database.update("""
                UPDATE pf_case_analysis SET state = ?, system_urgency = ?, error_code = ?, completed_at = CURRENT_TIMESTAMP WHERE id = ?
                """, state, urgency == null ? null : urgency.name(), error, id);
    }

    public Optional<UUID> failure(UUID callRef) {
        return database.query("""
                SELECT a.id FROM pf_case_analysis a JOIN pf_case c ON c.id = a.case_id
                WHERE a.call_ref = ? AND a.state = 'FAILED' AND a.input_version = c.analysis_version
                ORDER BY a.completed_at DESC LIMIT 1
                """, (row, index) -> row.getObject("id", UUID.class), callRef).stream().findFirst();
    }

    public record Input(String text, UUID messageId, long version) {
        @Override
        public String toString() {
            return "Analysis input [redacted]";
        }
    }

    public record Analysis(UUID id, UUID attemptId, String state, Instant startedAt, long inputVersion, String urgency, String error) {
    }
}
