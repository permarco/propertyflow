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
@Table(name = "pf_case_access")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CaseAccessEntity {

    @Id
    @Column(name = "id", nullable = false)
    @Getter
    private UUID id;

    @Column(name = "case_id", nullable = false, length = 80)
    @Getter
    private String caseId;

    @Column(name = "token_hash", nullable = false, length = 64)
    @Getter
    private String tokenHash;

    @Column(name = "encrypted_token", nullable = false, columnDefinition = "text")
    @Getter
    private String encryptedToken;

    @Column(name = "revoked_at")
    @Getter
    private Instant revokedAt;

    public CaseAccessEntity(UUID id, String caseId, String tokenHash, String encryptedToken) {
        this.id = Objects.requireNonNull(id, "id");
        this.caseId = Objects.requireNonNull(caseId, "caseId");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        this.encryptedToken = Objects.requireNonNull(encryptedToken, "encryptedToken");
    }

    public void revoke(Instant revokedAt) {
        Objects.requireNonNull(revokedAt, "revokedAt");
        if (this.revokedAt == null) {
            this.revokedAt = revokedAt;
        }
    }
}
