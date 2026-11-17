package com.pertegato.propertyflow.caseprocessing.workflow;

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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("propertyflow-backend & !frontend-demo")
@ConditionalOnProperty(name = "propertyflow.workflow.enabled", havingValue = "true")
public class TenantReplyWorkers {

    public static final String PROCESS = "PropertyFlow_TenantReply_Draft";
    public static final String REGISTER = "propertyflow.reply.register";
    public static final String EVALUATE = "propertyflow.reply.evaluate";
    public static final String VALIDATE = "propertyflow.reply.validate";
    public static final String PREPARE_REVIEW = "propertyflow.reply.prepare-review";
    public static final String WAKE_REVIEW = "propertyflow.reply.wake-review";
    private final CamundaClient engine;
    private final TenantReplyService service;
    private final TenantReplyStore replies;

    public TenantReplyWorkers(CamundaClient engine, TenantReplyService service, TenantReplyStore replies) {
        this.engine = engine;
        this.service = service;
        this.replies = replies;
    }

    @JobWorker(type = REGISTER, autoComplete = false)
    public void register(JobClient client, ActivatedJob job) {
        try {
            ProcessInstance child = verify(job, Set.of("RStatus"));
            long leader = child.getParentProcessInstanceKey();
            UUID entry = UUID.fromString(text(job, "entryContextRef"));
            TenantReplyStore.Source source = service.source(entry, text(job, "caseId"), leader);
            ProcessInstance staff = engine.newProcessInstanceGetRequest(source.instance())
                    .send()
                    .join();
            require((source.automatic() ? InitialCaseWorkers.AUTOMATIC_PROCESS : StaffReviewWorkers.STAFF_PROCESS).equals(staff.getProcessDefinitionId())
                    && Long.valueOf(leader).equals(staff.getParentProcessInstanceKey()) && job.getTenantId().equals(staff.getTenantId()));
            if ("ACTIVE".equals(staff.getState().name())) {
                // Der Suchindex kann dem bereits aktivierten Folgeaufruf kurz hinterherlaufen.
                throw new IllegalStateException("STAFF_COMPLETION_NOT_YET_VISIBLE");
            }
            require("COMPLETED".equals(staff.getState().name()) || source.interrupted() && "TERMINATED".equals(staff.getState().name()));
            Call call = service.register(text(job, "caseId"), entry, leader, job.getProcessInstanceKey(),
                    child.getParentElementInstanceKey(), job.getTenantId(), source.instance());
            if ("REGISTERING".equals(call.state())) {
                // Erst nach der Initialisierung darf die dauerhafte Zustellung den Wartepunkt aufwecken.
                engine.newSetVariablesCommand(call.child())
                        .variables(Map.of("tenantEvaluationVersion", 0, "consumedTenantEvaluationVersion", call.consumed()))
                        .local(true)
                        .send()
                        .join();
                engine.newSetVariablesCommand(leader)
                        .variables(Map.of("activeReplyCallRef", call.id().toString(), "activeReplyEntryRef", call.entry().toString()))
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

    @JobWorker(type = EVALUATE, autoComplete = false)
    public void evaluate(JobClient client, ActivatedJob job) {
        try {
            ProcessInstance child = verify(job, Set.of("REvaluate", "RCurrentAnalysis"));
            Result result = service.evaluate(text(job, "caseId"), UUID.fromString(text(job, "entryContextRef")),
                    child.getParentProcessInstanceKey(), job.getProcessInstanceKey(), job.getTenantId());
            if (Set.of("BUSY", "STALE", "OBSOLETE").contains(result.state())) {
                retry(client, job);
                return;
            }
            client.newCompleteCommand(job.getKey())
                    .variables(Map.of("evaluationRef", result.evaluationId() == null ? "" : result.evaluationId().toString()))
                    .send()
                    .join();
        } catch (RuntimeException exception) {
            fail(client, job, exception);
        }
    }

    @JobWorker(type = VALIDATE, autoComplete = false)
    public void validate(JobClient client, ActivatedJob job) {
        try {
            ProcessInstance child = verify(job, Set.of("RValidate"));
            String reference = (String) job.getVariablesAsMap().get("evaluationRef");
            Outcome outcome = service.validate(text(job, "caseId"), UUID.fromString(text(job, "entryContextRef")),
                    child.getParentProcessInstanceKey(), job.getProcessInstanceKey(), job.getTenantId(),
                    reference == null || reference.isBlank() ? null : UUID.fromString(reference));
            if ("STALE".equals(outcome.state())) {
                client.newThrowErrorCommand(job.getKey())
                        .errorCode("ANALYSIS_BASIS_STALE")
                        .errorMessage("REPLY_BASIS_CHANGED")
                        .send()
                        .join();
            } else {
                client.newCompleteCommand(job.getKey())
                        .variables(Map.of("outcome", outcome.state(), "decisionRef", outcome.decision().toString()))
                        .send()
                        .join();
            }
        } catch (RuntimeException exception) {
            fail(client, job, exception);
        }
    }

    @JobWorker(type = PREPARE_REVIEW, autoComplete = false)
    public void prepareReview(JobClient client, ActivatedJob job) {
        try {
            verifyRoot(job, "ReviewNeedPrepare");
            Call call = replies.get(UUID.fromString(text(job, "activeReplyCallRef")));
            require(call.caseId().equals(text(job, "caseId")) && call.leader() == job.getProcessInstanceKey()
                    && call.tenant().equals(job.getTenantId()));
            Outcome result = service.review(call.id());
            require("REVIEW".equals(result.state()));
            client.newCompleteCommand(job.getKey())
                    .variables(Map.of("staffEntryContextRef", result.decision().toString()))
                    .send()
                    .join();
        } catch (RuntimeException exception) {
            fail(client, job, exception);
        }
    }

    @JobWorker(type = WAKE_REVIEW, autoComplete = false)
    public void wakeReview(JobClient client, ActivatedJob job) {
        try {
            verifyRoot(job, "ReviewNeedWakeReply");
            Call call = replies.active(text(job, "caseId"), job.getProcessInstanceKey()).orElse(null);
            if (call != null && "REGISTERING".equals(call.state())) {
                retry(client, job);
                return;
            }
            if (call != null) {
                Outcome result = service.review(call.id());
                if ("REVIEW".equals(result.state())) {
                    engine.newSetVariablesCommand(call.leader())
                            .variables(Map.of("reviewNeedReplyCallRef", call.id().toString()))
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
        require(PROCESS.equals(job.getBpmnProcessId()) && elements.contains(job.getElementId()));
        ProcessInstance child = engine.newProcessInstanceGetRequest(job.getProcessInstanceKey())
                .send()
                .join();
        require(child.getParentProcessInstanceKey() != null && child.getParentElementInstanceKey() != null
                && Long.toString(child.getParentProcessInstanceKey()).equals(text(job, "leadingProcessInstanceKey")));
        ElementInstance parent = engine.newElementInstanceGetRequest(child.getParentElementInstanceKey())
                .send()
                .join();
        require("Reply".equals(parent.getElementId()) && InitialCaseWorkers.CASE_PROCESS.equals(parent.getProcessDefinitionId())
                && child.getParentProcessInstanceKey().equals(parent.getProcessInstanceKey()) && job.getTenantId().equals(parent.getTenantId()));
        return child;
    }

    private void verifyRoot(ActivatedJob job, String element) {
        ProcessInstance root = engine.newProcessInstanceGetRequest(job.getProcessInstanceKey())
                .send()
                .join();
        require(InitialCaseWorkers.CASE_PROCESS.equals(job.getBpmnProcessId()) && element.equals(job.getElementId())
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
