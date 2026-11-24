package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.delivery.TenantMessageGateway;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantMessageStore.Message;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantMessageStore;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("propertyflow-backend & !frontend-demo")
@ConditionalOnProperty(name = "propertyflow.workflow.enabled", havingValue = "true")
@RequiredArgsConstructor
public class TenantMessageDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(TenantMessageDispatcher.class);
    private final TenantMessageStore messages;
    private final TenantMessageGateway gateway;

    @Scheduled(fixedDelayString = "${propertyflow.workflow.poll-delay-ms:5000}",
            initialDelayString = "${propertyflow.workflow.initial-delay-ms:5000}")
    public void dispatch() {
        for (int index = 0; index < 20; index++) {
            Optional<Message> pending = messages.claimDelivery();
            if (pending.isEmpty()) {
                return;
            }
            Message message = pending.get();
            try {
                gateway.correlate(message.id(), message.leader(), message.tenant());
                messages.correlated(message.id());
            } catch (RuntimeException exception) {
                LOGGER.warn("Tenant message {} remains pending", message.id());
            }
        }
    }
}
