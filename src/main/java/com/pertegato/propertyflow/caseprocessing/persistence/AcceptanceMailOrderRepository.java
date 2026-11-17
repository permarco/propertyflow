package com.pertegato.propertyflow.caseprocessing.persistence;

import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

@Profile("propertyflow-backend & !frontend-demo")
public interface AcceptanceMailOrderRepository extends JpaRepository<AcceptanceMailOrderEntity, UUID> {
}
