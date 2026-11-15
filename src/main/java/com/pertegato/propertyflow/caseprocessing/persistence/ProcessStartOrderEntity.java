package com.pertegato.propertyflow.caseprocessing.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "pf_process_start_order")
public class ProcessStartOrderEntity {

    private static final String INITIAL_STATE = "PENDING";

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "case_id", nullable = false, unique = true, length = 80)
    private String caseId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "state", nullable = false, length = 32)
    private String state;

    protected ProcessStartOrderEntity() {
    }

    public ProcessStartOrderEntity(UUID id, String caseId, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.caseId = Objects.requireNonNull(caseId, "caseId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.state = INITIAL_STATE;
    }

    public UUID getId() {
        return id;
    }

    public String getCaseId() {
        return caseId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getState() {
        return state;
    }
}
