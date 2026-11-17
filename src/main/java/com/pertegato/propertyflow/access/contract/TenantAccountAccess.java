package com.pertegato.propertyflow.access.contract;

import java.util.Optional;
import java.util.UUID;

/** Aktuelle verifizierte Kontoadresse; Clientangaben sind kein Berechtigungsnachweis. */
public interface TenantAccountAccess {
    Optional<String> verifiedEmail(UUID accountId);
}
