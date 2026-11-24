package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.delivery.ReopenProcessGateway;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.ProcessInstance;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("propertyflow-backend & !frontend-demo")
public class CamundaReopenProcessGateway implements ReopenProcessGateway {

    private final ObjectProvider<CamundaClient> clients;
    private final boolean enabled;

    public CamundaReopenProcessGateway(ObjectProvider<CamundaClient> clients, @Value("${propertyflow.workflow.enabled:false}") boolean enabled) {
        this.clients = clients;
        this.enabled = enabled;
    }

    @Override
    public boolean completed(long leader, String tenant) {
        if (!enabled) {
            return false;
        }
        ProcessInstance instance = clients.getObject().newProcessInstanceGetRequest(leader).send().join();
        return "COMPLETED".equals(instance.getState().name()) && instance.getParentProcessInstanceKey() == null
                && CaseProcessBpmnConstants.PROCESS_ID.equals(instance.getProcessDefinitionId())
                && tenant.equals(instance.getTenantId());
    }
}
