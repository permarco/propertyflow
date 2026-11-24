package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.contract.CaseActionException;
import com.pertegato.propertyflow.caseprocessing.delivery.StaffMessageGateway;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientHttpException;
import io.camunda.client.api.response.CorrelateMessageResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.STAFF_MESSAGE_COMMAND_REF;

@Component
@Profile("propertyflow-backend & !frontend-demo")
public class CamundaStaffMessageGateway implements StaffMessageGateway {

    public static final String STAFF_MESSAGE = "Mitarbeiternachricht als Nebenaktion senden";
    public static final String FOLLOW_UP_MESSAGE = "Mitarbeiternachricht während Wiedervorlage angefordert";
    public static final String REPLY_MESSAGE = "Mitarbeiternachricht während Mieterwartephase senden";
    private final ObjectProvider<CamundaClient> clients;
    private final boolean enabled;

    public CamundaStaffMessageGateway(ObjectProvider<CamundaClient> clients, @Value("${propertyflow.workflow.enabled:false}") boolean enabled) {
        this.clients = clients;
        this.enabled = enabled;
    }

    @Override
    public void correlate(UUID command, long target, String tenant, boolean automatic, String correlationKey) {
        correlate(command, target, tenant, STAFF_MESSAGE, automatic, correlationKey);
    }

    @Override
    public void correlateFollowUp(UUID command, long target, String tenant, String correlationKey) {
        correlate(command, target, tenant, FOLLOW_UP_MESSAGE, false, correlationKey);
    }

    private void correlate(UUID command, long target, String tenant, String message, boolean automatic, String correlationKey) {
        CamundaClient client = enabled ? clients.getIfAvailable() : null;
        if (client == null) {
            throw new IllegalStateException("WORKFLOW_UNAVAILABLE");
        }
        try {
            CorrelateMessageResponse response = automatic
                    ? CaseActionMessage.STAFF_QUESTION.correlate(client, correlationKey, tenant, command)
                    : client.newCorrelateMessageCommand()
                            .messageName(message)
                            .correlationKey(correlationKey)
                            .tenantId(tenant)
                            .variables(Map.of(STAFF_MESSAGE_COMMAND_REF, command.toString()))
                            .send()
                            .join();
            if (!Long.valueOf(target).equals(response.getProcessInstanceKey()) || !tenant.equals(response.getTenantId())) {
                throw new IllegalStateException("OTHER_MESSAGE_TARGET");
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

    @Override
    public void correlateReply(UUID command, long target, String tenant, String correlationKey) {
        correlate(command, target, tenant, REPLY_MESSAGE, false, correlationKey);
    }
}
