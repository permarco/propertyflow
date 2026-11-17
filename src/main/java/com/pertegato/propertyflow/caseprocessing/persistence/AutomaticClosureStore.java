package com.pertegato.propertyflow.caseprocessing.persistence;

import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.CaseState;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.Entry;
import com.pertegato.propertyflow.caseprocessing.persistence.ConversationEvaluationStore.Evaluation;
import com.pertegato.propertyflow.recommendation.service.ClosureAssessment;
import com.pertegato.propertyflow.recommendation.service.ClosureMessage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
@Profile("propertyflow-backend & !frontend-demo")
public class AutomaticClosureStore {

    private final JdbcTemplate database;
    private final JsonMapper json = JsonMapper.builder().build();

    public AutomaticClosureStore(JdbcTemplate database) {
        this.database = database;
    }

    public Optional<Command> find(UUID entry, UUID evaluation) {
        return rows("automatic_entry_id = ? AND evaluation_id = ?", entry, evaluation).stream().findFirst();
    }

    public Optional<Command> find(UUID id) {
        return rows("id = ?", id).stream().findFirst();
    }

    public Command get(UUID id) {
        return find(id).orElseThrow();
    }

    private List<Command> rows(String condition, Object... arguments) {
        return database.query("SELECT * FROM pf_automatic_closure WHERE " + condition,
                (row, index) -> new Command(row.getObject("id", UUID.class), row.getString("case_id"),
                        row.getLong("leading_instance_key"), row.getObject("automatic_entry_id", UUID.class),
                        row.getObject("evaluation_id", UUID.class), row.getLong("case_version"), row.getLong("analysis_version"),
                        row.getString("basis_hash"), json.readValue(row.getString("ground"), ClosureAssessment.Result.class),
                        row.getString("state"), row.getObject("attempt_id", UUID.class),
                        row.getTimestamp("started_at") == null ? null : row.getTimestamp("started_at").toInstant(),
                        row.getObject("publication_id", UUID.class), row.getObject("published_version", Long.class),
                        row.getObject("published_analysis_version", Long.class), row.getObject("continuation_id", UUID.class)), arguments);
    }

    public Command prepare(CaseState state, Entry entry, Evaluation evaluation, String hash) {
        UUID id = UUID.randomUUID();
        database.update("""
                INSERT INTO pf_automatic_closure (id, case_id, leading_instance_key, automatic_entry_id, evaluation_id,
                    case_version, analysis_version, basis_hash, ground, state)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PREPARED')
                """, id, state.id(), entry.leadingKey(), entry.id(), evaluation.id(), state.version(), evaluation.analysisVersion(),
                hash, json.writeValueAsString(evaluation.outcome().closureAssessment()));
        return get(id);
    }

    public UUID published(Command command) {
        return database.query("""
                SELECT id FROM pf_closure_publication WHERE case_id = ? AND leading_instance_key = ? AND basis_hash = ?
                """, (row, index) -> row.getObject(1, UUID.class), command.caseId(), command.leader(), command.hash())
                .stream().findFirst().orElse(null);
    }

    public boolean staffActionPending(String caseId, long leader) {
        return Boolean.TRUE.equals(database.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM pf_case_data_command WHERE case_id = ? AND leading_instance_key = ? AND state = 'PENDING')
                    OR EXISTS (SELECT 1 FROM pf_staff_message_command WHERE case_id = ? AND leading_instance_key = ? AND state = 'PENDING')
                """, Boolean.class, caseId, leader, caseId, leader));
    }

    public Command begin(UUID id) {
        database.update("UPDATE pf_automatic_closure SET state = 'RUNNING', attempt_id = ?, started_at = CURRENT_TIMESTAMP WHERE id = ?",
                UUID.randomUUID(), id);
        return get(id);
    }

    public void state(UUID id, String state) {
        database.update("UPDATE pf_automatic_closure SET state = ? WHERE id = ?", state, id);
    }

    public void publish(Command command, ClosureMessage.Result message) {
        UUID publication = UUID.randomUUID();
        database.update("""
                INSERT INTO pf_closure_publication (id, case_id, leading_instance_key, basis_hash, model, prompt_version, validation_version)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, publication, command.caseId(), command.leader(), command.hash(), message.model(), message.promptVersion(),
                message.validationVersion());
        long sequence = database.queryForObject("""
                UPDATE pf_case SET version = version + 1, analysis_version = analysis_version + 1 WHERE id = ? RETURNING analysis_version
                """, Long.class, command.caseId());
        database.update("""
                INSERT INTO pf_staff_message (id, case_id, author, body, answer_required, analysis_version, closure_publication_id)
                VALUES (?, ?, 'SYSTEM', ?, FALSE, ?, ?)
                """, publication, command.caseId(), message.text(), sequence, publication);
        database.update("INSERT INTO pf_staff_message_mail_order (id, case_id) VALUES (?, ?)", publication, command.caseId());
        reuse(command.id(), publication, command.version() + 1, sequence);
    }

    public void reuse(UUID command, UUID publication, long version, long analysis) {
        database.update("""
                UPDATE pf_automatic_closure SET state = 'PUBLISHED', publication_id = ?, published_version = ?,
                    published_analysis_version = ? WHERE id = ?
                """, publication, version, analysis, command);
    }

    public void review(Command command, UUID continuation, UUID call) {
        database.update("UPDATE pf_automatic_closure SET state = 'REVIEW', continuation_id = ? WHERE id = ?", continuation, command.id());
        database.update("UPDATE pf_automatic_entry SET effects_blocked = TRUE WHERE call_ref = ?", call);
        database.update("""
                UPDATE pf_case_review_signal SET state = 'CONFIRMED', continuation_id = ?, leading_instance_key = ?
                WHERE case_id = ? AND state IN ('PENDING', 'CONFIRMED') AND considered_decision_id IS NULL
                    AND considered_wait_command_id IS NULL AND considered_direct_command_id IS NULL
                    AND (leading_instance_key IS NULL OR leading_instance_key = ?)
                """, continuation, command.leader(), command.caseId(), command.leader());
    }

    public record Command(UUID id, String caseId, long leader, UUID entry, UUID evaluation, long version, long analysisVersion,
            String hash, ClosureAssessment.Result ground, String state, UUID attempt, Instant started, UUID publication,
            Long publishedVersion, Long publishedAnalysisVersion, UUID continuation) {
    }
}
