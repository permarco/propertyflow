package com.pertegato.propertyflow.caseprocessing.workflow;

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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("propertyflow-backend & !frontend-demo")
@ConditionalOnProperty(name = "propertyflow.workflow.enabled", havingValue = "true")
public class AnswerCancellationWorkers {

    private final AnswerRequirementStore store;
    private final StaffReviewService reviews;
    private final CamundaClient engine;

    public AnswerCancellationWorkers(AnswerRequirementStore store, StaffReviewService reviews, CamundaClient engine) {
        this.store = store;
        this.reviews = reviews;
        this.engine = engine;
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
                ProcessInstance root = engine.newProcessInstanceGetRequest(delivery.leader())
                        .send()
                        .join();
                if (!InitialCaseWorkers.CASE_PROCESS.equals(root.getProcessDefinitionId()) || root.getParentProcessInstanceKey() != null
                        || !delivery.tenant().equals(root.getTenantId()) || !"ACTIVE".equals(root.getState().name())) {
                    continue;
                }
                Map<String, Object> variables = delivery.replyCall() == null
                        ? Map.of("automaticInterruptionReady", true, "automaticInterruptionCallRef", delivery.automaticCall().toString(),
                                "clearedAutomaticCallRef", delivery.automaticCall().toString(), "answerCancellationRef", delivery.id().toString(),
                                "clearedReplyEntryRef", store.automaticReplyEntry(delivery).map(UUID::toString).orElse(""))
                        : Map.of("clearedAnswerCallRef", delivery.replyCall().toString(), "answerCancellationRef", delivery.id().toString());
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

    @JobWorker(type = "propertyflow.answer.continue", autoComplete = false)
    public void continueWork(JobClient client, ActivatedJob job) {
        try {
            if (!InitialCaseWorkers.CASE_PROCESS.equals(job.getBpmnProcessId()) || !"ClearAnswerContinue".equals(job.getElementId())) {
                throw new WorkflowRejectedException("INVALID_CLEAR_ANSWER_ORIGIN");
            }
            Delivery delivery = store.delivery(UUID.fromString((String) job.getVariablesAsMap().get("answerCancellationRef")));
            if (delivery.leader() != job.getProcessInstanceKey() || !delivery.tenant().equals(job.getTenantId())
                    || !delivery.caseId().equals(job.getVariablesAsMap().get("caseId")) || !store.current(delivery)
                    || (delivery.replyCall() == null
                            ? !delivery.automaticCall().toString().equals(job.getVariablesAsMap().get("clearedAutomaticCallRef"))
                            : !delivery.replyCall().toString().equals(job.getVariablesAsMap().get("activeReplyCallRef")))) {
                throw new WorkflowRejectedException("INVALID_CLEAR_ANSWER_CONTEXT");
            }
            reviews.read(delivery.caseId(), delivery.leader(), delivery.continuation());
            client.newCompleteCommand(job.getKey())
                    .variables(Map.of("clearAnswerOutcome", "STAFF", "staffEntryContextRef", delivery.continuation().toString()))
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
