package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.access.contract.StaffAccessDecision;
import com.pertegato.propertyflow.access.contract.StaffAccountAccess;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.caseprocessing.contract.CaseActionException;
import com.pertegato.propertyflow.caseprocessing.contract.CaseReopening;
import com.pertegato.propertyflow.caseprocessing.delivery.ReopenProcessGateway;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseReopenStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseReopenStore.Order;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.CaseState;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("propertyflow-backend & !frontend-demo")
public class CaseReopenService implements CaseReopening {

    private final StaffAccountAccess access;
    private final CaseWorkflowStore workflow;
    private final CaseReopenStore store;
    private final ReopenProcessGateway engine;
    private final TransactionTemplate transaction;

    public CaseReopenService(StaffAccountAccess access, CaseWorkflowStore workflow, CaseReopenStore store,
            ReopenProcessGateway engine, PlatformTransactionManager transactions) {
        this.access = access;
        this.workflow = workflow;
        this.store = store;
        this.engine = engine;
        transaction = new TransactionTemplate(transactions);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public UUID request(WebAccountContext actor, String caseId, UUID operation, long version) {
        authorize(actor);
        if (operation == null || version < 0) {
            throw new CaseActionException("INVALID_REOPEN_REQUEST");
        }
        Order existing = store.find(caseId, actor.accountId(), operation).orElse(null);
        if (existing != null) {
            if (existing.version() != version || "REJECTED".equals(existing.state())) {
                throw new CaseActionException("INVALID_REOPEN_REQUEST");
            }
            return existing.id();
        }
        long previous = transaction.execute(status -> {
            CaseState state = lock(caseId);
            requireClosed(state, version);
            return workflow.leadingInstance(caseId).orElseThrow(() -> new CaseActionException("PROCESS_UNAVAILABLE"));
        });
        if (!engine.completed(previous, store.tenant(caseId))) {
            throw new CaseActionException("PROCESS_UNAVAILABLE");
        }
        return transaction.execute(status -> {
            authorize(actor);
            CaseState state = lock(caseId);
            Order repeated = store.find(caseId, actor.accountId(), operation).orElse(null);
            if (repeated != null && repeated.version() == version) {
                return repeated.id();
            }
            requireClosed(state, version);
            if (workflow.leadingInstance(caseId).filter(key -> key == previous).isEmpty() || !store.closedWithEvidence(caseId, previous)
                    || store.current(caseId, previous).isPresent()) {
                throw new CaseActionException("INVALID_REOPEN_REQUEST");
            }
            return store.request(state, actor.accountId(), operation).id();
        });
    }

    public Order order(UUID id) {
        return store.get(id).orElseThrow(() -> new WorkflowRejectedException("UNKNOWN_REOPEN_ORDER"));
    }

    public Order activate(String caseId, UUID orderId, long leader, long definition, String tenant, long element) {
        return transaction.execute(status -> {
            CaseState state = lock(caseId);
            Order order = order(orderId);
            if (!order.caseId().equals(caseId) || order.definition() != definition || !order.tenant().equals(tenant)) {
                throw new WorkflowRejectedException("INVALID_REOPEN_ORIGIN");
            }
            if ("APPLIED".equals(order.state()) && Long.valueOf(leader).equals(order.leader())) {
                return order;
            }
            if (!"PENDING".equals(order.state()) || !"CLOSED".equals(state.status()) || state.version() != order.version()
                    || workflow.leadingInstance(caseId).filter(key -> key == order.previous()).isEmpty()
                    || access.checkStaffAccess(new WebAccountContext(order.actor())) != StaffAccessDecision.ALLOWED) {
                store.reject(orderId);
                return null;
            }
            return store.activate(state, order, leader, element);
        });
    }

    private void authorize(WebAccountContext actor) {
        if (actor == null || access.checkStaffAccess(actor) != StaffAccessDecision.ALLOWED) {
            throw new CaseActionException(actor == null ? "UNAUTHENTICATED" : "FORBIDDEN");
        }
    }

    private CaseState lock(String caseId) {
        return workflow.lockCase(caseId).orElseThrow(() -> new CaseActionException("CASE_NOT_AVAILABLE"));
    }

    private void requireClosed(CaseState state, long version) {
        if (!"CLOSED".equals(state.status()) || state.version() != version) {
            throw new CaseActionException("INVALID_REOPEN_REQUEST");
        }
    }
}
