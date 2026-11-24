package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.StartOrder;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientHttpException;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.response.ProcessInstance;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.START_ORDER_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.CASE_ID;

@Component
@Profile("propertyflow-backend & !frontend-demo")
@ConditionalOnProperty(name = "propertyflow.workflow.enabled", havingValue = "true")
@RequiredArgsConstructor
public class CamundaProcessStartGateway implements ProcessStartGateway {

    private final CamundaClient client;

    @Override
    public StartResult ensureStarted(StartOrder order) {
        return ensureStarted(order.id(), order.definitionKey(), order.tenantId(),
                Map.of(CASE_ID, order.caseId(), START_ORDER_REF, order.id().toString()));
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
