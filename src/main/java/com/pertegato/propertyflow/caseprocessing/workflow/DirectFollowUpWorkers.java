package com.pertegato.propertyflow.caseprocessing.workflow;

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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("propertyflow-backend & !frontend-demo")
@ConditionalOnProperty(name = "propertyflow.workflow.enabled", havingValue = "true")
public class DirectFollowUpWorkers {

    public static final String APPLY = "propertyflow.direct-follow-up.apply";
    public static final String SIGNAL = "propertyflow.direct-follow-up.signal";
    public static final String CONTINUE = "propertyflow.direct-follow-up.continue";
    private final CamundaClient engine;
    private final CaseDataService service;
    private final CaseDataStore commands;
    private final DirectFollowUpStore store;
    private final TenantReplyStore replies;
    private final StaffReviewService reviews;

    public DirectFollowUpWorkers(CamundaClient engine, CaseDataService service, CaseDataStore commands,
            DirectFollowUpStore store, TenantReplyStore replies, StaffReviewService reviews) {
        this.engine = engine;
        this.service = service;
        this.commands = commands;
        this.store = store;
        this.replies = replies;
        this.reviews = reviews;
    }

    @JobWorker(type = APPLY, autoComplete = false)
    public void apply(JobClient client, ActivatedJob job) {
        String result;
        try {
            Command command = verify(job, "StaffFollowPublish", "directFollowCommandRef");
            result = service.directFollowUpState(command.id(), job.getProcessInstanceKey(), job.getTenantId());
        } catch (RuntimeException exception) {
            fail(client, job, exception);
            return;
        }
        client.newCompleteCommand(job.getKey()).variables(Map.of("directFollowOutcome", result)).send().join();
    }

    @JobWorker(type = SIGNAL, autoComplete = false)
    public void signal(JobClient client, ActivatedJob job) {
        try {
            Command command = verify(job, "StaffFollowSignal", "directFollowCommandRef");
            require("APPLIED".equals(command.state()));
            deliver(command.id());
        } catch (RuntimeException exception) {
            fail(client, job, exception);
            return;
        }
        client.newCompleteCommand(job.getKey()).send().join();
    }

    @JobWorker(type = CONTINUE, autoComplete = false)
    public void resume(JobClient client, ActivatedJob job) {
        UUID continuation;
        try {
            Command command = verify(job, "StaffFollowContinue", "directFollowContinuationRef");
            require("APPLIED".equals(command.state()));
            Intent intent = store.get(command.id());
            require(reviews.current(command.caseId(), command.leader()).id().equals(intent.continuation()));
            continuation = intent.continuation();
        } catch (RuntimeException exception) {
            fail(client, job, exception);
            return;
        }
        client.newCompleteCommand(job.getKey()).variables(Map.of("staffEntryContextRef", continuation.toString())).send().join();
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
        require(InitialCaseWorkers.CASE_PROCESS.equals(root.getProcessDefinitionId()) && root.getParentProcessInstanceKey() == null
                && command.tenant().equals(root.getTenantId()));
        if ("ACTIVE".equals(root.getState().name())) {
            String replyCall = intent.reply() == null ? "" : intent.reply().toString();
            String replyEntry = intent.reply() == null ? store.previousReplyEntry(intent.call()) : replies.get(intent.reply()).entry().toString();
            engine.newSetVariablesCommand(command.leader()).variables(Map.of("automaticInterruptionReady", true,
                    "automaticInterruptionCallRef", intent.call().toString(), "staffFollowAutomaticCallRef", intent.call().toString(),
                    "staffFollowReplyCallRef", replyCall,
                    "staffFollowReplyEntryRef", replyEntry, "staffFollowCommandRef", id.toString())).send().join();
        }
        store.delivered(id);
    }

    private Command verify(ActivatedJob job, String element, String variable) {
        ProcessInstance root = engine.newProcessInstanceGetRequest(job.getProcessInstanceKey()).send().join();
        require(InitialCaseWorkers.CASE_PROCESS.equals(job.getBpmnProcessId()) && element.equals(job.getElementId())
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
