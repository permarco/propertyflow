package com.pertegato.propertyflow.caseprocessing.contract;

import com.pertegato.propertyflow.access.contract.WebAccountContext;

public interface StaffMessageSubmission {

    Capability available(WebAccountContext context, String caseId);

    String send(WebAccountContext context, String caseId, String operation, String text, boolean answerRequired);

    String result(WebAccountContext context, String caseId, String operation);

    record Capability(boolean information, boolean question) {
    }
}
