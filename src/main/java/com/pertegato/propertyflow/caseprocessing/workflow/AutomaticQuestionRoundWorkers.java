package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.Entry;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantReplyStore.Call;
import com.pertegato.propertyflow.caseprocessing.service.AutomaticQuestionRoundService;
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
public class AutomaticQuestionRoundWorkers {

    public static final String PREPARE = "propertyflow.automatic.prepare-question-round";
    public static final String PREPARE_CLOSE = "propertyflow.automatic.prepare-close-round";
    private final CamundaClient engine;
    private final AutomaticQuestionRoundService service;

    public AutomaticQuestionRoundWorkers(CamundaClient engine, AutomaticQuestionRoundService service) {
        this.engine = engine;
        this.service = service;
    }

    @JobWorker(type = PREPARE, autoComplete = false)
    public void prepare(JobClient client, ActivatedJob job) {
        prepare(client, job, "QUESTION", "PrepareQuestionAuto");
    }

    @JobWorker(type = PREPARE_CLOSE, autoComplete = false)
    public void prepareClose(JobClient client, ActivatedJob job) {
        prepare(client, job, "CLOSE", "PrepareCloseAuto");
    }

    private void prepare(JobClient client, ActivatedJob job, String kind, String element) {
        Entry entry;
        try {
            require(InitialCaseWorkers.CASE_PROCESS.equals(job.getBpmnProcessId()) && element.equals(job.getElementId()));
            ProcessInstance root = engine.newProcessInstanceGetRequest(job.getProcessInstanceKey()).send().join();
            require(root.getParentProcessInstanceKey() == null && kind.equals(text(job, "replyOutcome")));
            UUID decision = UUID.fromString(text(job, "replyDecisionRef"));
            String caseId = text(job, "caseId");
            Call source = service.source(decision, caseId, job.getProcessInstanceKey(), kind);
            require(source.id().toString().equals(text(job, "activeReplyCallRef"))
                    && source.entry().toString().equals(text(job, "replyEntryContextRef")) && source.tenant().equals(job.getTenantId()));
            ProcessInstance reply = engine.newProcessInstanceGetRequest(source.child()).send().join();
            ElementInstance call = engine.newElementInstanceGetRequest(source.parent()).send().join();
            require(TenantReplyWorkers.PROCESS.equals(reply.getProcessDefinitionId())
                    && Long.valueOf(source.leader()).equals(reply.getParentProcessInstanceKey())
                    && Long.valueOf(source.parent()).equals(reply.getParentElementInstanceKey()) && job.getTenantId().equals(reply.getTenantId())
                    && "Reply".equals(call.getElementId()) && Long.valueOf(source.leader()).equals(call.getProcessInstanceKey())
                    && InitialCaseWorkers.CASE_PROCESS.equals(call.getProcessDefinitionId()) && job.getTenantId().equals(call.getTenantId()));
            if (!"COMPLETED".equals(reply.getState().name()) || !"COMPLETED".equals(call.getState().name())) {
                throw new IllegalStateException("REPLY_COMPLETION_NOT_YET_VISIBLE");
            }
            entry = service.prepare(decision, caseId, source.leader(), job.getElementInstanceKey(), source.id(), kind);
        } catch (RuntimeException exception) {
            boolean rejected = exception instanceof WorkflowRejectedException || exception instanceof IllegalArgumentException;
            client.newFailCommand(job.getKey())
                    .retries(rejected ? 0 : Math.max(1, job.getRetries()))
                    .retryBackoff(Duration.ofSeconds(2))
                    .errorMessage(rejected ? "INVALID_QUESTION_ROUND_ORIGIN" : "QUESTION_ROUND_PENDING")
                    .send()
                    .join();
            return;
        }
        client.newCompleteCommand(job.getKey())
                .variables(Map.of("automaticEntryContextRef", entry.id().toString(), "automaticCallRef", entry.callRef().toString()))
                .send()
                .join();
    }

    private String text(ActivatedJob job, String name) {
        Object value = job.getVariablesAsMap().get(name);
        require(value instanceof String && !((String) value).isBlank());
        return (String) value;
    }

    private void require(boolean valid) {
        if (!valid) {
            throw new WorkflowRejectedException("INVALID_QUESTION_ROUND_ORIGIN");
        }
    }
}
