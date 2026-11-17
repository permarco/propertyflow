package com.pertegato.propertyflow.intake.contract;

import com.pertegato.propertyflow.access.contract.SubmissionContext;
import com.pertegato.propertyflow.caseprocessing.contract.CaseAccepted;
import com.pertegato.propertyflow.caseprocessing.contract.CreateCaseCommand;

/**
 * Intake boundary. A successful return confirms committed acceptance, not completion of the workflow.
 */
public interface CaseSubmission {
    CaseAccepted submit(SubmissionContext context, CreateCaseCommand command);
}
