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
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.CLARIFICATION_CLOSE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.CLARIFICATION_CLOSURE_COMMAND_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.CLARIFICATION_CLOSURE_OUTCOME;

@Component
@Profile("propertyflow-backend")
@ConditionalOnProperty(name = "propertyflow.workflow.enabled", havingValue = "true")
@RequiredArgsConstructor
public class ClarificationClosureWorkers {

    private final CamundaClient engine;
    private final CaseDataService service;

    @JobWorker(type = CLARIFICATION_CLOSE, autoComplete = false)
    public void close(JobClient client, ActivatedJob job) {
        String result;
        try {
            ProcessInstance root = engine.newProcessInstanceGetRequest(job.getProcessInstanceKey()).send().join();
            if (!CaseProcessBpmnConstants.PROCESS_ID.equals(job.getBpmnProcessId())
                    || !CaseProcessBpmnConstants.ServiceTask.CLOSE_DURING_CLARIFICATION.equals(job.getElementId())
                    || root.getParentProcessInstanceKey() != null || !job.getTenantId().equals(root.getTenantId())) {
                throw new WorkflowRejectedException("INVALID_CLARIFICATION_CLOSURE_ORIGIN");
            }
            UUID command = UUID.fromString((String) job.getVariablesAsMap().get(CLARIFICATION_CLOSURE_COMMAND_REF));
            result = service.clarificationClosureState(command, job.getProcessInstanceKey(), job.getTenantId());
        } catch (RuntimeException exception) {
            boolean rejected = exception instanceof WorkflowRejectedException || exception instanceof IllegalArgumentException;
            client.newFailCommand(job.getKey())
                    .retries(rejected ? 0 : Math.max(1, job.getRetries()))
                    .retryBackoff(Duration.ofSeconds(5))
                    .errorMessage(rejected ? "INVALID_CLARIFICATION_CLOSURE" : "CLARIFICATION_CLOSURE_PENDING")
                    .send()
                    .join();
            return;
        }
        // Nach verloren gegangener Bestätigung liest der Retry denselben gespeicherten Abschluss.
        client.newCompleteCommand(job.getKey())
                .variables(Map.of(CLARIFICATION_CLOSURE_OUTCOME, result))
                .send()
                .join();
    }
}
