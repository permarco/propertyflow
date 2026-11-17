package com.pertegato.propertyflow.caseprocessing.workflow;

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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("propertyflow-backend & !frontend-demo")
@ConditionalOnProperty(name = {"propertyflow.workflow.enabled", "propertyflow.case-mail.enabled"}, havingValue = "true")
public class CaseMailWorkers {

    public static final String PROCESS_ID = "PropertyFlow_Mail_Draft";
    private static final String REVIEW_ERROR = "MAIL_CLARIFICATION_REQUIRED";
    private final CamundaClient camunda;
    private final CaseMailService service;

    public CaseMailWorkers(CamundaClient camunda, CaseMailService service) {
        this.camunda = camunda;
        this.service = service;
    }

    @JobWorker(type = "propertyflow.mail.prepare", autoComplete = false)
    public void prepare(JobClient client, ActivatedJob job) {
        try {
            UUID id = verify(job, "MailLoad");
            String route = service.prepare(id, job.getProcessInstanceKey(), job.getProcessDefinitionKey(), job.getTenantId());
            client.newCompleteCommand(job.getKey()).variables(Map.of("mailRoute", route)).send().join();
        } catch (RuntimeException exception) {
            fail(client, job, exception);
        }
    }

    @JobWorker(type = "propertyflow.mail.send", autoComplete = false)
    public void send(JobClient client, ActivatedJob job) {
        try {
            UUID id = verify(job, "MailSendChange", "MailSendMessage");
            MailOutcome outcome = service.send(id, job.getProcessInstanceKey());
            switch (outcome) {
                case SENT -> client.newCompleteCommand(job.getKey()).send().join();
                case REVIEW -> client.newThrowErrorCommand(job.getKey()).errorCode(REVIEW_ERROR).send().join();
                case RETRY, BUSY -> client.newFailCommand(job.getKey()).retries(Math.max(1, job.getRetries()))
                        .retryBackoff(Duration.ofSeconds(30)).errorMessage("MAIL_RETRY_PENDING").send().join();
            }
        } catch (RuntimeException exception) {
            fail(client, job, exception);
        }
    }

    @JobWorker(type = "propertyflow.mail.record-problem", autoComplete = false)
    public void recordProblem(JobClient client, ActivatedJob job) {
        try {
            UUID id = verify(job, "MailProblem");
            service.recordProblem(id, job.getProcessInstanceKey());
            client.newCompleteCommand(job.getKey()).send().join();
        } catch (RuntimeException exception) {
            fail(client, job, exception);
        }
    }

    private UUID verify(ActivatedJob job, String... allowedElements) {
        boolean allowed = java.util.Arrays.asList(allowedElements).contains(job.getElementId());
        Object reference = job.getVariablesAsMap().get("mailOrderRef");
        if (!allowed || !PROCESS_ID.equals(job.getBpmnProcessId()) || !(reference instanceof String)) {
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
