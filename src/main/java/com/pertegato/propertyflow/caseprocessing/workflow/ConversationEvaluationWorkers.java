package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.TenantMessageBpmnConstants;
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
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.ErrorCode.TRIAGE_ANALYSIS_FAILED;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.CASE_ID;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.EVALUATION_CURRENT;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.EVALUATION_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.LEADING_PROCESS_INSTANCE_KEY;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.TENANT_MESSAGE_ID;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.TenantMessageBpmnConstants.ErrorCode.TENANT_EVALUATION_STALE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.TenantMessageBpmnConstants.JobType.CONVERSATION_APPLY;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.TenantMessageBpmnConstants.JobType.CONVERSATION_CONTEXT;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.TenantMessageBpmnConstants.JobType.CONVERSATION_RECOMMENDATION;

@Component
@Profile("propertyflow-backend")
@ConditionalOnProperty(name = "propertyflow.workflow.enabled", havingValue = "true")
@RequiredArgsConstructor
public class ConversationEvaluationWorkers {

    private final CamundaClient engine;
    private final ConversationEvaluationService service;

    @JobWorker(type = CONVERSATION_APPLY, autoComplete = false)
    public void apply(JobClient client, ActivatedJob job) {
        Result result;
        try {
            verify(job, Set.of(TenantMessageBpmnConstants.ServiceTask.APPLY_EVALUATION));
            if (Boolean.FALSE.equals(job.getVariablesAsMap().get(EVALUATION_CURRENT))) {
                client.newCompleteCommand(job.getKey()).send().join();
                return;
            }
            result = service.apply(text(job, CASE_ID), UUID.fromString(text(job, TENANT_MESSAGE_ID)),
                    Long.parseLong(text(job, LEADING_PROCESS_INSTANCE_KEY)), job.getProcessInstanceKey(), job.getTenantId(),
                    UUID.fromString(text(job, EVALUATION_REF)));
        } catch (RuntimeException exception) {
            fail(client, job, exception);
            return;
        }
        if ("BUSY".equals(result.state())) {
            retry(client, job);
        } else if ("STALE".equals(result.state())) {
            client.newThrowErrorCommand(job.getKey()).errorCode(TENANT_EVALUATION_STALE).errorMessage("EVALUATION_BASIS_CHANGED").send().join();
        } else {
            client.newCompleteCommand(job.getKey()).send().join();
        }
    }

    @JobWorker(type = CONVERSATION_CONTEXT, autoComplete = false)
    public void context(JobClient client, ActivatedJob job) {
        try {
            verify(job, Set.of(TenantMessageBpmnConstants.ServiceTask.UPDATE_OPEN_REVIEW, TenantMessageBpmnConstants.ServiceTask.NOTIFY_FOLLOW_UP,
                    TenantMessageBpmnConstants.ServiceTask.MARK_NEW_MESSAGE, TenantMessageBpmnConstants.ServiceTask.RECORD_EVALUATION_FAILURE));
            if (!service.contextReady(text(job, CASE_ID), UUID.fromString(text(job, TENANT_MESSAGE_ID)),
                    Long.parseLong(text(job, LEADING_PROCESS_INSTANCE_KEY)), job.getProcessInstanceKey(), job.getTenantId())) {
                retry(client, job);
                return;
            }
        } catch (RuntimeException exception) {
            fail(client, job, exception);
            return;
        }
        client.newCompleteCommand(job.getKey()).send().join();
    }

    @JobWorker(type = CONVERSATION_RECOMMENDATION, autoComplete = false)
    public void recommendation(JobClient client, ActivatedJob job) {
        boolean failed;
        try {
            verify(job, Set.of(TenantMessageBpmnConstants.ServiceTask.CHECK_EVALUATION));
            failed = !Boolean.FALSE.equals(job.getVariablesAsMap().get(EVALUATION_CURRENT))
                    && service.failed(text(job, CASE_ID), UUID.fromString(text(job, TENANT_MESSAGE_ID)),
                            Long.parseLong(text(job, LEADING_PROCESS_INSTANCE_KEY)), job.getProcessInstanceKey(), job.getTenantId(),
                            UUID.fromString(text(job, EVALUATION_REF)));
        } catch (RuntimeException exception) {
            fail(client, job, exception);
            return;
        }
        if (failed) {
            client.newThrowErrorCommand(job.getKey()).errorCode(TRIAGE_ANALYSIS_FAILED).errorMessage("CURRENT_EVALUATION_REQUIRES_REVIEW").send().join();
        } else {
            client.newCompleteCommand(job.getKey()).send().join();
        }
    }

    private void verify(ActivatedJob job, Set<String> elements) {
        require(TenantMessageBpmnConstants.PROCESS_ID.equals(job.getBpmnProcessId()) && elements.contains(job.getElementId()));
        ProcessInstance child = engine.newProcessInstanceGetRequest(job.getProcessInstanceKey()).send().join();
        require(child.getParentProcessInstanceKey() != null && child.getParentElementInstanceKey() != null
                && Long.toString(child.getParentProcessInstanceKey()).equals(text(job, LEADING_PROCESS_INSTANCE_KEY)));
        ElementInstance call = engine.newElementInstanceGetRequest(child.getParentElementInstanceKey()).send().join();
        require(CaseProcessBpmnConstants.CallActivity.TENANT_MESSAGE_CALL.equals(call.getElementId())
                && CaseProcessBpmnConstants.PROCESS_ID.equals(call.getProcessDefinitionId())
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
