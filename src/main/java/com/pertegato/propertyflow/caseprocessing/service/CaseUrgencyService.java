package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.caseprocessing.contract.Urgency;
import com.pertegato.propertyflow.caseprocessing.contract.UrgencySource;
import com.pertegato.propertyflow.caseprocessing.persistence.UrgencyStore.Current;
import com.pertegato.propertyflow.caseprocessing.persistence.UrgencyStore;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("propertyflow-backend")
@RequiredArgsConstructor
public class CaseUrgencyService {

    private final UrgencyStore store;

    @Transactional(propagation = Propagation.MANDATORY)
    public long manual(String caseId, UUID command, UUID actor, Urgency urgency, String reason) {
        Current current = store.current(caseId);
        if ("CLOSED".equals(current.status()) || "RECEIVED".equals(current.status())) {
            throw new WorkflowRejectedException("URGENCY_CHANGE_NOT_ALLOWED");
        }
        if (current.source() == UrgencySource.STAFF && current.urgency() == urgency) {
            return current.version();
        }
        store.manual(caseId, command, actor, urgency, reason, current, current.urgency() != urgency,
                urgency == Urgency.CRITICAL && current.urgency() != Urgency.CRITICAL);
        return current.version() + 1;
    }
}
