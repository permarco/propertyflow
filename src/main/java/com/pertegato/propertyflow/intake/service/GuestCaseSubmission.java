package com.pertegato.propertyflow.intake.service;

import com.pertegato.propertyflow.access.contract.SubmissionContext;
import com.pertegato.propertyflow.caseprocessing.contract.CaseAccepted;
import com.pertegato.propertyflow.caseprocessing.contract.CaseCreation;
import com.pertegato.propertyflow.caseprocessing.contract.CaseErrorCode;
import com.pertegato.propertyflow.caseprocessing.contract.CaseInputValidation;
import com.pertegato.propertyflow.caseprocessing.contract.CaseServiceException;
import com.pertegato.propertyflow.caseprocessing.contract.CreateCaseCommand;
import com.pertegato.propertyflow.intake.contract.CaseSubmission;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("propertyflow-backend & !frontend-demo")
@RequiredArgsConstructor
public class GuestCaseSubmission implements CaseSubmission {

    private final CaseCreation creation;

    @Override
    public CaseAccepted submit(SubmissionContext context, CreateCaseCommand command) {
        if (context instanceof SubmissionContext.Tenant) {
            return creation.create(context, command);
        }
        if (!(context instanceof SubmissionContext.Guest)) {
            throw new CaseServiceException(CaseErrorCode.UNAUTHENTICATED, Map.of());
        }
        return creation.create(context, CaseInputValidation.validateGuest(command));
    }
}
