package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.StartOrder;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientHttpException;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.response.ProcessInstance;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("propertyflow-backend & !frontend-demo")
@ConditionalOnProperty(name = "propertyflow.workflow.enabled", havingValue = "true")
public class CamundaProcessStartGateway implements ProcessStartGateway {

    private final CamundaClient client;

    public CamundaProcessStartGateway(CamundaClient client) {
        this.client = client;
    }

    @Override
    public StartResult ensureStarted(StartOrder order) {
        return ensureStarted(order.id(), order.definitionKey(), order.tenantId(),
                Map.of("caseId", order.caseId(), "startOrderRef", order.id().toString()));
    }

    public StartResult ensureStarted(UUID id, long definitionKey, String tenantId, Map<String, Object> variables) {
        try {
            // Search is eventually consistent. An empty result is not proof that no instance exists;
            // the engine's enabled business-ID constraint is the second, authoritative guard.
            List<ProcessInstance> instances = client.newProcessInstanceSearchRequest()
                    .filter(filter -> filter.businessId(id.toString()).processDefinitionKey(definitionKey).tenantId(tenantId))
                    .send().join().items();
            if (!instances.isEmpty()) {
                boolean active = instances.stream().anyMatch(instance -> instance.getState() == ProcessInstanceState.ACTIVE);
                return active ? StartResult.EXISTING : StartResult.ENDED_BEFORE_REGISTRATION;
            }
            client.newCreateInstanceCommand()
                    .processDefinitionKey(definitionKey)
                    .businessId(id.toString())
                    .tenantId(tenantId)
                    .variables(variables)
                    .send().join();
            return StartResult.STARTED;
        } catch (RuntimeException exception) {
            Throwable cause = exception;
            while (cause.getCause() != null && !(cause instanceof ClientHttpException)) {
                cause = cause.getCause();
            }
            if (cause instanceof ClientHttpException http) {
                if (http.code() == 409) {
                    return StartResult.EXISTING;
                }
                if (http.code() == 400 || http.code() == 401 || http.code() == 403 || http.code() == 404) {
                    return StartResult.REJECTED;
                }
            }
            return StartResult.UNKNOWN;
        }
    }
}
