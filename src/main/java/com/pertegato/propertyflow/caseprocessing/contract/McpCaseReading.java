package com.pertegato.propertyflow.caseprocessing.contract;

import com.pertegato.propertyflow.access.contract.McpAccountContext;
import java.util.List;

/**
 * Fallstand für eine aktuell berechtigte MCP-Delegation des Mieterkontos.
 */
public interface McpCaseReading {

    String CASE_ID_PATTERN = "^[A-Za-z0-9_-]{1,80}$";

    CaseStatusView readStatusForMcp(McpAccountContext context, String caseId);

    List<TenantCaseSummary> listForMcp(McpAccountContext context);

    StaffCasePage listForMcpStaff(McpAccountContext context, CaseStateFilter state, CaseStatus status, String urgency, int page);

    StaffCaseDetail readForMcpStaff(McpAccountContext context, String caseId);

    TenantCaseView readPublicForMcp(McpAccountContext context, String caseId, boolean staff);

    enum CaseStateFilter {
        ACTIVE,
        CLOSED,
        ALL
    }

    record TenantCaseSummary(CaseStatusView state, String title) {
    }

    record StaffCaseDetail(CaseStatusView state, String title, List<PublicMessage> messages) {
    }
}
