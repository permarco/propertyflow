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

@Entity
@Table(name = "pf_case")
public class CaseEntity {

    @Id
    @Column(name = "id", nullable = false, length = 80)
    private String id;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "original_unit", nullable = false, length = 200)
    private String originalUnit;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "contact_email", nullable = false, length = 254)
    private String contactEmail;

    @Column(name = "original_contact_email", length = 254)
    private String originalContactEmail;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "last_tenant_contact_at", nullable = false)
    private Instant lastTenantContactAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private CaseStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "urgency", length = 16)
    private Urgency urgency;

    @Enumerated(EnumType.STRING)
    @Column(name = "urgency_source", length = 16)
    private UrgencySource urgencySource;

    @Column(name = "assigned_unit", length = 200)
    private String assignedUnit;

    @Column(name = "follow_up_due_at")
    private Instant followUpDueAt;

    public Instant getFollowUpDueAt() {
        return followUpDueAt;
    }

    @org.hibernate.annotations.Formula("coalesce(assigned_unit, original_unit)")
    private String displayUnit;

    @org.hibernate.annotations.Formula("case urgency when 'CRITICAL' then 1 when 'HIGH' then 2 when 'NORMAL' then 3 when 'LOW' then 4 else 0 end")
    private int urgencyRank;

    @Column(name = "initial_message_id", nullable = false)
    private UUID initialMessageId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected CaseEntity() {
    }

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

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getOriginalUnit() {
        return originalUnit;
    }

    public String getDescription() {
        return description;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Instant getLastTenantContactAt() {
        return lastTenantContactAt;
    }

    public CaseStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    public Urgency getUrgency() {
        return urgency;
    }

    public UrgencySource getUrgencySource() {
        return urgencySource;
    }

    public String getAssignedUnit() {
        return assignedUnit;
    }

    public String getDisplayUnit() {
        return assignedUnit == null ? originalUnit : assignedUnit;
    }

    public UUID getInitialMessageId() {
        return initialMessageId;
    }
}
