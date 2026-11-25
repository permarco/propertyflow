package com.pertegato.propertyflow.caseprocessing.contract;

import com.pertegato.propertyflow.access.contract.WebAccountContext;
import java.util.UUID;

public interface CaseReopening {

    UUID request(WebAccountContext actor, String caseId, UUID operation, long version);

    /**
     * Liefert den gespeicherten Stand des eigenen Wiedereröffnungsauftrags
     * (PENDING, APPLIED, BLOCKED oder REJECTED).
     */
    String orderState(WebAccountContext actor, String caseId, UUID order);
}
