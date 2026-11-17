package com.pertegato.propertyflow.access.contract;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Internal display-name lookup for an already authorized staff projection.
 */
public interface StaffDirectory {
    Map<UUID, String> displayNames(Set<UUID> accountIds);
}
