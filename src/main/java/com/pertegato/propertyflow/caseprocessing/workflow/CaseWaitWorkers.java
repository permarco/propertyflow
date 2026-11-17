package com.pertegato.propertyflow.caseprocessing.workflow;

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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("propertyflow-backend & !frontend-demo")
@ConditionalOnProperty(name = "propertyflow.workflow.enabled", havingValue = "true")
public class CaseWaitWorkers {

    public static final String FOLLOW_UP_PROCESS = "PropertyFlow_FollowUp_Draft";
    public static final String SAVE = "propertyflow.follow-up.save";
    public static final String TIMER = "propertyflow.follow-up.timer";
    public static final String CONTACT = "propertyflow.follow-up.contact";
    public static final String EXTERNAL = "propertyflow.staff.wait-external";
    public static final String STOP = "propertyflow.follow-up.stop-contact";
    private final CamundaClient engine;
    private final CaseWaitService service;

    public CaseWaitWorkers(CamundaClient engine, CaseWaitService service) {
        this.engine = engine;
        this.service = service;
    }

    @JobWorker(type = SAVE, autoComplete = false)
    public void save(JobClient client, ActivatedJob job) {
        Wait wait;
        try {
            ProcessInstance child = followUp(job, "WSave");
            wait = service.enter(text(job, "caseId"), leader(job), child.getParentProcessInstanceKey(), job.getProcessInstanceKey(),
                    child.getParentElementInstanceKey(), UUID.fromString(text(job, "staffDecisionRef")), true);
        } catch (RuntimeException exception) {
            fail(client, job, exception);
            return;
        }
        client.newCompleteCommand(job.getKey()).variables(Map.of("followUpDueAt", wait.dueAt().toString(),
                "activeWaitRef", wait.id().toString(), "waitActive", "ACTIVE".equals(wait.state()))).send().join();
    }

    @JobWorker(type = EXTERNAL, autoComplete = false)
    public void external(JobClient client, ActivatedJob job) {
        try {
            require(StaffReviewWorkers.STAFF_PROCESS.equals(job.getBpmnProcessId()) && "SExternalStatus".equals(job.getElementId()));
            verifyStaff(job.getProcessInstanceKey(), job);
            service.enter(text(job, "caseId"), leader(job), job.getProcessInstanceKey(), null, null,
                    UUID.fromString(text(job, "staffDecisionRef")), false);
        } catch (RuntimeException exception) {
            fail(client, job, exception);
            return;
        }
        client.newCompleteCommand(job.getKey()).send().join();
    }

    @JobWorker(type = TIMER, autoComplete = false)
    public void timer(JobClient client, ActivatedJob job) {
        resolve(client, job, false);
    }

    @JobWorker(type = STOP, autoComplete = false)
    public void stopContact(JobClient client, ActivatedJob job) {
        String result;
        try {
            require(StaffReviewWorkers.STAFF_PROCESS.equals(job.getBpmnProcessId()) && "SStopFollowUp".equals(job.getElementId()));
            verifyStaff(job.getProcessInstanceKey(), job);
            UUID waitId = UUID.fromString(text(job, "activeFollowUpVersion"));
            boolean review = waitId.toString().equals(job.getVariablesAsMap().get("pendingReviewFollowUpVersion"));
            result = review
                    ? service.reviewNeeded(text(job, "caseId"), leader(job), job.getProcessInstanceKey(), waitId,
                            UUID.fromString(text(job, "phaseReviewSignalRef")))
                    : service.contact(text(job, "caseId"), leader(job), job.getProcessInstanceKey(), waitId,
                            UUID.fromString(text(job, "contactMessageRef")));
            require("REVIEW".equals(result));
        } catch (RuntimeException exception) {
            fail(client, job, exception);
            return;
        }
        client.newCompleteCommand(job.getKey()).send().join();
    }

    @JobWorker(type = CONTACT, autoComplete = false)
    public void contact(JobClient client, ActivatedJob job) {
        resolve(client, job, true);
    }

    private void resolve(JobClient client, ActivatedJob job, boolean contact) {
        String result;
        try {
            ProcessInstance child = followUp(job, contact ? "WClearContact" : "WClearTimer");
            UUID waitId = UUID.fromString(text(job, "activeWaitRef"));
            result = contact
                    ? service.contact(text(job, "caseId"), leader(job), child.getParentProcessInstanceKey(), waitId,
                            UUID.fromString(text(job, "contactMessageRef")))
                    : service.timer(text(job, "caseId"), leader(job), job.getProcessInstanceKey(), waitId);
        } catch (RuntimeException exception) {
            fail(client, job, exception);
            return;
        }
        client.newCompleteCommand(job.getKey()).variables(Map.of("outcome", result, "decisionRef", text(job, "activeWaitRef"))).send().join();
    }

    private ProcessInstance followUp(ActivatedJob job, String element) {
        require(FOLLOW_UP_PROCESS.equals(job.getBpmnProcessId()) && element.equals(job.getElementId()));
        ProcessInstance child = engine.newProcessInstanceGetRequest(job.getProcessInstanceKey()).send().join();
        require(child.getParentProcessInstanceKey() != null && child.getParentElementInstanceKey() != null);
        ElementInstance call = engine.newElementInstanceGetRequest(child.getParentElementInstanceKey()).send().join();
        require("FollowUp".equals(call.getElementId()) && StaffReviewWorkers.STAFF_PROCESS.equals(call.getProcessDefinitionId())
                && child.getParentProcessInstanceKey().equals(call.getProcessInstanceKey()) && job.getTenantId().equals(call.getTenantId()));
        verifyStaff(child.getParentProcessInstanceKey(), job);
        return child;
    }

    private void verifyStaff(long key, ActivatedJob job) {
        ProcessInstance staff = engine.newProcessInstanceGetRequest(key).send().join();
        require(StaffReviewWorkers.STAFF_PROCESS.equals(staff.getProcessDefinitionId()) && Long.valueOf(leader(job)).equals(staff.getParentProcessInstanceKey())
                && job.getTenantId().equals(staff.getTenantId()) && staff.getParentElementInstanceKey() != null);
        ElementInstance call = engine.newElementInstanceGetRequest(staff.getParentElementInstanceKey()).send().join();
        require("Staff".equals(call.getElementId()) && InitialCaseWorkers.CASE_PROCESS.equals(call.getProcessDefinitionId())
                && Long.valueOf(leader(job)).equals(call.getProcessInstanceKey()) && job.getTenantId().equals(call.getTenantId()));
    }

    private long leader(ActivatedJob job) {
        return Long.parseLong(text(job, "leadingProcessInstanceKey"));
    }

    private String text(ActivatedJob job, String name) {
        Object value = job.getVariablesAsMap().get(name);
        require(value instanceof String && !((String) value).isBlank());
        return (String) value;
    }

    private void fail(JobClient client, ActivatedJob job, RuntimeException exception) {
        if (exception instanceof WorkflowRejectedException && "STAFF_WAIT_BASIS_STALE".equals(exception.getMessage())) {
            client.newThrowErrorCommand(job.getKey()).errorCode(StaffDecisionWorkers.STALE_DECISION).errorMessage("WAIT_BASIS_CHANGED").send().join();
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
