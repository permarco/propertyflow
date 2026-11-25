package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.TenantReplyBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantReplyStore;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantReplyStore.Call;
import com.pertegato.propertyflow.caseprocessing.service.ConversationEvaluationService.Result;
import com.pertegato.propertyflow.caseprocessing.service.TenantReplyService;
import com.pertegato.propertyflow.caseprocessing.service.TenantReplyService.Outcome;
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

import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.REPLY_PREPARE_REVIEW;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.REPLY_WAKE_REVIEW;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.ErrorCode.ANALYSIS_BASIS_STALE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.ACTIVE_REPLY_CALL_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.ACTIVE_REPLY_ENTRY_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.CASE_ID;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.DECISION_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.ENTRY_CONTEXT_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.EVALUATION_CURRENT;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.EVALUATION_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.LEADING_PROCESS_INSTANCE_KEY;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.OUTCOME;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.REVIEW_NEED_REPLY_CALL_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.STAFF_ENTRY_CONTEXT_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.TenantReplyBpmnConstants.JobType.REPLY_APPLY_EVALUATION;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.TenantReplyBpmnConstants.JobType.REPLY_REGISTER;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.TenantReplyBpmnConstants.JobType.REPLY_VALIDATE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.TenantReplyBpmnConstants.Variablen.CONSUMED_TENANT_EVALUATION_VERSION;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.TenantReplyBpmnConstants.Variablen.TENANT_EVALUATION_VERSION;

@Component
@Profile("propertyflow-backend")
@ConditionalOnProperty(name = "propertyflow.workflow.enabled", havingValue = "true")
@RequiredArgsConstructor
public class TenantReplyWorkers {

    private final CamundaClient engine;
    private final TenantReplyService service;
    private final TenantReplyStore replies;

    @JobWorker(type = REPLY_REGISTER, autoComplete = false)
    public void register(JobClient client, ActivatedJob job) {
        try {
            ProcessInstance child = verify(job, Set.of(TenantReplyBpmnConstants.ServiceTask.REGISTER_REPLY_WAIT));
            long leader = child.getParentProcessInstanceKey();
            UUID entry = UUID.fromString(text(job, ENTRY_CONTEXT_REF));
            TenantReplyStore.Source source = service.source(entry, text(job, CASE_ID), leader);
            ProcessInstance staff = engine.newProcessInstanceGetRequest(source.instance())
                    .send()
                    .join();
            require((source.automatic() ? AutomaticClarificationBpmnConstants.PROCESS_ID : StaffProcessingBpmnConstants.PROCESS_ID)
                    .equals(staff.getProcessDefinitionId())
                    && Long.valueOf(leader).equals(staff.getParentProcessInstanceKey()) && job.getTenantId().equals(staff.getTenantId()));
            if ("ACTIVE".equals(staff.getState().name())) {
                // Der Suchindex kann dem bereits aktivierten Folgeaufruf kurz hinterherlaufen.
                throw new IllegalStateException("STAFF_COMPLETION_NOT_YET_VISIBLE");
            }
            require("COMPLETED".equals(staff.getState().name()) || source.interrupted() && "TERMINATED".equals(staff.getState().name()));
            Call call = service.register(text(job, CASE_ID), entry, leader, job.getProcessInstanceKey(),
                    child.getParentElementInstanceKey(), job.getTenantId(), source.instance());
            if ("REGISTERING".equals(call.state())) {
                // Erst nach der Initialisierung darf die dauerhafte Zustellung den Wartepunkt aufwecken.
                engine.newSetVariablesCommand(call.child())
                        .variables(Map.of(TENANT_EVALUATION_VERSION, 0, CONSUMED_TENANT_EVALUATION_VERSION, call.consumed()))
                        .local(true)
                        .send()
                        .join();
                engine.newSetVariablesCommand(leader)
                        .variables(Map.of(ACTIVE_REPLY_CALL_REF, call.id().toString(), ACTIVE_REPLY_ENTRY_REF, call.entry().toString()))
                        .send()
                        .join();
                service.ready(call.id());
            }
        } catch (RuntimeException exception) {
            fail(client, job, exception);
            return;
        }
        client.newCompleteCommand(job.getKey())
                .send()
                .join();
    }

    @JobWorker(type = REPLY_APPLY_EVALUATION, autoComplete = false)
    public void applyEvaluation(JobClient client, ActivatedJob job) {
        try {
            ProcessInstance child = verify(job, Set.of(TenantReplyBpmnConstants.ServiceTask.APPLY_EVALUATION));
            Object current = job.getVariablesAsMap().get(EVALUATION_CURRENT);
            require(current instanceof Boolean);
            UUID evaluation = Boolean.TRUE.equals(current) ? UUID.fromString(text(job, EVALUATION_REF)) : null;
            Result result = service.applyEvaluation(text(job, CASE_ID), UUID.fromString(text(job, ENTRY_CONTEXT_REF)),
                    child.getParentProcessInstanceKey(), job.getProcessInstanceKey(), job.getTenantId(), evaluation);
            switch (result.state()) {
                case "STALE" -> staleEvaluation(client, job);
                case "BUSY", "OBSOLETE" -> retry(client, job);
                case "APPLIED", "REVIEW" -> client.newCompleteCommand(job.getKey())
                        .variables(Map.of(EVALUATION_REF, result.evaluationId() == null ? "" : result.evaluationId().toString()))
                        .send()
                        .join();
                default -> throw new WorkflowRejectedException("UNEXPECTED_REPLY_EVALUATION_RESULT");
            }
        } catch (RuntimeException exception) {
            fail(client, job, exception);
        }
    }

    private void staleEvaluation(JobClient client, ActivatedJob job) {
        client.newThrowErrorCommand(job.getKey())
                .errorCode(ANALYSIS_BASIS_STALE)
                .errorMessage("REPLY_BASIS_CHANGED")
                .send()
                .join();
    }

    @JobWorker(type = REPLY_VALIDATE, autoComplete = false)
    public void validate(JobClient client, ActivatedJob job) {
        try {
            ProcessInstance child = verify(job, Set.of(TenantReplyBpmnConstants.ServiceTask.VALIDATE_CONTINUATION));
            String reference = (String) job.getVariablesAsMap().get(EVALUATION_REF);
            Outcome outcome = service.validate(text(job, CASE_ID), UUID.fromString(text(job, ENTRY_CONTEXT_REF)),
                    child.getParentProcessInstanceKey(), job.getProcessInstanceKey(), job.getTenantId(),
                    reference == null || reference.isBlank() ? null : UUID.fromString(reference));
            if ("STALE".equals(outcome.state())) {
                staleEvaluation(client, job);
            } else {
                client.newCompleteCommand(job.getKey())
                        .variables(Map.of(OUTCOME, outcome.state(), DECISION_REF, outcome.decision().toString()))
                        .send()
                        .join();
            }
        } catch (RuntimeException exception) {
            fail(client, job, exception);
        }
    }

    @JobWorker(type = REPLY_PREPARE_REVIEW, autoComplete = false)
    public void prepareReview(JobClient client, ActivatedJob job) {
        try {
            verifyRoot(job, CaseProcessBpmnConstants.ServiceTask.PREPARE_STAFF_REVIEW);
            Call call = replies.get(UUID.fromString(text(job, ACTIVE_REPLY_CALL_REF)));
            require(call.caseId().equals(text(job, CASE_ID)) && call.leader() == job.getProcessInstanceKey()
                    && call.tenant().equals(job.getTenantId()));
            Outcome result = service.review(call.id());
            require("REVIEW".equals(result.state()));
            client.newCompleteCommand(job.getKey())
                    .variables(Map.of(STAFF_ENTRY_CONTEXT_REF, result.decision().toString()))
                    .send()
                    .join();
        } catch (RuntimeException exception) {
            fail(client, job, exception);
        }
    }

    @JobWorker(type = REPLY_WAKE_REVIEW, autoComplete = false)
    public void wakeReview(JobClient client, ActivatedJob job) {
        try {
            verifyRoot(job, CaseProcessBpmnConstants.ServiceTask.NOTIFY_REPLY_WAIT);
            Call call = replies.active(text(job, CASE_ID), job.getProcessInstanceKey()).orElse(null);
            if (call != null && "REGISTERING".equals(call.state())) {
                retry(client, job);
                return;
            }
            if (call != null) {
                Outcome result = service.review(call.id());
                if ("REVIEW".equals(result.state())) {
                    engine.newSetVariablesCommand(call.leader())
                            .variables(Map.of(REVIEW_NEED_REPLY_CALL_REF, call.id().toString()))
                            .send()
                            .join();
                    replies.reviewDelivered(call.id());
                }
            }
            // Während eines Aufrufwechsels bleibt der Prüfgrund dauerhaft offen. Der Dispatcher
            // ermittelt den neuen Empfangsbereich; dieser überholte Nebenjob hält den Hauptpfad nicht fest.
            client.newCompleteCommand(job.getKey())
                    .send()
                    .join();
        } catch (RuntimeException exception) {
            fail(client, job, exception);
        }
    }

    private ProcessInstance verify(ActivatedJob job, Set<String> elements) {
        require(TenantReplyBpmnConstants.PROCESS_ID.equals(job.getBpmnProcessId())
                && elements.contains(job.getElementId()));
        ProcessInstance child = engine.newProcessInstanceGetRequest(job.getProcessInstanceKey())
                .send()
                .join();
        require(child.getParentProcessInstanceKey() != null && child.getParentElementInstanceKey() != null
                && Long.toString(child.getParentProcessInstanceKey()).equals(text(job, LEADING_PROCESS_INSTANCE_KEY)));
        ElementInstance parent = engine.newElementInstanceGetRequest(child.getParentElementInstanceKey())
                .send()
                .join();
        require(CaseProcessBpmnConstants.CallActivity.TENANT_REPLY_CALL.equals(parent.getElementId())
                && CaseProcessBpmnConstants.PROCESS_ID.equals(parent.getProcessDefinitionId())
                && child.getParentProcessInstanceKey().equals(parent.getProcessInstanceKey()) && job.getTenantId().equals(parent.getTenantId()));
        return child;
    }

    private void verifyRoot(ActivatedJob job, String element) {
        ProcessInstance root = engine.newProcessInstanceGetRequest(job.getProcessInstanceKey())
                .send()
                .join();
        require(CaseProcessBpmnConstants.PROCESS_ID.equals(job.getBpmnProcessId())
                && element.equals(job.getElementId())
                && root.getParentProcessInstanceKey() == null);
    }

    private void retry(JobClient client, ActivatedJob job) {
        client.newFailCommand(job.getKey())
                .retries(Math.max(1, job.getRetries()))
                .retryBackoff(Duration.ofSeconds(2))
                .errorMessage("REPLY_CONTEXT_PENDING")
                .send()
                .join();
    }

    private void fail(JobClient client, ActivatedJob job, RuntimeException exception) {
        boolean rejected = exception instanceof WorkflowRejectedException || exception instanceof IllegalArgumentException;
        client.newFailCommand(job.getKey())
                .retries(rejected ? 0 : Math.max(1, job.getRetries()))
                .retryBackoff(Duration.ofSeconds(5))
                .errorMessage(rejected ? "INVALID_REPLY_CONTEXT" : "REPLY_TEMPORARILY_UNAVAILABLE")
                .send()
                .join();
    }

    private String text(ActivatedJob job, String name) {
        Object value = job.getVariablesAsMap().get(name);
        require(value instanceof String && !((String) value).isBlank());
        return (String) value;
    }

    private void require(boolean valid) {
        if (!valid) {
            throw new WorkflowRejectedException("INVALID_REPLY_ORIGIN");
        }
    }
}
