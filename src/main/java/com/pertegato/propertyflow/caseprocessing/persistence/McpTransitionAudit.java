package com.pertegato.propertyflow.caseprocessing.persistence;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcOperations;

final class McpTransitionAudit {

    private McpTransitionAudit() {
    }

    static void applied(JdbcOperations database, String caseId, UUID account, UUID operation) {
        database.update("""
                INSERT INTO pf_mcp_audit (id, action, account_id, client_id, case_id, operation_id, outcome)
                SELECT ?, action, account_id, client_id, case_id, operation_id, 'APPLIED'
                FROM pf_mcp_audit WHERE case_id = ? AND account_id = ? AND operation_id = ?
                    AND action IN ('STATUS_FORTSETZEN', 'STATUS_ABSCHLIESSEN', 'STATUS_WIEDEREROEFFNEN')
                    AND outcome = 'PREPARED'
                """, UUID.randomUUID(), caseId, account, operation);
    }
}
