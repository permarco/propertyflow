package com.pertegato.propertyflow.caseprocessing.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

@Profile("propertyflow-backend")
public interface CaseAccessRepository extends JpaRepository<CaseAccessEntity, UUID> {
    Optional<CaseAccessEntity> findByTokenHashAndRevokedAtIsNull(String tokenHash);
}
