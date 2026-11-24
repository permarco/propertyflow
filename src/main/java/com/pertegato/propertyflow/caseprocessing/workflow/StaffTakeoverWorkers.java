package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffTakeoverStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffTakeoverStore.Delivery;
import com.pertegato.propertyflow.caseprocessing.service.CaseDataService;
import com.pertegato.propertyflow.caseprocessing.service.WorkflowRejectedException;
import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.worker.JobClient;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.TAKEOVER_APPLY;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.TAKEOVER_SIGNAL;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.STAFF_TAKEOVER_CALL_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.STAFF_TAKEOVER_COMMAND_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.STAFF_TAKEOVER_OUTCOME;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.STAFF_TAKEOVER_REPLY_ENTRY_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.AUTOMATIC_INTERRUPTION_CALL_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.AUTOMATIC_INTERRUPTION_READY;

@Component
@Profile("propertyflow-backend & !frontend-demo")
@ConditionalOnProperty(name = "propertyflow.workflow.enabled", havingValue = "true")
@RequiredArgsConstructor
public class StaffTakeoverWorkers {

    private final CamundaClient engine;
    private final CaseDataService service;
    private final StaffTakeoverStore store;

    @JobWorker(type = TAKEOVER_APPLY, autoComplete = false)
    public void apply(JobClient client, ActivatedJob job) {
        String result;
        try {
            verify(job, CaseProcessBpmnConstants.ServiceTask.SAVE_STAFF_TAKEOVER);
            result = service.takeoverState(reference(job), job.getProcessInstanceKey(), job.getTenantId());
        } catch (RuntimeException exception) {
            fail(client, job, exception);
            return;
        }
        client.newCompleteCommand(job.getKey())
                .variables(Map.of(STAFF_TAKEOVER_OUTCOME, result))
                .send()
                .join();
    }

    @JobWorker(type = TAKEOVER_SIGNAL, autoComplete = false)
    public void signal(JobClient client, ActivatedJob job) {
        try {
            verify(job, CaseProcessBpmnConstants.ServiceTask.NOTIFY_STAFF_TAKEOVER);
            Delivery delivery = store.delivery(reference(job));
            require(delivery.leader() == job.getProcessInstanceKey() && delivery.tenant().equals(job.getTenantId()));
            deliver(delivery);
        } catch (RuntimeException exception) {
            fail(client, job, exception);
            return;
        }
        client.newCompleteCommand(job.getKey()).send().join();
    }

    @Scheduled(fixedDelayString = "${propertyflow.workflow.poll-delay-ms:5000}",
            initialDelayString = "${propertyflow.workflow.initial-delay-ms:5000}")
    public void dispatch() {
        for (int index = 0; index < 20; index++) {
            Delivery delivery = store.claim().orElse(null);
            if (delivery == null) {
                return;
            }
            try {
                deliver(delivery);
            } catch (RuntimeException exception) {
                // Die bestätigte Übernahme bleibt bestehen; ein verlorener Marker wird nachgeliefert.
            }
        }
    }

    private void deliver(Delivery delivery) {
        ProcessInstance root = engine.newProcessInstanceGetRequest(delivery.leader()).send().join();
        require(CaseProcessBpmnConstants.PROCESS_ID.equals(root.getProcessDefinitionId())
                && root.getParentProcessInstanceKey() == null
                && delivery.tenant().equals(root.getTenantId()));
        if ("ACTIVE".equals(root.getState().name())) {
            engine.newSetVariablesCommand(delivery.leader())
                    .variables(Map.of(AUTOMATIC_INTERRUPTION_READY, true, AUTOMATIC_INTERRUPTION_CALL_REF, delivery.call().toString(),
                            STAFF_TAKEOVER_CALL_REF, delivery.call().toString(), STAFF_TAKEOVER_REPLY_ENTRY_REF, delivery.replyEntry()))
                    .send()
                    .join();
        }
        store.delivered(delivery.id());
    }

    private void verify(ActivatedJob job, String element) {
        ProcessInstance root = engine.newProcessInstanceGetRequest(job.getProcessInstanceKey()).send().join();
        require(CaseProcessBpmnConstants.PROCESS_ID.equals(job.getBpmnProcessId())
                && element.equals(job.getElementId())
                && root.getParentProcessInstanceKey() == null && job.getTenantId().equals(root.getTenantId()));
    }

    private UUID reference(ActivatedJob job) {
        return UUID.fromString((String) job.getVariablesAsMap().get(STAFF_TAKEOVER_COMMAND_REF));
    }

    private void fail(JobClient client, ActivatedJob job, RuntimeException exception) {
        boolean rejected = exception instanceof WorkflowRejectedException || exception instanceof IllegalArgumentException;
        client.newFailCommand(job.getKey())
                .retries(rejected ? 0 : Math.max(1, job.getRetries()))
                .retryBackoff(Duration.ofSeconds(2))
                .errorMessage(rejected ? "INVALID_TAKEOVER_CONTEXT" : "TAKEOVER_TEMPORARILY_UNAVAILABLE")
                .send()
                .join();
    }

    private void require(boolean valid) {
        if (!valid) {
            throw new WorkflowRejectedException("INVALID_TAKEOVER_CONTEXT");
        }
    }
}
