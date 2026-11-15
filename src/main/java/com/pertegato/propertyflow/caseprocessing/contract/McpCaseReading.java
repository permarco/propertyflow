package com.pertegato.propertyflow.caseprocessing.contract;

import com.pertegato.propertyflow.access.contract.McpAccountContext;

/**
 * Stage-two read boundary requiring current account, delegation and case authorization.
 */
public interface McpCaseReading {
    CaseStatusView readStatusForMcp(McpAccountContext context, String caseId);
}
