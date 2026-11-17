package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.StartOrder;

public interface ProcessStartGateway {

    StartResult ensureStarted(StartOrder order);

    enum StartResult {
        STARTED,
        EXISTING,
        UNKNOWN,
        REJECTED,
        ENDED_BEFORE_REGISTRATION
    }
}
