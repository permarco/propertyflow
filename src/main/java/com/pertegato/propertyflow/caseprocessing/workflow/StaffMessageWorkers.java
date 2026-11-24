package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.FollowUpBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.TenantReplyBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.service.StaffMessageService;
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

import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.JobType.STAFF_MESSAGE_PUBLISH;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.DECISION_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.OUTCOME;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.STAFF_DECISION_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.STAFF_MESSAGE_COMMAND_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.STAFF_MESSAGE_OUTCOME;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants.ErrorCode.STAFF_DECISION_STALE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants.JobType.STAFF_QUESTION_PUBLISH;

@Component
@Profile("propertyflow-backend & !frontend-demo")
@ConditionalOnProperty(name = "propertyflow.workflow.enabled", havingValue = "true")
@RequiredArgsConstructor
public class StaffMessageWorkers {

    private final CamundaClient engine;
    private final StaffMessageService service;

    @JobWorker(type = STAFF_MESSAGE_PUBLISH, autoComplete = false)
    public void publish(JobClient client, ActivatedJob job) {
        String outcome;
        try {
            ProcessInstance process = engine.newProcessInstanceGetRequest(job.getProcessInstanceKey()).send().join();
            long leader;
            if (CaseProcessBpmnConstants.PROCESS_ID.equals(job.getBpmnProcessId())
                    && CaseProcessBpmnConstants.ServiceTask.SAVE_STAFF_MESSAGE.equals(job.getElementId())) {
                require(process.getParentProcessInstanceKey() == null);
                leader = job.getProcessInstanceKey();
            } else if (TenantReplyBpmnConstants.PROCESS_ID.equals(job.getBpmnProcessId())
                    && TenantReplyBpmnConstants.ServiceTask.PUBLISH_STAFF_MESSAGE.equals(job.getElementId())) {
                require(process.getParentProcessInstanceKey() != null && process.getParentElementInstanceKey() != null);
                ElementInstance call = engine.newElementInstanceGetRequest(process.getParentElementInstanceKey()).send().join();
                require(CaseProcessBpmnConstants.CallActivity.TENANT_REPLY_CALL.equals(call.getElementId())
                        && CaseProcessBpmnConstants.PROCESS_ID.equals(call.getProcessDefinitionId())
                        && process.getParentProcessInstanceKey().equals(call.getProcessInstanceKey()) && job.getTenantId().equals(call.getTenantId()));
                leader = process.getParentProcessInstanceKey();
            } else if (FollowUpBpmnConstants.PROCESS_ID.equals(job.getBpmnProcessId())
                    && FollowUpBpmnConstants.ServiceTask.PUBLISH_STAFF_MESSAGE.equals(job.getElementId())) {
                require(process.getParentProcessInstanceKey() != null && process.getParentElementInstanceKey() != null);
                ElementInstance call = engine.newElementInstanceGetRequest(process.getParentElementInstanceKey()).send().join();
                ProcessInstance staff = engine.newProcessInstanceGetRequest(process.getParentProcessInstanceKey()).send().join();
                require(StaffProcessingBpmnConstants.CallActivity.FOLLOW_UP_CALL.equals(call.getElementId())
                        && StaffProcessingBpmnConstants.PROCESS_ID.equals(call.getProcessDefinitionId())
                        && process.getParentProcessInstanceKey().equals(call.getProcessInstanceKey()) && job.getTenantId().equals(call.getTenantId())
                        && staff.getParentProcessInstanceKey() != null && staff.getParentElementInstanceKey() != null
                        && job.getTenantId().equals(staff.getTenantId()));
                ElementInstance parent = engine.newElementInstanceGetRequest(staff.getParentElementInstanceKey()).send().join();
                require(CaseProcessBpmnConstants.CallActivity.STAFF_PROCESSING_CALL.equals(parent.getElementId())
                        && CaseProcessBpmnConstants.PROCESS_ID.equals(parent.getProcessDefinitionId())
                        && staff.getParentProcessInstanceKey().equals(parent.getProcessInstanceKey()) && job.getTenantId().equals(parent.getTenantId()));
                leader = staff.getParentProcessInstanceKey();
            } else {
                require(StaffProcessingBpmnConstants.PROCESS_ID.equals(job.getBpmnProcessId())
                        && StaffProcessingBpmnConstants.ServiceTask.PUBLISH_STAFF_MESSAGE.equals(job.getElementId())
                        && process.getParentProcessInstanceKey() != null && process.getParentElementInstanceKey() != null);
                ElementInstance call = engine.newElementInstanceGetRequest(process.getParentElementInstanceKey()).send().join();
                require(CaseProcessBpmnConstants.CallActivity.STAFF_PROCESSING_CALL.equals(call.getElementId())
                        && CaseProcessBpmnConstants.PROCESS_ID.equals(call.getProcessDefinitionId())
                        && process.getParentProcessInstanceKey().equals(call.getProcessInstanceKey()) && job.getTenantId().equals(call.getTenantId()));
                leader = process.getParentProcessInstanceKey();
            }
            UUID command = UUID.fromString((String) job.getVariablesAsMap().get(STAFF_MESSAGE_COMMAND_REF));
            String result = service.publish(command, leader, job.getProcessInstanceKey(), job.getTenantId());
            outcome = "PUBLISHED".equals(result) ? service.interruptsAutomatic(command) ? "QUESTION" : "INFORMATION" : "REJECTED";
        } catch (RuntimeException exception) {
            boolean rejected = exception instanceof WorkflowRejectedException || exception instanceof IllegalArgumentException;
            client.newFailCommand(job.getKey())
                    .retries(rejected ? 0 : Math.max(1, job.getRetries()))
                    .retryBackoff(Duration.ofSeconds(5))
                    .errorMessage(rejected ? "STAFF_MESSAGE_CONTRACT_REJECTED" : "STAFF_MESSAGE_TEMPORARILY_UNAVAILABLE")
                    .send()
                    .join();
            return;
        }
        client.newCompleteCommand(job.getKey()).variables(Map.of(STAFF_MESSAGE_OUTCOME, outcome)).send().join();
    }

    @JobWorker(type = STAFF_QUESTION_PUBLISH, autoComplete = false)
    public void question(JobClient client, ActivatedJob job) {
        UUID command;
        String result;
        try {
            require(StaffProcessingBpmnConstants.PROCESS_ID.equals(job.getBpmnProcessId())
                    && StaffProcessingBpmnConstants.ServiceTask.PUBLISH_QUESTION.equals(job.getElementId()));
            ProcessInstance child = engine.newProcessInstanceGetRequest(job.getProcessInstanceKey()).send().join();
            require(child.getParentProcessInstanceKey() != null && child.getParentElementInstanceKey() != null);
            ElementInstance call = engine.newElementInstanceGetRequest(child.getParentElementInstanceKey()).send().join();
            require(CaseProcessBpmnConstants.CallActivity.STAFF_PROCESSING_CALL.equals(call.getElementId())
                    && CaseProcessBpmnConstants.PROCESS_ID.equals(call.getProcessDefinitionId())
                    && child.getParentProcessInstanceKey().equals(call.getProcessInstanceKey()) && job.getTenantId().equals(call.getTenantId()));
            command = service.questionCommand(UUID.fromString((String) job.getVariablesAsMap().get(STAFF_DECISION_REF)));
            result = service.publishQuestion(command, child.getParentProcessInstanceKey(), job.getProcessInstanceKey(), job.getTenantId());
        } catch (RuntimeException exception) {
            boolean rejected = exception instanceof WorkflowRejectedException || exception instanceof IllegalArgumentException;
            client.newFailCommand(job.getKey()).retries(rejected ? 0 : Math.max(1, job.getRetries())).retryBackoff(Duration.ofSeconds(5))
                    .errorMessage(rejected ? "INVALID_QUESTION_ORIGIN" : "QUESTION_TEMPORARILY_UNAVAILABLE").send().join();
            return;
        }
        if ("PUBLISHED".equals(result)) {
            client.newCompleteCommand(job.getKey()).variables(Map.of(OUTCOME, "WAIT_FOR_REPLY", DECISION_REF, command.toString())).send().join();
        } else {
            client.newThrowErrorCommand(job.getKey()).errorCode(STAFF_DECISION_STALE)
                    .errorMessage("QUESTION_BASIS_CHANGED").send().join();
        }
    }

    private void require(boolean valid) {
        if (!valid) {
            throw new WorkflowRejectedException("INVALID_STAFF_MESSAGE_ORIGIN");
        }
    }
}
