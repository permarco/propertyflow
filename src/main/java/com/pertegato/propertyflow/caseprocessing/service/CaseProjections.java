package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.caseprocessing.contract.CaseStatusView;
import com.pertegato.propertyflow.caseprocessing.contract.PublicAuthor;
import com.pertegato.propertyflow.caseprocessing.contract.PublicMessage;
import com.pertegato.propertyflow.caseprocessing.contract.TenantCaseView;
import com.pertegato.propertyflow.caseprocessing.contract.UrgencyChange;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseEntity;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

final class CaseProjections {

    private CaseProjections() {
    }

    static CaseStatusView state(CaseEntity stored) {
        return new CaseStatusView(stored.getId(), stored.getStatus(), Optional.ofNullable(stored.getUrgency()));
    }

    static TenantCaseView tenantView(CaseEntity stored, List<UrgencyChange> urgencyHistory) {
        return tenantView(stored, urgencyHistory, List.of());
    }

    static TenantCaseView tenantView(CaseEntity stored, List<UrgencyChange> urgencyHistory, List<PublicMessage> messages) {
        String email = stored.getContactEmail();
        int domainLength = email.length() - email.indexOf('@') - 1;
        int suffixLength = Math.max(0, Math.min(6, domainLength - 1));
        String maskedContact = email.substring(0, email.offsetByCodePoints(0, 1)) + "***" + email.substring(email.length() - suffixLength);
        PublicMessage initial = new PublicMessage(stored.getInitialMessageId().toString(), PublicAuthor.TENANT,
                stored.getDescription(), stored.getReceivedAt());
        return new TenantCaseView(state(stored),
                stored.getTitle(), stored.getDescription(), stored.getOriginalUnit(), Optional.ofNullable(stored.getAssignedUnit()), maskedContact,
                stored.getReceivedAt(), stored.getLastTenantContactAt(), stored.getVersion(), Optional.ofNullable(stored.getUrgencySource()),
                Stream.concat(Stream.of(initial), messages.stream()).toList(), urgencyHistory);
    }

    static TenantCaseView tenantView(CaseEntity stored, List<UrgencyChange> urgencyHistory,
            List<PublicMessage> tenantMessages, List<PublicMessage> staffMessages) {
        List<PublicMessage> history = Stream.concat(tenantMessages.stream(), staffMessages.stream())
                .sorted(Comparator.comparing(PublicMessage::occurredAt).thenComparing(PublicMessage::id))
                .toList();
        return tenantView(stored, urgencyHistory, history);
    }
}
