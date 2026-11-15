package com.pertegato.propertyflow.caseprocessing.contract;

import java.util.Objects;

/**
 * Confirmed acceptance. The token is only for the authorized personal-link redirect.
 */
public record CaseAccepted(String caseId, String personalAccessToken, boolean replayed) {
    public CaseAccepted {
        Objects.requireNonNull(caseId, "caseId");
        Objects.requireNonNull(personalAccessToken, "personalAccessToken");
    }

    @Override
    public String toString() {
        return "CaseAccepted[caseId=" + caseId + ", personalAccessToken=[redacted], replayed=" + replayed + "]";
    }
}
