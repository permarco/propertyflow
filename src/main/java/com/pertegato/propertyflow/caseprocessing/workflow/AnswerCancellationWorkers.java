package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.persistence.AnswerRequirementStore;
import com.pertegato.propertyflow.caseprocessing.persistence.AnswerRequirementStore.Delivery;
import com.pertegato.propertyflow.caseprocessing.service.StaffReviewService;
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

import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.ANSWER_CONTINUE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.ANSWER_CANCELLATION_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.CLEARED_ANSWER_CALL_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.CLEARED_AUTOMATIC_CALL_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.CLEARED_REPLY_ENTRY_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.CLEAR_ANSWER_OUTCOME;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.ACTIVE_REPLY_CALL_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.AUTOMATIC_INTERRUPTION_CALL_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.AUTOMATIC_INTERRUPTION_READY;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.CASE_ID;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.STAFF_ENTRY_CONTEXT_REF;

@Component
@Profile("propertyflow-backend & !frontend-demo")
@ConditionalOnProperty(name = "propertyflow.workflow.enabled", havingValue = "true")
@RequiredArgsConstructor
public class AnswerCancellationWorkers {

    private final AnswerRequirementStore store;
    private final StaffReviewService reviews;
    private final CamundaClient engine;

    @Scheduled(fixedDelayString = "${propertyflow.workflow.poll-delay-ms:5000}",
            initialDelayString = "${propertyflow.workflow.initial-delay-ms:5000}")
    public void dispatch() {
        for (int index = 0; index < 20; index++) {
            Delivery delivery = store.claim().orElse(null);
            if (delivery == null) {
                return;
            }
            try {
                ProcessInstance root = engine.newProcessInstanceGetRequest(delivery.leader())
                        .send()
                        .join();
                if (!CaseProcessBpmnConstants.PROCESS_ID.equals(root.getProcessDefinitionId())
                        || root.getParentProcessInstanceKey() != null
                        || !delivery.tenant().equals(root.getTenantId()) || !"ACTIVE".equals(root.getState().name())) {
                    continue;
                }
                Map<String, Object> variables = delivery.replyCall() == null
                        ? Map.of(AUTOMATIC_INTERRUPTION_READY, true, AUTOMATIC_INTERRUPTION_CALL_REF, delivery.automaticCall().toString(),
                                CLEARED_AUTOMATIC_CALL_REF, delivery.automaticCall().toString(), ANSWER_CANCELLATION_REF, delivery.id().toString(),
                                CLEARED_REPLY_ENTRY_REF, store.automaticReplyEntry(delivery).map(UUID::toString).orElse(""))
                        : Map.of(CLEARED_ANSWER_CALL_REF, delivery.replyCall().toString(), ANSWER_CANCELLATION_REF, delivery.id().toString());
                engine.newSetVariablesCommand(delivery.leader())
                        .variables(variables)
                        .send()
                        .join();
                store.delivered(delivery.id());
            } catch (RuntimeException exception) {
                // Die bereits gespeicherte Aufhebung bleibt gültig; ihre Fortsetzung wird dauerhaft nachgeliefert.
            }
        }
    }

    @JobWorker(type = ANSWER_CONTINUE, autoComplete = false)
    public void continueWork(JobClient client, ActivatedJob job) {
        try {
            if (!CaseProcessBpmnConstants.PROCESS_ID.equals(job.getBpmnProcessId())
                    || !CaseProcessBpmnConstants.ServiceTask.CONTINUE_WITHOUT_ANSWER.equals(job.getElementId())) {
                throw new WorkflowRejectedException("INVALID_CLEAR_ANSWER_ORIGIN");
            }
            Delivery delivery = store.delivery(UUID.fromString((String) job.getVariablesAsMap().get(ANSWER_CANCELLATION_REF)));
            if (delivery.leader() != job.getProcessInstanceKey() || !delivery.tenant().equals(job.getTenantId())
                    || !delivery.caseId().equals(job.getVariablesAsMap().get(CASE_ID)) || !store.current(delivery)
                    || (delivery.replyCall() == null
                            ? !delivery.automaticCall().toString().equals(job.getVariablesAsMap().get(CLEARED_AUTOMATIC_CALL_REF))
                            : !delivery.replyCall().toString().equals(job.getVariablesAsMap().get(ACTIVE_REPLY_CALL_REF)))) {
                throw new WorkflowRejectedException("INVALID_CLEAR_ANSWER_CONTEXT");
            }
            reviews.read(delivery.caseId(), delivery.leader(), delivery.continuation());
            client.newCompleteCommand(job.getKey())
                    .variables(Map.of(CLEAR_ANSWER_OUTCOME, "STAFF", STAFF_ENTRY_CONTEXT_REF, delivery.continuation().toString()))
                    .send()
                    .join();
        } catch (RuntimeException exception) {
            boolean rejected = exception instanceof WorkflowRejectedException || exception instanceof IllegalArgumentException;
            client.newFailCommand(job.getKey())
                    .retries(rejected ? 0 : Math.max(1, job.getRetries()))
                    .retryBackoff(Duration.ofSeconds(5))
                    .errorMessage(rejected ? "INVALID_CLEAR_ANSWER_CONTEXT" : "CLEAR_ANSWER_TEMPORARILY_UNAVAILABLE")
                    .send()
                    .join();
        }
    }
}
