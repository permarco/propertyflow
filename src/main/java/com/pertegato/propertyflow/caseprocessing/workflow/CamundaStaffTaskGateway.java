package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.contract.CaseActionException;
import com.pertegato.propertyflow.caseprocessing.contract.StaffDecisionAction;
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

@Component
@Profile("propertyflow-backend & !frontend-demo")
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
                || !InitialCaseWorkers.CASE_PROCESS.equals(root.getProcessDefinitionId()) || !binding.tenant().equals(root.getTenantId())
                || !"ACTIVE".equals(child.getState().name()) || !StaffReviewWorkers.STAFF_PROCESS.equals(child.getProcessDefinitionId())
                || !Long.valueOf(binding.leader()).equals(child.getParentProcessInstanceKey())
                || !Long.valueOf(binding.parentElement()).equals(child.getParentElementInstanceKey())
                || !binding.tenant().equals(child.getTenantId()) || !"Staff".equals(call.getElementId())
                || !Long.valueOf(binding.leader()).equals(call.getProcessInstanceKey()) || !binding.tenant().equals(call.getTenantId())) {
            return Optional.empty();
        }
        List<UserTask> tasks = client.newUserTaskSearchRequest().filter(filter -> filter.processInstanceKey(binding.child())
                .elementId(binding.element()).state(UserTaskState.CREATED).tenantId(binding.tenant())).send().join().items();
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
                            ? Map.of("staffDecision", action.name(), "staffDecisionRef", decisionId.toString(),
                                    "outcome", "CLOSE", "decisionRef", decisionId.toString())
                            : action == StaffDecisionAction.FOLLOW_UP
                                    ? Map.of("staffDecision", action.name(), "staffDecisionRef", decisionId.toString(),
                                            "followUpEntryContextRef", decisionId.toString(), "activeFollowUpVersion", decisionId.toString(),
                                            "pendingTenantContactFollowUpVersion", "", "pendingReviewFollowUpVersion", "")
                            : action == StaffDecisionAction.CONTINUE
                                    ? Map.of("staffDecision", action.name(), "staffDecisionRef", decisionId.toString(),
                                            "activeWorkTaskRef", decisionId.toString(), "pendingWorkReviewRef", "")
                            : action == StaffDecisionAction.WAIT_EXTERNAL
                                    ? Map.of("staffDecision", action.name(), "staffDecisionRef", decisionId.toString(),
                                            "activeExternalWaitRef", decisionId.toString(), "externalWaitReviewRequired", false, "reviewWaitRef", "")
                            : Map.of("staffDecision", action.name(), "staffDecisionRef", decisionId.toString())).send().join();
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
