package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.caseprocessing.contract.CaseStatusView;
import com.pertegato.propertyflow.caseprocessing.contract.PublicAuthor;
import com.pertegato.propertyflow.caseprocessing.contract.PublicMessage;
import com.pertegato.propertyflow.caseprocessing.contract.TenantCaseView;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseEntity;
import java.util.List;
import java.util.Optional;

final class CaseProjections {

    private CaseProjections() {
    }

    static CaseStatusView state(CaseEntity stored) {
        return new CaseStatusView(stored.getId(), stored.getStatus(), Optional.empty());
    }

    static TenantCaseView tenantView(CaseEntity stored) {
        String email = stored.getContactEmail();
        String maskedContact = email.charAt(0) + "***" + email.substring(email.indexOf('@'));
        PublicMessage initial = new PublicMessage(stored.getInitialMessageId().toString(), PublicAuthor.TENANT,
                stored.getDescription(), stored.getReceivedAt());
        return new TenantCaseView(state(stored),
                stored.getTitle(), stored.getDescription(), stored.getOriginalUnit(), Optional.empty(), maskedContact,
                stored.getReceivedAt(), stored.getLastTenantContactAt(), stored.getVersion(), Optional.empty(), List.of(initial), List.of());
    }
}
