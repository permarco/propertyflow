package com.pertegato.propertyflow.caseprocessing.contract;

import com.pertegato.propertyflow.access.contract.WebAccountContext;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface StaffCaseDataCommands {

    boolean available(WebAccountContext context, String caseId);

    CaseDataResult submit(WebAccountContext context, String caseId, String operationId, long expectedVersion, String text);

    CaseDataResult submitUrgency(WebAccountContext context, String caseId, String operationId, long expectedVersion, Urgency urgency, String reason);

    CaseDataResult submitAssignment(WebAccountContext context, String caseId, String operationId, long expectedVersion, String assignment);

    List<String> assignmentSuggestions(WebAccountContext context, String query);

    CaseDataResult read(WebAccountContext context, String caseId, String operationId);

    default CaseDataResult submit(WebAccountContext context, String caseId, UUID operationId, long expectedVersion, String text) {
        return submit(context, caseId, operationId.toString(), expectedVersion, text);
    }

    default CaseDataResult submitUrgency(WebAccountContext context, String caseId, UUID operationId, long expectedVersion,
            Urgency urgency, String reason) {
        return submitUrgency(context, caseId, operationId.toString(), expectedVersion, urgency, reason);
    }

    default CaseDataResult submitAssignment(WebAccountContext context, String caseId, UUID operationId, long expectedVersion, String assignment) {
        return submitAssignment(context, caseId, operationId.toString(), expectedVersion, assignment);
    }

    default CaseDataResult read(WebAccountContext context, String caseId, UUID operationId) {
        return read(context, caseId, operationId.toString());
    }

    CaseDataResult submitMemo(WebAccountContext context, String caseId, String operation, String text);

    CaseDataResult clearAnswer(WebAccountContext context, String caseId, String operation, long version, UUID message);

    boolean takeoverAvailable(WebAccountContext context, String caseId);

    CaseDataResult takeOver(WebAccountContext context, String caseId, String operation, long version);

    ClosureApproval clarificationClosure(WebAccountContext context, String caseId);

    CaseDataResult closeClarification(WebAccountContext context, String caseId, String operation, long version, String reviewToken);

    ClosureApproval replyClosure(WebAccountContext context, String caseId);

    CaseDataResult closeReply(WebAccountContext context, String caseId, String operation, long version, String reviewToken);

    ClosureApproval directFollowUp(WebAccountContext context, String caseId);

    CaseDataResult setDirectFollowUp(WebAccountContext context, String caseId, String operation, long version,
            String reviewToken, Integer days, LocalDate date, String reason);

    record ClosureApproval(boolean available, long version, String reviewToken) {
    }

    record CaseDataResult(String operationId, String state, String code, Long version) {
    }
}
