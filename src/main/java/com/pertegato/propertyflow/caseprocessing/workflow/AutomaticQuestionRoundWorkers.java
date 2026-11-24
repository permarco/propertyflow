package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.TenantReplyBpmnConstants;
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
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.AUTOMATIC_PREPARE_CLOSE_ROUND;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.AUTOMATIC_PREPARE_QUESTION_ROUND;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.AUTOMATIC_ENTRY_CONTEXT_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.REPLY_DECISION_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.REPLY_OUTCOME;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.ACTIVE_REPLY_CALL_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.AUTOMATIC_CALL_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.CASE_ID;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.REPLY_ENTRY_CONTEXT_REF;

@Component
@Profile("propertyflow-backend & !frontend-demo")
@ConditionalOnProperty(name = "propertyflow.workflow.enabled", havingValue = "true")
@RequiredArgsConstructor
public class AutomaticQuestionRoundWorkers {

    private final CamundaClient engine;
    private final AutomaticQuestionRoundService service;

    @JobWorker(type = AUTOMATIC_PREPARE_QUESTION_ROUND, autoComplete = false)
    public void prepare(JobClient client, ActivatedJob job) {
        prepare(client, job, "QUESTION", CaseProcessBpmnConstants.ServiceTask.PREPARE_QUESTION_ROUND);
    }

    @JobWorker(type = AUTOMATIC_PREPARE_CLOSE_ROUND, autoComplete = false)
    public void prepareClose(JobClient client, ActivatedJob job) {
        prepare(client, job, "CLOSE", CaseProcessBpmnConstants.ServiceTask.PREPARE_AUTOMATIC_CLOSURE);
    }

    private void prepare(JobClient client, ActivatedJob job, String kind, String element) {
        Entry entry;
        try {
            require(CaseProcessBpmnConstants.PROCESS_ID.equals(job.getBpmnProcessId())
                    && element.equals(job.getElementId()));
            ProcessInstance root = engine.newProcessInstanceGetRequest(job.getProcessInstanceKey()).send().join();
            require(root.getParentProcessInstanceKey() == null && kind.equals(text(job, REPLY_OUTCOME)));
            UUID decision = UUID.fromString(text(job, REPLY_DECISION_REF));
            String caseId = text(job, CASE_ID);
            Call source = service.source(decision, caseId, job.getProcessInstanceKey(), kind);
            require(source.id().toString().equals(text(job, ACTIVE_REPLY_CALL_REF))
                    && source.entry().toString().equals(text(job, REPLY_ENTRY_CONTEXT_REF)) && source.tenant().equals(job.getTenantId()));
            ProcessInstance reply = engine.newProcessInstanceGetRequest(source.child()).send().join();
            ElementInstance call = engine.newElementInstanceGetRequest(source.parent()).send().join();
            require(TenantReplyBpmnConstants.PROCESS_ID.equals(reply.getProcessDefinitionId())
                    && Long.valueOf(source.leader()).equals(reply.getParentProcessInstanceKey())
                    && Long.valueOf(source.parent()).equals(reply.getParentElementInstanceKey()) && job.getTenantId().equals(reply.getTenantId())
                    && CaseProcessBpmnConstants.CallActivity.TENANT_REPLY_CALL.equals(call.getElementId())
                    && Long.valueOf(source.leader()).equals(call.getProcessInstanceKey())
                    && CaseProcessBpmnConstants.PROCESS_ID.equals(call.getProcessDefinitionId())
                    && job.getTenantId().equals(call.getTenantId()));
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
                .variables(Map.of(AUTOMATIC_ENTRY_CONTEXT_REF, entry.id().toString(), AUTOMATIC_CALL_REF, entry.callRef().toString()))
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
