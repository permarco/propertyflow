package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.service.FollowUpCommandService;
import com.pertegato.propertyflow.caseprocessing.service.FollowUpCommandService.Applied;
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
public class FollowUpCommandWorkers {

    public static final String CLOSE = "propertyflow.follow-up.close";
    public static final String APPLY = "propertyflow.follow-up.change";
    public static final String STALE = "FOLLOW_UP_CHANGE_STALE";
    private final CamundaClient engine;
    private final FollowUpCommandService service;

    public FollowUpCommandWorkers(CamundaClient engine, FollowUpCommandService service) {
        this.engine = engine;
        this.service = service;
    }

    @JobWorker(type = APPLY, autoComplete = false)
    public void apply(JobClient client, ActivatedJob job) {
        Applied applied;
        try {
            boolean delete = "WDeleteTask".equals(job.getElementId());
            require(CaseWaitWorkers.FOLLOW_UP_PROCESS.equals(job.getBpmnProcessId()) && (delete || "WUpdate".equals(job.getElementId())));
            Origin origin = origin(job);
            applied = service.apply(UUID.fromString((String) job.getVariablesAsMap().get("followUpCommandRef")),
                    origin.leader(), job.getProcessInstanceKey(), origin.staff(), delete);
            if (applied.accepted()) {
                Map<String, Object> epoch = delete
                        ? Map.of("activeWorkTaskRef", applied.command().toString(), "pendingWorkReviewRef", "",
                                "staffDecisionRef", applied.command().toString())
                        : Map.of("activeFollowUpVersion", applied.waitId().toString(), "pendingTenantContactFollowUpVersion", "",
                                "pendingReviewFollowUpVersion", "");
                engine.newSetVariablesCommand(origin.staff()).variables(epoch).local(true).send().join();
                service.confirmed(applied.command());
            }
        } catch (RuntimeException exception) {
            boolean rejected = exception instanceof WorkflowRejectedException || exception instanceof IllegalArgumentException;
            client.newFailCommand(job.getKey()).retries(rejected ? 0 : Math.max(1, job.getRetries())).retryBackoff(Duration.ofSeconds(5))
                    .errorMessage(rejected ? "INVALID_FOLLOW_UP_CHANGE" : "FOLLOW_UP_CHANGE_PENDING").send().join();
            return;
        }
        if (!applied.accepted()) {
            client.newThrowErrorCommand(job.getKey()).errorCode(STALE).errorMessage("FOLLOW_UP_BASIS_CHANGED").send().join();
        } else if (applied.delete()) {
            client.newCompleteCommand(job.getKey()).variables(Map.of("outcome", "CONTINUE", "decisionRef", applied.command().toString())).send().join();
        } else {
            client.newCompleteCommand(job.getKey()).variables(Map.of("activeWaitRef", applied.waitId().toString(),
                    "followUpDueAt", applied.due().toString())).send().join();
        }
    }

    @JobWorker(type = CLOSE, autoComplete = false)
    public void close(JobClient client, ActivatedJob job) {
        boolean accepted;
        UUID command;
        try {
            require(CaseWaitWorkers.FOLLOW_UP_PROCESS.equals(job.getBpmnProcessId()) && "WCloseTask".equals(job.getElementId()));
            Origin origin = origin(job);
            command = UUID.fromString((String) job.getVariablesAsMap().get("followUpCommandRef"));
            accepted = service.prepareClosure(command, origin.leader(), job.getProcessInstanceKey(), origin.staff());
        } catch (RuntimeException exception) {
            boolean rejected = exception instanceof WorkflowRejectedException || exception instanceof IllegalArgumentException;
            client.newFailCommand(job.getKey())
                    .retries(rejected ? 0 : Math.max(1, job.getRetries()))
                    .retryBackoff(Duration.ofSeconds(5))
                    .errorMessage(rejected ? "INVALID_FOLLOW_UP_CLOSURE" : "FOLLOW_UP_CLOSURE_PENDING")
                    .send()
                    .join();
            return;
        }
        if (!accepted) {
            client.newThrowErrorCommand(job.getKey())
                    .errorCode(STALE)
                    .errorMessage("FOLLOW_UP_BASIS_CHANGED")
                    .send()
                    .join();
        } else {
            client.newCompleteCommand(job.getKey())
                    .variables(Map.of("outcome", "CLOSE", "decisionRef", command.toString()))
                    .send()
                    .join();
        }
    }

    private Origin origin(ActivatedJob job) {
        ProcessInstance child = engine.newProcessInstanceGetRequest(job.getProcessInstanceKey()).send().join();
        require(child.getParentProcessInstanceKey() != null && child.getParentElementInstanceKey() != null);
        ElementInstance call = engine.newElementInstanceGetRequest(child.getParentElementInstanceKey()).send().join();
        ProcessInstance staff = engine.newProcessInstanceGetRequest(child.getParentProcessInstanceKey()).send().join();
        require("FollowUp".equals(call.getElementId()) && StaffReviewWorkers.STAFF_PROCESS.equals(call.getProcessDefinitionId())
                && child.getParentProcessInstanceKey().equals(call.getProcessInstanceKey()) && job.getTenantId().equals(call.getTenantId())
                && staff.getParentProcessInstanceKey() != null && staff.getParentElementInstanceKey() != null);
        ElementInstance parent = engine.newElementInstanceGetRequest(staff.getParentElementInstanceKey()).send().join();
        require("Staff".equals(parent.getElementId()) && InitialCaseWorkers.CASE_PROCESS.equals(parent.getProcessDefinitionId())
                && staff.getParentProcessInstanceKey().equals(parent.getProcessInstanceKey()) && job.getTenantId().equals(parent.getTenantId()));
        return new Origin(staff.getParentProcessInstanceKey(), child.getParentProcessInstanceKey());
    }

    private record Origin(long leader, long staff) {
    }

    private void require(boolean valid) {
        if (!valid) {
            throw new WorkflowRejectedException("INVALID_FOLLOW_UP_ORIGIN");
        }
    }
}
