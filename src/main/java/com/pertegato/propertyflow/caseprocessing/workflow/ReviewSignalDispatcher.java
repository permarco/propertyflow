package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.persistence.StaffReviewStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffReviewStore.SignalDelivery;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.CorrelateMessageResponse;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("propertyflow-backend & !frontend-demo")
@ConditionalOnProperty(name = "propertyflow.workflow.enabled", havingValue = "true")
public class ReviewSignalDispatcher {

    public static final String MESSAGE_NAME = "Fachlicher Prüfbedarf festgestellt";
    private static final Logger LOGGER = LoggerFactory.getLogger(ReviewSignalDispatcher.class);
    private final StaffReviewStore store;
    private final CamundaClient camunda;

    public ReviewSignalDispatcher(StaffReviewStore store, CamundaClient camunda) {
        this.store = store;
        this.camunda = camunda;
    }

    @Scheduled(fixedDelayString = "${propertyflow.workflow.poll-delay-ms:5000}",
            initialDelayString = "${propertyflow.workflow.initial-delay-ms:5000}")
    public void dispatch() {
        for (int index = 0; index < 20; index++) {
            Optional<SignalDelivery> pending = store.claimSignal();
            if (pending.isEmpty()) {
                return;
            }
            SignalDelivery signal = pending.get();
            try {
                CorrelateMessageResponse response = camunda.newCorrelateMessageCommand().messageName(MESSAGE_NAME)
                        .correlationKey(Long.toString(signal.leader())).tenantId(signal.tenant())
                        .variables(Map.of("reviewSignalRef", signal.id().toString())).send().join();
                if (!Long.valueOf(signal.leader()).equals(response.getProcessInstanceKey()) || !signal.tenant().equals(response.getTenantId())) {
                    throw new IllegalStateException("WRONG_REVIEW_CORRELATION_TARGET");
                }
            } catch (RuntimeException exception) {
                // Only the receiving worker confirms delivery. Unknown publication outcomes remain retryable.
                LOGGER.warn("Review signal {} remains pending", signal.id());
            }
        }
    }
}
