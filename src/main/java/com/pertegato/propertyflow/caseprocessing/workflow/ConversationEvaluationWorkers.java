package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.service.ConversationEvaluationService;
import com.pertegato.propertyflow.caseprocessing.service.ConversationEvaluationService.Result;
import com.pertegato.propertyflow.caseprocessing.service.WorkflowRejectedException;
import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.worker.JobClient;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("propertyflow-backend & !frontend-demo")
@ConditionalOnProperty(name = "propertyflow.workflow.enabled", havingValue = "true")
public class ConversationEvaluationWorkers {

    public static final String EVALUATE = "propertyflow.conversation.evaluate";
    public static final String APPLY = "propertyflow.conversation.apply";
    public static final String STALE = "TENANT_EVALUATION_STALE";
    public static final String CONTEXT = "propertyflow.conversation.context";
    public static final String RECOMMENDATION = "propertyflow.conversation.recommendation";
    private final CamundaClient engine;
    private final ConversationEvaluationService service;

    public ConversationEvaluationWorkers(CamundaClient engine, ConversationEvaluationService service) {
        this.engine = engine;
        this.service = service;
    }

    @JobWorker(type = EVALUATE, autoComplete = false)
    public void evaluate(JobClient client, ActivatedJob job) {
        Result result;
        try {
            verify(job, Set.of("NAnalyze", "NCurrentEvaluation"));
            result = service.evaluate(text(job, "caseId"), UUID.fromString(text(job, "tenantMessageId")),
                    Long.parseLong(text(job, "leadingProcessInstanceKey")), job.getProcessInstanceKey(), job.getTenantId());
        } catch (RuntimeException exception) {
            fail(client, job, exception);
            return;
        }
        if ("BUSY".equals(result.state()) || "STALE".equals(result.state())) {
            retry(client, job);
        } else {
            client.newCompleteCommand(job.getKey()).variables(Map.of("evaluationRef", result.evaluationId() == null ? "" : result.evaluationId().toString(),
                    "evaluationCurrent", !"OBSOLETE".equals(result.state()))).send().join();
        }
    }

    @JobWorker(type = APPLY, autoComplete = false)
    public void apply(JobClient client, ActivatedJob job) {
        Result result;
        try {
            verify(job, Set.of("NApply"));
            if (Boolean.FALSE.equals(job.getVariablesAsMap().get("evaluationCurrent"))) {
                client.newCompleteCommand(job.getKey()).send().join();
                return;
            }
            result = service.apply(text(job, "caseId"), UUID.fromString(text(job, "tenantMessageId")),
                    Long.parseLong(text(job, "leadingProcessInstanceKey")), job.getProcessInstanceKey(), job.getTenantId(),
                    UUID.fromString(text(job, "evaluationRef")));
        } catch (RuntimeException exception) {
            fail(client, job, exception);
            return;
        }
        if ("BUSY".equals(result.state())) {
            retry(client, job);
        } else if ("STALE".equals(result.state())) {
            client.newThrowErrorCommand(job.getKey()).errorCode(STALE).errorMessage("EVALUATION_BASIS_CHANGED").send().join();
        } else {
            client.newCompleteCommand(job.getKey()).send().join();
        }
    }

    @JobWorker(type = CONTEXT, autoComplete = false)
    public void context(JobClient client, ActivatedJob job) {
        try {
            verify(job, Set.of("NKeepReview", "NWake", "NRefresh", "NFailure"));
            if (!service.contextReady(text(job, "caseId"), UUID.fromString(text(job, "tenantMessageId")),
                    Long.parseLong(text(job, "leadingProcessInstanceKey")), job.getProcessInstanceKey(), job.getTenantId())) {
                retry(client, job);
                return;
            }
        } catch (RuntimeException exception) {
            fail(client, job, exception);
            return;
        }
        client.newCompleteCommand(job.getKey()).send().join();
    }

    @JobWorker(type = RECOMMENDATION, autoComplete = false)
    public void recommendation(JobClient client, ActivatedJob job) {
        boolean failed;
        try {
            verify(job, Set.of("NRecommend"));
            failed = !Boolean.FALSE.equals(job.getVariablesAsMap().get("evaluationCurrent"))
                    && service.failed(text(job, "caseId"), UUID.fromString(text(job, "tenantMessageId")),
                            Long.parseLong(text(job, "leadingProcessInstanceKey")), job.getProcessInstanceKey(), job.getTenantId(),
                            UUID.fromString(text(job, "evaluationRef")));
        } catch (RuntimeException exception) {
            fail(client, job, exception);
            return;
        }
        if (failed) {
            client.newThrowErrorCommand(job.getKey()).errorCode(TriageWorkers.ANALYSIS_FAILED).errorMessage("CURRENT_EVALUATION_REQUIRES_REVIEW").send().join();
        } else {
            client.newCompleteCommand(job.getKey()).send().join();
        }
    }

    private void verify(ActivatedJob job, Set<String> elements) {
        require(TenantMessageWorkers.PROCESS.equals(job.getBpmnProcessId()) && elements.contains(job.getElementId()));
        ProcessInstance child = engine.newProcessInstanceGetRequest(job.getProcessInstanceKey()).send().join();
        require(child.getParentProcessInstanceKey() != null && child.getParentElementInstanceKey() != null
                && Long.toString(child.getParentProcessInstanceKey()).equals(text(job, "leadingProcessInstanceKey")));
        ElementInstance call = engine.newElementInstanceGetRequest(child.getParentElementInstanceKey()).send().join();
        require("ProcessTenantMessage".equals(call.getElementId()) && InitialCaseWorkers.CASE_PROCESS.equals(call.getProcessDefinitionId())
                && child.getParentProcessInstanceKey().equals(call.getProcessInstanceKey()) && job.getTenantId().equals(call.getTenantId()));
    }

    private void retry(JobClient client, ActivatedJob job) {
        client.newFailCommand(job.getKey()).retries(Math.max(1, job.getRetries())).retryBackoff(Duration.ofSeconds(2))
                .errorMessage("CURRENT_EVALUATION_PENDING").send().join();
    }

    private void fail(JobClient client, ActivatedJob job, RuntimeException exception) {
        boolean rejected = exception instanceof WorkflowRejectedException || exception instanceof IllegalArgumentException;
        client.newFailCommand(job.getKey()).retries(rejected ? 0 : Math.max(1, job.getRetries())).retryBackoff(Duration.ofSeconds(5))
                .errorMessage(rejected ? "INVALID_EVALUATION_CONTEXT" : "EVALUATION_TEMPORARILY_UNAVAILABLE").send().join();
    }

    private String text(ActivatedJob job, String name) {
        Object value = job.getVariablesAsMap().get(name);
        require(value instanceof String && !((String) value).isBlank());
        return (String) value;
    }

    private void require(boolean valid) {
        if (!valid) {
            throw new WorkflowRejectedException("INVALID_EVALUATION_ORIGIN");
        }
    }
}
