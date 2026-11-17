package com.pertegato.propertyflow.caseprocessing.delivery;

import java.util.UUID;

public interface CaseProcessGateway {

    boolean active(long instanceKey, String tenantId);

    void deliverCaseData(UUID commandId, long instanceKey, String tenantId);

    default void deliverDirectFollowUp(UUID commandId, long instanceKey, String tenantId) {
        throw new IllegalStateException("DIRECT_FOLLOW_UP_UNAVAILABLE");
    }

    default void deliverReplyClosure(UUID commandId, long instanceKey, String tenantId) {
        throw new IllegalStateException("REPLY_CLOSURE_UNAVAILABLE");
    }

    default void deliverClarificationClosure(UUID commandId, long instanceKey, String tenantId) {
        throw new IllegalStateException("CLARIFICATION_CLOSURE_UNAVAILABLE");
    }

    default void deliverTakeover(UUID commandId, long instanceKey, String tenantId) {
        throw new IllegalStateException("STAFF_TAKEOVER_UNAVAILABLE");
    }

    default void deliverClearAnswer(UUID commandId, long instanceKey, String tenantId) {
        throw new IllegalStateException("ANSWER_CANCELLATION_UNAVAILABLE");
    }
}
