package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.TenantReplyBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffReviewStore.Continuation;
import com.pertegato.propertyflow.caseprocessing.service.StaffReviewService;
import com.pertegato.propertyflow.caseprocessing.service.StaffReviewService.ExternalOwner;
import com.pertegato.propertyflow.caseprocessing.service.StaffReviewService.Interruption;
import com.pertegato.propertyflow.caseprocessing.service.StaffReviewService.ReviewRoute;
import com.pertegato.propertyflow.caseprocessing.service.WorkflowRejectedException;
import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.worker.JobClient;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.REVIEW_ANALYSIS_FAILURE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.REVIEW_CHECK;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.REVIEW_INTERRUPT_AUTOMATIC;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.REVIEW_MERGE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.REVIEW_PREPARE_STAFF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.STAFF_CLAIM;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.STAFF_MERGE_CONTINUATION;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.STAFF_RECONCILE_CONTINUATION;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.ACTIVE_FOLLOW_UP_VERSION;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.ACTIVE_WORK_TASK_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.AUTOMATIC_CALL_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.AUTOMATIC_INTERRUPTION_CALL_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.AUTOMATIC_INTERRUPTION_OUTCOME;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.AUTOMATIC_INTERRUPTION_READY;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.CASE_ID;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.ENTRY_CONTEXT_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.LEADING_PROCESS_INSTANCE_KEY;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.PENDING_REVIEW_FOLLOW_UP_VERSION;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.PENDING_WORK_REVIEW_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.PHASE_REVIEW_SIGNAL_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.REPLY_ENTRY_CONTEXT_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.REVIEW_CONTINUATION_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.REVIEW_ROUTE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.REVIEW_SIGNAL_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.STAFF_CONTINUATION_OUTCOME;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.STAFF_DECISION_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.STAFF_ENTRY_CONTEXT_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants.JobType.STAFF_REGISTER;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants.JobType.STAFF_START_REVIEW;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants.Variablen.EXTERNAL_WAIT_REVIEW_REQUIRED;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants.Variablen.FOLLOW_UP_ENTRY_CONTEXT_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants.Variablen.REVIEW_WAIT_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants.Variablen.STAFF_ENTRY_KIND;

@Component
@Profile("propertyflow-backend & !frontend-demo")
@ConditionalOnProperty(name = "propertyflow.workflow.enabled", havingValue = "true")
@RequiredArgsConstructor
public class StaffReviewWorkers {

    private final CamundaClient camunda;
    private final StaffReviewService service;

    @JobWorker(type = REVIEW_CHECK, autoComplete = false)
    public void check(JobClient client, ActivatedJob job) {
        execute(client, job, () -> {
            root(job, CaseProcessBpmnConstants.ServiceTask.CHECK_REVIEW_REASON);
            ReviewRoute route = service.check(text(job, CASE_ID), job.getProcessInstanceKey(), uuid(job, REVIEW_SIGNAL_REF));
            return route.continuationId() == null ? Map.of(REVIEW_ROUTE, route.route())
                    : Map.of(REVIEW_ROUTE, route.route(), REVIEW_CONTINUATION_REF, route.continuationId().toString());
        });
    }

    @JobWorker(type = REVIEW_INTERRUPT_AUTOMATIC, autoComplete = false)
    public void interruptAutomatic(JobClient client, ActivatedJob job) {
        execute(client, job, () -> {
            root(job, CaseProcessBpmnConstants.ServiceTask.NOTIFY_CURRENT_PHASE);
            Continuation continuation = service.read(text(job, CASE_ID), job.getProcessInstanceKey(), uuid(job, REVIEW_CONTINUATION_REF));
            if (!continuation.reviewApplied()) {
                // Explicit root-scope transfer. The boundary also compares the call reference, so a late
                // transfer cannot cancel a subsequent automatic call or a staff child.
                camunda.newSetVariablesCommand(job.getProcessInstanceKey())
                        .variables(Map.of(AUTOMATIC_INTERRUPTION_READY, true,
                                AUTOMATIC_INTERRUPTION_CALL_REF, continuation.callRef().toString())).local(true).send().join();
            } else {
                StaffReviewService.StaffInterruption target = service.staffInterruption(text(job, CASE_ID), job.getProcessInstanceKey(),
                        continuation.id(), uuid(job, REVIEW_SIGNAL_REF));
                if (target != null) {
                    ProcessInstance staff = camunda.newProcessInstanceGetRequest(target.child()).send().join();
                    require(StaffProcessingBpmnConstants.PROCESS_ID.equals(staff.getProcessDefinitionId())
                            && Long.valueOf(job.getProcessInstanceKey()).equals(staff.getParentProcessInstanceKey())
                            && job.getTenantId().equals(staff.getTenantId()), "OTHER_STAFF_GENERATION");
                    Map<String, Object> marker = switch (target.phase()) {
                        case "IN_PROGRESS" -> Map.of(PENDING_WORK_REVIEW_REF, target.reference().toString());
                        case "WAITING_FOR_EXTERNAL" -> Map.of(EXTERNAL_WAIT_REVIEW_REQUIRED, true, REVIEW_WAIT_REF, target.reference().toString());
                        case "WAITING_FOR_FOLLOW_UP" -> Map.of(PENDING_REVIEW_FOLLOW_UP_VERSION, target.reference().toString(),
                                PHASE_REVIEW_SIGNAL_REF, text(job, REVIEW_SIGNAL_REF));
                        default -> throw new WorkflowRejectedException("INVALID_REVIEW_PHASE");
                    };
                    camunda.newSetVariablesCommand(target.child()).variables(marker).local(true).send().join();
                }
            }
            return Map.of();
        });
    }

    @JobWorker(type = REVIEW_PREPARE_STAFF, autoComplete = false)
    public void prepareStaff(JobClient client, ActivatedJob job) {
        execute(client, job, () -> {
            root(job, CaseProcessBpmnConstants.ServiceTask.PREPARE_CONFIRMED_INTERRUPTION);
            Interruption result = service.routeInterruption(text(job, CASE_ID), job.getProcessInstanceKey(), uuid(job, AUTOMATIC_CALL_REF));
            return Map.of("STAFF".equals(result.outcome()) ? STAFF_ENTRY_CONTEXT_REF : REPLY_ENTRY_CONTEXT_REF,
                    result.reference().toString(), AUTOMATIC_INTERRUPTION_OUTCOME, result.outcome());
        });
    }

    @JobWorker(type = STAFF_CLAIM, autoComplete = false)
    public void claim(JobClient client, ActivatedJob job) {
        execute(client, job, () -> {
            root(job, CaseProcessBpmnConstants.ServiceTask.CLAIM_STAFF_CONTINUATION);
            UUID id = uuid(job, STAFF_ENTRY_CONTEXT_REF);
            Continuation continuation = service.read(text(job, CASE_ID), job.getProcessInstanceKey(), id);
            ExternalOwner external = service.externalOwner(text(job, CASE_ID), job.getProcessInstanceKey(), id);
            if (external != null) {
                verifyExternalOwner(external, job);
                return Map.of(STAFF_CONTINUATION_OUTCOME, service.claimExternal(text(job, CASE_ID), job.getProcessInstanceKey(), id, external));
            }
            Continuation owner = service.current(text(job, CASE_ID), job.getProcessInstanceKey());
            Long liveChild = null;
            if (!owner.id().equals(id)) {
                liveChild = liveStaff(owner, job.getTenantId());
            } else if (continuation.claimElementKey() != null && continuation.claimElementKey() != job.getElementInstanceKey()) {
                liveChild = liveStaff(continuation, job.getTenantId());
            }
            String outcome = service.claim(text(job, CASE_ID), job.getProcessInstanceKey(), id, job.getElementInstanceKey(), liveChild);
            return Map.of(STAFF_CONTINUATION_OUTCOME, outcome);
        });
    }

    @JobWorker(type = REVIEW_ANALYSIS_FAILURE, autoComplete = false)
    public void analysisFailure(JobClient client, ActivatedJob job) {
        execute(client, job, () -> {
            root(job, CaseProcessBpmnConstants.ServiceTask.PREPARE_ANALYSIS_FAILURE);
            Continuation continuation = service.analysisFailure(text(job, CASE_ID), job.getProcessInstanceKey(), uuid(job, AUTOMATIC_CALL_REF));
            return Map.of(STAFF_ENTRY_CONTEXT_REF, continuation.id().toString());
        });
    }

    @JobWorker(type = STAFF_REGISTER, autoComplete = false)
    public void register(JobClient client, ActivatedJob job) {
        execute(client, job, () -> {
            ProcessInstance child = child(job, StaffProcessingBpmnConstants.ServiceTask.CHECK_ENTRY);
            service.register(text(job, CASE_ID), child.getParentProcessInstanceKey(), uuid(job, ENTRY_CONTEXT_REF),
                    job.getProcessInstanceKey(), child.getParentElementInstanceKey());
            String kind = service.read(text(job, CASE_ID), child.getParentProcessInstanceKey(), uuid(job, ENTRY_CONTEXT_REF)).entryKind();
            String directWait = service.directWaitReference(uuid(job, ENTRY_CONTEXT_REF));
            if (!directWait.isEmpty()) {
                return Map.of(STAFF_ENTRY_KIND, kind, STAFF_DECISION_REF, directWait, FOLLOW_UP_ENTRY_CONTEXT_REF, directWait,
                        ACTIVE_FOLLOW_UP_VERSION, directWait, ACTIVE_WORK_TASK_REF, "");
            }
            return Map.of(STAFF_ENTRY_KIND, "CONTINUE".equals(kind) ? "WORK" : kind, ACTIVE_WORK_TASK_REF, service.workReference(text(job, CASE_ID),
                    child.getParentProcessInstanceKey(), uuid(job, ENTRY_CONTEXT_REF)));
        });
    }

    @JobWorker(type = STAFF_START_REVIEW, autoComplete = false)
    public void startReview(JobClient client, ActivatedJob job) {
        execute(client, job, () -> {
            ProcessInstance child = child(job, StaffProcessingBpmnConstants.ServiceTask.START_REVIEW);
            service.startReview(text(job, CASE_ID), child.getParentProcessInstanceKey(), uuid(job, ENTRY_CONTEXT_REF),
                    job.getProcessInstanceKey());
            return Map.of();
        });
    }

    @JobWorker(type = REVIEW_MERGE, autoComplete = false)
    public void merge(JobClient client, ActivatedJob job) {
        execute(client, job, () -> {
            root(job, CaseProcessBpmnConstants.ServiceTask.UPDATE_OPEN_REVIEW);
            ReviewRoute current = service.check(text(job, CASE_ID), job.getProcessInstanceKey(), uuid(job, REVIEW_SIGNAL_REF));
            if ("NONE".equals(current.route())) {
                return Map.of();
            }
            if (!"EXISTING".equals(current.route())) {
                throw new IllegalStateException("CURRENT_REVIEW_NOT_YET_READY");
            }
            UUID id = current.continuationId();
            Continuation continuation = service.read(text(job, CASE_ID), job.getProcessInstanceKey(), id);
            long liveChild = liveReview(continuation, job.getTenantId());
            service.merge(text(job, CASE_ID), job.getProcessInstanceKey(), id, uuid(job, REVIEW_SIGNAL_REF), liveChild);
            return Map.of();
        });
    }

    @JobWorker(type = STAFF_MERGE_CONTINUATION, autoComplete = false)
    public void mergeContinuation(JobClient client, ActivatedJob job) {
        execute(client, job, () -> {
            root(job, CaseProcessBpmnConstants.ServiceTask.UPDATE_STAFF_CONTINUATION);
            reconcile(job);
            return Map.of();
        });
    }

    @JobWorker(type = STAFF_RECONCILE_CONTINUATION, autoComplete = false)
    public void reconcileContinuation(JobClient client, ActivatedJob job) {
        execute(client, job, () -> {
            root(job, CaseProcessBpmnConstants.ServiceTask.RECONCILE_STAFF_CONTINUATION);
            reconcile(job);
            return Map.of();
        });
    }

    private void reconcile(ActivatedJob job) {
        ExternalOwner external = service.externalOwner(text(job, CASE_ID), job.getProcessInstanceKey(), uuid(job, STAFF_ENTRY_CONTEXT_REF));
        if (external != null) {
            verifyExternalOwner(external, job);
            service.reconcileExternal(text(job, CASE_ID), job.getProcessInstanceKey(), uuid(job, STAFF_ENTRY_CONTEXT_REF), external);
            return;
        }
        Continuation current = service.current(text(job, CASE_ID), job.getProcessInstanceKey());
        long liveChild = liveStaff(current, job.getTenantId());
        service.reconcile(text(job, CASE_ID), job.getProcessInstanceKey(), uuid(job, STAFF_ENTRY_CONTEXT_REF), current.id(), liveChild);
    }

    private void verifyExternalOwner(ExternalOwner owner, ActivatedJob job) {
        String process = "REPLY".equals(owner.kind()) ? TenantReplyBpmnConstants.PROCESS_ID : AutomaticClarificationBpmnConstants.PROCESS_ID;
        String element = "REPLY".equals(owner.kind()) ? CaseProcessBpmnConstants.CallActivity.TENANT_REPLY_CALL
                : CaseProcessBpmnConstants.CallActivity.AUTOMATIC_CLARIFICATION_CALL;
        ProcessInstance child = camunda.newProcessInstanceGetRequest(owner.child()).send().join();
        ElementInstance call = camunda.newElementInstanceGetRequest(owner.parent()).send().join();
        require(process.equals(child.getProcessDefinitionId())
                && Long.valueOf(job.getProcessInstanceKey()).equals(child.getParentProcessInstanceKey())
                && Long.valueOf(owner.parent()).equals(child.getParentElementInstanceKey()) && job.getTenantId().equals(child.getTenantId())
                && element.equals(call.getElementId())
                && CaseProcessBpmnConstants.PROCESS_ID.equals(call.getProcessDefinitionId())
                && Long.valueOf(job.getProcessInstanceKey()).equals(call.getProcessInstanceKey()) && job.getTenantId().equals(call.getTenantId()),
                "INVALID_SUCCESSOR_ORIGIN");
        if (!"ACTIVE".equals(child.getState().name()) || !"ACTIVE".equals(call.getState().name())) {
            throw new IllegalStateException("SUCCESSOR_NOT_YET_ACTIVE");
        }
    }

    private long liveStaff(Continuation continuation, String tenant) {
        if (continuation.childKey() == null || continuation.parentElementKey() == null || !continuation.reviewApplied()) {
            throw new IllegalStateException("STAFF_OWNER_NOT_YET_ACTIVE");
        }
        ProcessInstance child = camunda.newProcessInstanceGetRequest(continuation.childKey()).send().join();
        ElementInstance call = camunda.newElementInstanceGetRequest(continuation.parentElementKey()).send().join();
        require(StaffProcessingBpmnConstants.PROCESS_ID.equals(child.getProcessDefinitionId())
                && tenant.equals(child.getTenantId())
                && Long.valueOf(continuation.leader()).equals(child.getParentProcessInstanceKey())
                && continuation.parentElementKey().equals(child.getParentElementInstanceKey())
                && CaseProcessBpmnConstants.CallActivity.STAFF_PROCESSING_CALL.equals(call.getElementId())
                && CaseProcessBpmnConstants.PROCESS_ID.equals(call.getProcessDefinitionId())
                && Long.valueOf(continuation.leader()).equals(call.getProcessInstanceKey()) && tenant.equals(call.getTenantId()),
                "OTHER_STAFF_OWNER");
        if (!"ACTIVE".equals(child.getState().name()) || !"ACTIVE".equals(call.getState().name())) {
            throw new IllegalStateException("STAFF_OWNER_NOT_YET_ACTIVE");
        }
        return continuation.childKey();
    }

    private long liveReview(Continuation continuation, String tenant) {
        if (continuation.childKey() == null || !continuation.reviewApplied()) {
            throw new IllegalStateException("STAFF_REVIEW_NOT_YET_ACTIVE");
        }
        ProcessInstance instance = camunda.newProcessInstanceGetRequest(continuation.childKey()).send().join();
        require(StaffProcessingBpmnConstants.PROCESS_ID.equals(instance.getProcessDefinitionId())
                && tenant.equals(instance.getTenantId())
                && Long.valueOf(continuation.leader()).equals(instance.getParentProcessInstanceKey()), "OTHER_STAFF_PROCESS");
        if (!"ACTIVE".equals(instance.getState().name()) || camunda.newUserTaskSearchRequest()
                .filter(filter -> filter.processInstanceKey(continuation.childKey())
                        .elementId(StaffProcessingBpmnConstants.UserTask.REVIEW_TASK)
                        .state(UserTaskState.CREATED).tenantId(tenant))
                .send().join().items().size() != 1) {
            throw new IllegalStateException("STAFF_REVIEW_NOT_YET_ACTIVE");
        }
        return continuation.childKey();
    }

    private void root(ActivatedJob job, String element) {
        require(CaseProcessBpmnConstants.PROCESS_ID.equals(job.getBpmnProcessId())
                && element.equals(job.getElementId()), "WRONG_REVIEW_JOB");
        ProcessInstance instance = camunda.newProcessInstanceGetRequest(job.getProcessInstanceKey()).send().join();
        require(instance.getParentProcessInstanceKey() == null, "EXPECTED_LEADING_PROCESS");
    }

    private ProcessInstance child(ActivatedJob job, String element) {
        require(StaffProcessingBpmnConstants.PROCESS_ID.equals(job.getBpmnProcessId())
                && element.equals(job.getElementId()), "WRONG_STAFF_JOB");
        ProcessInstance instance = camunda.newProcessInstanceGetRequest(job.getProcessInstanceKey()).send().join();
        require(instance.getParentProcessInstanceKey() != null && instance.getParentElementInstanceKey() != null
                && Long.toString(instance.getParentProcessInstanceKey()).equals(text(job, LEADING_PROCESS_INSTANCE_KEY)), "WRONG_STAFF_PARENT");
        ElementInstance call = camunda.newElementInstanceGetRequest(instance.getParentElementInstanceKey()).send().join();
        require(CaseProcessBpmnConstants.CallActivity.STAFF_PROCESSING_CALL.equals(call.getElementId())
                && CaseProcessBpmnConstants.PROCESS_ID.equals(call.getProcessDefinitionId())
                && instance.getParentProcessInstanceKey().equals(call.getProcessInstanceKey()) && job.getTenantId().equals(call.getTenantId()),
                "WRONG_STAFF_CALL");
        return instance;
    }

    private void execute(JobClient client, ActivatedJob job, Supplier<Map<String, Object>> action) {
        Map<String, Object> result;
        try {
            result = action.get();
        } catch (RuntimeException exception) {
            boolean rejected = exception instanceof WorkflowRejectedException || exception instanceof IllegalArgumentException;
            client.newFailCommand(job.getKey()).retries(rejected ? 0 : Math.max(1, job.getRetries()))
                    .retryBackoff(Duration.ofSeconds(5))
                    .errorMessage(rejected ? "REVIEW_CONTRACT_REJECTED" : "REVIEW_TEMPORARILY_UNAVAILABLE").send().join();
            return;
        }
        client.newCompleteCommand(job.getKey()).variables(result).send().join();
    }

    private String text(ActivatedJob job, String name) {
        Object value = job.getVariablesAsMap().get(name);
        require(value instanceof String && !((String) value).isBlank(), "MISSING_REVIEW_REFERENCE");
        return (String) value;
    }

    private UUID uuid(ActivatedJob job, String name) {
        return UUID.fromString(text(job, name));
    }

    private void require(boolean condition, String reason) {
        if (!condition) {
            throw new WorkflowRejectedException(reason);
        }
    }
}
