package com.pertegato.propertyflow.caseprocessing.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("propertyflow-backend & !frontend-demo")
@RequiredArgsConstructor
public class CaseOperationsStore {

    private final JdbcTemplate database;

    public List<Entry> attention() {
        // Ausschliesslich technische Metadaten; keine Texte, Adressen, Prüfcodes oder Fall-Links.
        return database.query("""
                SELECT * FROM (
                    SELECT 'PROCESS_START' AS kind, id, case_id, state, created_at AS since FROM pf_process_start_order
                    WHERE state = 'BLOCKED' OR (state = 'PENDING' AND created_at < CURRENT_TIMESTAMP - INTERVAL '5 minutes')
                    UNION ALL
                    SELECT 'CASE_MAIL', id, case_id, CASE WHEN start_state = 'BLOCKED' THEN 'BLOCKED' ELSE state END, created_at
                    FROM pf_mail_delivery WHERE state IN ('FAILED', 'UNCERTAIN') OR start_state = 'BLOCKED'
                        OR (state IN ('PENDING', 'SENDING') AND created_at < CURRENT_TIMESTAMP - INTERVAL '5 minutes')
                    UNION ALL
                    SELECT 'ACCESS_MAIL', o.id, o.case_id, o.state, COALESCE(v.consumed_at, v.created_at)
                    FROM pf_access_mail_order o JOIN pf_access_verification v ON v.id = o.id
                    WHERE o.state IN ('FAILED', 'UNCERTAIN')
                        OR (o.state IN ('PENDING', 'SENDING') AND v.consumed_at < CURRENT_TIMESTAMP - INTERVAL '5 minutes')
                    UNION ALL
                    SELECT 'CASE_COMMAND', id, case_id, state, created_at FROM pf_case_data_command
                    WHERE (state = 'PENDING' AND created_at < CURRENT_TIMESTAMP - INTERVAL '5 minutes')
                        OR (state = 'REJECTED' AND created_at > CURRENT_TIMESTAMP - INTERVAL '7 days')
                    UNION ALL
                    SELECT 'STAFF_MESSAGE', id, case_id, state, created_at FROM pf_staff_message_command
                    WHERE (state = 'PENDING' AND created_at < CURRENT_TIMESTAMP - INTERVAL '5 minutes')
                        OR (state = 'REJECTED' AND created_at > CURRENT_TIMESTAMP - INTERVAL '7 days')
                    UNION ALL
                    SELECT 'FOLLOW_UP', id, case_id, state, created_at FROM pf_follow_up_command
                    WHERE (state IN ('PENDING', 'APPLYING') AND created_at < CURRENT_TIMESTAMP - INTERVAL '5 minutes')
                        OR (state = 'REJECTED' AND created_at > CURRENT_TIMESTAMP - INTERVAL '7 days')
                    UNION ALL
                    SELECT 'STAFF_DECISION', id, case_id, state, created_at FROM pf_staff_decision
                    WHERE (state IN ('PREPARED', 'CONFIRMED') AND created_at < CURRENT_TIMESTAMP - INTERVAL '5 minutes')
                        OR (state = 'REJECTED' AND created_at > CURRENT_TIMESTAMP - INTERVAL '7 days')
                    UNION ALL
                    SELECT 'REOPEN', id, case_id, state, created_at FROM pf_case_reopen_order
                    WHERE state = 'BLOCKED' OR (state = 'PENDING' AND created_at < CURRENT_TIMESTAMP - INTERVAL '5 minutes')
                        OR (state = 'REJECTED' AND created_at > CURRENT_TIMESTAMP - INTERVAL '7 days')
                ) issues ORDER BY since DESC, kind, id LIMIT 101
                """, (row, index) -> new Entry(row.getString("kind"), row.getObject("id", UUID.class),
                        row.getString("case_id"), row.getString("state"), row.getTimestamp("since").toInstant()));
    }

    public record Entry(String kind, UUID id, String caseId, String state, Instant since) {
    }
}
