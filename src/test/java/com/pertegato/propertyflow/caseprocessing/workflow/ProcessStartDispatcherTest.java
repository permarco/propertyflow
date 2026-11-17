package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.StartOrder;
import com.pertegato.propertyflow.caseprocessing.workflow.ProcessStartGateway.StartResult;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessStartDispatcherTest {

    @Test
    void successfulStartAndConflictStillRequireRegistration() {
        verifyResult(StartResult.STARTED, false);
        verifyResult(StartResult.EXISTING, false);
    }

    @Test
    void ambiguousResponseRetriesSameOrder() {
        verifyResult(StartResult.UNKNOWN, false);
    }

    @Test
    void rejectedOrAlreadyEndedStartRequiresReconciliation() {
        verifyResult(StartResult.REJECTED, true);
        verifyResult(StartResult.ENDED_BEFORE_REGISTRATION, true);
    }

    private void verifyResult(StartResult result, boolean blocked) {
        CaseWorkflowStore store = mock(CaseWorkflowStore.class);
        ProcessStartGateway gateway = mock(ProcessStartGateway.class);
        StartOrder order = new StartOrder(UUID.randomUUID(), "case", 100, "<default>", UUID.randomUUID(), 1);
        when(store.claim(eq(100L), eq("<default>"), any())).thenReturn(Optional.of(order), Optional.empty());
        when(gateway.ensureStarted(order)).thenReturn(result);
        new ProcessStartDispatcher(store, gateway, 100, "<default>").dispatch();
        verify(gateway).ensureStarted(order);
        verify(store).finishAttempt(order, result.name(), blocked, Duration.ofSeconds(5));
    }
}
