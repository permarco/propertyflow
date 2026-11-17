package com.pertegato.propertyflow.caseprocessing.workflow;

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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("propertyflow-backend & !frontend-demo")
@ConditionalOnProperty(name = "propertyflow.workflow.enabled", havingValue = "true")
public class StaffReviewWorkers {

    public static final String STAFF_PROCESS = "PropertyFlow_Staff_Draft";
    private final CamundaClient camunda;
    private final StaffReviewService service;

    public StaffReviewWorkers(CamundaClient camunda, StaffReviewService service) {
        this.camunda = camunda;
        this.service = service;
    }

    @JobWorker(type = "propertyflow.review.check", autoComplete = false)
    public void check(JobClient client, ActivatedJob job) {
        execute(client, job, () -> {
            root(job, "ReviewNeedCheck");
            ReviewRoute route = service.check(text(job, "caseId"), job.getProcessInstanceKey(), uuid(job, "reviewSignalRef"));
            return route.continuationId() == null ? Map.of("reviewRoute", route.route())
                    : Map.of("reviewRoute", route.route(), "reviewContinuationRef", route.continuationId().toString());
        });
    }

    @JobWorker(type = "propertyflow.review.interrupt-automatic", autoComplete = false)
    public void interruptAutomatic(JobClient client, ActivatedJob job) {
        execute(client, job, () -> {
            root(job, "ReviewNeedOther");
            Continuation continuation = service.read(text(job, "caseId"), job.getProcessInstanceKey(), uuid(job, "reviewContinuationRef"));
            if (!continuation.reviewApplied()) {
                // Explicit root-scope transfer. The boundary also compares the call reference, so a late
                // transfer cannot cancel a subsequent automatic call or a staff child.
                camunda.newSetVariablesCommand(job.getProcessInstanceKey())
                        .variables(Map.of("automaticInterruptionReady", true,
                                "automaticInterruptionCallRef", continuation.callRef().toString())).local(true).send().join();
            } else {
                StaffReviewService.StaffInterruption target = service.staffInterruption(text(job, "caseId"), job.getProcessInstanceKey(),
                        continuation.id(), uuid(job, "reviewSignalRef"));
                if (target != null) {
                    ProcessInstance staff = camunda.newProcessInstanceGetRequest(target.child()).send().join();
                    require(STAFF_PROCESS.equals(staff.getProcessDefinitionId())
                            && Long.valueOf(job.getProcessInstanceKey()).equals(staff.getParentProcessInstanceKey())
                            && job.getTenantId().equals(staff.getTenantId()), "OTHER_STAFF_GENERATION");
                    Map<String, Object> marker = switch (target.phase()) {
                        case "IN_PROGRESS" -> Map.of("pendingWorkReviewRef", target.reference().toString());
                        case "WAITING_FOR_EXTERNAL" -> Map.of("externalWaitReviewRequired", true, "reviewWaitRef", target.reference().toString());
                        case "WAITING_FOR_FOLLOW_UP" -> Map.of("pendingReviewFollowUpVersion", target.reference().toString(),
                                "phaseReviewSignalRef", text(job, "reviewSignalRef"));
                        default -> throw new WorkflowRejectedException("INVALID_REVIEW_PHASE");
                    };
                    camunda.newSetVariablesCommand(target.child()).variables(marker).local(true).send().join();
                }
            }
            return Map.of();
        });
    }

    @JobWorker(type = "propertyflow.review.prepare-staff", autoComplete = false)
    public void prepareStaff(JobClient client, ActivatedJob job) {
        execute(client, job, () -> {
            root(job, "AutomaticInterruptionPrepare");
            Interruption result = service.routeInterruption(text(job, "caseId"), job.getProcessInstanceKey(), uuid(job, "automaticCallRef"));
            return Map.of("STAFF".equals(result.outcome()) ? "staffEntryContextRef" : "replyEntryContextRef",
                    result.reference().toString(), "automaticInterruptionOutcome", result.outcome());
        });
    }

    @JobWorker(type = "propertyflow.staff.claim", autoComplete = false)
    public void claim(JobClient client, ActivatedJob job) {
        execute(client, job, () -> {
            root(job, "StaffContinuationClaim");
            UUID id = uuid(job, "staffEntryContextRef");
            Continuation continuation = service.read(text(job, "caseId"), job.getProcessInstanceKey(), id);
            ExternalOwner external = service.externalOwner(text(job, "caseId"), job.getProcessInstanceKey(), id);
            if (external != null) {
                verifyExternalOwner(external, job);
                return Map.of("staffContinuationOutcome", service.claimExternal(text(job, "caseId"), job.getProcessInstanceKey(), id, external));
            }
            Continuation owner = service.current(text(job, "caseId"), job.getProcessInstanceKey());
            Long liveChild = null;
            if (!owner.id().equals(id)) {
                liveChild = liveStaff(owner, job.getTenantId());
            } else if (continuation.claimElementKey() != null && continuation.claimElementKey() != job.getElementInstanceKey()) {
                liveChild = liveStaff(continuation, job.getTenantId());
            }
            String outcome = service.claim(text(job, "caseId"), job.getProcessInstanceKey(), id, job.getElementInstanceKey(), liveChild);
            return Map.of("staffContinuationOutcome", outcome);
        });
    }

    @JobWorker(type = "propertyflow.review.analysis-failure", autoComplete = false)
    public void analysisFailure(JobClient client, ActivatedJob job) {
        execute(client, job, () -> {
            root(job, "AutomaticFailurePrepare");
            Continuation continuation = service.analysisFailure(text(job, "caseId"), job.getProcessInstanceKey(), uuid(job, "automaticCallRef"));
            return Map.of("staffEntryContextRef", continuation.id().toString());
        });
    }

    @JobWorker(type = "propertyflow.staff.register", autoComplete = false)
    public void register(JobClient client, ActivatedJob job) {
        execute(client, job, () -> {
            ProcessInstance child = child(job, "SEntryCheck");
            service.register(text(job, "caseId"), child.getParentProcessInstanceKey(), uuid(job, "entryContextRef"),
                    job.getProcessInstanceKey(), child.getParentElementInstanceKey());
            String kind = service.read(text(job, "caseId"), child.getParentProcessInstanceKey(), uuid(job, "entryContextRef")).entryKind();
            String directWait = service.directWaitReference(uuid(job, "entryContextRef"));
            if (!directWait.isEmpty()) {
                return Map.of("staffEntryKind", kind, "staffDecisionRef", directWait, "followUpEntryContextRef", directWait,
                        "activeFollowUpVersion", directWait, "activeWorkTaskRef", "");
            }
            return Map.of("staffEntryKind", "CONTINUE".equals(kind) ? "WORK" : kind, "activeWorkTaskRef", service.workReference(text(job, "caseId"),
                    child.getParentProcessInstanceKey(), uuid(job, "entryContextRef")));
        });
    }

    @JobWorker(type = "propertyflow.staff.start-review", autoComplete = false)
    public void startReview(JobClient client, ActivatedJob job) {
        execute(client, job, () -> {
            ProcessInstance child = child(job, "SReviewStatus");
            service.startReview(text(job, "caseId"), child.getParentProcessInstanceKey(), uuid(job, "entryContextRef"),
                    job.getProcessInstanceKey());
            return Map.of();
        });
    }

    @JobWorker(type = "propertyflow.review.merge", autoComplete = false)
    public void merge(JobClient client, ActivatedJob job) {
        execute(client, job, () -> {
            root(job, "ReviewNeedExisting");
            ReviewRoute current = service.check(text(job, "caseId"), job.getProcessInstanceKey(), uuid(job, "reviewSignalRef"));
            if ("NONE".equals(current.route())) {
                return Map.of();
            }
            if (!"EXISTING".equals(current.route())) {
                throw new IllegalStateException("CURRENT_REVIEW_NOT_YET_READY");
            }
            UUID id = current.continuationId();
            Continuation continuation = service.read(text(job, "caseId"), job.getProcessInstanceKey(), id);
            long liveChild = liveReview(continuation, job.getTenantId());
            service.merge(text(job, "caseId"), job.getProcessInstanceKey(), id, uuid(job, "reviewSignalRef"), liveChild);
            return Map.of();
        });
    }

    @JobWorker(type = "propertyflow.staff.merge-continuation", autoComplete = false)
    public void mergeContinuation(JobClient client, ActivatedJob job) {
        execute(client, job, () -> {
            root(job, "StaffContinuationMerge");
            reconcile(job);
            return Map.of();
        });
    }

    @JobWorker(type = "propertyflow.staff.reconcile-continuation", autoComplete = false)
    public void reconcileContinuation(JobClient client, ActivatedJob job) {
        execute(client, job, () -> {
            root(job, "StaffContinuationReconcile");
            reconcile(job);
            return Map.of();
        });
    }

    private void reconcile(ActivatedJob job) {
        ExternalOwner external = service.externalOwner(text(job, "caseId"), job.getProcessInstanceKey(), uuid(job, "staffEntryContextRef"));
        if (external != null) {
            verifyExternalOwner(external, job);
            service.reconcileExternal(text(job, "caseId"), job.getProcessInstanceKey(), uuid(job, "staffEntryContextRef"), external);
            return;
        }
        Continuation current = service.current(text(job, "caseId"), job.getProcessInstanceKey());
        long liveChild = liveStaff(current, job.getTenantId());
        service.reconcile(text(job, "caseId"), job.getProcessInstanceKey(), uuid(job, "staffEntryContextRef"), current.id(), liveChild);
    }

    private void verifyExternalOwner(ExternalOwner owner, ActivatedJob job) {
        String process = "REPLY".equals(owner.kind()) ? TenantReplyWorkers.PROCESS : InitialCaseWorkers.AUTOMATIC_PROCESS;
        String element = "REPLY".equals(owner.kind()) ? "Reply" : "Automatic";
        ProcessInstance child = camunda.newProcessInstanceGetRequest(owner.child()).send().join();
        ElementInstance call = camunda.newElementInstanceGetRequest(owner.parent()).send().join();
        require(process.equals(child.getProcessDefinitionId()) && Long.valueOf(job.getProcessInstanceKey()).equals(child.getParentProcessInstanceKey())
                && Long.valueOf(owner.parent()).equals(child.getParentElementInstanceKey()) && job.getTenantId().equals(child.getTenantId())
                && element.equals(call.getElementId()) && InitialCaseWorkers.CASE_PROCESS.equals(call.getProcessDefinitionId())
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
        require(STAFF_PROCESS.equals(child.getProcessDefinitionId()) && tenant.equals(child.getTenantId())
                && Long.valueOf(continuation.leader()).equals(child.getParentProcessInstanceKey())
                && continuation.parentElementKey().equals(child.getParentElementInstanceKey())
                && "Staff".equals(call.getElementId()) && InitialCaseWorkers.CASE_PROCESS.equals(call.getProcessDefinitionId())
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
        require(STAFF_PROCESS.equals(instance.getProcessDefinitionId()) && tenant.equals(instance.getTenantId())
                && Long.valueOf(continuation.leader()).equals(instance.getParentProcessInstanceKey()), "OTHER_STAFF_PROCESS");
        if (!"ACTIVE".equals(instance.getState().name()) || camunda.newUserTaskSearchRequest()
                .filter(filter -> filter.processInstanceKey(continuation.childKey()).elementId("SReview")
                        .state(UserTaskState.CREATED).tenantId(tenant))
                .send().join().items().size() != 1) {
            throw new IllegalStateException("STAFF_REVIEW_NOT_YET_ACTIVE");
        }
        return continuation.childKey();
    }

    private void root(ActivatedJob job, String element) {
        require(InitialCaseWorkers.CASE_PROCESS.equals(job.getBpmnProcessId()) && element.equals(job.getElementId()), "WRONG_REVIEW_JOB");
        ProcessInstance instance = camunda.newProcessInstanceGetRequest(job.getProcessInstanceKey()).send().join();
        require(instance.getParentProcessInstanceKey() == null, "EXPECTED_LEADING_PROCESS");
    }

    private ProcessInstance child(ActivatedJob job, String element) {
        require(STAFF_PROCESS.equals(job.getBpmnProcessId()) && element.equals(job.getElementId()), "WRONG_STAFF_JOB");
        ProcessInstance instance = camunda.newProcessInstanceGetRequest(job.getProcessInstanceKey()).send().join();
        require(instance.getParentProcessInstanceKey() != null && instance.getParentElementInstanceKey() != null
                && Long.toString(instance.getParentProcessInstanceKey()).equals(text(job, "leadingProcessInstanceKey")), "WRONG_STAFF_PARENT");
        ElementInstance call = camunda.newElementInstanceGetRequest(instance.getParentElementInstanceKey()).send().join();
        require("Staff".equals(call.getElementId()) && InitialCaseWorkers.CASE_PROCESS.equals(call.getProcessDefinitionId())
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
