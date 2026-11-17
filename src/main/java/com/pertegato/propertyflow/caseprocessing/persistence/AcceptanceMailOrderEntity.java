package com.pertegato.propertyflow.caseprocessing.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "pf_acceptance_mail_order")
public class AcceptanceMailOrderEntity {

    private static final String INITIAL_STATE = "PENDING";

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "case_id", nullable = false, unique = true, length = 80)
    private String caseId;

    @Column(name = "access_id", nullable = false)
    private UUID accessId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "state", nullable = false, length = 32)
    private String state;

    protected AcceptanceMailOrderEntity() {
    }

    public AcceptanceMailOrderEntity(UUID id, String caseId, UUID accessId, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.caseId = Objects.requireNonNull(caseId, "caseId");
        this.accessId = Objects.requireNonNull(accessId, "accessId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.state = INITIAL_STATE;
    }

    public UUID getId() {
        return id;
    }

    public String getCaseId() {
        return caseId;
    }

    public UUID getAccessId() {
        return accessId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getState() {
        return state;
    }
}
