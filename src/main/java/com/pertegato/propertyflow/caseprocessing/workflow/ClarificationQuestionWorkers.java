package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.persistence.ClarificationQuestionStore;
import com.pertegato.propertyflow.caseprocessing.persistence.ClarificationQuestionStore.Delivery;
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

import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.CLARIFICATION_QUESTION_SIGNAL;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.MANUAL_QUESTION_CALL_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.MANUAL_QUESTION_PREVIOUS_REPLY_ENTRY_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.AUTOMATIC_INTERRUPTION_CALL_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.AUTOMATIC_INTERRUPTION_READY;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.STAFF_MESSAGE_COMMAND_REF;

@Component
@Profile("propertyflow-backend")
@ConditionalOnProperty(name = "propertyflow.workflow.enabled", havingValue = "true")
@RequiredArgsConstructor
public class ClarificationQuestionWorkers {

    private final CamundaClient engine;
    private final ClarificationQuestionStore store;

    @JobWorker(type = CLARIFICATION_QUESTION_SIGNAL, autoComplete = false)
    public void signal(JobClient client, ActivatedJob job) {
        try {
            ProcessInstance root = engine.newProcessInstanceGetRequest(job.getProcessInstanceKey()).send().join();
            require(CaseProcessBpmnConstants.PROCESS_ID.equals(job.getBpmnProcessId())
                    && CaseProcessBpmnConstants.ServiceTask.NOTIFY_STAFF_QUESTION.equals(job.getElementId())
                    && root.getParentProcessInstanceKey() == null);
            UUID command = UUID.fromString((String) job.getVariablesAsMap().get(STAFF_MESSAGE_COMMAND_REF));
            Delivery delivery = store.delivery(command).orElse(null);
            if (delivery != null) {
                require(delivery.leader() == job.getProcessInstanceKey() && delivery.tenant().equals(job.getTenantId()));
                deliver(delivery);
            }
        } catch (RuntimeException exception) {
            boolean rejected = exception instanceof WorkflowRejectedException || exception instanceof IllegalArgumentException;
            client.newFailCommand(job.getKey())
                    .retries(rejected ? 0 : Math.max(1, job.getRetries()))
                    .retryBackoff(Duration.ofSeconds(2))
                    .errorMessage(rejected ? "INVALID_CLARIFICATION_QUESTION" : "CLARIFICATION_QUESTION_PENDING")
                    .send()
                    .join();
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
                // Die veröffentlichte Rückfrage und ihr Antwortbedarf bleiben erhalten; die Übergabe wird nachgeliefert.
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
                            MANUAL_QUESTION_CALL_REF, delivery.call().toString(), MANUAL_QUESTION_PREVIOUS_REPLY_ENTRY_REF, delivery.previousReply()))
                    .send()
                    .join();
        }
        store.delivered(delivery.id());
    }

    private void require(boolean valid) {
        if (!valid) {
            throw new WorkflowRejectedException("INVALID_CLARIFICATION_QUESTION");
        }
    }
}
