package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.Entry;
import com.pertegato.propertyflow.caseprocessing.service.AutomaticClosureService;
import com.pertegato.propertyflow.caseprocessing.service.AutomaticQuestionService.Result;
import com.pertegato.propertyflow.caseprocessing.service.AutomaticQuestionService;
import com.pertegato.propertyflow.caseprocessing.service.WorkflowRejectedException;
import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.worker.JobClient;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("propertyflow-backend & !frontend-demo")
@ConditionalOnProperty(name = "propertyflow.workflow.enabled", havingValue = "true")
public class AutomaticQuestionWorkers {

    public static final String VALIDATE = "propertyflow.automatic.validate-questions";
    public static final String PUBLISH = "propertyflow.automatic.publish-questions";
    public static final String FINISH = "propertyflow.automatic.prepare-result";
    public static final String ACCEPT = "propertyflow.automatic.accept-result";
    public static final String CLOSURE_MESSAGE = "propertyflow.automatic.publish-closure";
    public static final String CLOSE = "propertyflow.automatic.close";
    public static final String CLOSED = "propertyflow.automatic.confirm-closure";
    private final CamundaClient engine;
    private final AutomaticQuestionService service;
    private final AutomaticClosureService closures;

    public AutomaticQuestionWorkers(CamundaClient engine, AutomaticQuestionService service, AutomaticClosureService closures) {
        this.engine = engine;
        this.service = service;
        this.closures = closures;
    }

    @JobWorker(type = VALIDATE, autoComplete = false)
    public void validate(JobClient client, ActivatedJob job) {
        Result result;
        try {
            long leader = child(job, Set.of("Validate"));
            result = service.validate(text(job, "caseId"), uuid(job, "entryContextRef"), uuid(job, "automaticCallRef"),
                    leader, job.getProcessInstanceKey(), uuid(job, "evaluationRef"));
        } catch (RuntimeException exception) {
            fail(client, job, exception);
            return;
        }
        if (!defer(client, job, result, "ANALYSIS_BASIS_STALE")) {
            client.newCompleteCommand(job.getKey())
                    .variables(Map.of("automaticAction", result.state(),
                            "CLOSE".equals(result.state()) ? "automaticClosureRef" : "questionBatchRef", result.reference().toString()))
                    .send()
                    .join();
        }
    }

    @JobWorker(type = PUBLISH, autoComplete = false)
    public void publish(JobClient client, ActivatedJob job) {
        Result result;
        try {
            long leader = child(job, Set.of("Question"));
            result = service.publish(uuid(job, "questionBatchRef"), leader, job.getProcessInstanceKey());
        } catch (RuntimeException exception) {
            fail(client, job, exception);
            return;
        }
        if (!defer(client, job, result, "PUBLICATION_BASIS_STALE")) {
            client.newCompleteCommand(job.getKey()).send().join();
        }
    }

    @JobWorker(type = FINISH, autoComplete = false)
    public void finish(JobClient client, ActivatedJob job) {
        Result result;
        try {
            long leader = child(job, Set.of("AWait", "AReview"));
            result = "CLOSE".equals(text(job, "automaticAction"))
                    ? closures.finish(uuid(job, "automaticClosureRef"), leader, job.getProcessInstanceKey())
                    : service.finish(uuid(job, "questionBatchRef"), leader, job.getProcessInstanceKey());
        } catch (RuntimeException exception) {
            fail(client, job, exception);
            return;
        }
        if (!defer(client, job, result, "ANALYSIS_BASIS_STALE")) {
            client.newCompleteCommand(job.getKey())
                    .variables(Map.of("outcome", result.state(), "decisionRef",
                            text(job, "CLOSE".equals(text(job, "automaticAction")) ? "automaticClosureRef" : "questionBatchRef")))
                    .send()
                    .join();
        }
    }

    @JobWorker(type = ACCEPT, autoComplete = false)
    public void accept(JobClient client, ActivatedJob job) {
        Result result;
        try {
            require(InitialCaseWorkers.CASE_PROCESS.equals(job.getBpmnProcessId()) && "AutomaticResult".equals(job.getElementId()));
            require(Set.of("WAIT_FOR_REPLY", "REVIEW", "CLOSED").contains(text(job, "automaticOutcome")));
            ProcessInstance root = engine.newProcessInstanceGetRequest(job.getProcessInstanceKey()).send().join();
            require(root.getParentProcessInstanceKey() == null);
            UUID batch = uuid(job, "automaticDecisionRef");
            boolean closure = closures.owns(batch);
            Entry source = closure ? closures.source(batch, text(job, "caseId"), job.getProcessInstanceKey())
                    : service.source(batch, text(job, "caseId"), job.getProcessInstanceKey());
            ProcessInstance automatic = engine.newProcessInstanceGetRequest(source.childKey()).send().join();
            ElementInstance call = engine.newElementInstanceGetRequest(source.parentElementKey()).send().join();
            require(InitialCaseWorkers.AUTOMATIC_PROCESS.equals(automatic.getProcessDefinitionId())
                    && Long.valueOf(job.getProcessInstanceKey()).equals(automatic.getParentProcessInstanceKey())
                    && source.parentElementKey().equals(automatic.getParentElementInstanceKey())
                    && job.getTenantId().equals(automatic.getTenantId()) && "Automatic".equals(call.getElementId())
                    && Long.valueOf(job.getProcessInstanceKey()).equals(call.getProcessInstanceKey())
                    && job.getTenantId().equals(call.getTenantId()));
            if (!"COMPLETED".equals(automatic.getState().name()) || !"COMPLETED".equals(call.getState().name())) {
                throw new IllegalStateException("AUTOMATIC_COMPLETION_NOT_YET_VISIBLE");
            }
            result = closure ? closures.finish(batch, job.getProcessInstanceKey(), source.childKey())
                    : service.finish(batch, job.getProcessInstanceKey(), source.childKey());
            require(result.state().equals(text(job, "automaticOutcome")));
        } catch (RuntimeException exception) {
            fail(client, job, exception);
            return;
        }
        if (!defer(client, job, result, "ANALYSIS_BASIS_STALE")) {
            if ("CLOSED".equals(result.state())) {
                client.newCompleteCommand(job.getKey())
                        .variables(Map.of("automaticOutcome", "CLOSED"))
                        .send()
                        .join();
                return;
            }
            client.newCompleteCommand(job.getKey())
                    .variables(Map.of("automaticOutcome", result.state(),
                            "REVIEW".equals(result.state()) ? "staffEntryContextRef" : "replyEntryContextRef", result.reference().toString()))
                    .send()
                    .join();
        }
    }

    @JobWorker(type = CLOSURE_MESSAGE, autoComplete = false)
    public void closureMessage(JobClient client, ActivatedJob job) {
        closure(client, job, "AutoAnswer");
    }

    @JobWorker(type = CLOSE, autoComplete = false)
    public void close(JobClient client, ActivatedJob job) {
        closure(client, job, "AutoClose");
    }

    @JobWorker(type = CLOSED, autoComplete = false)
    public void closed(JobClient client, ActivatedJob job) {
        closure(client, job, "AClosed");
    }

    private void closure(JobClient client, ActivatedJob job, String element) {
        Result result;
        try {
            long leader = child(job, Set.of(element));
            UUID reference = uuid(job, "automaticClosureRef");
            require("CLOSE".equals(text(job, "automaticAction")));
            Entry source = closures.source(reference, text(job, "caseId"), leader);
            require(source.id().equals(uuid(job, "entryContextRef")) && source.callRef().equals(uuid(job, "automaticCallRef")));
            result = switch (element) {
                case "AutoAnswer" -> closures.publish(reference, leader, job.getProcessInstanceKey());
                case "AutoClose" -> closures.close(reference, leader, job.getProcessInstanceKey());
                default -> closures.finish(reference, leader, job.getProcessInstanceKey());
            };
            if ("AClosed".equals(element)) {
                require("CLOSED".equals(result.state()));
            }
        } catch (RuntimeException exception) {
            boolean invalid = exception instanceof WorkflowRejectedException || exception instanceof IllegalArgumentException;
            client.newFailCommand(job.getKey())
                    .retries(invalid ? 0 : Math.max(0, job.getRetries() - 1))
                    .retryBackoff(Duration.ofSeconds(5))
                    .errorMessage(invalid ? "INVALID_CLOSURE_ORIGIN" : "CLOSURE_TECHNICAL_FAILURE")
                    .send()
                    .join();
            return;
        }
        if ("REVIEW".equals(result.state())) {
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("CLOSURE_MESSAGE_INVALID")
                    .send()
                    .join();
        } else if ("BUSY".equals(result.state())) {
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries())
                    .retryBackoff(Duration.ofSeconds(5))
                    .errorMessage("CLOSURE_WORK_PENDING")
                    .send()
                    .join();
        } else if (!defer(client, job, result, "AutoClose".equals(element) ? "AUTO_CLOSURE_STALE" : "PUBLICATION_BASIS_STALE")) {
            client.newCompleteCommand(job.getKey())
                    .variables("AClosed".equals(element)
                            ? Map.of("outcome", "CLOSED", "decisionRef", result.reference().toString()) : Map.of())
                    .send()
                    .join();
        }
    }

    private long child(ActivatedJob job, Set<String> elements) {
        require(InitialCaseWorkers.AUTOMATIC_PROCESS.equals(job.getBpmnProcessId()) && elements.contains(job.getElementId()));
        ProcessInstance process = engine.newProcessInstanceGetRequest(job.getProcessInstanceKey()).send().join();
        require(process.getParentProcessInstanceKey() != null && process.getParentElementInstanceKey() != null
                && Long.toString(process.getParentProcessInstanceKey()).equals(text(job, "leadingProcessInstanceKey"))
                && job.getTenantId().equals(process.getTenantId()));
        ElementInstance call = engine.newElementInstanceGetRequest(process.getParentElementInstanceKey()).send().join();
        require("Automatic".equals(call.getElementId()) && InitialCaseWorkers.CASE_PROCESS.equals(call.getProcessDefinitionId())
                && process.getParentProcessInstanceKey().equals(call.getProcessInstanceKey()) && job.getTenantId().equals(call.getTenantId()));
        return process.getParentProcessInstanceKey();
    }

    private boolean defer(JobClient client, ActivatedJob job, Result result, String error) {
        if ("STALE".equals(result.state())) {
            client.newThrowErrorCommand(job.getKey()).errorCode(error).errorMessage("AUTOMATIC_QUESTION_BASIS_CHANGED").send().join();
            return true;
        }
        if ("OBSOLETE".equals(result.state())) {
            fail(client, job, new IllegalStateException("AUTOMATIC_PATH_SUPERSEDED"));
            return true;
        }
        return false;
    }

    private void fail(JobClient client, ActivatedJob job, RuntimeException exception) {
        boolean invalid = exception instanceof WorkflowRejectedException || exception instanceof IllegalArgumentException;
        client.newFailCommand(job.getKey())
                .retries(invalid ? 0 : Math.max(1, job.getRetries()))
                .retryBackoff(Duration.ofSeconds(5))
                .errorMessage(invalid ? "AUTOMATIC_QUESTION_CONTRACT_REJECTED" : "AUTOMATIC_QUESTION_PENDING")
                .send()
                .join();
    }

    private UUID uuid(ActivatedJob job, String name) {
        return UUID.fromString(text(job, name));
    }

    private String text(ActivatedJob job, String name) {
        Object value = job.getVariablesAsMap().get(name);
        require(value instanceof String && !((String) value).isBlank());
        return (String) value;
    }

    private void require(boolean valid) {
        if (!valid) {
            throw new WorkflowRejectedException("INVALID_AUTOMATIC_QUESTION_ORIGIN");
        }
    }
}
