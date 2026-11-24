package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.ConversationEvaluationBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.TenantMessageBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.TenantReplyBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.service.ConversationEvaluationService;
import com.pertegato.propertyflow.caseprocessing.service.ConversationEvaluationService.AutomaticContext;
import com.pertegato.propertyflow.caseprocessing.service.ConversationEvaluationService.EvaluationStep;
import com.pertegato.propertyflow.caseprocessing.service.ConversationEvaluationService.MessageContext;
import com.pertegato.propertyflow.caseprocessing.service.ConversationEvaluationService.ReplyContext;
import com.pertegato.propertyflow.caseprocessing.service.ConversationEvaluationService.Result;
import com.pertegato.propertyflow.caseprocessing.service.ConversationEvaluationService.StageResult;
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

import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants.JobType.AUTOMATIC_RECOMMENDATION;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.ErrorCode.ANALYSIS_BASIS_STALE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.ErrorCode.TRIAGE_ANALYSIS_FAILED;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.AUTOMATIC_CALL_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.CASE_ID;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.ENTRY_CONTEXT_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.EVALUATION_CURRENT;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.EVALUATION_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.LEADING_PROCESS_INSTANCE_KEY;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.TENANT_MESSAGE_ID;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.ConversationEvaluationBpmnConstants.JobType.AUTOMATIC_ADVISE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.ConversationEvaluationBpmnConstants.JobType.AUTOMATIC_ANALYZE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.ConversationEvaluationBpmnConstants.JobType.AUTOMATIC_ASSESS_CLOSURE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.ConversationEvaluationBpmnConstants.Variablen.EVALUATION_ATTEMPT;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.ConversationEvaluationBpmnConstants.Variablen.EVALUATION_RESTART;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.ConversationEvaluationBpmnConstants.Variablen.NEEDS_EVALUATION_ADVICE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.ConversationEvaluationBpmnConstants.Variablen.NEEDS_EVALUATION_CLOSURE;

@Component
@Profile("propertyflow-backend & !frontend-demo")
@ConditionalOnProperty(name = "propertyflow.workflow.enabled", havingValue = "true")
@RequiredArgsConstructor
public class AutomaticEvaluationWorkers {

    private final CamundaClient engine;
    private final ConversationEvaluationService service;

    @JobWorker(type = AUTOMATIC_ANALYZE, autoComplete = false)
    public void analyze(JobClient client, ActivatedJob job) {
        evaluateStage(client, job, ConversationEvaluationBpmnConstants.ServiceTask.ANALYZE_CONVERSATION, EvaluationStep.ANALYZE);
    }

    @JobWorker(type = AUTOMATIC_ADVISE, autoComplete = false)
    public void advise(JobClient client, ActivatedJob job) {
        evaluateStage(client, job, ConversationEvaluationBpmnConstants.ServiceTask.RECOMMEND_PROCEDURE, EvaluationStep.ADVISE);
    }

    @JobWorker(type = AUTOMATIC_ASSESS_CLOSURE, autoComplete = false)
    public void assessClosure(JobClient client, ActivatedJob job) {
        evaluateStage(client, job, ConversationEvaluationBpmnConstants.ServiceTask.ASSESS_CLOSURE, EvaluationStep.ASSESS_CLOSURE);
    }

    private void evaluateStage(JobClient client, ActivatedJob job, String element, EvaluationStep step) {
        StageResult stage;
        EvaluationCaller caller = null;
        UUID attempt;
        boolean first = step == EvaluationStep.ANALYZE;
        try {
            caller = evaluationCaller(job, element);
            // Der Jobschlüssel bleibt bei einer Wiederholung gleich, ein neuer Umlauf erhält einen neuen Schlüssel.
            attempt = first ? new UUID(0L, job.getKey()) : UUID.fromString(text(job, EVALUATION_ATTEMPT));
            UUID reference = first ? null : UUID.fromString(text(job, EVALUATION_REF));
            stage = runStage(job, caller, step, attempt, reference);
        } catch (RuntimeException exception) {
            fail(client, job, exception, caller == null || caller.automatic());
            return;
        }
        Result result = stage.result();
        boolean restart = "STALE".equals(result.state());
        if ("BUSY".equals(result.state()) || first && restart) {
            retry(client, job);
            return;
        }
        boolean current = "APPLIED".equals(result.state()) || (!caller.automatic() && "READY".equals(result.state()));
        client.newCompleteCommand(job.getKey())
                .variables(Map.of(EVALUATION_ATTEMPT, attempt.toString(),
                        EVALUATION_REF, result.evaluationId() == null ? "" : result.evaluationId().toString(),
                        EVALUATION_CURRENT, current,
                        EVALUATION_RESTART, restart,
                        NEEDS_EVALUATION_ADVICE, stage.nextStep() == EvaluationStep.ADVISE,
                        NEEDS_EVALUATION_CLOSURE, stage.nextStep() == EvaluationStep.ASSESS_CLOSURE))
                .send()
                .join();
    }

    private StageResult runStage(ActivatedJob job, EvaluationCaller caller, EvaluationStep step, UUID attempt, UUID reference) {
        String caseId = text(job, CASE_ID);
        long leader = Long.parseLong(text(job, LEADING_PROCESS_INSTANCE_KEY));
        if (TenantReplyBpmnConstants.PROCESS_ID.equals(caller.process())) {
            ReplyContext origin = new ReplyContext(caseId, UUID.fromString(text(job, ENTRY_CONTEXT_REF)), leader,
                    caller.instance(), job.getTenantId());
            return service.evaluateReplyStage(origin, step, attempt, reference);
        }
        if (!caller.automatic()) {
            MessageContext origin = new MessageContext(caseId, UUID.fromString(text(job, TENANT_MESSAGE_ID)), leader,
                    caller.instance(), job.getTenantId());
            return service.evaluateMessageStage(origin, step, attempt, reference);
        }
        AutomaticContext origin = new AutomaticContext(caseId, UUID.fromString(text(job, ENTRY_CONTEXT_REF)),
                UUID.fromString(text(job, AUTOMATIC_CALL_REF)), leader, caller.instance());
        StageResult stage = service.evaluateAutomaticStage(origin, step, attempt, reference);
        Result result = stage.result();
        if ("READY".equals(result.state()) || "APPLIED".equals(result.state())) {
            Result applied = service.applyAutomatic(caseId, origin.entryId(), origin.callRef(), leader, origin.child(), result.evaluationId());
            return new StageResult(applied, null);
        }
        return stage;
    }

    @JobWorker(type = AUTOMATIC_RECOMMENDATION, autoComplete = false)
    public void recommend(JobClient client, ActivatedJob job) {
        String result;
        try {
            verifyResultCheck(job);
            result = Boolean.FALSE.equals(job.getVariablesAsMap().get(EVALUATION_CURRENT)) ? "OBSOLETE"
                    : service.automaticResult(text(job, CASE_ID), UUID.fromString(text(job, ENTRY_CONTEXT_REF)),
                            UUID.fromString(text(job, AUTOMATIC_CALL_REF)), Long.parseLong(text(job, LEADING_PROCESS_INSTANCE_KEY)),
                            job.getProcessInstanceKey(), UUID.fromString(text(job, EVALUATION_REF)));
        } catch (RuntimeException exception) {
            fail(client, job, exception);
            return;
        }
        if ("STALE".equals(result)) {
            client.newThrowErrorCommand(job.getKey()).errorCode(ANALYSIS_BASIS_STALE).errorMessage("EVALUATION_BASIS_CHANGED").send().join();
        } else if ("FAILED".equals(result)) {
            client.newThrowErrorCommand(job.getKey()).errorCode(TRIAGE_ANALYSIS_FAILED)
                    .errorMessage("CURRENT_EVALUATION_REQUIRES_REVIEW").send().join();
        } else {
            client.newCompleteCommand(job.getKey()).variables(Map.of(EVALUATION_CURRENT, "CURRENT".equals(result))).send().join();
        }
    }

    private EvaluationCaller evaluationCaller(ActivatedJob job, String element) {
        require(ConversationEvaluationBpmnConstants.PROCESS_ID.equals(job.getBpmnProcessId())
                && element.equals(job.getElementId()));
        ProcessInstance evaluation = engine.newProcessInstanceGetRequest(job.getProcessInstanceKey()).send().join();
        ElementInstance call = parentCall(job, evaluation);
        ProcessInstance caller = engine.newProcessInstanceGetRequest(call.getProcessInstanceKey()).send().join();
        String process = caller.getProcessDefinitionId();
        require(ConversationEvaluationBpmnConstants.PROCESS_ID.equals(evaluation.getProcessDefinitionId())
                && process.equals(call.getProcessDefinitionId())
                && job.getTenantId().equals(evaluation.getTenantId()) && job.getTenantId().equals(caller.getTenantId()));
        String callElement = call.getElementId();
        if (AutomaticClarificationBpmnConstants.PROCESS_ID.equals(process)) {
            require(AutomaticClarificationBpmnConstants.CallActivity.EVALUATE_CONVERSATION.equals(callElement));
            verifyLeadingCall(job, caller, CaseProcessBpmnConstants.CallActivity.AUTOMATIC_CLARIFICATION_CALL);
        } else if (TenantReplyBpmnConstants.PROCESS_ID.equals(process)) {
            require(TenantReplyBpmnConstants.CallActivity.EVALUATE_CONVERSATION.equals(callElement));
            verifyLeadingCall(job, caller, CaseProcessBpmnConstants.CallActivity.TENANT_REPLY_CALL);
        } else {
            require(TenantMessageBpmnConstants.PROCESS_ID.equals(process)
                    && Set.of(TenantMessageBpmnConstants.CallActivity.EVALUATE_MESSAGE,
                            TenantMessageBpmnConstants.CallActivity.REFRESH_EVALUATION).contains(callElement));
            verifyLeadingCall(job, caller, CaseProcessBpmnConstants.CallActivity.TENANT_MESSAGE_CALL);
        }
        return new EvaluationCaller(process, call.getProcessInstanceKey());
    }

    private void verifyResultCheck(ActivatedJob job) {
        require(AutomaticClarificationBpmnConstants.PROCESS_ID.equals(job.getBpmnProcessId())
                && AutomaticClarificationBpmnConstants.ServiceTask.CHECK_EVALUATION.equals(job.getElementId()));
        ProcessInstance child = engine.newProcessInstanceGetRequest(job.getProcessInstanceKey()).send().join();
        verifyLeadingCall(job, child, CaseProcessBpmnConstants.CallActivity.AUTOMATIC_CLARIFICATION_CALL);
    }

    private void verifyLeadingCall(ActivatedJob job, ProcessInstance caller, String expectedCall) {
        ElementInstance call = parentCall(job, caller);
        require(Long.toString(call.getProcessInstanceKey()).equals(text(job, LEADING_PROCESS_INSTANCE_KEY))
                && expectedCall.equals(call.getElementId())
                && CaseProcessBpmnConstants.PROCESS_ID.equals(call.getProcessDefinitionId()));
    }

    private ElementInstance parentCall(ActivatedJob job, ProcessInstance child) {
        require(child.getParentProcessInstanceKey() != null && child.getParentElementInstanceKey() != null);
        ElementInstance call = engine.newElementInstanceGetRequest(child.getParentElementInstanceKey()).send().join();
        require(child.getParentProcessInstanceKey().equals(call.getProcessInstanceKey()) && job.getTenantId().equals(call.getTenantId()));
        return call;
    }

    private void retry(JobClient client, ActivatedJob job) {
        client.newFailCommand(job.getKey()).retries(Math.max(1, job.getRetries())).retryBackoff(Duration.ofSeconds(2))
                .errorMessage("CURRENT_EVALUATION_PENDING").send().join();
    }

    private void fail(JobClient client, ActivatedJob job, RuntimeException exception) {
        fail(client, job, exception, true);
    }

    private void fail(JobClient client, ActivatedJob job, RuntimeException exception, boolean automatic) {
        boolean rejected = exception instanceof WorkflowRejectedException || exception instanceof IllegalArgumentException;
        String invalid = automatic ? "INVALID_AUTOMATIC_CONTEXT" : "INVALID_EVALUATION_CONTEXT";
        client.newFailCommand(job.getKey()).retries(rejected ? 0 : Math.max(1, job.getRetries())).retryBackoff(Duration.ofSeconds(5))
                .errorMessage(rejected ? invalid : "EVALUATION_TEMPORARILY_UNAVAILABLE").send().join();
    }

    private String text(ActivatedJob job, String name) {
        Object value = job.getVariablesAsMap().get(name);
        require(value instanceof String && !((String) value).isBlank());
        return (String) value;
    }

    private void require(boolean valid) {
        if (!valid) {
            throw new WorkflowRejectedException("INVALID_AUTOMATIC_ORIGIN");
        }
    }

    private record EvaluationCaller(String process, long instance) {

        private boolean automatic() {
            return AutomaticClarificationBpmnConstants.PROCESS_ID.equals(process);
        }
    }
}
