package com.pertegato.propertyflow.access.contract;

import java.util.Optional;
import java.util.UUID;

public interface StaffAuthentication {

    void requestCode(UUID loginFlowId, String email);

    Optional<WebAccountContext> verifyCode(UUID loginFlowId, String code);
}
