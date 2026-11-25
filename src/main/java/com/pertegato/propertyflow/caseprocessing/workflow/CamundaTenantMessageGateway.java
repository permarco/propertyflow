package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.delivery.TenantMessageGateway;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.CorrelateMessageResponse;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("propertyflow-backend")
public class CamundaTenantMessageGateway implements TenantMessageGateway {

    private final ObjectProvider<CamundaClient> clients;
    private final boolean enabled;

    public CamundaTenantMessageGateway(ObjectProvider<CamundaClient> clients, @Value("${propertyflow.workflow.enabled:false}") boolean enabled) {
        this.clients = clients;
        this.enabled = enabled;
    }

    @Override
    public void correlate(UUID messageId, long leader, String tenant) {
        CamundaClient client = enabled ? clients.getIfAvailable() : null;
        if (client == null) {
            throw new IllegalStateException("WORKFLOW_UNAVAILABLE");
        }
        CorrelateMessageResponse response = CaseActionMessage.TENANT_MESSAGE.correlate(client, Long.toString(leader), tenant, messageId);
        if (!Long.valueOf(leader).equals(response.getProcessInstanceKey()) || !tenant.equals(response.getTenantId())) {
            throw new IllegalStateException("WRONG_MESSAGE_CORRELATION_TARGET");
        }
    }
}
