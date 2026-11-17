package com.pertegato.propertyflow.access.contract;

import java.util.UUID;

public interface AccountAdministration {
    void requireCaseAccessAdministrator(UUID actorId);
}
