package com.pertegato.propertyflow.caseprocessing.contract;

import com.pertegato.propertyflow.access.contract.WebAccountContext;
import java.util.UUID;

public interface CaseReopening {

    UUID request(WebAccountContext actor, String caseId, UUID operation, long version);
}
