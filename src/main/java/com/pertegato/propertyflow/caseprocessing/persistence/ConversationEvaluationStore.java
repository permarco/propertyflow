package com.pertegato.propertyflow.caseprocessing.persistence;

import com.pertegato.propertyflow.caseprocessing.contract.CaseRecommendation;
import com.pertegato.propertyflow.recommendation.service.ClosureAssessment;
import com.pertegato.propertyflow.recommendation.service.Recommendation;
import com.pertegato.propertyflow.triage.service.ConversationAnalysis.Answer;
import com.pertegato.propertyflow.triage.service.ConversationAnalysis.Input;
import com.pertegato.propertyflow.triage.service.ConversationAnalysis.Message;
import com.pertegato.propertyflow.triage.service.ConversationAnalysis.Question;
import com.pertegato.propertyflow.triage.service.ConversationAnalysis;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
@Profile("propertyflow-backend & !frontend-demo")
@RequiredArgsConstructor
public class ConversationEvaluationStore {

    private final JdbcTemplate database;
    private final JsonMapper json = JsonMapper.builder().build();

    public boolean replyCurrent(String caseId, UUID entry, long leader, long child, String tenant) {
        return Boolean.TRUE.equals(database.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM pf_reply_call WHERE case_id = ? AND entry_id = ? AND leading_instance_key = ?
                    AND child_instance_key = ? AND tenant_id = ? AND state = 'WAITING')
                """, Boolean.class, caseId, entry, leader, child, tenant));
    }

    public boolean automaticBlocked(UUID callRef) {
        return Boolean.TRUE.equals(database.queryForObject(
                "SELECT effects_blocked FROM pf_automatic_entry WHERE call_ref = ?", Boolean.class, callRef));
    }

    public long analysisVersion(String caseId) {
        return database.queryForObject("SELECT analysis_version FROM pf_case WHERE id = ?", Long.class, caseId);
    }

    public boolean decisionPending(String caseId, long version) {
        return Boolean.TRUE.equals(database.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM pf_staff_decision d JOIN pf_staff_continuation s ON s.id = d.continuation_id
                    JOIN pf_case_process p ON p.case_id = s.case_id AND p.process_instance_key = s.leading_instance_key
                    WHERE d.case_id = ? AND d.expected_version = ? AND d.state IN ('PREPARED', 'CONFIRMED') AND NOT s.superseded)
                """, Boolean.class, caseId, version));
    }

    public boolean contactPending(UUID messageId) {
        return Boolean.TRUE.equals(database.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM pf_wait_contact c JOIN pf_case_wait w ON w.id = c.wait_id
                    WHERE c.message_id = ? AND w.state = 'ACTIVE')
                """, Boolean.class, messageId));
    }

    public Optional<CaseRecommendation> recommendation(String caseId, long version) {
        return find("case_id = ? AND state = 'APPLIED'", caseId).stream().findFirst().map(evaluation -> {
            Recommendation.Result result = evaluation.outcome().recommendation();
            boolean current = evaluation.appliedVersion() != null && evaluation.appliedVersion() == version;
            if (result == null || evaluation.outcome().failure() != null) {
                return new CaseRecommendation("", "", List.of(),
                        "Automatische Empfehlung nicht verfügbar. Mitarbeiterprüfung erforderlich.", current, null);
            }
            return new CaseRecommendation(result.responsibility(), result.procedure(), result.sources().stream()
                    .map(source -> source.title() + " · " + source.id() + " · Fassung " + source.version() + " · " + source.location()).toList(),
                    null, current, closureAssessment(evaluation.outcome().closureAssessment()));
        });
    }

    private String closureAssessment(ClosureAssessment.Result assessment) {
        if (assessment == null || assessment.outcome() == ClosureAssessment.Outcome.NONE && assessment.failure() == null) {
            return null;
        }
        if (assessment.failure() != null) {
            return "Die Abschlussbewertung ist nicht verfügbar. Bitte beurteilen Sie den Abschluss selbst.";
        }
        String reason = switch (assessment.outcome()) {
            case RESOLVED -> "Vorgeschlagener Abschlussgrund: Problem behoben.";
            case TENANT_RESPONSIBILITY -> "Vorgeschlagener Abschlussgrund: Kein Bearbeitungsbedarf der Verwaltung.\nRegel: "
                    + assessment.ruleId() + " · Fassung " + assessment.ruleVersion();
            case REVIEW -> "Der Abschlussgrund ist unklar.";
            case NONE -> "";
        };
        String evidence = String.join("\n", assessment.proofs().stream().map(ClosureAssessment.Proof::quote).distinct().toList());
        return reason + "\nBitte anhand des gesamten Fallverlaufs prüfen.\n" + assessment.explanation()
                + (evidence.isEmpty() ? "" : "\nGenannte Belegstellen:\n" + evidence);
    }

    public Input input(String caseId) {
        List<Message> messages = database.query("""
                SELECT initial_message_id AS id, 'TENANT' AS author, description AS body, 0 AS sequence FROM pf_case WHERE id = ?
                UNION ALL SELECT id, 'TENANT', body, analysis_version FROM pf_tenant_message
                    WHERE case_id = ? AND published AND visibility = 'EXTERNAL'
                UNION ALL SELECT id, author, body, analysis_version FROM pf_staff_message
                    WHERE case_id = ? AND published AND visibility = 'EXTERNAL'
                ORDER BY sequence, id
                """, (row, index) -> new Message(row.getObject("id", UUID.class), row.getString("author"), row.getString("body"),
                        row.getLong("sequence")), caseId, caseId, caseId);
        List<Question> questions = database.query("""
                SELECT m.id, m.body, m.analysis_version FROM pf_staff_answer_requirement q
                JOIN pf_staff_message m ON m.id = q.message_id
                WHERE q.case_id = ? AND q.state = 'OPEN' ORDER BY m.analysis_version, m.id
                """, (row, index) -> new Question(row.getObject("id", UUID.class), row.getString("body"),
                        row.getLong("analysis_version")), caseId);
        return new Input(messages, questions);
    }

    public Optional<Evaluation> current(String caseId, long leader, long version, long analysisVersion) {
        return find("case_id = ? AND leading_instance_key = ? AND analysis_version = ? AND (case_version = ? OR applied_version = ?)",
                caseId, leader, analysisVersion, version, version).stream().findFirst();
    }

    public Evaluation get(UUID id) {
        return find("id = ?", id).stream().findFirst().orElseThrow(() -> new IllegalArgumentException("UNKNOWN_EVALUATION"));
    }

    private List<Evaluation> find(String condition, Object... args) {
        return database.query("SELECT * FROM pf_conversation_evaluation WHERE " + condition + " ORDER BY applied_at DESC NULLS LAST",
                (row, index) -> new Evaluation(row.getObject("id", UUID.class), row.getString("case_id"),
                        row.getLong("leading_instance_key"), row.getLong("case_version"), row.getLong("analysis_version"),
                        row.getString("phase"), row.getObject("attempt_id", UUID.class), row.getString("state"),
                        row.getTimestamp("started_at").toInstant(), row.getObject("applied_version", Long.class),
                        row.getString("result_json") == null ? null : json.readValue(row.getString("result_json"), Outcome.class)), args);
    }

    public Evaluation begin(String caseId, long leader, long version, long analysisVersion, String phase, UUID attempt) {
        UUID id = UUID.randomUUID();
        database.update("""
                INSERT INTO pf_conversation_evaluation (id, case_id, leading_instance_key, case_version, analysis_version, phase, attempt_id, state)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'RUNNING')
                ON CONFLICT (case_id, leading_instance_key, case_version, analysis_version)
                DO UPDATE SET attempt_id = EXCLUDED.attempt_id, state = 'RUNNING', started_at = CURRENT_TIMESTAMP, result_json = NULL
                """, id, caseId, leader, version, analysisVersion, phase, attempt);
        return current(caseId, leader, version, analysisVersion).orElseThrow();
    }

    public void checkpoint(UUID id, Outcome outcome) {
        database.update("""
                UPDATE pf_conversation_evaluation SET result_json = ?, started_at = CURRENT_TIMESTAMP WHERE id = ?
                """, json.writeValueAsString(outcome), id);
    }

    public void ready(UUID id, Outcome outcome) {
        database.update("UPDATE pf_conversation_evaluation SET state = 'READY', result_json = ? WHERE id = ?",
                json.writeValueAsString(outcome), id);
    }

    public void supersede(UUID id) {
        database.update("UPDATE pf_conversation_evaluation SET state = 'SUPERSEDED' WHERE id = ? AND state <> 'APPLIED'", id);
    }

    public void applyAnswers(Evaluation evaluation) {
        for (Answer answer : evaluation.outcome().analysis().answers()) {
            database.update("""
                    UPDATE pf_staff_answer_requirement SET state = 'ANSWERED', answered_message_id = ?, evaluation_id = ?,
                        resolved_at = CURRENT_TIMESTAMP, resolution_type = 'ANSWERED', evidence = ?
                    WHERE message_id = ? AND case_id = ? AND state = 'OPEN'
                    """, answer.messageId(), evaluation.id(), answer.evidence(), answer.questionId(), evaluation.caseId());
        }
    }

    public void signal(Evaluation evaluation, String reason) {
        database.update("""
                INSERT INTO pf_case_review_signal (id, case_id, source_type, source_ref, reason, leading_instance_key)
                VALUES (?, ?, 'CONVERSATION', ?, ?, ?) ON CONFLICT DO NOTHING
                """, UUID.randomUUID(), evaluation.caseId(), evaluation.id(), reason, evaluation.leader());
    }

    public void finish(Evaluation evaluation) {
        // Every accepted evaluation changes the decision basis, even when urgency is unchanged.
        database.update("UPDATE pf_case SET version = version + 1 WHERE id = ?", evaluation.caseId());
        database.update("""
                UPDATE pf_conversation_evaluation SET state = 'APPLIED', applied_at = CURRENT_TIMESTAMP,
                    applied_version = (SELECT version FROM pf_case WHERE id = ?) WHERE id = ?
                """, evaluation.caseId(), evaluation.id());
        database.update("""
                UPDATE pf_tenant_message SET evaluation_id = ? WHERE case_id = ? AND leading_instance_key = ? AND analysis_version <= ?
                """, evaluation.id(), evaluation.caseId(), evaluation.leader(), evaluation.analysisVersion());
        database.update("""
                INSERT INTO pf_evaluation_delivery (evaluation_id, case_id, leading_instance_key, case_version, analysis_version)
                SELECT id, case_id, leading_instance_key, applied_version, analysis_version FROM pf_conversation_evaluation WHERE id = ?
                ON CONFLICT DO NOTHING
                """, evaluation.id());
    }

    public boolean allAnswered(String caseId) {
        return Boolean.TRUE.equals(database.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM pf_staff_answer_requirement WHERE case_id = ? AND state = 'ANSWERED')
                AND NOT EXISTS (SELECT 1 FROM pf_staff_answer_requirement WHERE case_id = ? AND state = 'OPEN')
                """, Boolean.class, caseId, caseId));
    }

    public record Outcome(ConversationAnalysis.Result analysis, Recommendation.Result recommendation, ClosureAssessment.Result closureAssessment) {
        public String failure() {
            return analysis.failure() != null ? analysis.failure() : recommendation == null ? "RECOMMENDATION_MISSING" : recommendation.failure();
        }
    }

    public record Evaluation(UUID id, String caseId, long leader, long version, long analysisVersion, String phase, UUID attempt,
            String state, Instant startedAt, Long appliedVersion, Outcome outcome) {
    }
}
