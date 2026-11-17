package com.pertegato.propertyflow.caseprocessing.contract;

import com.pertegato.propertyflow.access.contract.WebAccountContext;
import java.time.LocalDate;
import java.util.UUID;

public interface FollowUpCommands {

    Current current(WebAccountContext actor, String caseId);

    String submit(WebAccountContext actor, String caseId, UUID operation, UUID waitId, long version,
            boolean delete, Integer days, LocalDate date, String reason);

    String result(WebAccountContext actor, String caseId, UUID operation);

    String close(WebAccountContext actor, String caseId, UUID operation, UUID waitId, long version, String reason);

    record Current(boolean available, UUID waitId, long version) {
    }
}
