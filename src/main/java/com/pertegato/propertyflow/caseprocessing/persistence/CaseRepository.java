package com.pertegato.propertyflow.caseprocessing.persistence;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

@Profile("propertyflow-backend")
public interface CaseRepository extends JpaRepository<CaseEntity, String>, JpaSpecificationExecutor<CaseEntity> {
    List<CaseEntity> findByContactEmailOrderByReceivedAtDesc(String email);
}
