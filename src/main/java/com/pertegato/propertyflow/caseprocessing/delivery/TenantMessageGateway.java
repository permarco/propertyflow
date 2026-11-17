package com.pertegato.propertyflow.caseprocessing.delivery;

import java.util.UUID;

public interface TenantMessageGateway {
    void correlate(UUID messageId, long leader, String tenant);
}
