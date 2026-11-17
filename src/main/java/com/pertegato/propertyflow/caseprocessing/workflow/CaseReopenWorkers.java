package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.persistence.CaseReopenStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseReopenStore.Order;
import com.pertegato.propertyflow.caseprocessing.service.CaseReopenService;
import com.pertegato.propertyflow.caseprocessing.service.WorkflowRejectedException;
import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.search.enums.MessageSubscriptionState;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.worker.JobClient;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("propertyflow-backend & !frontend-demo")
@ConditionalOnProperty(name = "propertyflow.workflow.enabled", havingValue = "true")
public class CaseReopenWorkers {

    public static final String REOPEN = "propertyflow.case.reopen";
    private final CamundaClient engine;
    private final CaseReopenService service;
    private final CaseReopenStore store;

    public CaseReopenWorkers(CamundaClient engine, CaseReopenService service, CaseReopenStore store) {
        this.engine = engine;
        this.service = service;
        this.store = store;
    }

    @Scheduled(fixedDelayString = "${propertyflow.workflow.poll-delay-ms:5000}",
            initialDelayString = "${propertyflow.workflow.initial-delay-ms:5000}")
    public void dispatch() {
        for (int index = 0; index < 20; index++) {
            Order order = store.claim().orElse(null);
            if (order == null) {
                return;
            }
            ProcessStartGateway.StartResult result = new CamundaProcessStartGateway(engine).ensureStarted(order.id(), order.definition(), order.tenant(),
                    Map.of("caseId", order.caseId(), "startOrderRef", order.id().toString()));
            if (result == ProcessStartGateway.StartResult.REJECTED || result == ProcessStartGateway.StartResult.ENDED_BEFORE_REGISTRATION) {
                store.blocked(order.id());
            }
        }
    }

    @JobWorker(type = REOPEN, autoComplete = false)
    public void reopen(JobClient client, ActivatedJob job) {
        Order activated;
        try {
            require(InitialCaseWorkers.CASE_PROCESS.equals(job.getBpmnProcessId()) && "ReopenCase".equals(job.getElementId()));
            UUID orderId = UUID.fromString(text(job, "startOrderRef"));
            Order order = service.order(orderId);
            ProcessInstance current = engine.newProcessInstanceGetRequest(job.getProcessInstanceKey()).send().join();
            require(current.getParentProcessInstanceKey() == null && orderId.toString().equals(current.getBusinessId()));
            ProcessInstance previous = engine.newProcessInstanceGetRequest(order.previous()).send().join();
            require(previous.getParentProcessInstanceKey() == null && InitialCaseWorkers.CASE_PROCESS.equals(previous.getProcessDefinitionId())
                    && order.tenant().equals(previous.getTenantId()));
            if (!"COMPLETED".equals(previous.getState().name())) {
                throw new IllegalStateException("PREVIOUS_PROCESS_NOT_COMPLETED");
            }
            boolean ready = !engine.newMessageSubscriptionSearchRequest().filter(filter -> filter.processInstanceKey(job.getProcessInstanceKey())
                    .elementId("NStart").messageName(CamundaTenantMessageGateway.MESSAGE_NAME).correlationKey(Long.toString(job.getProcessInstanceKey()))
                    .tenantId(job.getTenantId()).messageSubscriptionState(MessageSubscriptionState.CREATED)).send().join().items().isEmpty();
            if (!ready) {
                throw new IllegalStateException("NEW_MESSAGE_RECEIVER_NOT_READY");
            }
            activated = service.activate(text(job, "caseId"), orderId, job.getProcessInstanceKey(), job.getProcessDefinitionKey(),
                    job.getTenantId(), job.getElementInstanceKey());
        } catch (RuntimeException exception) {
            boolean rejected = exception instanceof WorkflowRejectedException || exception instanceof IllegalArgumentException;
            client.newFailCommand(job.getKey()).retries(rejected ? 0 : Math.max(1, job.getRetries())).retryBackoff(Duration.ofSeconds(5))
                    .errorMessage(rejected ? "INVALID_REOPEN_CONTEXT" : "REOPEN_TEMPORARILY_UNAVAILABLE").send().join();
            return;
        }
        if (activated == null) {
            client.newThrowErrorCommand(job.getKey()).errorCode("REOPEN_REJECTED").errorMessage("REOPEN_ORDER_SUPERSEDED").send().join();
        } else {
            client.newCompleteCommand(job.getKey()).variables(Map.of("staffEntryContextRef", activated.continuation().toString())).send().join();
        }
    }

    private String text(ActivatedJob job, String name) {
        Object value = job.getVariablesAsMap().get(name);
        require(value instanceof String && !((String) value).isBlank());
        return (String) value;
    }

    private void require(boolean valid) {
        if (!valid) {
            throw new WorkflowRejectedException("INVALID_REOPEN_ORIGIN");
        }
    }
}
