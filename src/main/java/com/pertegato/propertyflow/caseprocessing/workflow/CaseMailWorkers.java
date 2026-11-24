package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseNotificationBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.service.CaseMailService;
import com.pertegato.propertyflow.caseprocessing.service.CaseMailService.MailOutcome;
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
import org.springframework.stereotype.Component;

import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseNotificationBpmnConstants.ErrorCode.MAIL_CLARIFICATION_REQUIRED;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseNotificationBpmnConstants.JobType.MAIL_PREPARE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseNotificationBpmnConstants.JobType.MAIL_RECORD_PROBLEM;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseNotificationBpmnConstants.JobType.MAIL_SEND;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseNotificationBpmnConstants.Variablen.MAIL_ORDER_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseNotificationBpmnConstants.Variablen.MAIL_ROUTE;

@Component
@Profile("propertyflow-backend & !frontend-demo")
@ConditionalOnProperty(name = {"propertyflow.workflow.enabled", "propertyflow.case-mail.enabled"}, havingValue = "true")
@RequiredArgsConstructor
public class CaseMailWorkers {

    private final CamundaClient camunda;
    private final CaseMailService service;

    @JobWorker(type = MAIL_PREPARE, autoComplete = false)
    public void prepare(JobClient client, ActivatedJob job) {
        try {
            UUID id = verify(job, CaseNotificationBpmnConstants.ServiceTask.PREPARE_MAIL);
            String route = service.prepare(id, job.getProcessInstanceKey(), job.getProcessDefinitionKey(), job.getTenantId());
            client.newCompleteCommand(job.getKey()).variables(Map.of(MAIL_ROUTE, route)).send().join();
        } catch (RuntimeException exception) {
            fail(client, job, exception);
        }
    }

    @JobWorker(type = MAIL_SEND, autoComplete = false)
    public void send(JobClient client, ActivatedJob job) {
        try {
            UUID id = verify(job, CaseNotificationBpmnConstants.SendTask.SEND_STATUS_MAIL,
                    CaseNotificationBpmnConstants.SendTask.SEND_ACCEPTANCE_OR_MESSAGE_MAIL);
            MailOutcome outcome = service.send(id, job.getProcessInstanceKey());
            switch (outcome) {
                case SENT -> client.newCompleteCommand(job.getKey()).send().join();
                case REVIEW -> client.newThrowErrorCommand(job.getKey()).errorCode(MAIL_CLARIFICATION_REQUIRED).send().join();
                case RETRY, BUSY -> client.newFailCommand(job.getKey()).retries(Math.max(1, job.getRetries()))
                        .retryBackoff(Duration.ofSeconds(30)).errorMessage("MAIL_RETRY_PENDING").send().join();
            }
        } catch (RuntimeException exception) {
            fail(client, job, exception);
        }
    }

    @JobWorker(type = MAIL_RECORD_PROBLEM, autoComplete = false)
    public void recordProblem(JobClient client, ActivatedJob job) {
        try {
            UUID id = verify(job, CaseNotificationBpmnConstants.ServiceTask.RECORD_MAIL_PROBLEM);
            service.recordProblem(id, job.getProcessInstanceKey());
            client.newCompleteCommand(job.getKey()).send().join();
        } catch (RuntimeException exception) {
            fail(client, job, exception);
        }
    }

    private UUID verify(ActivatedJob job, String... allowedElements) {
        boolean allowed = java.util.Arrays.asList(allowedElements).contains(job.getElementId());
        Object reference = job.getVariablesAsMap().get(MAIL_ORDER_REF);
        if (!allowed || !CaseNotificationBpmnConstants.PROCESS_ID.equals(job.getBpmnProcessId())
                || !(reference instanceof String)) {
            throw new WorkflowRejectedException("INVALID_MAIL_JOB_ORIGIN");
        }
        UUID id = UUID.fromString((String) reference);
        ProcessInstance instance = camunda.newProcessInstanceGetRequest(job.getProcessInstanceKey()).send().join();
        if (instance.getParentProcessInstanceKey() != null || !id.toString().equals(instance.getBusinessId())) {
            throw new WorkflowRejectedException("INVALID_MAIL_START_IDENTITY");
        }
        return id;
    }

    private void fail(JobClient client, ActivatedJob job, RuntimeException exception) {
        boolean rejected = exception instanceof WorkflowRejectedException || exception instanceof IllegalArgumentException;
        client.newFailCommand(job.getKey()).retries(rejected ? 0 : Math.max(1, job.getRetries()))
                .retryBackoff(Duration.ofSeconds(5)).errorMessage(rejected ? "MAIL_CONTRACT_REJECTED" : "MAIL_TEMPORARILY_UNAVAILABLE")
                .send().join();
    }
}
