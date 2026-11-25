package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.access.contract.McpAccountContext;
import com.pertegato.propertyflow.access.contract.McpScope;
import com.pertegato.propertyflow.access.contract.McpTenantAuthorization;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.caseprocessing.contract.CaseErrorCode;
import com.pertegato.propertyflow.caseprocessing.contract.CaseServiceException;
import com.pertegato.propertyflow.caseprocessing.contract.CaseStatus;
import com.pertegato.propertyflow.caseprocessing.contract.CaseStatusView;
import com.pertegato.propertyflow.caseprocessing.contract.McpCaseReading;
import com.pertegato.propertyflow.caseprocessing.contract.StaffCasePage;
import com.pertegato.propertyflow.caseprocessing.contract.TenantCaseView;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("propertyflow-backend")
@ConditionalOnProperty(name = "propertyflow.mcp.backend.enabled", havingValue = "true")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class PersistentMcpCaseReading implements McpCaseReading {

    private static final Pattern CASE_ID = Pattern.compile(CASE_ID_PATTERN);
    private final McpTenantAuthorization authorization;
    private final PersistentCaseService cases;
    private final StaffCaseService staffCases;

    PersistentMcpCaseReading(McpTenantAuthorization authorization, PersistentCaseService cases, StaffCaseService staffCases) {
        this.authorization = authorization;
        this.cases = cases;
        this.staffCases = staffCases;
    }

    @Override
    public CaseStatusView readStatusForMcp(McpAccountContext context, String caseId) {
        require(context, McpScope.TENANT_CASES);
        checkCaseId(caseId);
        return cases.readStatusForAccount(context.accountId(), caseId);
    }

    @Override
    public List<TenantCaseSummary> listForMcp(McpAccountContext context) {
        require(context, McpScope.TENANT_CASES);
        return cases.listForAccount(context.accountId()).stream()
                .map(view -> new TenantCaseSummary(view.state(), view.title()))
                .toList();
    }

    @Override
    public StaffCasePage listForMcpStaff(McpAccountContext context, CaseStateFilter state, CaseStatus status, String urgency, int page) {
        require(context, McpScope.STAFF_CASES);
        return staffCases.listForMcp(new WebAccountContext(context.accountId()), state, status, urgency, page);
    }

    @Override
    public StaffCaseDetail readForMcpStaff(McpAccountContext context, String caseId) {
        TenantCaseView view = readPublicForMcp(context, caseId, true);
        return new StaffCaseDetail(view.state(), view.title(), view.messages());
    }

    @Override
    public TenantCaseView readPublicForMcp(McpAccountContext context, String caseId, boolean staff) {
        require(context, staff ? McpScope.STAFF_CASES : McpScope.TENANT_CASES);
        checkCaseId(caseId);
        return staff ? staffCases.readPublicForStaff(new WebAccountContext(context.accountId()), caseId)
                : cases.readForAccount(context.accountId(), caseId);
    }

    private void require(McpAccountContext context, McpScope scope) {
        if (context == null || !authorization.allows(context, scope)) {
            throw new CaseServiceException(CaseErrorCode.FORBIDDEN, Map.of());
        }
    }

    private static void checkCaseId(String caseId) {
        if (caseId == null || !CASE_ID.matcher(caseId).matches()) {
            throw new CaseServiceException(CaseErrorCode.INVALID_INPUT, Map.of("caseId", "INVALID"));
        }
    }
}
