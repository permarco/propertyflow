package com.pertegato.propertyflow.caseprocessing.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "pf_submission", uniqueConstraints = @UniqueConstraint(
        name = "uk_pf_submission_scope_operation", columnNames = {"guest_scope", "operation_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubmissionEntity {

    @Id
    @Column(name = "id", nullable = false)
    @Getter
    private UUID id;

    @Column(name = "guest_scope", nullable = false)
    @Getter
    private UUID guestScope;

    @Column(name = "operation_id", nullable = false, length = 80)
    @Getter
    private String operationId;

    @Column(name = "fingerprint", nullable = false, length = 64)
    @Getter
    private String fingerprint;

    @Column(name = "case_id", nullable = false, length = 80)
    @Getter
    private String caseId;

    @Column(name = "access_id", nullable = false)
    @Getter
    private UUID accessId;

    public SubmissionEntity(UUID id, UUID guestScope, String operationId, String fingerprint, String caseId, UUID accessId) {
        this.id = Objects.requireNonNull(id, "id");
        this.guestScope = Objects.requireNonNull(guestScope, "guestScope");
        this.operationId = Objects.requireNonNull(operationId, "operationId");
        this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        this.caseId = Objects.requireNonNull(caseId, "caseId");
        this.accessId = Objects.requireNonNull(accessId, "accessId");
    }
}
