package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.TenantReplyBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseDataStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseDataStore.Command;
import com.pertegato.propertyflow.caseprocessing.persistence.ReplyClosureStore;
import com.pertegato.propertyflow.caseprocessing.persistence.ReplyClosureStore.Intent;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantReplyStore;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantReplyStore.Call;
import com.pertegato.propertyflow.caseprocessing.service.CaseDataService;
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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.REPLY_CLOSURE_CHECK;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.REPLY_CLOSURE_SIGNAL;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.REPLY_CLOSURE_COMMAND_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.REPLY_CLOSURE_OUTCOME;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.STAFF_CLOSE_REPLY_CALL_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.STAFF_CLOSE_REPLY_ENTRY_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.STAFF_REPLY_CLOSURE_DECISION_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.CASE_ID;

@Component
@Profile("propertyflow-backend & !frontend-demo")
@ConditionalOnProperty(name = "propertyflow.workflow.enabled", havingValue = "true")
@RequiredArgsConstructor
public class ReplyClosureWorkers {

    private final CamundaClient engine;
    private final CaseDataService service;
    private final CaseDataStore commands;
    private final ReplyClosureStore closures;
    private final TenantReplyStore replies;

    @JobWorker(type = REPLY_CLOSURE_CHECK, autoComplete = false)
    public void check(JobClient client, ActivatedJob job) {
        String outcome;
        try {
            UUID id = verify(job, CaseProcessBpmnConstants.ServiceTask.CHECK_CLOSURE_DURING_REPLY);
            Intent intent = closures.find(id).orElseThrow();
            Call source = replies.get(intent.call());
            ProcessInstance child = engine.newProcessInstanceGetRequest(source.child()).send().join();
            ElementInstance call = engine.newElementInstanceGetRequest(source.parent()).send().join();
            require(source.leader() == job.getProcessInstanceKey() && source.tenant().equals(job.getTenantId())
                    && TenantReplyBpmnConstants.PROCESS_ID.equals(child.getProcessDefinitionId())
                    && Long.valueOf(source.leader()).equals(child.getParentProcessInstanceKey())
                    && Long.valueOf(source.parent()).equals(child.getParentElementInstanceKey())
                    && source.tenant().equals(child.getTenantId())
                    && CaseProcessBpmnConstants.CallActivity.TENANT_REPLY_CALL.equals(call.getElementId())
                    && Long.valueOf(source.leader()).equals(call.getProcessInstanceKey()) && source.tenant().equals(call.getTenantId()));
            if (!intent.accepted() && (!"ACTIVE".equals(child.getState().name()) || !"ACTIVE".equals(call.getState().name()))) {
                outcome = service.rejectReplyClosure(id, source.leader(), source.tenant());
            } else {
                outcome = service.replyClosureState(id, source.leader(), source.tenant());
            }
        } catch (RuntimeException exception) {
            fail(client, job, exception);
            return;
        }
        client.newCompleteCommand(job.getKey()).variables(Map.of(REPLY_CLOSURE_OUTCOME, outcome)).send().join();
    }

    @JobWorker(type = REPLY_CLOSURE_SIGNAL, autoComplete = false)
    public void signal(JobClient client, ActivatedJob job) {
        try {
            deliver(verify(job, CaseProcessBpmnConstants.ServiceTask.NOTIFY_CLOSURE_DURING_REPLY));
        } catch (RuntimeException exception) {
            fail(client, job, exception);
            return;
        }
        client.newCompleteCommand(job.getKey()).send().join();
    }

    @Scheduled(fixedDelayString = "${propertyflow.workflow.poll-delay-ms:5000}",
            initialDelayString = "${propertyflow.workflow.initial-delay-ms:5000}")
    public void dispatch() {
        for (int index = 0; index < 20; index++) {
            UUID id = closures.claim().orElse(null);
            if (id == null) {
                return;
            }
            try {
                deliver(id);
            } catch (RuntimeException exception) {
                // Die reservierte Abschlussentscheidung wird ohne erneute Zustimmung nachgeliefert.
            }
        }
    }

    private void deliver(UUID id) {
        Intent intent = closures.find(id).orElseThrow();
        require(intent.accepted());
        Command command = commands.get(id);
        Call call = replies.get(intent.call());
        if (!"CLOSING".equals(call.state()) || !"PENDING".equals(command.state())) {
            closures.delivered(id);
            return;
        }
        ProcessInstance root = engine.newProcessInstanceGetRequest(command.leader()).send().join();
        require(CaseProcessBpmnConstants.PROCESS_ID.equals(root.getProcessDefinitionId())
                && root.getParentProcessInstanceKey() == null
                && command.tenant().equals(root.getTenantId()) && command.leader() == call.leader());
        if (!"ACTIVE".equals(root.getState().name())) {
            throw new IllegalStateException("REPLY_CLOSURE_ROOT_NOT_ACTIVE");
        }
        engine.newSetVariablesCommand(command.leader())
                .variables(Map.of(STAFF_CLOSE_REPLY_CALL_REF, call.id().toString(), STAFF_CLOSE_REPLY_ENTRY_REF, call.entry().toString(),
                        STAFF_REPLY_CLOSURE_DECISION_REF, id.toString()))
                .send()
                .join();
        closures.delivered(id);
    }

    private UUID verify(ActivatedJob job, String element) {
        require(CaseProcessBpmnConstants.PROCESS_ID.equals(job.getBpmnProcessId())
                && element.equals(job.getElementId()));
        ProcessInstance root = engine.newProcessInstanceGetRequest(job.getProcessInstanceKey()).send().join();
        require(root.getParentProcessInstanceKey() == null && job.getTenantId().equals(root.getTenantId()));
        UUID id = UUID.fromString((String) job.getVariablesAsMap().get(REPLY_CLOSURE_COMMAND_REF));
        Command command = commands.get(id);
        require(command.leader() == job.getProcessInstanceKey() && command.tenant().equals(job.getTenantId())
                && command.caseId().equals(job.getVariablesAsMap().get(CASE_ID)) && "CLOSE_REPLY".equals(command.type()));
        return id;
    }

    private void fail(JobClient client, ActivatedJob job, RuntimeException exception) {
        boolean rejected = exception instanceof WorkflowRejectedException || exception instanceof IllegalArgumentException;
        client.newFailCommand(job.getKey()).retries(rejected ? 0 : Math.max(1, job.getRetries()))
                .retryBackoff(Duration.ofSeconds(5)).errorMessage(rejected ? "INVALID_REPLY_CLOSURE" : "REPLY_CLOSURE_PENDING")
                .send().join();
    }

    private void require(boolean valid) {
        if (!valid) {
            throw new WorkflowRejectedException("INVALID_REPLY_CLOSURE");
        }
    }
}
