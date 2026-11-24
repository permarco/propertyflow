package com.pertegato.propertyflow.caseprocessing.persistence;

import com.pertegato.propertyflow.caseprocessing.contract.InternalMemo;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@Profile("propertyflow-backend & !frontend-demo")
@RequiredArgsConstructor
public class CaseDataStore {

    private static final RowMapper<Command> COMMAND = (row, index) -> new Command(row.getObject("id", UUID.class),
            row.getString("operation_id"), row.getString("case_id"), row.getObject("actor_id", UUID.class),
            row.getString("payload"), row.getLong("expected_version"), row.getLong("leading_instance_key"), row.getString("tenant_id"),
            row.getString("state"), row.getString("result_code"), row.getObject("result_version", Long.class),
            row.getString("action_type"), row.getString("reason"), row.getString("request_fingerprint"), row.getBoolean("correlated"));
    private final JdbcTemplate database;

    public Optional<Command> find(String caseId, UUID actor, String operationId) {
        return database.query("SELECT * FROM pf_case_data_command WHERE case_id = ? AND operation_id = ? AND operation_reserved",
                COMMAND, caseId, operationId).stream().findFirst();
    }

    public Optional<Command> latest(String caseId, String operationId) {
        return database.query("""
                SELECT * FROM pf_case_data_command WHERE case_id = ? AND operation_id = ?
                ORDER BY operation_reserved DESC, created_at DESC, id DESC LIMIT 1
                """, COMMAND, caseId, operationId).stream().findFirst();
    }

    public void rejectCorrelation(UUID id, String code) {
        database.update("""
                UPDATE pf_case_data_command SET state = 'REJECTED', result_code = ?, reason = NULL,
                    operation_reserved = FALSE, completed_at = CURRENT_TIMESTAMP
                WHERE id = ? AND state = 'PENDING' AND NOT correlated
                """, code, id);
    }

    public Optional<Command> find(String caseId, UUID actor, UUID operationId) {
        return find(caseId, actor, operationId.toString());
    }

    public Command get(UUID id) {
        return database.query("SELECT * FROM pf_case_data_command WHERE id = ?", COMMAND, id).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("UNKNOWN_CASE_DATA_COMMAND"));
    }

    public boolean externalOperationUsed(String caseId, String operation) {
        return Boolean.TRUE.equals(database.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM pf_staff_message_command WHERE case_id = ? AND operation_id = ? AND operation_reserved)
                """, Boolean.class, caseId, operation));
    }

    public Optional<Leader> leader(String caseId) {
        return database.query("SELECT process_instance_key, tenant_id FROM pf_case_process WHERE case_id = ?",
                (row, index) -> new Leader(row.getLong(1), row.getString(2)), caseId).stream().findFirst();
    }

    public Command insert(String caseId, UUID actor, String operationId, long version, String type, String text, String reason,
            String fingerprint, Leader leader) {
        UUID id = UUID.randomUUID();
        database.update("""
                INSERT INTO pf_case_data_command (id, operation_id, case_id, actor_id, action_type, payload, expected_version,
                    leading_instance_key, tenant_id, reason, request_fingerprint) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, operationId, caseId, actor, type, text, version, leader.key(), leader.tenant(), reason, fingerprint);
        return get(id);
    }

    public void reject(UUID id, String code) {
        database.update("UPDATE pf_case_data_command SET state = 'REJECTED', result_code = ?, reason = NULL, completed_at = CURRENT_TIMESTAMP WHERE id = ? AND state = 'PENDING'",
                code, id);
    }

    public void apply(Command command, long version) {
        database.update("INSERT INTO pf_internal_memo (id, case_id, actor_id, text) VALUES (?, ?, ?, ?)",
                command.id(), command.caseId(), command.actor(), command.text());
        database.update("UPDATE pf_case SET version = version + 1 WHERE id = ?", command.caseId());
        complete(command.id(), version + 1);
    }

    public void complete(UUID id, long version) {
        database.update("""
                UPDATE pf_case_data_command SET state = 'APPLIED', result_version = ?, reason = NULL,
                    completed_at = CURRENT_TIMESTAMP WHERE id = ?
                """, version, id);
    }

    public void assign(Command command, long version) {
        String previous = database.queryForObject("SELECT assigned_unit FROM pf_case WHERE id = ?", String.class, command.caseId());
        if (command.text().equals(previous)) {
            complete(command.id(), version);
            return;
        }
        database.update("""
                INSERT INTO pf_assignment_event (id, case_id, actor_id, previous_assignment, current_assignment) VALUES (?, ?, ?, ?, ?)
                """, command.id(), command.caseId(), command.actor(), previous, command.text());
        database.update("UPDATE pf_case SET assigned_unit = ?, version = version + 1, analysis_version = analysis_version + 1 WHERE id = ?",
                command.text(), command.caseId());
        complete(command.id(), version + 1);
    }

    public List<String> assignmentSuggestions(String query) {
        return database.query("""
                SELECT DISTINCT assigned_unit FROM pf_case WHERE assigned_unit IS NOT NULL
                    AND position(lower(?) in lower(assigned_unit)) > 0 ORDER BY assigned_unit LIMIT 20
                """, (row, index) -> row.getString("assigned_unit"), query);
    }

    public void correlated(UUID id) {
        database.update("UPDATE pf_case_data_command SET correlated = TRUE WHERE id = ?", id);
    }

    public List<InternalMemo> memos(String caseId) {
        return database.query("SELECT id, text, occurred_at FROM pf_internal_memo WHERE case_id = ? ORDER BY occurred_at, id",
                (row, index) -> new InternalMemo(row.getObject("id", UUID.class).toString(), row.getString("text"),
                        row.getTimestamp("occurred_at").toInstant()), caseId);
    }

    public record Leader(long key, String tenant) {
    }

    public record Command(UUID id, String operationId, String caseId, UUID actor, String text, long expectedVersion, long leader,
            String tenant, String state, String code, Long resultVersion, String type, String reason, String fingerprint, boolean correlated) {
        @Override
        public String toString() {
            return "Case data command " + id + " [payload redacted]";
        }
    }
}
