package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWaitStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWaitStore.Contact;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.ProcessInstance;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.CONTACT_MESSAGE_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.PENDING_TENANT_CONTACT_FOLLOW_UP_VERSION;

@Component
@Profile("propertyflow-backend & !frontend-demo")
@ConditionalOnProperty(name = "propertyflow.workflow.enabled", havingValue = "true")
@RequiredArgsConstructor
public class FollowUpContactDispatcher {

    private final CaseWaitStore store;
    private final CamundaClient engine;

    @Scheduled(fixedDelayString = "${propertyflow.workflow.poll-delay-ms:5000}",
            initialDelayString = "${propertyflow.workflow.initial-delay-ms:5000}")
    public void dispatch() {
        for (int index = 0; index < 20; index++) {
            Contact contact = store.claimContact().orElse(null);
            if (contact == null) {
                return;
            }
            try {
                ProcessInstance staff = engine.newProcessInstanceGetRequest(contact.staff()).send().join();
                if (!StaffProcessingBpmnConstants.PROCESS_ID.equals(staff.getProcessDefinitionId())
                        || !Long.valueOf(contact.leader()).equals(staff.getParentProcessInstanceKey())
                        || !contact.tenant().equals(staff.getTenantId()) || !"ACTIVE".equals(staff.getState().name())) {
                    continue;
                }
                // The native decision initializes activeFollowUpVersion before the call starts.
                // A delayed marker therefore cannot match a subsequently selected wait.
                engine.newSetVariablesCommand(contact.staff()).variables(Map.of(
                        PENDING_TENANT_CONTACT_FOLLOW_UP_VERSION, contact.waitId().toString(),
                        CONTACT_MESSAGE_REF, contact.messageId().toString())).local(true).send().join();
                store.delivered(contact.messageId());
            } catch (RuntimeException exception) {
                // The durable contact remains pending; no public text or identity is logged.
            }
        }
    }
}
