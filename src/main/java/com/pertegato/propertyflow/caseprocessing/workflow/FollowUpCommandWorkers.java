package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.FollowUpBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.service.FollowUpCommandService;
import com.pertegato.propertyflow.caseprocessing.service.FollowUpCommandService.Applied;
import com.pertegato.propertyflow.caseprocessing.service.WorkflowRejectedException;
import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.worker.JobClient;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.ACTIVE_FOLLOW_UP_VERSION;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.ACTIVE_WAIT_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.ACTIVE_WORK_TASK_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.DECISION_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.OUTCOME;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.PENDING_REVIEW_FOLLOW_UP_VERSION;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.PENDING_TENANT_CONTACT_FOLLOW_UP_VERSION;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.PENDING_WORK_REVIEW_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.STAFF_DECISION_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.FollowUpBpmnConstants.ErrorCode.FOLLOW_UP_CHANGE_STALE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.FollowUpBpmnConstants.JobType.FOLLOW_UP_CHANGE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.FollowUpBpmnConstants.JobType.FOLLOW_UP_CLOSE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.FollowUpBpmnConstants.Variablen.FOLLOW_UP_COMMAND_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.FollowUpBpmnConstants.Variablen.FOLLOW_UP_DUE_AT;

@Component
@Profile("propertyflow-backend & !frontend-demo")
@ConditionalOnProperty(name = "propertyflow.workflow.enabled", havingValue = "true")
@RequiredArgsConstructor
public class FollowUpCommandWorkers {

    private final CamundaClient engine;
    private final FollowUpCommandService service;

    @JobWorker(type = FOLLOW_UP_CHANGE, autoComplete = false)
    public void apply(JobClient client, ActivatedJob job) {
        Applied applied;
        try {
            boolean delete = FollowUpBpmnConstants.ServiceTask.DELETE_FOLLOW_UP.equals(job.getElementId());
            require(FollowUpBpmnConstants.PROCESS_ID.equals(job.getBpmnProcessId()) && (delete
                    || FollowUpBpmnConstants.ServiceTask.UPDATE_FOLLOW_UP.equals(job.getElementId())));
            Origin origin = origin(job);
            applied = service.apply(UUID.fromString((String) job.getVariablesAsMap().get(FOLLOW_UP_COMMAND_REF)),
                    origin.leader(), job.getProcessInstanceKey(), origin.staff(), delete);
            if (applied.accepted()) {
                Map<String, Object> epoch = delete
                        ? Map.of(ACTIVE_WORK_TASK_REF, applied.command().toString(), PENDING_WORK_REVIEW_REF, "",
                                STAFF_DECISION_REF, applied.command().toString())
                        : Map.of(ACTIVE_FOLLOW_UP_VERSION, applied.waitId().toString(), PENDING_TENANT_CONTACT_FOLLOW_UP_VERSION, "",
                                PENDING_REVIEW_FOLLOW_UP_VERSION, "");
                engine.newSetVariablesCommand(origin.staff()).variables(epoch).local(true).send().join();
                service.confirmed(applied.command());
            }
        } catch (RuntimeException exception) {
            boolean rejected = exception instanceof WorkflowRejectedException || exception instanceof IllegalArgumentException;
            client.newFailCommand(job.getKey()).retries(rejected ? 0 : Math.max(1, job.getRetries())).retryBackoff(Duration.ofSeconds(5))
                    .errorMessage(rejected ? "INVALID_FOLLOW_UP_CHANGE" : "FOLLOW_UP_CHANGE_PENDING").send().join();
            return;
        }
        if (!applied.accepted()) {
            client.newThrowErrorCommand(job.getKey()).errorCode(FOLLOW_UP_CHANGE_STALE).errorMessage("FOLLOW_UP_BASIS_CHANGED").send().join();
        } else if (applied.delete()) {
            client.newCompleteCommand(job.getKey()).variables(Map.of(OUTCOME, "CONTINUE", DECISION_REF, applied.command().toString())).send().join();
        } else {
            client.newCompleteCommand(job.getKey()).variables(Map.of(ACTIVE_WAIT_REF, applied.waitId().toString(),
                    FOLLOW_UP_DUE_AT, applied.due().toString())).send().join();
        }
    }

    @JobWorker(type = FOLLOW_UP_CLOSE, autoComplete = false)
    public void close(JobClient client, ActivatedJob job) {
        boolean accepted;
        UUID command;
        try {
            require(FollowUpBpmnConstants.PROCESS_ID.equals(job.getBpmnProcessId())
                    && FollowUpBpmnConstants.ServiceTask.CLOSE_FOLLOW_UP.equals(job.getElementId()));
            Origin origin = origin(job);
            command = UUID.fromString((String) job.getVariablesAsMap().get(FOLLOW_UP_COMMAND_REF));
            accepted = service.prepareClosure(command, origin.leader(), job.getProcessInstanceKey(), origin.staff());
        } catch (RuntimeException exception) {
            boolean rejected = exception instanceof WorkflowRejectedException || exception instanceof IllegalArgumentException;
            client.newFailCommand(job.getKey())
                    .retries(rejected ? 0 : Math.max(1, job.getRetries()))
                    .retryBackoff(Duration.ofSeconds(5))
                    .errorMessage(rejected ? "INVALID_FOLLOW_UP_CLOSURE" : "FOLLOW_UP_CLOSURE_PENDING")
                    .send()
                    .join();
            return;
        }
        if (!accepted) {
            client.newThrowErrorCommand(job.getKey())
                    .errorCode(FOLLOW_UP_CHANGE_STALE)
                    .errorMessage("FOLLOW_UP_BASIS_CHANGED")
                    .send()
                    .join();
        } else {
            client.newCompleteCommand(job.getKey())
                    .variables(Map.of(OUTCOME, "CLOSE", DECISION_REF, command.toString()))
                    .send()
                    .join();
        }
    }

    private Origin origin(ActivatedJob job) {
        ProcessInstance child = engine.newProcessInstanceGetRequest(job.getProcessInstanceKey()).send().join();
        require(child.getParentProcessInstanceKey() != null && child.getParentElementInstanceKey() != null);
        ElementInstance call = engine.newElementInstanceGetRequest(child.getParentElementInstanceKey()).send().join();
        ProcessInstance staff = engine.newProcessInstanceGetRequest(child.getParentProcessInstanceKey()).send().join();
        require(StaffProcessingBpmnConstants.CallActivity.FOLLOW_UP_CALL.equals(call.getElementId())
                && StaffProcessingBpmnConstants.PROCESS_ID.equals(call.getProcessDefinitionId())
                && child.getParentProcessInstanceKey().equals(call.getProcessInstanceKey()) && job.getTenantId().equals(call.getTenantId())
                && staff.getParentProcessInstanceKey() != null && staff.getParentElementInstanceKey() != null);
        ElementInstance parent = engine.newElementInstanceGetRequest(staff.getParentElementInstanceKey()).send().join();
        require(CaseProcessBpmnConstants.CallActivity.STAFF_PROCESSING_CALL.equals(parent.getElementId())
                && CaseProcessBpmnConstants.PROCESS_ID.equals(parent.getProcessDefinitionId())
                && staff.getParentProcessInstanceKey().equals(parent.getProcessInstanceKey()) && job.getTenantId().equals(parent.getTenantId()));
        return new Origin(staff.getParentProcessInstanceKey(), child.getParentProcessInstanceKey());
    }

    private record Origin(long leader, long staff) {
    }

    private void require(boolean valid) {
        if (!valid) {
            throw new WorkflowRejectedException("INVALID_FOLLOW_UP_ORIGIN");
        }
    }
}
