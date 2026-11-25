package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.service.CaseDataService;
import com.pertegato.propertyflow.caseprocessing.service.WorkflowRejectedException;
import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.worker.JobClient;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.ANSWER_CLEAR;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.CASE_DATA_APPLY;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.CASE_DATA_COMMAND_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.CLEAR_ANSWER_COMMAND_REF;

@Component
@Profile("propertyflow-backend")
@ConditionalOnProperty(name = "propertyflow.workflow.enabled", havingValue = "true")
@RequiredArgsConstructor
public class CaseDataWorkers {

    private final CamundaClient camunda;
    private final CaseDataService service;

    @JobWorker(type = CASE_DATA_APPLY, autoComplete = false)
    public void apply(JobClient client, ActivatedJob job) {
        apply(client, job, CaseProcessBpmnConstants.ServiceTask.APPLY_CASE_DATA, CASE_DATA_COMMAND_REF);
    }

    @JobWorker(type = ANSWER_CLEAR, autoComplete = false)
    public void clearAnswer(JobClient client, ActivatedJob job) {
        apply(client, job, CaseProcessBpmnConstants.ServiceTask.CLEAR_ANSWER_REQUIREMENT, CLEAR_ANSWER_COMMAND_REF);
    }

    private void apply(JobClient client, ActivatedJob job, String element, String variable) {
        try {
            ProcessInstance instance = camunda.newProcessInstanceGetRequest(job.getProcessInstanceKey()).send().join();
            if (!CaseProcessBpmnConstants.PROCESS_ID.equals(job.getBpmnProcessId())
                    || !element.equals(job.getElementId())
                    || instance.getParentProcessInstanceKey() != null || !"ACTIVE".equals(instance.getState().name())) {
                throw new WorkflowRejectedException("WRONG_CASE_DATA_JOB");
            }
            UUID id = UUID.fromString((String) job.getVariablesAsMap().get(variable));
            service.apply(id, job.getProcessInstanceKey(), job.getTenantId());
        } catch (RuntimeException exception) {
            boolean rejected = exception instanceof WorkflowRejectedException || exception instanceof IllegalArgumentException;
            client.newFailCommand(job.getKey()).retries(rejected ? 0 : Math.max(1, job.getRetries()))
                    .retryBackoff(Duration.ofSeconds(5)).errorMessage(rejected ? "CASE_DATA_CONTRACT_REJECTED" : "CASE_DATA_TEMPORARILY_UNAVAILABLE")
                    .send().join();
            return;
        }
        client.newCompleteCommand(job.getKey()).send().join();
    }
}
