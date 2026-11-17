package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.persistence.MailDeliveryStore;
import com.pertegato.propertyflow.caseprocessing.persistence.MailDeliveryStore.MailStart;
import com.pertegato.propertyflow.caseprocessing.workflow.ProcessStartGateway.StartResult;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("propertyflow-backend & !frontend-demo")
@ConditionalOnProperty(name = {"propertyflow.workflow.enabled", "propertyflow.case-mail.enabled"}, havingValue = "true")
public class MailProcessDispatcher {

    private final MailDeliveryStore store;
    private final CamundaProcessStartGateway gateway;
    private final long definitionKey;
    private final String tenantId;

    public MailProcessDispatcher(MailDeliveryStore store, CamundaProcessStartGateway gateway,
            @Value("${propertyflow.case-mail.definition-key}") long definitionKey,
            @Value("${propertyflow.workflow.tenant-id:<default>}") String tenantId) {
        if (definitionKey <= 0) {
            throw new IllegalArgumentException("A deployed mail process definition is required");
        }
        this.store = store;
        this.gateway = gateway;
        this.definitionKey = definitionKey;
        this.tenantId = tenantId;
    }

    @Scheduled(fixedDelayString = "${propertyflow.workflow.poll-delay-ms:5000}",
            initialDelayString = "${propertyflow.workflow.initial-delay-ms:5000}")
    public void dispatch() {
        store.discoverOrders();
        for (int index = 0; index < 20; index++) {
            Optional<MailStart> claimed = store.claimStart(definitionKey, tenantId);
            if (claimed.isEmpty()) {
                return;
            }
            MailStart start = claimed.get();
            StartResult result = gateway.ensureStarted(start.id(), start.definitionKey(), start.tenantId(),
                    Map.of("mailOrderRef", start.id().toString()));
            boolean blocked = result == StartResult.REJECTED || result == StartResult.ENDED_BEFORE_REGISTRATION;
            store.finishStart(start, result.name(), blocked, Duration.ofSeconds(Math.min(300, 5L << Math.min(start.attempts() - 1, 6))));
        }
    }
}
