package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.contract.CaseActionException;
import com.pertegato.propertyflow.caseprocessing.contract.StaffDecisionAction;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.delivery.StaffTaskGateway;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientHttpException;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.UserTask;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.ACTIVE_FOLLOW_UP_VERSION;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.ACTIVE_WORK_TASK_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.DECISION_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.OUTCOME;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.PENDING_REVIEW_FOLLOW_UP_VERSION;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.PENDING_TENANT_CONTACT_FOLLOW_UP_VERSION;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.PENDING_WORK_REVIEW_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.STAFF_DECISION_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants.Variablen.ACTIVE_EXTERNAL_WAIT_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants.Variablen.EXTERNAL_WAIT_REVIEW_REQUIRED;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants.Variablen.FOLLOW_UP_ENTRY_CONTEXT_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants.Variablen.REVIEW_WAIT_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants.Variablen.STAFF_DECISION;

@Component
@Profile("propertyflow-backend")
public class CamundaStaffTaskGateway implements StaffTaskGateway {

    private final ObjectProvider<CamundaClient> clients;
    private final boolean enabled;

    public CamundaStaffTaskGateway(ObjectProvider<CamundaClient> clients, @Value("${propertyflow.workflow.enabled:false}") boolean enabled) {
        this.clients = clients;
        this.enabled = enabled;
    }

    @Override
    public Optional<Long> currentTask(Binding binding) {
        if (!enabled) {
            return Optional.empty();
        }
        CamundaClient client = clients.getObject();
        ProcessInstance root = client.newProcessInstanceGetRequest(binding.leader()).send().join();
        ProcessInstance child = client.newProcessInstanceGetRequest(binding.child()).send().join();
        ElementInstance call = client.newElementInstanceGetRequest(binding.parentElement()).send().join();
        if (!"ACTIVE".equals(root.getState().name()) || root.getParentProcessInstanceKey() != null
                || !CaseProcessBpmnConstants.PROCESS_ID.equals(root.getProcessDefinitionId())
                || !binding.tenant().equals(root.getTenantId())
                || !"ACTIVE".equals(child.getState().name())
                || !StaffProcessingBpmnConstants.PROCESS_ID.equals(child.getProcessDefinitionId())
                || !Long.valueOf(binding.leader()).equals(child.getParentProcessInstanceKey())
                || !Long.valueOf(binding.parentElement()).equals(child.getParentElementInstanceKey())
                || !binding.tenant().equals(child.getTenantId())
                || !CaseProcessBpmnConstants.CallActivity.STAFF_PROCESSING_CALL.equals(call.getElementId())
                || !Long.valueOf(binding.leader()).equals(call.getProcessInstanceKey()) || !binding.tenant().equals(call.getTenantId())) {
            return Optional.empty();
        }
        String element = binding.element();
        List<UserTask> tasks = client.newUserTaskSearchRequest().filter(filter -> filter.processInstanceKey(binding.child())
                .elementId(element).state(UserTaskState.CREATED).tenantId(binding.tenant())).send().join().items();
        return tasks.size() == 1 ? Optional.of(tasks.getFirst().getUserTaskKey()) : Optional.empty();
    }

    @Override
    public void completeTask(Binding binding, long taskKey, UUID decisionId, StaffDecisionAction action) {
        if (currentTask(binding).filter(key -> key == taskKey).isEmpty()) {
            throw new CaseActionException("INVALID_DECISION");
        }
        try {
            clients.getObject().newCompleteUserTaskCommand(taskKey)
                    .variables(action == StaffDecisionAction.CLOSE
                            ? Map.of(STAFF_DECISION, action.name(), STAFF_DECISION_REF, decisionId.toString(),
                                    OUTCOME, "CLOSE", DECISION_REF, decisionId.toString())
                            : action == StaffDecisionAction.FOLLOW_UP
                                    ? Map.of(STAFF_DECISION, action.name(), STAFF_DECISION_REF, decisionId.toString(),
                                            FOLLOW_UP_ENTRY_CONTEXT_REF, decisionId.toString(), ACTIVE_FOLLOW_UP_VERSION, decisionId.toString(),
                                            PENDING_TENANT_CONTACT_FOLLOW_UP_VERSION, "", PENDING_REVIEW_FOLLOW_UP_VERSION, "")
                            : action == StaffDecisionAction.CONTINUE
                                    ? Map.of(STAFF_DECISION, action.name(), STAFF_DECISION_REF, decisionId.toString(),
                                            ACTIVE_WORK_TASK_REF, decisionId.toString(), PENDING_WORK_REVIEW_REF, "")
                            : action == StaffDecisionAction.WAIT_EXTERNAL
                                    ? Map.of(STAFF_DECISION, action.name(), STAFF_DECISION_REF, decisionId.toString(),
                                            ACTIVE_EXTERNAL_WAIT_REF, decisionId.toString(),
                                            EXTERNAL_WAIT_REVIEW_REQUIRED, false, REVIEW_WAIT_REF, "")
                            : Map.of(STAFF_DECISION, action.name(), STAFF_DECISION_REF, decisionId.toString())).send().join();
        } catch (RuntimeException exception) {
            Throwable cause = exception;
            while (cause != null) {
                if (cause instanceof ClientHttpException http && (http.code() == 400 || http.code() == 404 || http.code() == 409)) {
                    throw new CaseActionException("INVALID_DECISION");
                }
                cause = cause.getCause();
            }
            throw exception;
        }
    }
}
