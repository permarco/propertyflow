package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.contract.CaseActionException;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.FollowUpBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.delivery.FollowUpGateway;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientHttpException;
import io.camunda.client.api.response.CorrelateMessageResponse;
import io.camunda.client.api.search.enums.MessageSubscriptionState;
import io.camunda.client.api.search.response.ProcessInstance;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.FollowUpBpmnConstants.Variablen.FOLLOW_UP_COMMAND_REF;

@Component
@Profile("propertyflow-backend & !frontend-demo")
public class CamundaFollowUpGateway implements FollowUpGateway {

    public static final String CHANGE = "Wiedervorlage ändern";
    public static final String DELETE = "Wiedervorlage löschen";
    public static final String CLOSE = "Mitarbeiterabschluss bestätigt";
    private final ObjectProvider<CamundaClient> clients;
    private final boolean enabled;

    public CamundaFollowUpGateway(ObjectProvider<CamundaClient> clients, @Value("${propertyflow.workflow.enabled:false}") boolean enabled) {
        this.clients = clients;
        this.enabled = enabled;
    }

    @Override
    public boolean available(long child, long leader, long staff, String tenant, UUID waitId) {
        if (!enabled || clients.getIfAvailable() == null) {
            return false;
        }
        CamundaClient client = clients.getObject();
        ProcessInstance process = client.newProcessInstanceGetRequest(child).send().join();
        ProcessInstance parent = client.newProcessInstanceGetRequest(staff).send().join();
        if (!FollowUpBpmnConstants.PROCESS_ID.equals(process.getProcessDefinitionId())
                || !"ACTIVE".equals(process.getState().name())
                || !Long.valueOf(staff).equals(process.getParentProcessInstanceKey()) || !tenant.equals(process.getTenantId())
                || !StaffProcessingBpmnConstants.PROCESS_ID.equals(parent.getProcessDefinitionId())
                || !Long.valueOf(leader).equals(parent.getParentProcessInstanceKey()) || !tenant.equals(parent.getTenantId())) {
            return false;
        }
        return client.newMessageSubscriptionSearchRequest().filter(filter -> filter.processInstanceKey(child).messageName(CHANGE)
                .correlationKey(waitId.toString()).tenantId(tenant).messageSubscriptionState(MessageSubscriptionState.CREATED))
                .send().join().items().size() == 1;
    }

    @Override
    public void correlate(UUID command, long child, String tenant, UUID waitId, boolean delete) {
        correlate(command, child, tenant, waitId, delete ? DELETE : CHANGE);
    }

    @Override
    public void close(UUID command, long child, String tenant, UUID waitId) {
        correlate(command, child, tenant, waitId, CLOSE);
    }

    private void correlate(UUID command, long child, String tenant, UUID waitId, String message) {
        if (!enabled || clients.getIfAvailable() == null) {
            throw new IllegalStateException("WORKFLOW_UNAVAILABLE");
        }
        try {
            CorrelateMessageResponse response = clients.getObject().newCorrelateMessageCommand()
                    .messageName(message)
                    .correlationKey(waitId.toString())
                    .tenantId(tenant)
                    .variables(Map.of(FOLLOW_UP_COMMAND_REF, command.toString()))
                    .send()
                    .join();
            if (!Long.valueOf(child).equals(response.getProcessInstanceKey()) || !tenant.equals(response.getTenantId())) {
                throw new IllegalStateException("OTHER_FOLLOW_UP_TARGET");
            }
        } catch (RuntimeException exception) {
            Throwable cause = exception;
            while (cause != null) {
                if (cause instanceof ClientHttpException http && (http.code() == 400 || http.code() == 404 || http.code() == 409)) {
                    throw new CaseActionException("CASE_CHANGED");
                }
                cause = cause.getCause();
            }
            throw exception;
        }
    }
}
