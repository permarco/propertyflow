package com.pertegato.propertyflow.caseprocessing.persistence;

import com.pertegato.propertyflow.caseprocessing.contract.CaseStatus;
import com.pertegato.propertyflow.caseprocessing.contract.Urgency;
import com.pertegato.propertyflow.caseprocessing.contract.UrgencySource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "pf_case")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CaseEntity {

    @Id
    @Column(name = "id", nullable = false, length = 80)
    @Getter
    private String id;

    @Column(name = "title", nullable = false, length = 150)
    @Getter
    private String title;

    @Column(name = "original_unit", nullable = false, length = 200)
    @Getter
    private String originalUnit;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    @Getter
    private String description;

    @Column(name = "contact_email", nullable = false, length = 254)
    @Getter
    private String contactEmail;

    @Column(name = "original_contact_email", length = 254)
    private String originalContactEmail;

    @Column(name = "received_at", nullable = false)
    @Getter
    private Instant receivedAt;

    @Column(name = "last_tenant_contact_at", nullable = false)
    @Getter
    private Instant lastTenantContactAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Getter
    private CaseStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "urgency", length = 16)
    @Getter
    private Urgency urgency;

    @Enumerated(EnumType.STRING)
    @Column(name = "urgency_source", length = 16)
    @Getter
    private UrgencySource urgencySource;

    @Column(name = "assigned_unit", length = 200)
    @Getter
    private String assignedUnit;

    @Column(name = "follow_up_due_at")
    @Getter
    private Instant followUpDueAt;

    @org.hibernate.annotations.Formula("coalesce(assigned_unit, original_unit)")
    private String displayUnit;

    @org.hibernate.annotations.Formula("case urgency when 'CRITICAL' then 1 when 'HIGH' then 2 when 'NORMAL' then 3 when 'LOW' then 4 else 0 end")
    private int urgencyRank;

    @Column(name = "initial_message_id", nullable = false)
    @Getter
    private UUID initialMessageId;

    @Version
    @Column(name = "version", nullable = false)
    @Getter
    private long version;

    public CaseEntity(String id, String title, String originalUnit, String description, String contactEmail, Instant receivedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.title = Objects.requireNonNull(title, "title");
        this.originalUnit = Objects.requireNonNull(originalUnit, "originalUnit");
        this.description = Objects.requireNonNull(description, "description");
        this.contactEmail = Objects.requireNonNull(contactEmail, "contactEmail");
        this.originalContactEmail = contactEmail;
        this.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");
        this.lastTenantContactAt = receivedAt;
        this.status = CaseStatus.RECEIVED;
        this.initialMessageId = UUID.randomUUID();
    }

    public String getDisplayUnit() {
        return assignedUnit == null ? originalUnit : assignedUnit;
    }
}
