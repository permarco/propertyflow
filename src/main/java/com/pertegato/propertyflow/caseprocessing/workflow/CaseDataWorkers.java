package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.service.CaseDataService;
import com.pertegato.propertyflow.caseprocessing.service.WorkflowRejectedException;
import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.worker.JobClient;
import java.time.Duration;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("propertyflow-backend & !frontend-demo")
@ConditionalOnProperty(name = "propertyflow.workflow.enabled", havingValue = "true")
public class CaseDataWorkers {

    public static final String MESSAGE_NAME = "Falldaten ergänzen angefordert";
    private final CamundaClient camunda;
    private final CaseDataService service;

    public CaseDataWorkers(CamundaClient camunda, CaseDataService service) {
        this.camunda = camunda;
        this.service = service;
    }

    @JobWorker(type = "propertyflow.case-data.apply", autoComplete = false)
    public void apply(JobClient client, ActivatedJob job) {
        apply(client, job, "CaseDataApply", "caseDataCommandRef");
    }

    @JobWorker(type = "propertyflow.answer.clear", autoComplete = false)
    public void clearAnswer(JobClient client, ActivatedJob job) {
        apply(client, job, "ClearAnswerApply", "clearAnswerCommandRef");
    }

    private void apply(JobClient client, ActivatedJob job, String element, String variable) {
        try {
            ProcessInstance instance = camunda.newProcessInstanceGetRequest(job.getProcessInstanceKey()).send().join();
            if (!InitialCaseWorkers.CASE_PROCESS.equals(job.getBpmnProcessId()) || !element.equals(job.getElementId())
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
