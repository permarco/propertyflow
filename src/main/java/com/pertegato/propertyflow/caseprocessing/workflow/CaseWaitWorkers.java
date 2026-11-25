package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.FollowUpBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWaitStore.Wait;
import com.pertegato.propertyflow.caseprocessing.service.CaseWaitService;
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
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.ACTIVE_FOLLOW_UP_VERSION;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.ACTIVE_WAIT_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.CASE_ID;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.CONTACT_MESSAGE_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.DECISION_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.LEADING_PROCESS_INSTANCE_KEY;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.OUTCOME;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.PENDING_REVIEW_FOLLOW_UP_VERSION;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.PHASE_REVIEW_SIGNAL_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.STAFF_DECISION_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.FollowUpBpmnConstants.JobType.FOLLOW_UP_SAVE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.FollowUpBpmnConstants.JobType.FOLLOW_UP_TIMER;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.FollowUpBpmnConstants.Variablen.FOLLOW_UP_DUE_AT;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.FollowUpBpmnConstants.Variablen.WAIT_ACTIVE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants.ErrorCode.STAFF_DECISION_STALE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants.JobType.FOLLOW_UP_STOP_CONTACT;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants.JobType.STAFF_WAIT_EXTERNAL;

@Component
@Profile("propertyflow-backend")
@ConditionalOnProperty(name = "propertyflow.workflow.enabled", havingValue = "true")
@RequiredArgsConstructor
public class CaseWaitWorkers {

    private final CamundaClient engine;
    private final CaseWaitService service;

    @JobWorker(type = FOLLOW_UP_SAVE, autoComplete = false)
    public void save(JobClient client, ActivatedJob job) {
        Wait wait;
        try {
            ProcessInstance child = followUp(job, FollowUpBpmnConstants.ServiceTask.SAVE_FOLLOW_UP);
            wait = service.enter(text(job, CASE_ID), leader(job), child.getParentProcessInstanceKey(), job.getProcessInstanceKey(),
                    child.getParentElementInstanceKey(), UUID.fromString(text(job, STAFF_DECISION_REF)), true);
        } catch (RuntimeException exception) {
            fail(client, job, exception);
            return;
        }
        client.newCompleteCommand(job.getKey()).variables(Map.of(FOLLOW_UP_DUE_AT, wait.dueAt().toString(),
                ACTIVE_WAIT_REF, wait.id().toString(), WAIT_ACTIVE, "ACTIVE".equals(wait.state()))).send().join();
    }

    @JobWorker(type = STAFF_WAIT_EXTERNAL, autoComplete = false)
    public void external(JobClient client, ActivatedJob job) {
        try {
            require(StaffProcessingBpmnConstants.PROCESS_ID.equals(job.getBpmnProcessId())
                    && StaffProcessingBpmnConstants.ServiceTask.START_EXTERNAL_WAIT.equals(job.getElementId()));
            verifyStaff(job.getProcessInstanceKey(), job);
            service.enter(text(job, CASE_ID), leader(job), job.getProcessInstanceKey(), null, null,
                    UUID.fromString(text(job, STAFF_DECISION_REF)), false);
        } catch (RuntimeException exception) {
            fail(client, job, exception);
            return;
        }
        client.newCompleteCommand(job.getKey()).send().join();
    }

    @JobWorker(type = FOLLOW_UP_TIMER, autoComplete = false)
    public void timer(JobClient client, ActivatedJob job) {
        String result;
        try {
            followUp(job, FollowUpBpmnConstants.ServiceTask.RESOLVE_DUE_TIMER);
            UUID waitId = UUID.fromString(text(job, ACTIVE_WAIT_REF));
            result = service.timer(text(job, CASE_ID), leader(job), job.getProcessInstanceKey(), waitId);
        } catch (RuntimeException exception) {
            fail(client, job, exception);
            return;
        }
        client.newCompleteCommand(job.getKey())
                .variables(Map.of(OUTCOME, result, DECISION_REF, text(job, ACTIVE_WAIT_REF)))
                .send()
                .join();
    }

    @JobWorker(type = FOLLOW_UP_STOP_CONTACT, autoComplete = false)
    public void stopContact(JobClient client, ActivatedJob job) {
        String result;
        try {
            require(StaffProcessingBpmnConstants.PROCESS_ID.equals(job.getBpmnProcessId())
                    && StaffProcessingBpmnConstants.ServiceTask.STOP_FOLLOW_UP.equals(job.getElementId()));
            verifyStaff(job.getProcessInstanceKey(), job);
            UUID waitId = UUID.fromString(text(job, ACTIVE_FOLLOW_UP_VERSION));
            boolean review = waitId.toString().equals(job.getVariablesAsMap().get(PENDING_REVIEW_FOLLOW_UP_VERSION));
            result = review
                    ? service.reviewNeeded(text(job, CASE_ID), leader(job), job.getProcessInstanceKey(), waitId,
                            UUID.fromString(text(job, PHASE_REVIEW_SIGNAL_REF)))
                    : service.contact(text(job, CASE_ID), leader(job), job.getProcessInstanceKey(), waitId,
                            UUID.fromString(text(job, CONTACT_MESSAGE_REF)));
            require("REVIEW".equals(result));
        } catch (RuntimeException exception) {
            fail(client, job, exception);
            return;
        }
        client.newCompleteCommand(job.getKey()).send().join();
    }

    private ProcessInstance followUp(ActivatedJob job, String element) {
        require(FollowUpBpmnConstants.PROCESS_ID.equals(job.getBpmnProcessId()) && element.equals(job.getElementId()));
        ProcessInstance child = engine.newProcessInstanceGetRequest(job.getProcessInstanceKey()).send().join();
        require(child.getParentProcessInstanceKey() != null && child.getParentElementInstanceKey() != null);
        ElementInstance call = engine.newElementInstanceGetRequest(child.getParentElementInstanceKey()).send().join();
        require(StaffProcessingBpmnConstants.CallActivity.FOLLOW_UP_CALL.equals(call.getElementId())
                && StaffProcessingBpmnConstants.PROCESS_ID.equals(call.getProcessDefinitionId())
                && child.getParentProcessInstanceKey().equals(call.getProcessInstanceKey()) && job.getTenantId().equals(call.getTenantId()));
        verifyStaff(child.getParentProcessInstanceKey(), job);
        return child;
    }

    private void verifyStaff(long key, ActivatedJob job) {
        ProcessInstance staff = engine.newProcessInstanceGetRequest(key).send().join();
        require(StaffProcessingBpmnConstants.PROCESS_ID.equals(staff.getProcessDefinitionId())
                && Long.valueOf(leader(job)).equals(staff.getParentProcessInstanceKey())
                && job.getTenantId().equals(staff.getTenantId()) && staff.getParentElementInstanceKey() != null);
        ElementInstance call = engine.newElementInstanceGetRequest(staff.getParentElementInstanceKey()).send().join();
        require(CaseProcessBpmnConstants.CallActivity.STAFF_PROCESSING_CALL.equals(call.getElementId())
                && CaseProcessBpmnConstants.PROCESS_ID.equals(call.getProcessDefinitionId())
                && Long.valueOf(leader(job)).equals(call.getProcessInstanceKey()) && job.getTenantId().equals(call.getTenantId()));
    }

    private long leader(ActivatedJob job) {
        return Long.parseLong(text(job, LEADING_PROCESS_INSTANCE_KEY));
    }

    private String text(ActivatedJob job, String name) {
        Object value = job.getVariablesAsMap().get(name);
        require(value instanceof String && !((String) value).isBlank());
        return (String) value;
    }

    private void fail(JobClient client, ActivatedJob job, RuntimeException exception) {
        if (exception instanceof WorkflowRejectedException && "STAFF_WAIT_BASIS_STALE".equals(exception.getMessage())) {
            client.newThrowErrorCommand(job.getKey()).errorCode(STAFF_DECISION_STALE).errorMessage("WAIT_BASIS_CHANGED").send().join();
            return;
        }
        boolean rejected = exception instanceof WorkflowRejectedException || exception instanceof IllegalArgumentException;
        client.newFailCommand(job.getKey()).retries(rejected ? 0 : Math.max(1, job.getRetries())).retryBackoff(Duration.ofSeconds(5))
                .errorMessage(rejected ? "INVALID_WAIT_CONTEXT" : "WAIT_TEMPORARILY_UNAVAILABLE").send().join();
    }

    private void require(boolean valid) {
        if (!valid) {
            throw new WorkflowRejectedException("INVALID_WAIT_ORIGIN");
        }
    }
}
