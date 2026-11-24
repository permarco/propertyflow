package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.TenantMessageBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.service.TenantMessageService;
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

import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.CASE_ID;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.LEADING_PROCESS_INSTANCE_KEY;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.TENANT_MESSAGE_ID;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.TenantMessageBpmnConstants.JobType.TENANT_MESSAGE_IDENTIFY;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.TenantMessageBpmnConstants.Variablen.CASE_PHASE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.TenantMessageBpmnConstants.Variablen.TENANT_MESSAGE_ACCEPTED;

@Component
@Profile("propertyflow-backend & !frontend-demo")
@ConditionalOnProperty(name = "propertyflow.workflow.enabled", havingValue = "true")
@RequiredArgsConstructor
public class TenantMessageWorkers {

    private final CamundaClient engine;
    private final TenantMessageService service;

    @JobWorker(type = TENANT_MESSAGE_IDENTIFY, autoComplete = false)
    public void identify(JobClient client, ActivatedJob job) {
        boolean accepted;
        try {
            require(TenantMessageBpmnConstants.PROCESS_ID.equals(job.getBpmnProcessId())
                    && TenantMessageBpmnConstants.ServiceTask.CHECK_MESSAGE.equals(job.getElementId()));
            ProcessInstance child = engine.newProcessInstanceGetRequest(job.getProcessInstanceKey()).send().join();
            require(child.getParentProcessInstanceKey() != null && child.getParentElementInstanceKey() != null
                    && Long.toString(child.getParentProcessInstanceKey()).equals(text(job, LEADING_PROCESS_INSTANCE_KEY)));
            ElementInstance call = engine.newElementInstanceGetRequest(child.getParentElementInstanceKey()).send().join();
            require(CaseProcessBpmnConstants.CallActivity.TENANT_MESSAGE_CALL.equals(call.getElementId())
                    && CaseProcessBpmnConstants.PROCESS_ID.equals(call.getProcessDefinitionId())
                    && child.getParentProcessInstanceKey().equals(call.getProcessInstanceKey()) && job.getTenantId().equals(call.getTenantId()));
            accepted = service.receive(text(job, CASE_ID), UUID.fromString(text(job, TENANT_MESSAGE_ID)),
                    child.getParentProcessInstanceKey(), job.getProcessInstanceKey(), child.getParentElementInstanceKey(), job.getTenantId());
        } catch (RuntimeException exception) {
            boolean rejected = exception instanceof WorkflowRejectedException || exception instanceof IllegalArgumentException;
            client.newFailCommand(job.getKey()).retries(rejected ? 0 : Math.max(1, job.getRetries())).retryBackoff(Duration.ofSeconds(5))
                    .errorMessage(rejected ? "TENANT_MESSAGE_CONTRACT_REJECTED" : "TENANT_MESSAGE_TEMPORARILY_UNAVAILABLE").send().join();
            return;
        }
        client.newCompleteCommand(job.getKey()).variables(Map.of(TENANT_MESSAGE_ACCEPTED, accepted,
                CASE_PHASE, service.phase(text(job, CASE_ID), Long.parseLong(text(job, LEADING_PROCESS_INSTANCE_KEY))))).send().join();
    }

    private String text(ActivatedJob job, String name) {
        Object value = job.getVariablesAsMap().get(name);
        require(value instanceof String && !((String) value).isBlank());
        return (String) value;
    }

    private void require(boolean condition) {
        if (!condition) {
            throw new WorkflowRejectedException("INVALID_TENANT_MESSAGE_ORIGIN");
        }
    }
}
