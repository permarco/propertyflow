package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.CaseState;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.Entry;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.StartIdentity;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("propertyflow-backend & !frontend-demo")
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class CaseWorkflowService {

    private final CaseWorkflowStore store;

    public CaseWorkflowService(CaseWorkflowStore store) {
        this.store = store;
    }

    public String verifyStart(String caseId, UUID orderId, long instanceKey, long definitionKey, String tenantId) {
        CaseState state = lock(caseId);
        StartIdentity reopening = store.reopenStart(orderId, caseId).orElse(null);
        if (reopening != null) {
            require(reopening.definitionKey() == definitionKey && reopening.tenantId().equals(tenantId), "INVALID_REOPEN_DEFINITION");
            return "REOPEN";
        }
        verifyIdentity(caseId, orderId, definitionKey, tenantId);
        store.leadingInstance(caseId).ifPresent(existing -> require(existing == instanceKey, "OTHER_LEADING_INSTANCE"));
        if (store.leadingInstance(caseId).isEmpty()) {
            require("RECEIVED".equals(state.status()), "NOT_INITIAL_INTAKE");
        }
        return "INITIAL";
    }

    public void register(String caseId, UUID orderId, long instanceKey, long definitionKey, String tenantId) {
        CaseState state = lock(caseId);
        verifyIdentity(caseId, orderId, definitionKey, tenantId);
        Long existing = store.leadingInstance(caseId).orElse(null);
        if (existing != null) {
            require(existing == instanceKey, "OTHER_LEADING_INSTANCE");
            return;
        }
        require("RECEIVED".equals(state.status()), "NOT_INITIAL_INTAKE");
        store.register(caseId, orderId, instanceKey, definitionKey, tenantId);
    }

    public Entry prepareInitial(String caseId, long leadingKey, long elementKey) {
        CaseState state = lock(caseId);
        verifyLeader(caseId, leadingKey);
        Entry existing = store.entryForCase(caseId).orElse(null);
        if (existing != null) {
            require(existing.prepareElementKey() == elementKey && existing.leadingKey() == leadingKey, "OTHER_INITIAL_ENTRY");
            return existing;
        }
        require("RECEIVED".equals(state.status()), "NOT_INITIAL_INTAKE");
        return store.createEntry(state, leadingKey, elementKey);
    }

    public void registerAutomatic(String caseId, UUID entryId, UUID callRef, long leadingKey, long childKey, long parentElementKey) {
        CaseState state = lock(caseId);
        Entry entry = verifiedEntry(caseId, entryId, callRef, leadingKey);
        require(childKey != leadingKey && childKey > 0 && parentElementKey > 0, "INVALID_CHILD_INSTANCE");
        if (entry.childKey() != null) {
            require(entry.childKey() == childKey && entry.parentElementKey() == parentElementKey, "OTHER_CHILD_INSTANCE");
            return;
        }
        boolean initial = "INITIAL".equals(store.entryKind(entry.id()));
        require(initial ? "RECEIVED".equals(state.status()) && state.version() == entry.caseVersion()
                : store.questionPhase(state), "STALE_AUTOMATIC_ENTRY");
        store.bindChild(entry.id(), childKey, parentElementKey);
    }

    public String entryKind(UUID entry) {
        return store.entryKind(entry);
    }

    public void startClarification(String caseId, UUID entryId, UUID callRef, long leadingKey, long childKey) {
        CaseState state = lock(caseId);
        Entry entry = verifiedEntry(caseId, entryId, callRef, leadingKey);
        require(entry.childKey() != null && entry.childKey() == childKey, "UNREGISTERED_CHILD");
        // A completed operation is replayed without reapplying it to a later case version.
        if (store.hasInitialTransition(entryId)) {
            return;
        }
        require("RECEIVED".equals(state.status()) && state.version() == entry.caseVersion(), "STALE_INITIAL_ENTRY");
        store.startClarification(state, entry);
    }

    private Entry verifiedEntry(String caseId, UUID entryId, UUID callRef, long leadingKey) {
        verifyLeader(caseId, leadingKey);
        Entry entry = store.entryForCase(caseId).orElseThrow(() -> rejected("MISSING_INITIAL_ENTRY"));
        require(entry.id().equals(entryId) && entry.callRef().equals(callRef) && entry.leadingKey() == leadingKey, "INVALID_ENTRY_REFERENCE");
        return entry;
    }

    private void verifyIdentity(String caseId, UUID orderId, long definitionKey, String tenantId) {
        StartIdentity identity = store.findStart(orderId, caseId).orElseThrow(() -> rejected("UNKNOWN_START_ORDER"));
        require(identity.definitionKey() != null && identity.definitionKey() == definitionKey && tenantId.equals(identity.tenantId()),
                "UNEXPECTED_PROCESS_DEFINITION");
    }

    private void verifyLeader(String caseId, long leadingKey) {
        require(store.leadingInstance(caseId).filter(key -> key == leadingKey).isPresent(), "UNREGISTERED_LEADING_INSTANCE");
    }

    private CaseState lock(String caseId) {
        return store.lockCase(caseId).orElseThrow(() -> rejected("UNKNOWN_CASE"));
    }

    private void require(boolean condition, String reason) {
        if (!condition) {
            throw rejected(reason);
        }
    }

    private WorkflowRejectedException rejected(String reason) {
        return new WorkflowRejectedException(reason);
    }
}
