package com.pertegato.propertyflow.caseprocessing.workflow;

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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;

@Component
@Profile("propertyflow-backend & !frontend-demo")
@ConditionalOnProperty(name = "propertyflow.workflow.enabled", havingValue = "true")
public class InitialCaseWorkers {

    public static final String CASE_PROCESS = "PropertyFlow_Case_Draft";
    public static final String AUTOMATIC_PROCESS = "PropertyFlow_Automatic_Draft";
    public static final String PREPARE_KEY = "propertyflow-prepare-process-key";
    public static final String REGISTER = "propertyflow-register-case";
    public static final String PREPARE_INITIAL = "propertyflow-prepare-initial";
    public static final String REGISTER_AUTOMATIC = "propertyflow-register-automatic";
    public static final String CLARIFICATION = "propertyflow-start-clarification";

    private final CamundaClient camunda;
    private final CaseWorkflowService service;

    public InitialCaseWorkers(CamundaClient camunda, CaseWorkflowService service) {
        this.camunda = camunda;
        this.service = service;
    }

    @JobWorker(type = PREPARE_KEY, autoComplete = false)
    public void prepareKey(JobClient client, ActivatedJob job) {
        execute(client, job, () -> {
            verifyRoot(job, "PrepareProcessKey");
            String kind = service.verifyStart(text(job, "caseId"), uuid(job, "startOrderRef"), job.getProcessInstanceKey(),
                    job.getProcessDefinitionKey(), job.getTenantId());
            return Map.of("processInstanceKey", Long.toString(job.getProcessInstanceKey()), "startKind", kind,
                    "automaticInterruptionReady", false, "automaticInterruptionCallRef", "");
        });
    }

    @JobWorker(type = REGISTER, autoComplete = false)
    public void register(JobClient client, ActivatedJob job) {
        execute(client, job, () -> {
            verifyRoot(job, "Register");
            service.register(text(job, "caseId"), uuid(job, "startOrderRef"), job.getProcessInstanceKey(),
                    job.getProcessDefinitionKey(), job.getTenantId());
            return Map.of("processInstanceKey", Long.toString(job.getProcessInstanceKey()));
        });
    }

    @JobWorker(type = PREPARE_INITIAL, autoComplete = false)
    public void prepareInitial(JobClient client, ActivatedJob job) {
        execute(client, job, () -> {
            verifyRoot(job, "PrepareInitialAuto");
            Entry entry = service.prepareInitial(text(job, "caseId"), job.getProcessInstanceKey(), job.getElementInstanceKey());
            return Map.of("automaticEntryContextRef", entry.id().toString(), "automaticCallRef", entry.callRef().toString());
        });
    }

    @JobWorker(type = REGISTER_AUTOMATIC, autoComplete = false)
    public void registerAutomatic(JobClient client, ActivatedJob job) {
        execute(client, job, () -> {
            ProcessInstance child = verifyChild(job, "AEntry");
            service.registerAutomatic(text(job, "caseId"), uuid(job, "entryContextRef"), uuid(job, "automaticCallRef"),
                    child.getParentProcessInstanceKey(), job.getProcessInstanceKey(), child.getParentElementInstanceKey());
            return Map.of("entryKind", service.entryKind(uuid(job, "entryContextRef")));
        });
    }

    @JobWorker(type = CLARIFICATION, autoComplete = false)
    public void clarification(JobClient client, ActivatedJob job) {
        execute(client, job, () -> {
            ProcessInstance child = verifyChild(job, "Clarification");
            service.startClarification(text(job, "caseId"), uuid(job, "entryContextRef"), uuid(job, "automaticCallRef"),
                    child.getParentProcessInstanceKey(), job.getProcessInstanceKey());
            return Map.of();
        });
    }

    private void verifyRoot(ActivatedJob job, String elementId) {
        require(CASE_PROCESS.equals(job.getBpmnProcessId()) && elementId.equals(job.getElementId()), "UNEXPECTED_JOB_ORIGIN");
        ProcessInstance instance = camunda.newProcessInstanceGetRequest(job.getProcessInstanceKey()).send().join();
        require(instance.getParentProcessInstanceKey() == null && text(job, "startOrderRef").equals(instance.getBusinessId()),
                "INVALID_START_IDENTITY");
    }

    private ProcessInstance verifyChild(ActivatedJob job, String elementId) {
        require(AUTOMATIC_PROCESS.equals(job.getBpmnProcessId()) && elementId.equals(job.getElementId()), "UNEXPECTED_JOB_ORIGIN");
        ProcessInstance child = camunda.newProcessInstanceGetRequest(job.getProcessInstanceKey()).send().join();
        require(child.getParentProcessInstanceKey() != null && child.getParentElementInstanceKey() != null, "MISSING_PARENT");
        require(Long.toString(child.getParentProcessInstanceKey()).equals(text(job, "leadingProcessInstanceKey")), "WRONG_PARENT");
        ElementInstance call = camunda.newElementInstanceGetRequest(child.getParentElementInstanceKey()).send().join();
        require("Automatic".equals(call.getElementId()) && CASE_PROCESS.equals(call.getProcessDefinitionId())
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
