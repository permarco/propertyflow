package com.pertegato.propertyflow.caseprocessing.delivery;

import java.util.UUID;

public interface FollowUpGateway {

    boolean available(long child, long leader, long staff, String tenant, UUID waitId);

    void correlate(UUID command, long child, String tenant, UUID waitId, boolean delete);

    default void close(UUID command, long child, String tenant, UUID waitId) {
        throw new IllegalStateException("FOLLOW_UP_CLOSURE_UNAVAILABLE");
    }
}
