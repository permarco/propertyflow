package com.pertegato.propertyflow.caseprocessing.delivery;

import java.util.UUID;

public interface StaffMessageGateway {

    void correlate(UUID command, long target, String tenant, boolean automatic, String correlationKey);
    void correlateFollowUp(UUID command, long target, String tenant, String correlationKey);

    void correlateReply(UUID command, long target, String tenant, String correlationKey);
}
