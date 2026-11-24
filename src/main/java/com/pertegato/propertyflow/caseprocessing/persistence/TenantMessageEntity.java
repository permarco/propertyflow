package com.pertegato.propertyflow.caseprocessing.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "pf_tenant_message")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenantMessageEntity {

    @Id
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private String caseId;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String text;
}
