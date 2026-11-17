package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.service.StaffDecisionService;
import com.pertegato.propertyflow.caseprocessing.service.WorkflowRejectedException;
import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.search.response.ElementInstance;
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
public class StaffDecisionWorkers {

    public static final String PROGRESS = "propertyflow.staff.progress";
    public static final String STALE_DECISION = "STAFF_DECISION_STALE";
    private final CamundaClient engine;
    private final StaffDecisionService service;

    public StaffDecisionWorkers(CamundaClient engine, StaffDecisionService service) {
        this.engine = engine;
        this.service = service;
    }

    @JobWorker(type = PROGRESS, autoComplete = false)
    public void progress(JobClient client, ActivatedJob job) {
        String result;
        try {
            require(StaffReviewWorkers.STAFF_PROCESS.equals(job.getBpmnProcessId()) && "SProgress".equals(job.getElementId()));
            ProcessInstance child = engine.newProcessInstanceGetRequest(job.getProcessInstanceKey()).send().join();
            require(child.getParentProcessInstanceKey() != null && child.getParentElementInstanceKey() != null
                    && Long.toString(child.getParentProcessInstanceKey()).equals(text(job, "leadingProcessInstanceKey")));
            ElementInstance call = engine.newElementInstanceGetRequest(child.getParentElementInstanceKey()).send().join();
            require("Staff".equals(call.getElementId()) && child.getParentProcessInstanceKey().equals(call.getProcessInstanceKey())
                    && InitialCaseWorkers.CASE_PROCESS.equals(call.getProcessDefinitionId()) && job.getTenantId().equals(call.getTenantId()));
            result = service.progress(text(job, "caseId"), child.getParentProcessInstanceKey(), job.getProcessInstanceKey(),
                    UUID.fromString(text(job, "staffDecisionRef")));
        } catch (RuntimeException exception) {
            fail(client, job, exception instanceof WorkflowRejectedException || exception instanceof IllegalArgumentException);
            return;
        }
        if ("APPLIED".equals(result)) {
            client.newCompleteCommand(job.getKey()).send().join();
        } else if ("REVIEW_AGAIN".equals(result)) {
            client.newThrowErrorCommand(job.getKey()).errorCode(STALE_DECISION).errorMessage("CURRENT_CASE_REQUIRES_NEW_DECISION").send().join();
        } else {
            fail(client, job, false);
        }
    }

    private void fail(JobClient client, ActivatedJob job, boolean rejected) {
        client.newFailCommand(job.getKey()).retries(rejected ? 0 : Math.max(1, job.getRetries())).retryBackoff(Duration.ofSeconds(5))
                .errorMessage(rejected ? "STAFF_DECISION_CONTRACT_REJECTED" : "STAFF_DECISION_TEMPORARILY_UNAVAILABLE").send().join();
    }

    private String text(ActivatedJob job, String name) {
        Object value = job.getVariablesAsMap().get(name);
        require(value instanceof String && !((String) value).isBlank());
        return (String) value;
    }

    private void require(boolean condition) {
        if (!condition) {
            throw new WorkflowRejectedException("INVALID_STAFF_DECISION_ORIGIN");
        }
    }
}
