package com.pertegato.propertyflow.caseprocessing.persistence;

import com.pertegato.propertyflow.caseprocessing.contract.InternalChange;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("propertyflow-backend & !frontend-demo")
public class StaffChangeStore {

    private final JdbcTemplate database;

    public StaffChangeStore(JdbcTemplate database) {
        this.database = database;
    }

    public List<InternalChange> history(String caseId) {
        return database.query("""
                WITH changes AS (
                    SELECT id, case_id, actor_id, 'CONTINUE' AS kind, NULL::text AS previous_value,
                        NULL::text AS current_value, internal_reason, completed_at AS occurred_at
                    FROM pf_staff_decision WHERE state = 'APPLIED' AND action = 'CONTINUE' AND case_id = ?
                    UNION ALL
                    SELECT c.id, c.case_id, c.actor_id, CASE WHEN c.source = 'SYSTEM' THEN 'SYSTEM_CLOSE' ELSE 'CLOSE' END, NULL, NULL,
                        CASE WHEN c.source = 'SYSTEM' THEN CASE c.reason WHEN 'RESOLVED' THEN 'Problem behoben.'
                            ELSE 'Kein Bearbeitungsbedarf der Verwaltung nach freigegebener Fachregel.' END
                            ELSE c.reason || coalesce(' ' || f.internal_reason, '') END, c.occurred_at
                    FROM pf_case_closure c LEFT JOIN pf_follow_up_command f ON f.id = c.follow_up_command_id WHERE c.case_id = ?
                    UNION ALL
                    SELECT id, case_id, actor_id, 'URGENCY', previous_urgency, current_urgency, internal_reason, occurred_at
                    FROM pf_urgency_event WHERE source = 'STAFF' AND case_id = ?
                    UNION ALL
                    SELECT id, case_id, actor_id, 'ASSIGNMENT', previous_assignment, current_assignment, NULL, occurred_at
                    FROM pf_assignment_event WHERE case_id = ?
                    UNION ALL
                    SELECT d.id, d.case_id, d.actor_id, d.action, NULL,
                        to_char(r.due_at AT TIME ZONE 'Europe/Zurich', 'DD.MM.YYYY'), d.internal_reason, d.completed_at
                    FROM pf_staff_decision d LEFT JOIN pf_staff_wait_request r ON r.decision_id = d.id
                    WHERE d.case_id = ? AND d.state = 'APPLIED' AND d.action IN ('FOLLOW_UP', 'WAIT_EXTERNAL')
                    UNION ALL
                    SELECT id, case_id, actor_id, 'REOPEN', NULL, target_status, NULL, applied_at
                    FROM pf_case_reopen_order WHERE case_id = ? AND state = 'APPLIED'
                    UNION ALL
                    SELECT f.id, f.case_id, f.actor_id, 'FOLLOW_UP_' || f.action,
                        to_char(w.due_at AT TIME ZONE 'Europe/Zurich', 'DD.MM.YYYY'),
                        to_char(f.due_at AT TIME ZONE 'Europe/Zurich', 'DD.MM.YYYY'), f.internal_reason, f.applied_at
                    FROM pf_follow_up_command f JOIN pf_case_wait w ON w.id = f.wait_id
                    WHERE f.case_id = ? AND f.state = 'APPLIED' AND f.changed AND f.action <> 'CLOSE'
                    UNION ALL
                    SELECT id, case_id, actor_id, 'CLEAR_ANSWER', NULL, message_id::text, NULL, occurred_at
                    FROM pf_answer_cancellation WHERE case_id = ?
                    UNION ALL
                    SELECT t.id, t.case_id, c.actor_id, 'TAKEOVER', NULL, t.target_status, NULL, t.applied_at
                    FROM pf_staff_takeover t JOIN pf_case_data_command c ON c.id = t.id
                    WHERE t.case_id = ? AND t.applied_at IS NOT NULL
                    UNION ALL
                    SELECT d.id, c.case_id, c.actor_id, 'FOLLOW_UP', NULL,
                        to_char(d.due_at AT TIME ZONE 'Europe/Zurich', 'DD.MM.YYYY'), c.payload::jsonb ->> 'reason', c.completed_at
                    FROM pf_direct_follow_up d JOIN pf_case_data_command c ON c.id = d.id
                    WHERE c.case_id = ? AND c.state = 'APPLIED'
                )
                SELECT * FROM changes ORDER BY occurred_at, id
                """, (row, index) -> new InternalChange(row.getString("id"),
                        describe(row.getString("kind"), row.getString("previous_value"), row.getString("current_value"),
                                row.getString("internal_reason")),
                        row.getObject("actor_id", UUID.class), "SYSTEM_CLOSE".equals(row.getString("kind")) ? "System" : "Mitarbeiter",
                        row.getTimestamp("occurred_at").toInstant()),
                caseId, caseId, caseId, caseId, caseId, caseId, caseId, caseId, caseId, caseId);
    }

    private String describe(String kind, String previous, String current, String reason) {
        String action = switch (kind) {
            case "CONTINUE" -> "Bearbeitung fortgesetzt.";
            case "TAKEOVER" -> "Automatische Abklärung durch Mitarbeiter übernommen.";
            case "CLOSE" -> "Fall abgeschlossen. Quelle: Mitarbeiter.";
            case "SYSTEM_CLOSE" -> "Fall abgeschlossen. Quelle: System.";
            case "CLEAR_ANSWER" -> "Antwortbedarf für Nachricht " + current + " manuell aufgehoben.";
            case "FOLLOW_UP" -> "Wiedervorlage gesetzt: " + current + " um 08:00 Uhr Schweizer Zeit. Offene Prüfgründe berücksichtigt.";
            case "FOLLOW_UP_CHANGE" -> "Wiedervorlage geändert: " + previous + " → " + current + " um 08:00 Uhr Schweizer Zeit.";
            case "FOLLOW_UP_DELETE" -> "Wiedervorlage vom " + previous + " gelöscht. Bearbeitung wieder aufgenommen.";
            case "WAIT_EXTERNAL" -> "Warten auf externe Rückmeldung bestätigt.";
            case "REOPEN" -> "Fall wiedereröffnet. Neuer Bearbeitungsdurchlauf bestätigt.";
            case "URGENCY" -> "Dringlichkeit manuell bestätigt: " + urgency(previous) + " → " + urgency(current) + ".";
            case "ASSIGNMENT" -> "Objektzuordnung: " + (previous == null ? "Noch nicht zugeordnet" : previous) + " → " + current + ".";
            default -> throw new IllegalArgumentException("UNKNOWN_STAFF_CHANGE");
        };
        return reason == null || reason.isBlank() ? action : action + " " + reason;
    }

    private String urgency(String value) {
        if (value == null) {
            return "Noch nicht bewertet";
        }
        return switch (value) {
            case "CRITICAL" -> "Kritisch";
            case "HIGH" -> "Hoch";
            case "NORMAL" -> "Normal";
            case "LOW" -> "Niedrig";
            default -> throw new IllegalArgumentException("UNKNOWN_URGENCY");
        };
    }
}
