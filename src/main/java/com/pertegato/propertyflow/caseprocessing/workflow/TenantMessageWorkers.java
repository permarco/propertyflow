package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.service.TenantMessageService;
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
public class TenantMessageWorkers {

    public static final String IDENTIFY = "propertyflow.tenant-message.identify";
    public static final String PROCESS = "PropertyFlow_TenantMessage_Draft";
    private final CamundaClient engine;
    private final TenantMessageService service;

    public TenantMessageWorkers(CamundaClient engine, TenantMessageService service) {
        this.engine = engine;
        this.service = service;
    }

    @JobWorker(type = IDENTIFY, autoComplete = false)
    public void identify(JobClient client, ActivatedJob job) {
        boolean accepted;
        try {
            require(PROCESS.equals(job.getBpmnProcessId()) && "NIdentify".equals(job.getElementId()));
            ProcessInstance child = engine.newProcessInstanceGetRequest(job.getProcessInstanceKey()).send().join();
            require(child.getParentProcessInstanceKey() != null && child.getParentElementInstanceKey() != null
                    && Long.toString(child.getParentProcessInstanceKey()).equals(text(job, "leadingProcessInstanceKey")));
            ElementInstance call = engine.newElementInstanceGetRequest(child.getParentElementInstanceKey()).send().join();
            require("ProcessTenantMessage".equals(call.getElementId()) && InitialCaseWorkers.CASE_PROCESS.equals(call.getProcessDefinitionId())
                    && child.getParentProcessInstanceKey().equals(call.getProcessInstanceKey()) && job.getTenantId().equals(call.getTenantId()));
            accepted = service.receive(text(job, "caseId"), UUID.fromString(text(job, "tenantMessageId")),
                    child.getParentProcessInstanceKey(), job.getProcessInstanceKey(), child.getParentElementInstanceKey(), job.getTenantId());
        } catch (RuntimeException exception) {
            boolean rejected = exception instanceof WorkflowRejectedException || exception instanceof IllegalArgumentException;
            client.newFailCommand(job.getKey()).retries(rejected ? 0 : Math.max(1, job.getRetries())).retryBackoff(Duration.ofSeconds(5))
                    .errorMessage(rejected ? "TENANT_MESSAGE_CONTRACT_REJECTED" : "TENANT_MESSAGE_TEMPORARILY_UNAVAILABLE").send().join();
            return;
        }
        client.newCompleteCommand(job.getKey()).variables(Map.of("tenantMessageAccepted", accepted,
                "casePhase", service.phase(text(job, "caseId"), Long.parseLong(text(job, "leadingProcessInstanceKey"))))).send().join();
    }

    private String text(ActivatedJob job, String name) {
        Object value = job.getVariablesAsMap().get(name);
        require(value instanceof String && !((String) value).isBlank());
        return (String) value;
    }

    private void require(boolean condition) {
        if (!condition) {
            throw new WorkflowRejectedException("INVALID_TENANT_MESSAGE_ORIGIN");
        }
    }
}
