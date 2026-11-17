package com.pertegato.propertyflow.caseprocessing.contract;

import com.pertegato.propertyflow.access.contract.WebAccountContext;
import java.util.UUID;
import java.time.LocalDate;

public interface StaffTaskDecisions {

    StaffTask current(WebAccountContext context, String caseId);

    default void continueWork(WebAccountContext context, String caseId, UUID operationId, long taskKey, long version, String reviewToken) {
        continueWork(context, caseId, operationId, taskKey, version, reviewToken, "");
    }

    void continueWork(WebAccountContext context, String caseId, UUID operationId, long taskKey, long version, String reviewToken, String reason);

    void closeCase(WebAccountContext context, String caseId, UUID operationId, long taskKey, long version, String reviewToken);

    void followUp(WebAccountContext context, String caseId, UUID operationId, long taskKey, long version,
            String reviewToken, String reason, Integer days, LocalDate date);

    void waitExternal(WebAccountContext context, String caseId, UUID operationId, long taskKey, long version, String reviewToken);

    record StaffTask(boolean available, String taskKey, long version, String reviewToken, boolean canContinue, boolean canClose) {
    }
}
