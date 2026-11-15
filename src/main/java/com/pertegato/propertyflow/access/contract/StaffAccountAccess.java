package com.pertegato.propertyflow.access.contract;

public interface StaffAccountAccess {

    StaffAccessDecision checkStaffAccess(WebAccountContext context);
}
