package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.service.CaseClosureService;
import com.pertegato.propertyflow.caseprocessing.service.CaseClosureService.ClosureContext;
import com.pertegato.propertyflow.caseprocessing.service.CaseClosureService.ClosureResult;
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
public class CaseClosureWorkers {

    public static final String CLOSE = "propertyflow.staff.close";
    public static final String STALE_CLOSURE = "STAFF_CLOSURE_STALE";
    private final CamundaClient engine;
    private final CaseClosureService service;

    public CaseClosureWorkers(CamundaClient engine, CaseClosureService service) {
        this.engine = engine;
        this.service = service;
    }

    @JobWorker(type = CLOSE, autoComplete = false)
    public void close(JobClient client, ActivatedJob job) {
        ClosureResult result;
        try {
            require(InitialCaseWorkers.CASE_PROCESS.equals(job.getBpmnProcessId()) && "Close".equals(job.getElementId())
                    && "CLOSE".equals(text(job, "staffOutcome")));
            String caseId = text(job, "caseId");
            UUID decision = UUID.fromString(text(job, "staffDecisionRef"));
            ClosureContext context = service.context(caseId, job.getProcessInstanceKey(), decision);
            ProcessInstance root = engine.newProcessInstanceGetRequest(job.getProcessInstanceKey()).send().join();
            ProcessInstance child = engine.newProcessInstanceGetRequest(context.child()).send().join();
            ElementInstance call = engine.newElementInstanceGetRequest(context.parentElement()).send().join();
            require(root.getParentProcessInstanceKey() == null && job.getTenantId().equals(context.tenant())
                    && (context.reply() ? TenantReplyWorkers.PROCESS : StaffReviewWorkers.STAFF_PROCESS).equals(child.getProcessDefinitionId())
                    && Long.valueOf(job.getProcessInstanceKey()).equals(child.getParentProcessInstanceKey())
                    && Long.valueOf(context.parentElement()).equals(child.getParentElementInstanceKey())
                    && context.tenant().equals(child.getTenantId()) && (context.reply() ? "Reply" : "Staff").equals(call.getElementId())
                    && Long.valueOf(job.getProcessInstanceKey()).equals(call.getProcessInstanceKey())
                    && context.tenant().equals(call.getTenantId()));
            String expected = context.reply() ? "TERMINATED" : "COMPLETED";
            if (!expected.equals(child.getState().name()) || !expected.equals(call.getState().name())) {
                throw new IllegalStateException("CLOSURE_CHILD_COMPLETION_NOT_YET_VISIBLE");
            }
            result = service.close(caseId, job.getProcessInstanceKey(), context.child(), decision);
        } catch (RuntimeException exception) {
            boolean rejected = exception instanceof WorkflowRejectedException || exception instanceof IllegalArgumentException;
            client.newFailCommand(job.getKey()).retries(rejected ? 0 : Math.max(1, job.getRetries())).retryBackoff(Duration.ofSeconds(5))
                    .errorMessage(rejected ? "CLOSURE_CONTRACT_REJECTED" : "CLOSURE_TEMPORARILY_UNAVAILABLE").send().join();
            return;
        }
        if ("STALE".equals(result.state())) {
            client.newThrowErrorCommand(job.getKey()).errorCode(STALE_CLOSURE).errorMessage("CONFIRMED_CLOSURE_BASIS_CHANGED")
                    .variables(Map.of("staffEntryContextRef", result.continuation().toString())).send().join();
        } else {
            client.newCompleteCommand(job.getKey()).send().join();
        }
    }

    private String text(ActivatedJob job, String name) {
        Object value = job.getVariablesAsMap().get(name);
        require(value instanceof String && !((String) value).isBlank());
        return (String) value;
    }

    private void require(boolean condition) {
        if (!condition) {
            throw new WorkflowRejectedException("INVALID_CLOSURE_ORIGIN");
        }
    }
}
