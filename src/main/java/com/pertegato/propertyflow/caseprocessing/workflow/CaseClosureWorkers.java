package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.TenantReplyBpmnConstants;
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
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.ErrorCode.STAFF_CLOSURE_STALE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.STAFF_CLOSE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.STAFF_OUTCOME;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.CASE_ID;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.STAFF_DECISION_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.STAFF_ENTRY_CONTEXT_REF;

@Component
@Profile("propertyflow-backend & !frontend-demo")
@ConditionalOnProperty(name = "propertyflow.workflow.enabled", havingValue = "true")
@RequiredArgsConstructor
public class CaseClosureWorkers {

    private final CamundaClient engine;
    private final CaseClosureService service;

    @JobWorker(type = STAFF_CLOSE, autoComplete = false)
    public void close(JobClient client, ActivatedJob job) {
        ClosureResult result;
        try {
            require(CaseProcessBpmnConstants.PROCESS_ID.equals(job.getBpmnProcessId())
                    && CaseProcessBpmnConstants.ServiceTask.SAVE_STAFF_CLOSURE.equals(job.getElementId())
                    && "CLOSE".equals(text(job, STAFF_OUTCOME)));
            String caseId = text(job, CASE_ID);
            UUID decision = UUID.fromString(text(job, STAFF_DECISION_REF));
            ClosureContext context = service.context(caseId, job.getProcessInstanceKey(), decision);
            ProcessInstance root = engine.newProcessInstanceGetRequest(job.getProcessInstanceKey()).send().join();
            ProcessInstance child = engine.newProcessInstanceGetRequest(context.child()).send().join();
            ElementInstance call = engine.newElementInstanceGetRequest(context.parentElement()).send().join();
            require(root.getParentProcessInstanceKey() == null && job.getTenantId().equals(context.tenant())
                    && (context.reply() ? TenantReplyBpmnConstants.PROCESS_ID : StaffProcessingBpmnConstants.PROCESS_ID)
                        .equals(child.getProcessDefinitionId())
                    && Long.valueOf(job.getProcessInstanceKey()).equals(child.getParentProcessInstanceKey())
                    && Long.valueOf(context.parentElement()).equals(child.getParentElementInstanceKey())
                    && context.tenant().equals(child.getTenantId())
                    && (context.reply() ? CaseProcessBpmnConstants.CallActivity.TENANT_REPLY_CALL
                            : CaseProcessBpmnConstants.CallActivity.STAFF_PROCESSING_CALL)
                        .equals(call.getElementId())
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
            client.newThrowErrorCommand(job.getKey()).errorCode(STAFF_CLOSURE_STALE).errorMessage("CONFIRMED_CLOSURE_BASIS_CHANGED")
                    .variables(Map.of(STAFF_ENTRY_CONTEXT_REF, result.continuation().toString())).send().join();
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
