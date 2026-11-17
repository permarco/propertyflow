package com.pertegato.propertyflow.caseprocessing.contract;

import java.util.UUID;

public interface TenantMessageSubmission {
    UUID send(String accessToken, String operationId, String text);

    default UUID send(String accessToken, UUID operationId, String text) {
        return send(accessToken, operationId == null ? null : operationId.toString(), text);
    }
}
