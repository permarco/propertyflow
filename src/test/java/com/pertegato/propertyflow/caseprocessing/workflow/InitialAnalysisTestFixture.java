package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import com.pertegato.propertyflow.caseprocessing.persistence.ConversationEvaluationStore;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantMessageStore;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantReplyStore;
import com.pertegato.propertyflow.caseprocessing.persistence.UrgencyStore;
import com.pertegato.propertyflow.caseprocessing.service.AutomaticClosureRelease;
import com.pertegato.propertyflow.caseprocessing.service.ConversationEvaluationService;
import com.pertegato.propertyflow.recommendation.service.Recommendation;
import com.pertegato.propertyflow.triage.service.ConversationAnalysis;
import io.camunda.client.CamundaClient;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import static com.pertegato.propertyflow.recommendation.ClosureAssessmentFixtures.noClosure;

final class InitialAnalysisTestFixture {

    private InitialAnalysisTestFixture() {
    }

    static AutomaticEvaluationWorkers failedEvaluation(CamundaClient engine, CaseWorkflowStore workflow, JdbcTemplate database,
            PlatformTransactionManager transactions) {
        return evaluation(engine, workflow, database, transactions,
                input -> new ConversationAnalysis.Result(null, true, List.of(), "INVALID_MODEL_OUTPUT", "test", "v1"));
    }

    static AutomaticEvaluationWorkers evaluation(CamundaClient engine, CaseWorkflowStore workflow, JdbcTemplate database,
            PlatformTransactionManager transactions, ConversationAnalysis analyzer) {
        ConversationEvaluationService service = new ConversationEvaluationService(workflow, new TenantMessageStore(database),
                new ConversationEvaluationStore(database), new UrgencyStore(database), analyzer,
                input -> new Recommendation.Result("Verwaltung", "Sachverhalt prüfen.", List.of(), List.of(), false, null, "test", "v1"),
                noClosure(), transactions, new AutomaticClosureRelease(), new TenantReplyStore(database));
        return new AutomaticEvaluationWorkers(engine, service);
    }
}
