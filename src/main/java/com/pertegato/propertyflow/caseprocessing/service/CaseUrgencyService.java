package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.caseprocessing.contract.Urgency;
import com.pertegato.propertyflow.caseprocessing.contract.UrgencySource;
import com.pertegato.propertyflow.caseprocessing.persistence.UrgencyStore;
import com.pertegato.propertyflow.caseprocessing.persistence.UrgencyStore.Current;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("propertyflow-backend & !frontend-demo")
public class CaseUrgencyService {

    private final UrgencyStore store;

    public CaseUrgencyService(UrgencyStore store) {
        this.store = store;
    }

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

    @Transactional(propagation = Propagation.MANDATORY)
    public void system(String caseId, UUID analysisId, Urgency urgency) {
        Current current = store.current(caseId);
        if (!"UNDER_CLARIFICATION".equals(current.status())) {
            throw new WorkflowRejectedException("SYSTEM_URGENCY_NOT_ALLOWED");
        }
        if (urgency == Urgency.CRITICAL) {
            store.criticalAnalysis(caseId, analysisId);
        }
        // The separate analysis record retains the assessment even when a human classification wins.
        if (current.source() == UrgencySource.STAFF || current.urgency() == urgency) {
            return;
        }
        store.system(caseId, analysisId, urgency, current);
    }
}
