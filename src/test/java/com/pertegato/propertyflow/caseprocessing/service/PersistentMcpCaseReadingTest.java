package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.access.contract.McpAccountContext;
import com.pertegato.propertyflow.access.contract.McpScope;
import com.pertegato.propertyflow.access.contract.McpTenantAuthorization;
import com.pertegato.propertyflow.access.contract.TenantAccountAccess;
import com.pertegato.propertyflow.caseprocessing.contract.CaseErrorCode;
import com.pertegato.propertyflow.caseprocessing.contract.CaseServiceException;
import com.pertegato.propertyflow.caseprocessing.contract.CaseStatus;
import com.pertegato.propertyflow.caseprocessing.contract.CaseStatusView;
import com.pertegato.propertyflow.caseprocessing.delivery.PersonalLinkCodec;
import com.pertegato.propertyflow.caseprocessing.persistence.AcceptanceMailOrderRepository;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseAccessRepository;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseEntity;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseRepository;
import com.pertegato.propertyflow.caseprocessing.persistence.ProcessStartOrderRepository;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffMessageStore;
import com.pertegato.propertyflow.caseprocessing.persistence.SubmissionRepository;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantMessageStore;
import com.pertegato.propertyflow.caseprocessing.persistence.UrgencyStore;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PersistentMcpCaseReadingTest {

    private static final String EMAIL = "tenant@example.test";
    private final McpAccountContext context = new McpAccountContext(UUID.randomUUID(), UUID.randomUUID(), "test-client");
    private final McpTenantAuthorization authorization = mock(McpTenantAuthorization.class);
    private final TenantAccountAccess accounts = mock(TenantAccountAccess.class);
    private final CaseRepository cases = mock(CaseRepository.class);
    private PersistentMcpCaseReading service;

    @BeforeEach
    void setup() {
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any(TransactionDefinition.class))).thenAnswer(invocation -> new SimpleTransactionStatus());
        PersistentCaseService persistent = new PersistentCaseService(cases, mock(CaseAccessRepository.class),
                mock(SubmissionRepository.class), mock(ProcessStartOrderRepository.class), mock(AcceptanceMailOrderRepository.class),
                mock(PersonalLinkCodec.class), transactions, mock(UrgencyStore.class), mock(TenantMessageStore.class),
                mock(StaffMessageStore.class), accounts);
        service = new PersistentMcpCaseReading(authorization, persistent, null);
    }

    @Test
    void readsOwnCaseAndRejectsAccessAsSoonAsContactAddressChanges() {
        allow();
        when(cases.findById("case-a")).thenReturn(Optional.of(stored("case-a", EMAIL)));
        assertEquals(new CaseStatusView("case-a", CaseStatus.RECEIVED, Optional.empty()), service.readStatusForMcp(context, "case-a"));

        when(cases.findById("case-a")).thenReturn(Optional.of(stored("case-a", "other@example.test")));
        CaseServiceException foreign = assertThrows(CaseServiceException.class, () -> service.readStatusForMcp(context, "case-a"));
        CaseServiceException missing = assertThrows(CaseServiceException.class, () -> service.readStatusForMcp(context, "unknown"));
        assertEquals(CaseErrorCode.CASE_NOT_AVAILABLE, foreign.code());
        assertEquals(foreign.code(), missing.code());
        assertEquals(foreign.fieldErrors(), missing.fieldErrors());
    }

    @Test
    void rechecksDelegationAndAvoidsFallLookupAfterRevocation() {
        when(authorization.allows(context, McpScope.TENANT_CASES)).thenReturn(true, false);
        when(accounts.verifiedEmail(context.accountId())).thenReturn(Optional.of(EMAIL));
        when(cases.findById("case-a")).thenReturn(Optional.of(stored("case-a", EMAIL)));
        service.readStatusForMcp(context, "case-a");

        CaseServiceException denied = assertThrows(CaseServiceException.class, () -> service.readStatusForMcp(context, "case-a"));
        assertEquals(CaseErrorCode.FORBIDDEN, denied.code());
        verify(cases).findById("case-a");
    }

    @Test
    void inactiveAccountAndInvalidIdNeverReachCaseRepository() {
        when(authorization.allows(context, McpScope.TENANT_CASES)).thenReturn(true);
        CaseServiceException invalid = assertThrows(CaseServiceException.class, () -> service.readStatusForMcp(context, "wrong/value"));
        CaseServiceException inactive = assertThrows(CaseServiceException.class, () -> service.readStatusForMcp(context, "case-a"));
        assertEquals(CaseErrorCode.INVALID_INPUT, invalid.code());
        assertEquals(CaseErrorCode.UNAUTHENTICATED, inactive.code());
        verifyNoInteractions(cases);
    }

    private void allow() {
        when(authorization.allows(context, McpScope.TENANT_CASES)).thenReturn(true);
        when(accounts.verifiedEmail(context.accountId())).thenReturn(Optional.of(EMAIL));
    }

    private static CaseEntity stored(String id, String email) {
        return new CaseEntity(id, "Testfall", "Testwohnung", "Nicht Teil des MCP-Ergebnisses", email, Instant.EPOCH);
    }
}
