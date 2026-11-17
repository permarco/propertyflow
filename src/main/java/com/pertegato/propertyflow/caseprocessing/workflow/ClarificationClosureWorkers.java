package com.pertegato.propertyflow.caseprocessing.workflow;

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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("propertyflow-backend & !frontend-demo")
@ConditionalOnProperty(name = "propertyflow.workflow.enabled", havingValue = "true")
public class ClarificationClosureWorkers {

    public static final String APPLY = "propertyflow.clarification.close";
    private final CamundaClient engine;
    private final CaseDataService service;

    public ClarificationClosureWorkers(CamundaClient engine, CaseDataService service) {
        this.engine = engine;
        this.service = service;
    }

    @JobWorker(type = APPLY, autoComplete = false)
    public void close(JobClient client, ActivatedJob job) {
        String result;
        try {
            ProcessInstance root = engine.newProcessInstanceGetRequest(job.getProcessInstanceKey()).send().join();
            if (!InitialCaseWorkers.CASE_PROCESS.equals(job.getBpmnProcessId()) || !"StaffCloseClarificationApply".equals(job.getElementId())
                    || root.getParentProcessInstanceKey() != null || !job.getTenantId().equals(root.getTenantId())) {
                throw new WorkflowRejectedException("INVALID_CLARIFICATION_CLOSURE_ORIGIN");
            }
            UUID command = UUID.fromString((String) job.getVariablesAsMap().get("clarificationClosureCommandRef"));
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
                .variables(Map.of("clarificationClosureOutcome", result))
                .send()
                .join();
    }
}
