package com.pertegato.propertyflow.caseprocessing.delivery;

import com.pertegato.propertyflow.caseprocessing.contract.StaffDecisionAction;
import java.util.Optional;
import java.util.UUID;

public interface StaffTaskGateway {

    Optional<Long> currentTask(Binding binding);

    void completeTask(Binding binding, long taskKey, UUID decisionId, StaffDecisionAction action);

    record Binding(long leader, long child, long parentElement, String tenant, String element) {
    }
}
