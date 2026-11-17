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
public class AutomaticEvaluationWorkers {

    public static final String EVALUATE = "propertyflow.automatic.evaluate";
    public static final String RECOMMEND = "propertyflow.automatic.recommendation";
    public static final String STALE = "ANALYSIS_BASIS_STALE";
    private final CamundaClient engine;
    private final ConversationEvaluationService service;

    public AutomaticEvaluationWorkers(CamundaClient engine, ConversationEvaluationService service) {
        this.engine = engine;
        this.service = service;
    }

    @JobWorker(type = EVALUATE, autoComplete = false)
    public void evaluate(JobClient client, ActivatedJob job) {
        Result result;
        try {
            verify(job, Set.of("Triage", "CurrentAnalysis"));
            String caseId = text(job, "caseId");
            UUID entry = UUID.fromString(text(job, "entryContextRef"));
            UUID call = UUID.fromString(text(job, "automaticCallRef"));
            long leader = Long.parseLong(text(job, "leadingProcessInstanceKey"));
            result = service.evaluateAutomatic(caseId, entry, call, leader, job.getProcessInstanceKey());
            if ("READY".equals(result.state()) || "APPLIED".equals(result.state())) {
                result = service.applyAutomatic(caseId, entry, call, leader, job.getProcessInstanceKey(), result.evaluationId());
            }
        } catch (RuntimeException exception) {
            fail(client, job, exception);
            return;
        }
        if ("BUSY".equals(result.state()) || "STALE".equals(result.state())) {
            retry(client, job);
        } else {
            client.newCompleteCommand(job.getKey())
                    .variables(Map.of("evaluationRef", result.evaluationId() == null ? "" : result.evaluationId().toString(),
                            "evaluationCurrent", !"OBSOLETE".equals(result.state())))
                    .send()
                    .join();
        }
    }

    @JobWorker(type = RECOMMEND, autoComplete = false)
    public void recommend(JobClient client, ActivatedJob job) {
        String result;
        try {
            verify(job, Set.of("Recommend"));
            result = Boolean.FALSE.equals(job.getVariablesAsMap().get("evaluationCurrent")) ? "OBSOLETE"
                    : service.automaticResult(text(job, "caseId"), UUID.fromString(text(job, "entryContextRef")),
                            UUID.fromString(text(job, "automaticCallRef")), Long.parseLong(text(job, "leadingProcessInstanceKey")),
                            job.getProcessInstanceKey(), UUID.fromString(text(job, "evaluationRef")));
        } catch (RuntimeException exception) {
            fail(client, job, exception);
            return;
        }
        if ("STALE".equals(result)) {
            refresh(client, job);
        } else if ("FAILED".equals(result)) {
            client.newThrowErrorCommand(job.getKey()).errorCode(TriageWorkers.ANALYSIS_FAILED)
                    .errorMessage("CURRENT_EVALUATION_REQUIRES_REVIEW").send().join();
        } else {
            client.newCompleteCommand(job.getKey()).variables(Map.of("evaluationCurrent", "CURRENT".equals(result))).send().join();
        }
    }

    private void refresh(JobClient client, ActivatedJob job) {
        try {
            String caseId = text(job, "caseId");
            UUID entry = UUID.fromString(text(job, "entryContextRef"));
            UUID call = UUID.fromString(text(job, "automaticCallRef"));
            long leader = Long.parseLong(text(job, "leadingProcessInstanceKey"));
            Result current = service.evaluateAutomatic(caseId, entry, call, leader, job.getProcessInstanceKey());
            if ("OBSOLETE".equals(current.state())) {
                client.newCompleteCommand(job.getKey()).variables(Map.of("evaluationCurrent", false)).send().join();
                return;
            }
            if ("READY".equals(current.state()) || "APPLIED".equals(current.state())) {
                Result applied = service.applyAutomatic(caseId, entry, call, leader, job.getProcessInstanceKey(), current.evaluationId());
                if ("APPLIED".equals(applied.state())) {
                    engine.newSetVariablesCommand(job.getElementInstanceKey())
                            .variables(Map.of("evaluationRef", current.evaluationId().toString())).send().join();
                }
            }
            retry(client, job);
        } catch (RuntimeException exception) {
            fail(client, job, exception);
        }
    }

    private void verify(ActivatedJob job, Set<String> elements) {
        require(InitialCaseWorkers.AUTOMATIC_PROCESS.equals(job.getBpmnProcessId()) && elements.contains(job.getElementId()));
        ProcessInstance child = engine.newProcessInstanceGetRequest(job.getProcessInstanceKey()).send().join();
        require(child.getParentProcessInstanceKey() != null && child.getParentElementInstanceKey() != null
                && Long.toString(child.getParentProcessInstanceKey()).equals(text(job, "leadingProcessInstanceKey")));
        ElementInstance call = engine.newElementInstanceGetRequest(child.getParentElementInstanceKey()).send().join();
        require("Automatic".equals(call.getElementId()) && InitialCaseWorkers.CASE_PROCESS.equals(call.getProcessDefinitionId())
                && child.getParentProcessInstanceKey().equals(call.getProcessInstanceKey()) && job.getTenantId().equals(call.getTenantId()));
    }

    private void retry(JobClient client, ActivatedJob job) {
        client.newFailCommand(job.getKey()).retries(Math.max(1, job.getRetries())).retryBackoff(Duration.ofSeconds(2))
                .errorMessage("CURRENT_EVALUATION_PENDING").send().join();
    }

    private void fail(JobClient client, ActivatedJob job, RuntimeException exception) {
        boolean rejected = exception instanceof WorkflowRejectedException || exception instanceof IllegalArgumentException;
        client.newFailCommand(job.getKey()).retries(rejected ? 0 : Math.max(1, job.getRetries())).retryBackoff(Duration.ofSeconds(5))
                .errorMessage(rejected ? "INVALID_AUTOMATIC_CONTEXT" : "EVALUATION_TEMPORARILY_UNAVAILABLE").send().join();
    }

    private String text(ActivatedJob job, String name) {
        Object value = job.getVariablesAsMap().get(name);
        require(value instanceof String && !((String) value).isBlank());
        return (String) value;
    }

    private void require(boolean valid) {
        if (!valid) {
            throw new WorkflowRejectedException("INVALID_AUTOMATIC_ORIGIN");
        }
    }
}
