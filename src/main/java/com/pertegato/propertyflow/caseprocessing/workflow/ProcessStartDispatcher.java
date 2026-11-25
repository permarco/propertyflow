package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.StartOrder;
import com.pertegato.propertyflow.caseprocessing.workflow.ProcessStartGateway.StartResult;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("propertyflow-backend")
@ConditionalOnProperty(name = "propertyflow.workflow.enabled", havingValue = "true")
public class ProcessStartDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessStartDispatcher.class);
    private final CaseWorkflowStore store;
    private final ProcessStartGateway gateway;
    private final long definitionKey;
    private final String tenantId;

    public ProcessStartDispatcher(CaseWorkflowStore store, ProcessStartGateway gateway,
            @Value("${propertyflow.workflow.definition-key}") long definitionKey,
            @Value("${propertyflow.workflow.tenant-id:<default>}") String tenantId) {
        if (definitionKey <= 0 || tenantId.isBlank()) {
            throw new IllegalArgumentException("A deployed workflow definition key and tenant are required");
        }
        this.store = store;
        this.gateway = gateway;
        this.definitionKey = definitionKey;
        this.tenantId = tenantId;
    }

    @Scheduled(fixedDelayString = "${propertyflow.workflow.poll-delay-ms:5000}",
            initialDelayString = "${propertyflow.workflow.initial-delay-ms:5000}")
    public void dispatch() {
        for (int index = 0; index < 20; index++) {
            Optional<StartOrder> claimed = store.claim(definitionKey, tenantId, Duration.ofMinutes(2));
            if (claimed.isEmpty()) {
                return;
            }
            StartOrder order = claimed.get();
            StartResult result = gateway.ensureStarted(order);
            boolean blocked = result == StartResult.REJECTED || result == StartResult.ENDED_BEFORE_REGISTRATION;
            long delay = Math.min(300, 5L << Math.min(order.attempts() - 1, 6));
            store.finishAttempt(order, result.name(), blocked, Duration.ofSeconds(delay));
            if (blocked) {
                LOGGER.warn("Workflow start order {} requires reconciliation: {}", order.id(), result);
            }
        }
    }
}
