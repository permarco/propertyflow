package com.pertegato.propertyflow.caseprocessing.persistence;

import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.CaseState;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.Entry;
import com.pertegato.propertyflow.recommendation.service.QuestionCatalog;
import java.util.Arrays;
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
public class AutomaticQuestionStore {

    private final JdbcTemplate database;

    public Optional<Batch> find(UUID entry, UUID evaluation) {
        return rows("automatic_entry_id = ? AND evaluation_id = ?", entry, evaluation).stream().findFirst();
    }

    public Batch get(UUID id) {
        return rows("id = ?", id).stream().findFirst().orElseThrow();
    }

    private List<Batch> rows(String condition, Object... arguments) {
        return database.query("SELECT * FROM pf_automatic_question_batch WHERE " + condition,
                (row, index) -> new Batch(row.getObject("id", UUID.class), row.getString("case_id"),
                        row.getLong("leading_instance_key"), row.getObject("automatic_entry_id", UUID.class),
                        row.getObject("evaluation_id", UUID.class), row.getLong("case_version"), row.getString("catalog_version"),
                        row.getString("question_ids").isEmpty() ? List.of() : Arrays.asList(row.getString("question_ids").split(",")),
                        row.getString("state"), row.getObject("continuation_id", UUID.class)), arguments);
    }

    public Batch prepare(CaseState state, Entry entry, UUID evaluation, List<String> questions, String result, String reason) {
        UUID id = UUID.randomUUID();
        database.update("""
                INSERT INTO pf_automatic_question_batch (id, case_id, leading_instance_key, automatic_entry_id, evaluation_id,
                    case_version, catalog_version, question_ids, state, reason)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, state.id(), entry.leadingKey(), entry.id(), evaluation, state.version(), QuestionCatalog.VERSION,
                String.join(",", questions), result, reason);
        return get(id);
    }

    public boolean known(String caseId, String question) {
        return Boolean.TRUE.equals(database.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM pf_staff_message WHERE case_id = ? AND
                    (catalog_question_id = ? OR lower(trim(body)) = lower(trim(?))))
                """, Boolean.class, caseId, question, QuestionCatalog.QUESTIONS.get(question)));
    }

    public boolean open(String caseId) {
        return Boolean.TRUE.equals(database.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pf_staff_answer_requirement WHERE case_id = ? AND state = 'OPEN')", Boolean.class, caseId));
    }

    public String restriction(String caseId, long leader) {
        return database.queryForObject("""
                SELECT CASE WHEN assigned_unit IS NULL THEN 'OBJECT_ASSIGNMENT_REQUIRED'
                    WHEN urgency = 'CRITICAL' THEN 'CRITICAL_URGENCY'
                    WHEN EXISTS (SELECT 1 FROM pf_case_review_signal WHERE case_id = ? AND (leading_instance_key = ? OR leading_instance_key IS NULL)
                        AND state IN ('PENDING', 'CONFIRMED') AND considered_decision_id IS NULL AND considered_wait_command_id IS NULL
                            AND considered_direct_command_id IS NULL)
                        THEN 'HUMAN_DECISION_REQUIRED' ELSE NULL END FROM pf_case WHERE id = ?
                """, String.class, caseId, leader, caseId);
    }

    public void stale(UUID id) {
        database.update("UPDATE pf_automatic_question_batch SET state = 'STALE', reason = 'CASE_CHANGED' WHERE id = ? AND state = 'PREPARED'", id);
    }

    public void requireReview(UUID id, String reason) {
        database.update("UPDATE pf_automatic_question_batch SET state = 'REVIEW', reason = ? WHERE id = ? AND state = 'PREPARED'", reason, id);
    }

    public void publish(Batch batch, long child) {
        long lastSequence = database.queryForObject("""
                UPDATE pf_case SET version = version + 1, analysis_version = analysis_version + ? WHERE id = ? RETURNING analysis_version
                """, Long.class, batch.questions().size(), batch.caseId());
        for (int index = 0; index < batch.questions().size(); index++) {
            String question = batch.questions().get(index);
            UUID message = index == 0 ? batch.id() : UUID.randomUUID();
            database.update("""
                    INSERT INTO pf_staff_message (id, case_id, author, body, answer_required, analysis_version, automatic_batch_id, catalog_question_id)
                    VALUES (?, ?, 'SYSTEM', ?, TRUE, ?, ?, ?)
                    """, message, batch.caseId(), QuestionCatalog.QUESTIONS.get(question),
                    lastSequence - batch.questions().size() + index + 1, batch.id(), question);
            database.update("INSERT INTO pf_staff_answer_requirement (message_id, case_id) VALUES (?, ?)", message, batch.caseId());
            database.update("INSERT INTO pf_staff_message_mail_order (id, case_id) VALUES (?, ?)", message, batch.caseId());
        }
        database.update("""
                INSERT INTO pf_reply_entry (id, case_id, leading_instance_key, source_automatic_instance_key, consumed_version, automatic_batch_id)
                VALUES (?, ?, ?, ?, ?, ?)
                """, batch.id(), batch.caseId(), batch.leader(), child, batch.version() + 1, batch.id());
        database.update("UPDATE pf_automatic_question_batch SET state = 'PUBLISHED', published_at = CURRENT_TIMESTAMP WHERE id = ?", batch.id());
    }

    public void review(UUID id, UUID continuation, UUID call) {
        database.update("UPDATE pf_automatic_question_batch SET continuation_id = ? WHERE id = ?", continuation, id);
        database.update("UPDATE pf_automatic_entry SET effects_blocked = TRUE WHERE call_ref = ?", call);
        database.update("""
                UPDATE pf_case_review_signal SET state = 'CONFIRMED', continuation_id = ?,
                    leading_instance_key = (SELECT leading_instance_key FROM pf_automatic_question_batch WHERE id = ?)
                WHERE case_id = (SELECT case_id FROM pf_automatic_question_batch WHERE id = ?) AND state IN ('PENDING', 'CONFIRMED')
                    AND considered_decision_id IS NULL AND considered_wait_command_id IS NULL AND considered_direct_command_id IS NULL
                    AND (leading_instance_key IS NULL OR leading_instance_key = (SELECT leading_instance_key FROM pf_automatic_question_batch WHERE id = ?))
                """, continuation, id, id, id);
    }

    public void waitEntry(Batch batch, long child, long version) {
        database.update("""
                INSERT INTO pf_reply_entry (id, case_id, leading_instance_key, source_automatic_instance_key, consumed_version, automatic_batch_id)
                VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING
                """, batch.id(), batch.caseId(), batch.leader(), child, version, batch.id());
    }

    public record Batch(UUID id, String caseId, long leader, UUID entry, UUID evaluation, long version, String catalog,
            List<String> questions, String state, UUID continuation) {
    }
}
