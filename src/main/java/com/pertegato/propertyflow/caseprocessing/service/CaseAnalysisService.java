package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.caseprocessing.contract.Urgency;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseAnalysisStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseAnalysisStore.Analysis;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseAnalysisStore.Input;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.CaseState;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.Entry;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffReviewStore;
import com.pertegato.propertyflow.triage.service.AnalyzeTool;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("propertyflow-backend & !frontend-demo")
public class CaseAnalysisService {

    private static final Duration ATTEMPT_LEASE = Duration.ofMinutes(2);
    private final CaseWorkflowStore workflow;
    private final StaffReviewStore reviews;
    private final CaseAnalysisStore analyses;
    private final CaseUrgencyService urgency;
    private final AnalyzeTool analyzer;
    private final TransactionTemplate transaction;

    public CaseAnalysisService(CaseWorkflowStore workflow, StaffReviewStore reviews, CaseAnalysisStore analyses,
            CaseUrgencyService urgency, AnalyzeTool analyzer, PlatformTransactionManager transactions) {
        this.workflow = workflow;
        this.reviews = reviews;
        this.analyses = analyses;
        this.urgency = urgency;
        this.analyzer = analyzer;
        this.transaction = new TransactionTemplate(transactions);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Result analyze(String caseId, UUID entryId, UUID callRef, long leader, long child, long jobKey) {
        if (jobKey <= 0) {
            throw new WorkflowRejectedException("INVALID_ANALYSIS_JOB");
        }
        Work work = transaction.execute(status -> begin(caseId, entryId, callRef, leader, child, jobKey));
        if (work.result() != null) {
            return work.result();
        }
        // No database lock/transaction is held across the external model call.
        AnalyzeTool.Response response = analyzer.analyze(new AnalyzeTool.Request(work.input().text()));
        return transaction.execute(status -> finish(caseId, entryId, callRef, leader, child, work, response));
    }

    private Work begin(String caseId, UUID entryId, UUID callRef, long leader, long child, long jobKey) {
        CaseState state = verify(caseId, entryId, callRef, leader, child);
        if (!allowed(state, callRef)) {
            return new Work(null, null, new Result("SUPERSEDED", null));
        }
        Input input = analyses.input(caseId);
        Analysis existing = analyses.find(callRef, input.version()).orElse(null);
        if (existing != null) {
            if ("SUCCEEDED".equals(existing.state()) || "FAILED".equals(existing.state())) {
                return new Work(null, null, new Result(existing.state(), existing.id()));
            }
            if ("RUNNING".equals(existing.state()) && existing.startedAt().plus(ATTEMPT_LEASE).isAfter(Instant.now())) {
                return new Work(null, null, new Result("BUSY", existing.id()));
            }
        }
        Analysis attempt = analyses.begin(caseId, callRef, input, AnalyzeTool.PROMPT_VERSION, jobKey);
        return new Work(input, attempt, null);
    }

    private Result finish(String caseId, UUID entryId, UUID callRef, long leader, long child, Work work, AnalyzeTool.Response response) {
        CaseState state = verify(caseId, entryId, callRef, leader, child);
        Analysis current = analyses.find(callRef, work.input().version()).orElseThrow();
        if (!current.attemptId().equals(work.attempt().attemptId()) || !"RUNNING".equals(current.state())) {
            return new Result("SUPERSEDED", current.id());
        }
        if (!allowed(state, callRef) || analyses.input(caseId).version() != work.input().version()) {
            analyses.finish(current.id(), "SUPERSEDED", null, null);
            return new Result("SUPERSEDED", current.id());
        }
        if (response instanceof AnalyzeTool.Success success) {
            Urgency assessment = switch (success.urgency()) {
                case KRITISCH -> Urgency.CRITICAL;
                case HOCH -> Urgency.HIGH;
                case NORMAL -> Urgency.NORMAL;
                case NIEDRIG -> Urgency.LOW;
            };
            analyses.finish(current.id(), "SUCCEEDED", assessment, null);
            urgency.system(caseId, current.id(), assessment);
            return new Result("SUCCEEDED", current.id());
        }
        AnalyzeTool.Failure failure = (AnalyzeTool.Failure) response;
        analyses.finish(current.id(), "FAILED", null, failure.code().name());
        return new Result("FAILED", current.id());
    }

    private CaseState verify(String caseId, UUID entryId, UUID callRef, long leader, long child) {
        CaseState state = workflow.lockCase(caseId).orElseThrow(() -> new WorkflowRejectedException("UNKNOWN_CASE"));
        Entry entry = workflow.entryForCase(caseId).orElseThrow(() -> new WorkflowRejectedException("MISSING_AUTOMATIC_ENTRY"));
        if (workflow.leadingInstance(caseId).filter(key -> key == leader).isEmpty() || entry.leadingKey() != leader
                || !entry.id().equals(entryId) || !entry.callRef().equals(callRef) || entry.childKey() == null || entry.childKey() != child) {
            throw new WorkflowRejectedException("OTHER_ANALYSIS_GENERATION");
        }
        return state;
    }

    private boolean allowed(CaseState state, UUID callRef) {
        return "UNDER_CLARIFICATION".equals(state.status()) && !reviews.blocked(callRef);
    }

    public record Result(String state, UUID analysisId) {
    }

    private record Work(Input input, Analysis attempt, Result result) {
    }
}
