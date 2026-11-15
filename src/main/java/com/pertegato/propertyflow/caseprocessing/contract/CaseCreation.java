package com.pertegato.propertyflow.caseprocessing.contract;

import com.pertegato.propertyflow.access.contract.SubmissionContext;

/**
 * Owns atomic case acceptance and its idempotency checks.
 */
public interface CaseCreation {
    CaseAccepted create(SubmissionContext context, CreateCaseCommand command);
}
