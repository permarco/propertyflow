package com.pertegato.propertyflow.caseprocessing.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "pf_process_start_order")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessStartOrderEntity {

    private static final String INITIAL_STATE = "PENDING";

    @Id
    @Column(name = "id", nullable = false)
    @Getter
    private UUID id;

    @Column(name = "case_id", nullable = false, unique = true, length = 80)
    @Getter
    private String caseId;

    @Column(name = "created_at", nullable = false)
    @Getter
    private Instant createdAt;

    @Column(name = "state", nullable = false, length = 32)
    @Getter
    private String state;

    public ProcessStartOrderEntity(UUID id, String caseId, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.caseId = Objects.requireNonNull(caseId, "caseId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.state = INITIAL_STATE;
    }
}
