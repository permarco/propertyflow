package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.contract.CaseActionException;
import com.pertegato.propertyflow.caseprocessing.delivery.CaseProcessGateway;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientHttpException;
import io.camunda.client.api.response.CorrelateMessageResponse;
import io.camunda.client.api.search.response.ProcessInstance;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("propertyflow-backend & !frontend-demo")
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
            return InitialCaseWorkers.CASE_PROCESS.equals(instance.getProcessDefinitionId()) && "ACTIVE".equals(instance.getState().name())
                    && instance.getParentProcessInstanceKey() == null && tenantId.equals(instance.getTenantId());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override
    public void deliverCaseData(UUID commandId, long instanceKey, String tenantId) {
        deliver(commandId, instanceKey, tenantId, CaseDataWorkers.MESSAGE_NAME, "caseDataCommandRef");
    }

    @Override
    public void deliverClearAnswer(UUID commandId, long instanceKey, String tenantId) {
        deliver(commandId, instanceKey, tenantId, "Antwortbedarf aufheben angefordert", "clearAnswerCommandRef");
    }

    @Override
    public void deliverDirectFollowUp(UUID commandId, long instanceKey, String tenantId) {
        deliver(commandId, instanceKey, tenantId, "Direkte Wiedervorlage angefordert", "directFollowCommandRef");
    }

    @Override
    public void deliverTakeover(UUID commandId, long instanceKey, String tenantId) {
        deliver(commandId, instanceKey, tenantId, "Bearbeitung während Abklärung übernehmen", "staffTakeoverCommandRef");
    }

    @Override
    public void deliverClarificationClosure(UUID commandId, long instanceKey, String tenantId) {
        deliver(commandId, instanceKey, tenantId, "Mitarbeiterabschluss während Abklärung angefordert", "clarificationClosureCommandRef");
    }

    @Override
    public void deliverReplyClosure(UUID commandId, long instanceKey, String tenantId) {
        deliver(commandId, instanceKey, tenantId, "Mitarbeiterabschluss im Mieterwartepfad angefordert", "replyClosureCommandRef");
    }

    private void deliver(UUID commandId, long instanceKey, String tenantId, String message, String variable) {
        CamundaClient client = enabled ? clients.getIfAvailable() : null;
        if (client == null) {
            throw new IllegalStateException("WORKFLOW_UNAVAILABLE");
        }
        try {
            CorrelateMessageResponse response = client.newCorrelateMessageCommand()
                    .messageName(message)
                    .correlationKey(Long.toString(instanceKey))
                    .tenantId(tenantId)
                    .variables(Map.of(variable, commandId.toString()))
                    .send()
                    .join();
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
