package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.service.CaseAnalysisService;
import com.pertegato.propertyflow.caseprocessing.service.CaseAnalysisService.Result;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("propertyflow-backend & !frontend-demo")
@ConditionalOnProperty(name = "propertyflow.workflow.enabled", havingValue = "true")
public class TriageWorkers {

    public static final String INITIAL = "propertyflow.triage.initial";
    public static final String ANALYSIS_FAILED = "TRIAGE_ANALYSIS_FAILED";
    private final CamundaClient camunda;
    private final CaseAnalysisService service;

    public TriageWorkers(CamundaClient camunda, CaseAnalysisService service) {
        this.camunda = camunda;
        this.service = service;
    }

    @JobWorker(type = INITIAL, autoComplete = false)
    public void analyze(JobClient client, ActivatedJob job) {
        Result result;
        try {
            ProcessInstance child = verify(job);
            result = service.analyze(text(job, "caseId"), UUID.fromString(text(job, "entryContextRef")),
                    UUID.fromString(text(job, "automaticCallRef")), child.getParentProcessInstanceKey(), job.getProcessInstanceKey(), job.getKey());
        } catch (RuntimeException exception) {
            fail(client, job, exception instanceof WorkflowRejectedException || exception instanceof IllegalArgumentException);
            return;
        }
        // Only a persisted, typed analysis failure becomes a BPMN error. Infrastructure failures retry.
        if ("FAILED".equals(result.state())) {
            client.newThrowErrorCommand(job.getKey()).errorCode(ANALYSIS_FAILED).errorMessage("ANALYSIS_REQUIRES_STAFF_REVIEW").send().join();
        } else if ("SUCCEEDED".equals(result.state())) {
            client.newCompleteCommand(job.getKey()).variables(Map.of("analysisRef", result.analysisId().toString())).send().join();
        } else {
            fail(client, job, false);
        }
    }

    private ProcessInstance verify(ActivatedJob job) {
        require(InitialCaseWorkers.AUTOMATIC_PROCESS.equals(job.getBpmnProcessId()) && "Triage".equals(job.getElementId()));
        ProcessInstance child = camunda.newProcessInstanceGetRequest(job.getProcessInstanceKey()).send().join();
        require(child.getParentProcessInstanceKey() != null && child.getParentElementInstanceKey() != null
                && Long.toString(child.getParentProcessInstanceKey()).equals(text(job, "leadingProcessInstanceKey")));
        ElementInstance call = camunda.newElementInstanceGetRequest(child.getParentElementInstanceKey()).send().join();
        require("Automatic".equals(call.getElementId()) && InitialCaseWorkers.CASE_PROCESS.equals(call.getProcessDefinitionId())
                && child.getParentProcessInstanceKey().equals(call.getProcessInstanceKey()) && job.getTenantId().equals(call.getTenantId()));
        return child;
    }

    private void fail(JobClient client, ActivatedJob job, boolean rejected) {
        client.newFailCommand(job.getKey()).retries(rejected ? 0 : Math.max(1, job.getRetries())).retryBackoff(Duration.ofSeconds(5))
                .errorMessage(rejected ? "INVALID_ANALYSIS_CONTEXT" : "ANALYSIS_TEMPORARILY_UNAVAILABLE").send().join();
    }

    private String text(ActivatedJob job, String name) {
        Object value = job.getVariablesAsMap().get(name);
        require(value instanceof String && !((String) value).isBlank());
        return (String) value;
    }

    private void require(boolean condition) {
        if (!condition) {
            throw new WorkflowRejectedException("INVALID_ANALYSIS_ORIGIN");
        }
    }
}
