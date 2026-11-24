package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseDataStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseDataStore.Command;
import com.pertegato.propertyflow.caseprocessing.persistence.DirectFollowUpStore;
import com.pertegato.propertyflow.caseprocessing.persistence.DirectFollowUpStore.Intent;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantReplyStore;
import com.pertegato.propertyflow.caseprocessing.service.CaseDataService;
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

import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.DIRECT_FOLLOW_UP_APPLY;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.DIRECT_FOLLOW_UP_CONTINUE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.DIRECT_FOLLOW_UP_SIGNAL;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.DIRECT_FOLLOW_COMMAND_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.DIRECT_FOLLOW_CONTINUATION_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.DIRECT_FOLLOW_OUTCOME;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.STAFF_FOLLOW_AUTOMATIC_CALL_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.STAFF_FOLLOW_COMMAND_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.STAFF_FOLLOW_REPLY_CALL_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.STAFF_FOLLOW_REPLY_ENTRY_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.AUTOMATIC_INTERRUPTION_CALL_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.AUTOMATIC_INTERRUPTION_READY;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.STAFF_ENTRY_CONTEXT_REF;

@Component
@Profile("propertyflow-backend & !frontend-demo")
@ConditionalOnProperty(name = "propertyflow.workflow.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DirectFollowUpWorkers {

    private final CamundaClient engine;
    private final CaseDataService service;
    private final CaseDataStore commands;
    private final DirectFollowUpStore store;
    private final TenantReplyStore replies;
    private final StaffReviewService reviews;

    @JobWorker(type = DIRECT_FOLLOW_UP_APPLY, autoComplete = false)
    public void apply(JobClient client, ActivatedJob job) {
        String result;
        try {
            Command command = verify(job, CaseProcessBpmnConstants.ServiceTask.SAVE_DIRECT_FOLLOW_UP, DIRECT_FOLLOW_COMMAND_REF);
            result = service.directFollowUpState(command.id(), job.getProcessInstanceKey(), job.getTenantId());
        } catch (RuntimeException exception) {
            fail(client, job, exception);
            return;
        }
        client.newCompleteCommand(job.getKey()).variables(Map.of(DIRECT_FOLLOW_OUTCOME, result)).send().join();
    }

    @JobWorker(type = DIRECT_FOLLOW_UP_SIGNAL, autoComplete = false)
    public void signal(JobClient client, ActivatedJob job) {
        try {
            Command command = verify(job, CaseProcessBpmnConstants.ServiceTask.NOTIFY_DIRECT_FOLLOW_UP, DIRECT_FOLLOW_COMMAND_REF);
            require("APPLIED".equals(command.state()));
            deliver(command.id());
        } catch (RuntimeException exception) {
            fail(client, job, exception);
            return;
        }
        client.newCompleteCommand(job.getKey()).send().join();
    }

    @JobWorker(type = DIRECT_FOLLOW_UP_CONTINUE, autoComplete = false)
    public void resume(JobClient client, ActivatedJob job) {
        UUID continuation;
        try {
            Command command = verify(job, CaseProcessBpmnConstants.ServiceTask.CONTINUE_TO_FOLLOW_UP, DIRECT_FOLLOW_CONTINUATION_REF);
            require("APPLIED".equals(command.state()));
            Intent intent = store.get(command.id());
            require(reviews.current(command.caseId(), command.leader()).id().equals(intent.continuation()));
            continuation = intent.continuation();
        } catch (RuntimeException exception) {
            fail(client, job, exception);
            return;
        }
        client.newCompleteCommand(job.getKey()).variables(Map.of(STAFF_ENTRY_CONTEXT_REF, continuation.toString())).send().join();
    }

    @Scheduled(fixedDelayString = "${propertyflow.workflow.poll-delay-ms:5000}",
            initialDelayString = "${propertyflow.workflow.initial-delay-ms:5000}")
    public void dispatch() {
        for (int index = 0; index < 20; index++) {
            UUID id = store.claim().orElse(null);
            if (id == null) {
                return;
            }
            try {
                deliver(id);
            } catch (RuntimeException exception) {
                // Der gespeicherte Termin bleibt gültig; der Marker wird erneut zugestellt.
            }
        }
    }

    private void deliver(UUID id) {
        Command command = commands.get(id);
        Intent intent = store.get(id);
        if (!reviews.current(command.caseId(), command.leader()).id().equals(intent.continuation())) {
            store.delivered(id);
            return;
        }
        ProcessInstance root = engine.newProcessInstanceGetRequest(command.leader()).send().join();
        require(CaseProcessBpmnConstants.PROCESS_ID.equals(root.getProcessDefinitionId())
                && root.getParentProcessInstanceKey() == null
                && command.tenant().equals(root.getTenantId()));
        if ("ACTIVE".equals(root.getState().name())) {
            String replyCall = intent.reply() == null ? "" : intent.reply().toString();
            String replyEntry = intent.reply() == null ? store.previousReplyEntry(intent.call()) : replies.get(intent.reply()).entry().toString();
            engine.newSetVariablesCommand(command.leader()).variables(Map.of(AUTOMATIC_INTERRUPTION_READY, true,
                    AUTOMATIC_INTERRUPTION_CALL_REF, intent.call().toString(), STAFF_FOLLOW_AUTOMATIC_CALL_REF, intent.call().toString(),
                    STAFF_FOLLOW_REPLY_CALL_REF, replyCall,
                    STAFF_FOLLOW_REPLY_ENTRY_REF, replyEntry, STAFF_FOLLOW_COMMAND_REF, id.toString())).send().join();
        }
        store.delivered(id);
    }

    private Command verify(ActivatedJob job, String element, String variable) {
        ProcessInstance root = engine.newProcessInstanceGetRequest(job.getProcessInstanceKey()).send().join();
        require(CaseProcessBpmnConstants.PROCESS_ID.equals(job.getBpmnProcessId())
                && element.equals(job.getElementId())
                && root.getParentProcessInstanceKey() == null && job.getTenantId().equals(root.getTenantId()));
        Command command = commands.get(UUID.fromString((String) job.getVariablesAsMap().get(variable)));
        require("DIRECT_FOLLOW_UP".equals(command.type()) && command.leader() == job.getProcessInstanceKey()
                && command.tenant().equals(job.getTenantId()));
        return command;
    }

    private void fail(JobClient client, ActivatedJob job, RuntimeException exception) {
        boolean rejected = exception instanceof WorkflowRejectedException || exception instanceof IllegalArgumentException;
        client.newFailCommand(job.getKey()).retries(rejected ? 0 : Math.max(1, job.getRetries())).retryBackoff(Duration.ofSeconds(5))
                .errorMessage(rejected ? "INVALID_DIRECT_FOLLOW_UP_CONTEXT" : "DIRECT_FOLLOW_UP_TEMPORARILY_UNAVAILABLE").send().join();
    }

    private void require(boolean valid) {
        if (!valid) {
            throw new WorkflowRejectedException("INVALID_DIRECT_FOLLOW_UP_CONTEXT");
        }
    }
}
