package com.pertegato.propertyflow.caseprocessing.contract;

import com.pertegato.propertyflow.access.contract.WebAccountContext;

public interface StaffCaseListing {
    StaffCasePage listForStaff(WebAccountContext context, StaffCaseQuery query);
}
