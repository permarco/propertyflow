package com.pertegato.propertyflow.caseprocessing.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "pf_internal_memo")
public class InternalMemoEntity {

    @Id
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private String caseId;

    @Column(name = "text", nullable = false, columnDefinition = "text")
    private String text;

    protected InternalMemoEntity() {
    }
}
