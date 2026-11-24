package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.Entry;
import com.pertegato.propertyflow.caseprocessing.service.CaseWorkflowService;
import com.pertegato.propertyflow.caseprocessing.service.WorkflowRejectedException;
import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.worker.JobClient;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;

import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants.JobType.REGISTER_AUTOMATIC;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants.JobType.START_CLARIFICATION;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.PREPARE_INITIAL;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.PREPARE_PROCESS_KEY;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.REGISTER_CASE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.AUTOMATIC_ENTRY_CONTEXT_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.PROCESS_INSTANCE_KEY;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.START_KIND;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.START_ORDER_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.AUTOMATIC_CALL_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.AUTOMATIC_INTERRUPTION_CALL_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.AUTOMATIC_INTERRUPTION_READY;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.CASE_ID;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.ENTRY_CONTEXT_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.ENTRY_KIND;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.LEADING_PROCESS_INSTANCE_KEY;

@Component
@Profile("propertyflow-backend & !frontend-demo")
@ConditionalOnProperty(name = "propertyflow.workflow.enabled", havingValue = "true")
@RequiredArgsConstructor
public class InitialCaseWorkers {

    private final CamundaClient camunda;
    private final CaseWorkflowService service;

    @JobWorker(type = PREPARE_PROCESS_KEY, autoComplete = false)
    public void prepareKey(JobClient client, ActivatedJob job) {
        execute(client, job, () -> {
            verifyRoot(job, CaseProcessBpmnConstants.ServiceTask.PREPARE_PROCESS_KEY);
            String kind = service.verifyStart(text(job, CASE_ID), uuid(job, START_ORDER_REF), job.getProcessInstanceKey(),
                    job.getProcessDefinitionKey(), job.getTenantId());
            return Map.of(PROCESS_INSTANCE_KEY, Long.toString(job.getProcessInstanceKey()), START_KIND, kind,
                    AUTOMATIC_INTERRUPTION_READY, false, AUTOMATIC_INTERRUPTION_CALL_REF, "");
        });
    }

    @JobWorker(type = REGISTER_CASE, autoComplete = false)
    public void register(JobClient client, ActivatedJob job) {
        execute(client, job, () -> {
            verifyRoot(job, CaseProcessBpmnConstants.ServiceTask.REGISTER_CASE);
            service.register(text(job, CASE_ID), uuid(job, START_ORDER_REF), job.getProcessInstanceKey(),
                    job.getProcessDefinitionKey(), job.getTenantId());
            return Map.of(PROCESS_INSTANCE_KEY, Long.toString(job.getProcessInstanceKey()));
        });
    }

    @JobWorker(type = PREPARE_INITIAL, autoComplete = false)
    public void prepareInitial(JobClient client, ActivatedJob job) {
        execute(client, job, () -> {
            verifyRoot(job, CaseProcessBpmnConstants.ServiceTask.PREPARE_INITIAL_CLARIFICATION);
            Entry entry = service.prepareInitial(text(job, CASE_ID), job.getProcessInstanceKey(), job.getElementInstanceKey());
            return Map.of(AUTOMATIC_ENTRY_CONTEXT_REF, entry.id().toString(), AUTOMATIC_CALL_REF, entry.callRef().toString());
        });
    }

    @JobWorker(type = REGISTER_AUTOMATIC, autoComplete = false)
    public void registerAutomatic(JobClient client, ActivatedJob job) {
        execute(client, job, () -> {
            ProcessInstance child = verifyChild(job, AutomaticClarificationBpmnConstants.ServiceTask.CHECK_ENTRY);
            service.registerAutomatic(text(job, CASE_ID), uuid(job, ENTRY_CONTEXT_REF), uuid(job, AUTOMATIC_CALL_REF),
                    child.getParentProcessInstanceKey(), job.getProcessInstanceKey(), child.getParentElementInstanceKey());
            return Map.of(ENTRY_KIND, service.entryKind(uuid(job, ENTRY_CONTEXT_REF)));
        });
    }

    @JobWorker(type = START_CLARIFICATION, autoComplete = false)
    public void clarification(JobClient client, ActivatedJob job) {
        execute(client, job, () -> {
            ProcessInstance child = verifyChild(job, AutomaticClarificationBpmnConstants.ServiceTask.START_CLARIFICATION);
            service.startClarification(text(job, CASE_ID), uuid(job, ENTRY_CONTEXT_REF), uuid(job, AUTOMATIC_CALL_REF),
                    child.getParentProcessInstanceKey(), job.getProcessInstanceKey());
            return Map.of();
        });
    }

    private void verifyRoot(ActivatedJob job, String elementId) {
        require(CaseProcessBpmnConstants.PROCESS_ID.equals(job.getBpmnProcessId())
                && elementId.equals(job.getElementId()), "UNEXPECTED_JOB_ORIGIN");
        ProcessInstance instance = camunda.newProcessInstanceGetRequest(job.getProcessInstanceKey()).send().join();
        require(instance.getParentProcessInstanceKey() == null && text(job, START_ORDER_REF).equals(instance.getBusinessId()),
                "INVALID_START_IDENTITY");
    }

    private ProcessInstance verifyChild(ActivatedJob job, String elementId) {
        require(AutomaticClarificationBpmnConstants.PROCESS_ID.equals(job.getBpmnProcessId())
                && elementId.equals(job.getElementId()), "UNEXPECTED_JOB_ORIGIN");
        ProcessInstance child = camunda.newProcessInstanceGetRequest(job.getProcessInstanceKey()).send().join();
        require(child.getParentProcessInstanceKey() != null && child.getParentElementInstanceKey() != null, "MISSING_PARENT");
        require(Long.toString(child.getParentProcessInstanceKey()).equals(text(job, LEADING_PROCESS_INSTANCE_KEY)), "WRONG_PARENT");
        ElementInstance call = camunda.newElementInstanceGetRequest(child.getParentElementInstanceKey()).send().join();
        require(CaseProcessBpmnConstants.CallActivity.AUTOMATIC_CLARIFICATION_CALL.equals(call.getElementId())
                && CaseProcessBpmnConstants.PROCESS_ID.equals(call.getProcessDefinitionId())
                && child.getParentProcessInstanceKey().equals(call.getProcessInstanceKey())
                && job.getTenantId().equals(call.getTenantId()), "WRONG_CALL");
        return child;
    }

    private void execute(JobClient client, ActivatedJob job, Supplier<Map<String, Object>> action) {
        Map<String, Object> result;
        try {
            result = action.get();
        } catch (RuntimeException exception) {
            boolean rejected = exception instanceof WorkflowRejectedException || exception instanceof IllegalArgumentException;
            boolean recoverable = exception instanceof TransientDataAccessException
                    || exception instanceof DataAccessResourceFailureException || exception instanceof TransactionException
                    || exception instanceof ClientException;
            int retries = rejected ? 0 : recoverable ? Math.max(1, job.getRetries()) : Math.max(0, job.getRetries() - 1);
            String reason = exception instanceof WorkflowRejectedException ? exception.getMessage()
                    : rejected ? "INVALID_WORKFLOW_REFERENCE" : "WORKFLOW_TEMPORARILY_UNAVAILABLE";
            client.newFailCommand(job.getKey())
                    .retries(retries)
                    .retryBackoff(Duration.ofSeconds(5))
                    .errorMessage(reason)
                    .send().join();
            return;
        }
        // Completion is deliberately outside the database transaction and error handling above.
        // Losing this response causes redelivery, not a rollback of the confirmed business effect.
        client.newCompleteCommand(job.getKey()).variables(result).send().join();
    }

    private String text(ActivatedJob job, String name) {
        Object value = job.getVariablesAsMap().get(name);
        require(value instanceof String && !((String) value).isBlank(), "MISSING_WORKFLOW_REFERENCE");
        return (String) value;
    }

    private UUID uuid(ActivatedJob job, String name) {
        return UUID.fromString(text(job, name));
    }

    private void require(boolean condition, String reason) {
        if (!condition) {
            throw new WorkflowRejectedException(reason);
        }
    }
}
