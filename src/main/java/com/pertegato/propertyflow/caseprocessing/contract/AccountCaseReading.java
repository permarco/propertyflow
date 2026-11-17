package com.pertegato.propertyflow.caseprocessing.contract;

import com.pertegato.propertyflow.access.contract.WebAccountContext;

/**
 * Account-based reads for stage two; implementations must check current account permissions.
 */
public interface AccountCaseReading {
    TenantCaseView readForTenant(WebAccountContext context, String caseId);

    StaffCaseView readForStaff(WebAccountContext context, String caseId);
}
