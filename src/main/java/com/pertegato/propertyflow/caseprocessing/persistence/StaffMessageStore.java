package com.pertegato.propertyflow.caseprocessing.persistence;

import com.pertegato.propertyflow.caseprocessing.contract.PublicAuthor;
import com.pertegato.propertyflow.caseprocessing.contract.PublicMessage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@Profile("propertyflow-backend & !frontend-demo")
public class StaffMessageStore {

    private static final RowMapper<Command> COMMAND = (row, index) -> new Command(row.getObject("id", UUID.class),
            row.getString("case_id"), row.getString("operation_id"), row.getObject("actor_id", UUID.class),
            row.getLong("expected_version"), row.getLong("leading_instance_key"), row.getString("tenant_id"),
            row.getLong("target_instance_key"), row.getObject("parent_element_key", Long.class), row.getObject("user_task_key", Long.class),
            row.getString("task_element"), row.getObject("automatic_call_ref", UUID.class), row.getString("body"),
            row.getBoolean("answer_required"), row.getString("state"), row.getBoolean("correlated"), row.getString("result_code"));
    private final JdbcTemplate database;

    public StaffMessageStore(JdbcTemplate database) {
        this.database = database;
    }

    public void bindReply(UUID command, UUID call) {
        database.update("UPDATE pf_staff_message_command SET reply_call_id = ? WHERE id = ?", call, command);
    }

    public Optional<UUID> replyFor(UUID command) {
        return Optional.ofNullable(database.queryForObject("SELECT reply_call_id FROM pf_staff_message_command WHERE id = ?", UUID.class, command));
    }

    public Optional<Command> find(String caseId, String operation) {
        return database.query("SELECT * FROM pf_staff_message_command WHERE case_id = ? AND operation_id = ? AND operation_reserved",
                COMMAND, caseId, operation).stream().findFirst();
    }

    public Command get(UUID id) {
        return database.query("SELECT * FROM pf_staff_message_command WHERE id = ?", COMMAND, id).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("UNKNOWN_STAFF_MESSAGE"));
    }

    public Optional<Command> latest(String caseId, String operation) {
        return database.query("""
                SELECT * FROM pf_staff_message_command WHERE case_id = ? AND operation_id = ?
                ORDER BY operation_reserved DESC, created_at DESC, id DESC LIMIT 1
                """, COMMAND, caseId, operation).stream().findFirst();
    }

    public Command prepare(String caseId, String operation, UUID actor, long version, long leader, String tenant, long target,
            Long parent, Long task, String element, UUID automatic, String text, boolean answer) {
        UUID id = UUID.randomUUID();
        database.update("""
                INSERT INTO pf_staff_message_command (id, case_id, operation_id, actor_id, expected_version,
                    leading_instance_key, tenant_id, target_instance_key, parent_element_key, user_task_key,
                    task_element, automatic_call_ref, body, answer_required)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, caseId, operation, actor, version, leader, tenant, target, parent, task, element, automatic, text, answer);
        return get(id);
    }

    public UUID questionCommand(UUID decision) {
        return database.queryForObject("SELECT id FROM pf_staff_message_command WHERE native_decision_id = ?", UUID.class, decision);
    }

    public void bindDecision(UUID command, UUID decision) {
        database.update("UPDATE pf_staff_message_command SET native_decision_id = ? WHERE id = ?", decision, command);
    }

    public Optional<UUID> decision(UUID command) {
        return database.query("SELECT native_decision_id FROM pf_staff_message_command WHERE id = ? AND native_decision_id IS NOT NULL",
                (row, index) -> row.getObject("native_decision_id", UUID.class), command).stream().findFirst();
    }

    public boolean nativeAttempt(UUID command) {
        return database.update("UPDATE pf_staff_message_command SET correlated = TRUE WHERE id = ? AND state = 'PENDING' AND NOT correlated", command) == 1;
    }

    public void replyEntry(Command command) {
        database.update("""
                INSERT INTO pf_reply_entry (id, case_id, leading_instance_key, source_staff_instance_key, consumed_version, staff_message_id)
                SELECT ?, id, ?, ?, version, ? FROM pf_case WHERE id = ? ON CONFLICT DO NOTHING
                """, command.id(), command.leader(), command.target(), command.id(), command.caseId());
    }

    public void bindWait(UUID command, UUID wait) {
        database.update("UPDATE pf_staff_message_command SET wait_id = ? WHERE id = ?", wait, command);
    }

    public Optional<UUID> waitFor(UUID command) {
        return database.query("SELECT wait_id FROM pf_staff_message_command WHERE id = ? AND wait_id IS NOT NULL",
                (row, index) -> row.getObject("wait_id", UUID.class), command).stream().findFirst();
    }

    public boolean memoOperationUsed(String caseId, String operation) {
        return Boolean.TRUE.equals(database.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM pf_case_data_command WHERE case_id = ? AND operation_id = ?
                    AND action_type = 'ADD_MEMO' AND operation_reserved)
                """, Boolean.class, caseId, operation));
    }

    public boolean decisionStarted(Command command) {
        return Boolean.TRUE.equals(database.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM pf_staff_decision WHERE case_id = ? AND user_task_key = ? AND state <> 'REJECTED')
                """, Boolean.class, command.caseId(), command.task()));
    }

    public String correlationKey(Command command) {
        if (command.automatic() != null) {
            return Long.toString(command.leader());
        }
        return database.queryForObject("""
                SELECT id::text FROM pf_staff_continuation WHERE case_id = ? AND leading_instance_key = ?
                    AND child_instance_key = ? AND parent_element_key = ?
                """, String.class, command.caseId(), command.leader(), command.target(), command.parent());
    }

    public void correlated(UUID id) {
        database.update("UPDATE pf_staff_message_command SET correlated = TRUE WHERE id = ?", id);
    }

    public void reject(UUID id, String code, boolean releaseOperation) {
        database.update("""
                UPDATE pf_staff_message_command SET state = 'REJECTED', result_code = ?, operation_reserved = ?
                WHERE id = ? AND state = 'PENDING'
                """, code, !releaseOperation, id);
    }

    public void publish(Command command) {
        long analysisVersion = database.queryForObject("""
                UPDATE pf_case SET version = version + 1, analysis_version = analysis_version + 1
                WHERE id = ? RETURNING analysis_version
                """, Long.class, command.caseId());
        database.update("""
                INSERT INTO pf_staff_message (id, staff_command_id, case_id, body, answer_required, analysis_version) VALUES (?, ?, ?, ?, ?, ?)
                """, command.id(), command.id(), command.caseId(), command.text(), command.answerRequired(), analysisVersion);
        if (command.answerRequired()) {
            database.update("INSERT INTO pf_staff_answer_requirement (message_id, case_id) VALUES (?, ?)", command.id(), command.caseId());
        }
        database.update("INSERT INTO pf_staff_message_mail_order (id, case_id) VALUES (?, ?)", command.id(), command.caseId());
        if (command.operation().startsWith("mcp:")) {
            database.update("""
                    INSERT INTO pf_mcp_audit (id, action, account_id, client_id, case_id, operation_id, outcome)
                    SELECT ?, action, account_id, client_id, case_id, operation_id, 'PUBLISHED'
                    FROM pf_mcp_audit WHERE operation_id = ? AND account_id = ? AND case_id = ?
                    AND action = 'STAFF_MESSAGES' AND outcome = 'PREPARED'
                    """, UUID.randomUUID(), UUID.fromString(command.operation().substring(4)), command.actor(), command.caseId());
        }
        database.update("UPDATE pf_staff_message_command SET state = 'PUBLISHED' WHERE id = ?", command.id());
    }

    public List<PublicMessage> history(String caseId) {
        return database.query("""
                SELECT m.id, m.author, m.body, m.occurred_at, coalesce(q.state, 'NONE') AS answer_state, q.resolved_at, q.resolution_type
                FROM pf_staff_message m LEFT JOIN pf_staff_answer_requirement q ON q.message_id = m.id
                WHERE m.case_id = ? AND m.author IN ('STAFF', 'SYSTEM') AND m.visibility = 'EXTERNAL' AND m.published
                ORDER BY m.occurred_at, m.id
                """, (row, index) -> new PublicMessage(row.getObject("id", UUID.class).toString(), PublicAuthor.valueOf(row.getString("author")),
                        row.getString("body"), row.getTimestamp("occurred_at").toInstant(),
                        PublicMessage.AnswerState.valueOf(row.getString("answer_state")),
                        row.getTimestamp("resolved_at") == null ? null : row.getTimestamp("resolved_at").toInstant(),
                        row.getString("resolution_type")), caseId);
    }

    public record Command(UUID id, String caseId, String operation, UUID actor, long version, long leader, String tenant,
            long target, Long parent, Long task, String element, UUID automatic, String text, boolean answerRequired,
            String state, boolean correlated, String resultCode) {
        @Override
        public String toString() {
            return "Staff message command " + id + " [content redacted]";
        }
    }
}
