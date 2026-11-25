package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.Entry;
import com.pertegato.propertyflow.caseprocessing.service.AutomaticClosureService;
import com.pertegato.propertyflow.caseprocessing.service.AutomaticQuestionService;
import com.pertegato.propertyflow.caseprocessing.service.AutomaticQuestionService.Result;
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
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants.ErrorCode.CLOSURE_DRAFT_MISSING;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants.JobType.AUTOMATIC_CHECK_CLOSURE_MESSAGE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants.JobType.AUTOMATIC_CLOSE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants.JobType.AUTOMATIC_CONFIRM_CLOSURE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants.JobType.AUTOMATIC_PREPARE_RESULT;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants.JobType.AUTOMATIC_PUBLISH_QUESTIONS;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants.JobType.AUTOMATIC_VALIDATE_QUESTIONS;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants.JobType.AUTOMATIC_WRITE_CLOSURE_MESSAGE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants.Variablen.AUTOMATIC_ACTION;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants.Variablen.AUTOMATIC_CLOSURE_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants.Variablen.CLOSURE_DRAFT_RECOVERIES;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants.Variablen.QUESTION_BATCH_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.AUTOMATIC_ACCEPT_RESULT;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.ErrorCode.ANALYSIS_BASIS_STALE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.ErrorCode.AUTO_CLOSURE_STALE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.ErrorCode.CLOSURE_MESSAGE_INVALID;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.ErrorCode.PUBLICATION_BASIS_STALE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.AUTOMATIC_CALL_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.AUTOMATIC_DECISION_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.AUTOMATIC_OUTCOME;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.CASE_ID;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.DECISION_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.ENTRY_CONTEXT_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.EVALUATION_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.LEADING_PROCESS_INSTANCE_KEY;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.OUTCOME;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.REPLY_ENTRY_CONTEXT_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.STAFF_ENTRY_CONTEXT_REF;

@Component
@Profile("propertyflow-backend")
@ConditionalOnProperty(name = "propertyflow.workflow.enabled", havingValue = "true")
@RequiredArgsConstructor
public class AutomaticQuestionWorkers {

    private static final int MAX_DRAFT_RECOVERIES = 3;
    private final CamundaClient engine;
    private final AutomaticQuestionService service;
    private final AutomaticClosureService closures;

    @JobWorker(type = AUTOMATIC_VALIDATE_QUESTIONS, autoComplete = false)
    public void validate(JobClient client, ActivatedJob job) {
        Result result;
        try {
            long leader = child(job, Set.of(AutomaticClarificationBpmnConstants.ServiceTask.VALIDATE_AUTOMATIC_ACTION));
            result = service.validate(text(job, CASE_ID), uuid(job, ENTRY_CONTEXT_REF), uuid(job, AUTOMATIC_CALL_REF),
                    leader, job.getProcessInstanceKey(), uuid(job, EVALUATION_REF));
        } catch (RuntimeException exception) {
            fail(client, job, exception);
            return;
        }
        if (!defer(client, job, result, ANALYSIS_BASIS_STALE)) {
            client.newCompleteCommand(job.getKey())
                    .variables(Map.of(AUTOMATIC_ACTION, result.state(),
                            "CLOSE".equals(result.state()) ? AUTOMATIC_CLOSURE_REF : QUESTION_BATCH_REF, result.reference().toString()))
                    .send()
                    .join();
        }
    }

    @JobWorker(type = AUTOMATIC_PUBLISH_QUESTIONS, autoComplete = false)
    public void publish(JobClient client, ActivatedJob job) {
        Result result;
        try {
            long leader = child(job, Set.of(AutomaticClarificationBpmnConstants.ServiceTask.PUBLISH_CATALOG_QUESTION));
            result = service.publish(uuid(job, QUESTION_BATCH_REF), leader, job.getProcessInstanceKey());
        } catch (RuntimeException exception) {
            fail(client, job, exception);
            return;
        }
        if (!defer(client, job, result, PUBLICATION_BASIS_STALE)) {
            client.newCompleteCommand(job.getKey()).send().join();
        }
    }

    @JobWorker(type = AUTOMATIC_PREPARE_RESULT, autoComplete = false)
    public void finish(JobClient client, ActivatedJob job) {
        Result result;
        try {
            long leader = child(job, Set.of(AutomaticClarificationBpmnConstants.ServiceTask.PREPARE_REPLY_WAIT,
                    AutomaticClarificationBpmnConstants.ServiceTask.PREPARE_REVIEW));
            result = "CLOSE".equals(text(job, AUTOMATIC_ACTION))
                    ? closures.finish(uuid(job, AUTOMATIC_CLOSURE_REF), leader, job.getProcessInstanceKey())
                    : service.finish(uuid(job, QUESTION_BATCH_REF), leader, job.getProcessInstanceKey());
        } catch (RuntimeException exception) {
            fail(client, job, exception);
            return;
        }
        if (!defer(client, job, result, ANALYSIS_BASIS_STALE)) {
            client.newCompleteCommand(job.getKey())
                    .variables(Map.of(OUTCOME, result.state(), DECISION_REF,
                            text(job, "CLOSE".equals(text(job, AUTOMATIC_ACTION)) ? AUTOMATIC_CLOSURE_REF : QUESTION_BATCH_REF)))
                    .send()
                    .join();
        }
    }

    @JobWorker(type = AUTOMATIC_ACCEPT_RESULT, autoComplete = false)
    public void accept(JobClient client, ActivatedJob job) {
        Result result;
        try {
            require(CaseProcessBpmnConstants.PROCESS_ID.equals(job.getBpmnProcessId())
                    && CaseProcessBpmnConstants.ServiceTask.ACCEPT_AUTOMATIC_RESULT.equals(job.getElementId()));
            require(Set.of("WAIT_FOR_REPLY", "REVIEW", "CLOSED").contains(text(job, AUTOMATIC_OUTCOME)));
            ProcessInstance root = engine.newProcessInstanceGetRequest(job.getProcessInstanceKey()).send().join();
            require(root.getParentProcessInstanceKey() == null);
            UUID batch = uuid(job, AUTOMATIC_DECISION_REF);
            boolean closure = closures.owns(batch);
            Entry source = closure ? closures.source(batch, text(job, CASE_ID), job.getProcessInstanceKey())
                    : service.source(batch, text(job, CASE_ID), job.getProcessInstanceKey());
            ProcessInstance automatic = engine.newProcessInstanceGetRequest(source.childKey()).send().join();
            ElementInstance call = engine.newElementInstanceGetRequest(source.parentElementKey()).send().join();
            require(AutomaticClarificationBpmnConstants.PROCESS_ID.equals(automatic.getProcessDefinitionId())
                    && Long.valueOf(job.getProcessInstanceKey()).equals(automatic.getParentProcessInstanceKey())
                    && source.parentElementKey().equals(automatic.getParentElementInstanceKey())
                    && job.getTenantId().equals(automatic.getTenantId())
                    && CaseProcessBpmnConstants.CallActivity.AUTOMATIC_CLARIFICATION_CALL.equals(call.getElementId())
                    && Long.valueOf(job.getProcessInstanceKey()).equals(call.getProcessInstanceKey())
                    && job.getTenantId().equals(call.getTenantId()));
            if (!"COMPLETED".equals(automatic.getState().name()) || !"COMPLETED".equals(call.getState().name())) {
                throw new IllegalStateException("AUTOMATIC_COMPLETION_NOT_YET_VISIBLE");
            }
            result = closure ? closures.finish(batch, job.getProcessInstanceKey(), source.childKey())
                    : service.finish(batch, job.getProcessInstanceKey(), source.childKey());
            require(result.state().equals(text(job, AUTOMATIC_OUTCOME)));
        } catch (RuntimeException exception) {
            fail(client, job, exception);
            return;
        }
        if (!defer(client, job, result, ANALYSIS_BASIS_STALE)) {
            if ("CLOSED".equals(result.state())) {
                client.newCompleteCommand(job.getKey())
                        .variables(Map.of(AUTOMATIC_OUTCOME, "CLOSED"))
                        .send()
                        .join();
                return;
            }
            client.newCompleteCommand(job.getKey())
                    .variables(Map.of(AUTOMATIC_OUTCOME, result.state(),
                            "REVIEW".equals(result.state()) ? STAFF_ENTRY_CONTEXT_REF : REPLY_ENTRY_CONTEXT_REF, result.reference().toString()))
                    .send()
                    .join();
        }
    }

    @JobWorker(type = AUTOMATIC_WRITE_CLOSURE_MESSAGE, autoComplete = false)
    public void writeClosureMessage(JobClient client, ActivatedJob job) {
        closure(client, job, AutomaticClarificationBpmnConstants.ServiceTask.WRITE_CLOSURE_MESSAGE);
    }

    @JobWorker(type = AUTOMATIC_CHECK_CLOSURE_MESSAGE, autoComplete = false)
    public void checkClosureMessage(JobClient client, ActivatedJob job) {
        closure(client, job, AutomaticClarificationBpmnConstants.ServiceTask.CHECK_CLOSURE_MESSAGE);
    }

    @JobWorker(type = AUTOMATIC_CLOSE, autoComplete = false)
    public void close(JobClient client, ActivatedJob job) {
        closure(client, job, AutomaticClarificationBpmnConstants.ServiceTask.SAVE_AUTOMATIC_CLOSURE);
    }

    @JobWorker(type = AUTOMATIC_CONFIRM_CLOSURE, autoComplete = false)
    public void closed(JobClient client, ActivatedJob job) {
        closure(client, job, AutomaticClarificationBpmnConstants.ServiceTask.CONFIRM_CLOSURE);
    }

    private void closure(JobClient client, ActivatedJob job, String element) {
        Result result;
        try {
            long leader = child(job, Set.of(element));
            UUID reference = uuid(job, AUTOMATIC_CLOSURE_REF);
            require("CLOSE".equals(text(job, AUTOMATIC_ACTION)));
            Entry source = closures.source(reference, text(job, CASE_ID), leader);
            require(source.id().equals(uuid(job, ENTRY_CONTEXT_REF)) && source.callRef().equals(uuid(job, AUTOMATIC_CALL_REF)));
            result = switch (element) {
                case AutomaticClarificationBpmnConstants.ServiceTask.WRITE_CLOSURE_MESSAGE -> closures.writeMessage(reference, leader,
                        job.getProcessInstanceKey());
                case AutomaticClarificationBpmnConstants.ServiceTask.CHECK_CLOSURE_MESSAGE -> closures.checkMessage(reference, leader,
                        job.getProcessInstanceKey());
                case AutomaticClarificationBpmnConstants.ServiceTask.SAVE_AUTOMATIC_CLOSURE -> closures.close(reference, leader,
                        job.getProcessInstanceKey());
                default -> closures.finish(reference, leader, job.getProcessInstanceKey());
            };
            if (AutomaticClarificationBpmnConstants.ServiceTask.CONFIRM_CLOSURE.equals(element)) {
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
        if ("DRAFT_MISSING".equals(result.state())) {
            recoverDraft(client, job, result.reference());
        } else if ("REVIEW".equals(result.state())) {
            client.newThrowErrorCommand(job.getKey())
                    .errorCode(CLOSURE_MESSAGE_INVALID)
                    .send()
                    .join();
        } else if ("BUSY".equals(result.state())) {
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries())
                    .retryBackoff(Duration.ofSeconds(5))
                    .errorMessage("CLOSURE_WORK_PENDING")
                    .send()
                    .join();
        } else if (!defer(client, job, result,
                AutomaticClarificationBpmnConstants.ServiceTask.SAVE_AUTOMATIC_CLOSURE.equals(element) ? AUTO_CLOSURE_STALE
                        : PUBLICATION_BASIS_STALE)) {
            client.newCompleteCommand(job.getKey())
                    .variables(AutomaticClarificationBpmnConstants.ServiceTask.CONFIRM_CLOSURE.equals(element)
                            ? Map.of(OUTCOME, "CLOSED", DECISION_REF, result.reference().toString()) : Map.of())
                    .send()
                    .join();
        }
    }

    private void recoverDraft(JobClient client, ActivatedJob job, UUID reference) {
        Object history = job.getVariablesAsMap().get(CLOSURE_DRAFT_RECOVERIES);
        int count = history instanceof Map<?, ?> attempts && attempts.get(reference.toString()) instanceof Number number
                ? number.intValue() : 0;
        if (count >= MAX_DRAFT_RECOVERIES) {
            client.newFailCommand(job.getKey())
                    .retries(0)
                    .errorMessage("CLOSURE_DRAFT_RECOVERY_EXHAUSTED")
                    .send()
                    .join();
            return;
        }
        client.newThrowErrorCommand(job.getKey())
                .errorCode(CLOSURE_DRAFT_MISSING)
                .variables(Map.of(CLOSURE_DRAFT_RECOVERIES, Map.of(reference.toString(), count + 1)))
                .send()
                .join();
    }

    private long child(ActivatedJob job, Set<String> elements) {
        require(AutomaticClarificationBpmnConstants.PROCESS_ID.equals(job.getBpmnProcessId())
                && elements.contains(job.getElementId()));
        ProcessInstance process = engine.newProcessInstanceGetRequest(job.getProcessInstanceKey()).send().join();
        require(process.getParentProcessInstanceKey() != null && process.getParentElementInstanceKey() != null
                && Long.toString(process.getParentProcessInstanceKey()).equals(text(job, LEADING_PROCESS_INSTANCE_KEY))
                && job.getTenantId().equals(process.getTenantId()));
        ElementInstance call = engine.newElementInstanceGetRequest(process.getParentElementInstanceKey()).send().join();
        require(CaseProcessBpmnConstants.CallActivity.AUTOMATIC_CLARIFICATION_CALL.equals(call.getElementId())
                && CaseProcessBpmnConstants.PROCESS_ID.equals(call.getProcessDefinitionId())
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
