package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.service.StaffMessageService;
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
public class StaffMessageWorkers {

    public static final String PUBLISH = "propertyflow.staff-message.publish";
    private final CamundaClient engine;
    private final StaffMessageService service;

    public StaffMessageWorkers(CamundaClient engine, StaffMessageService service) {
        this.engine = engine;
        this.service = service;
    }

    @JobWorker(type = PUBLISH, autoComplete = false)
    public void publish(JobClient client, ActivatedJob job) {
        String outcome;
        try {
            ProcessInstance process = engine.newProcessInstanceGetRequest(job.getProcessInstanceKey()).send().join();
            long leader;
            if (InitialCaseWorkers.CASE_PROCESS.equals(job.getBpmnProcessId()) && "StaffQuestionPublish".equals(job.getElementId())) {
                require(process.getParentProcessInstanceKey() == null);
                leader = job.getProcessInstanceKey();
            } else if (TenantReplyWorkers.PROCESS.equals(job.getBpmnProcessId()) && "RStaffMessagePublish".equals(job.getElementId())) {
                require(process.getParentProcessInstanceKey() != null && process.getParentElementInstanceKey() != null);
                ElementInstance call = engine.newElementInstanceGetRequest(process.getParentElementInstanceKey()).send().join();
                require("Reply".equals(call.getElementId()) && InitialCaseWorkers.CASE_PROCESS.equals(call.getProcessDefinitionId())
                        && process.getParentProcessInstanceKey().equals(call.getProcessInstanceKey()) && job.getTenantId().equals(call.getTenantId()));
                leader = process.getParentProcessInstanceKey();
            } else if (CaseWaitWorkers.FOLLOW_UP_PROCESS.equals(job.getBpmnProcessId()) && "WPublish".equals(job.getElementId())) {
                require(process.getParentProcessInstanceKey() != null && process.getParentElementInstanceKey() != null);
                ElementInstance call = engine.newElementInstanceGetRequest(process.getParentElementInstanceKey()).send().join();
                ProcessInstance staff = engine.newProcessInstanceGetRequest(process.getParentProcessInstanceKey()).send().join();
                require("FollowUp".equals(call.getElementId()) && StaffReviewWorkers.STAFF_PROCESS.equals(call.getProcessDefinitionId())
                        && process.getParentProcessInstanceKey().equals(call.getProcessInstanceKey()) && job.getTenantId().equals(call.getTenantId())
                        && staff.getParentProcessInstanceKey() != null && staff.getParentElementInstanceKey() != null
                        && job.getTenantId().equals(staff.getTenantId()));
                ElementInstance parent = engine.newElementInstanceGetRequest(staff.getParentElementInstanceKey()).send().join();
                require("Staff".equals(parent.getElementId()) && InitialCaseWorkers.CASE_PROCESS.equals(parent.getProcessDefinitionId())
                        && staff.getParentProcessInstanceKey().equals(parent.getProcessInstanceKey()) && job.getTenantId().equals(parent.getTenantId()));
                leader = staff.getParentProcessInstanceKey();
            } else {
                require(StaffReviewWorkers.STAFF_PROCESS.equals(job.getBpmnProcessId()) && "SMessagePublish".equals(job.getElementId())
                        && process.getParentProcessInstanceKey() != null && process.getParentElementInstanceKey() != null);
                ElementInstance call = engine.newElementInstanceGetRequest(process.getParentElementInstanceKey()).send().join();
                require("Staff".equals(call.getElementId()) && InitialCaseWorkers.CASE_PROCESS.equals(call.getProcessDefinitionId())
                        && process.getParentProcessInstanceKey().equals(call.getProcessInstanceKey()) && job.getTenantId().equals(call.getTenantId()));
                leader = process.getParentProcessInstanceKey();
            }
            UUID command = UUID.fromString((String) job.getVariablesAsMap().get("staffMessageCommandRef"));
            String result = service.publish(command, leader, job.getProcessInstanceKey(), job.getTenantId());
            outcome = "PUBLISHED".equals(result) ? service.interruptsAutomatic(command) ? "QUESTION" : "INFORMATION" : "REJECTED";
        } catch (RuntimeException exception) {
            boolean rejected = exception instanceof WorkflowRejectedException || exception instanceof IllegalArgumentException;
            client.newFailCommand(job.getKey())
                    .retries(rejected ? 0 : Math.max(1, job.getRetries()))
                    .retryBackoff(Duration.ofSeconds(5))
                    .errorMessage(rejected ? "STAFF_MESSAGE_CONTRACT_REJECTED" : "STAFF_MESSAGE_TEMPORARILY_UNAVAILABLE")
                    .send()
                    .join();
            return;
        }
        client.newCompleteCommand(job.getKey()).variables(Map.of("staffMessageOutcome", outcome)).send().join();
    }

    @JobWorker(type = "propertyflow.staff-question.publish", autoComplete = false)
    public void question(JobClient client, ActivatedJob job) {
        UUID command;
        String result;
        try {
            require(StaffReviewWorkers.STAFF_PROCESS.equals(job.getBpmnProcessId()) && "SQuestion".equals(job.getElementId()));
            ProcessInstance child = engine.newProcessInstanceGetRequest(job.getProcessInstanceKey()).send().join();
            require(child.getParentProcessInstanceKey() != null && child.getParentElementInstanceKey() != null);
            ElementInstance call = engine.newElementInstanceGetRequest(child.getParentElementInstanceKey()).send().join();
            require("Staff".equals(call.getElementId()) && InitialCaseWorkers.CASE_PROCESS.equals(call.getProcessDefinitionId())
                    && child.getParentProcessInstanceKey().equals(call.getProcessInstanceKey()) && job.getTenantId().equals(call.getTenantId()));
            command = service.questionCommand(UUID.fromString((String) job.getVariablesAsMap().get("staffDecisionRef")));
            result = service.publishQuestion(command, child.getParentProcessInstanceKey(), job.getProcessInstanceKey(), job.getTenantId());
        } catch (RuntimeException exception) {
            boolean rejected = exception instanceof WorkflowRejectedException || exception instanceof IllegalArgumentException;
            client.newFailCommand(job.getKey()).retries(rejected ? 0 : Math.max(1, job.getRetries())).retryBackoff(Duration.ofSeconds(5))
                    .errorMessage(rejected ? "INVALID_QUESTION_ORIGIN" : "QUESTION_TEMPORARILY_UNAVAILABLE").send().join();
            return;
        }
        if ("PUBLISHED".equals(result)) {
            client.newCompleteCommand(job.getKey()).variables(Map.of("outcome", "WAIT_FOR_REPLY", "decisionRef", command.toString())).send().join();
        } else {
            client.newThrowErrorCommand(job.getKey()).errorCode(StaffDecisionWorkers.STALE_DECISION)
                    .errorMessage("QUESTION_BASIS_CHANGED").send().join();
        }
    }

    private void require(boolean valid) {
        if (!valid) {
            throw new WorkflowRejectedException("INVALID_STAFF_MESSAGE_ORIGIN");
        }
    }
}
