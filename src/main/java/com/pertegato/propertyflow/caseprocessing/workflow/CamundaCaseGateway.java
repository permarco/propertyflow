package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.contract.CaseActionException;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.delivery.CaseProcessGateway;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientHttpException;
import io.camunda.client.api.response.CorrelateMessageResponse;
import io.camunda.client.api.search.response.ProcessInstance;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("propertyflow-backend")
public class CamundaCaseGateway implements CaseProcessGateway {

    private final ObjectProvider<CamundaClient> clients;
    private final boolean enabled;

    public CamundaCaseGateway(ObjectProvider<CamundaClient> clients, @Value("${propertyflow.workflow.enabled:false}") boolean enabled) {
        this.clients = clients;
        this.enabled = enabled;
    }

    @Override
    public boolean active(long instanceKey, String tenantId) {
        if (!enabled) {
            return false;
        }
        CamundaClient client = clients.getIfAvailable();
        if (client == null) {
            return false;
        }
        try {
            ProcessInstance instance = client.newProcessInstanceGetRequest(instanceKey).send().join();
            return CaseProcessBpmnConstants.PROCESS_ID.equals(instance.getProcessDefinitionId())
                    && "ACTIVE".equals(instance.getState().name())
                    && instance.getParentProcessInstanceKey() == null && tenantId.equals(instance.getTenantId());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override
    public void deliverCaseData(UUID commandId, long instanceKey, String tenantId) {
        deliver(commandId, instanceKey, tenantId, CaseActionMessage.CASE_DATA);
    }

    @Override
    public void deliverClearAnswer(UUID commandId, long instanceKey, String tenantId) {
        deliver(commandId, instanceKey, tenantId, CaseActionMessage.CLEAR_ANSWER);
    }

    @Override
    public void deliverDirectFollowUp(UUID commandId, long instanceKey, String tenantId) {
        deliver(commandId, instanceKey, tenantId, CaseActionMessage.DIRECT_FOLLOW_UP);
    }

    @Override
    public void deliverTakeover(UUID commandId, long instanceKey, String tenantId) {
        deliver(commandId, instanceKey, tenantId, CaseActionMessage.STAFF_TAKEOVER);
    }

    @Override
    public void deliverClarificationClosure(UUID commandId, long instanceKey, String tenantId) {
        deliver(commandId, instanceKey, tenantId, CaseActionMessage.CLOSE_CLARIFICATION);
    }

    @Override
    public void deliverReplyClosure(UUID commandId, long instanceKey, String tenantId) {
        deliver(commandId, instanceKey, tenantId, CaseActionMessage.CLOSE_REPLY);
    }

    private void deliver(UUID commandId, long instanceKey, String tenantId, CaseActionMessage action) {
        CamundaClient client = enabled ? clients.getIfAvailable() : null;
        if (client == null) {
            throw new IllegalStateException("WORKFLOW_UNAVAILABLE");
        }
        try {
            CorrelateMessageResponse response = action.correlate(client, Long.toString(instanceKey), tenantId, commandId);
            if (!Long.valueOf(instanceKey).equals(response.getProcessInstanceKey()) || !tenantId.equals(response.getTenantId())) {
                throw new IllegalStateException("WRONG_CORRELATION_TARGET");
            }
        } catch (RuntimeException exception) {
            Throwable cause = exception;
            while (cause != null) {
                if (cause instanceof ClientHttpException http && (http.code() == 400 || http.code() == 404 || http.code() == 409)) {
                    throw new CaseActionException("CASE_NOT_ACTIVE");
                }
                cause = cause.getCause();
            }
            throw exception;
        }
    }
}
