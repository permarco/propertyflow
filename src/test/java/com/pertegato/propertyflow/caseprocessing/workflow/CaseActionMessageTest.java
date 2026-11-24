package com.pertegato.propertyflow.caseprocessing.workflow;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.ClientHttpException;
import io.camunda.client.api.response.CorrelateMessageResponse;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.ACTION_TYPE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.TENANT_MESSAGE_ID;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CaseActionMessageTest {

    private static final String LEADER = "4503599627370497";
    private static final String TENANT = "test";
    private static final UUID REFERENCE = UUID.fromString("01a1e48a-4428-7908-854a-2526494599f3");
    private final CamundaClient client = mock(CamundaClient.class, RETURNS_DEEP_STUBS);

    @Test
    void sendsActionAndReferenceToTheCaseActionReceiver() {
        CorrelateMessageResponse response = mock(CorrelateMessageResponse.class);
        when(actionSend().join()).thenReturn(response);
        clearInvocations(client);

        assertSame(response, CaseActionMessage.TENANT_MESSAGE.correlate(client, LEADER, TENANT, REFERENCE));
        verify(client).newCorrelateMessageCommand();
    }

    @Test
    void propagatesTimeoutWithoutSendingAgain() {
        RuntimeException failure = new CompletionException(new TimeoutException());
        when(actionSend().join()).thenThrow(failure);
        clearInvocations(client);

        assertSame(failure, assertThrows(CompletionException.class,
                () -> CaseActionMessage.TENANT_MESSAGE.correlate(client, LEADER, TENANT, REFERENCE)));
        verify(client).newCorrelateMessageCommand();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 404, 409, 503})
    void propagatesHttpFailuresWithoutSendingAgain(int status) {
        ClientHttpException failure = new ClientHttpException(status, "Correlation failed");
        when(actionSend().join()).thenThrow(failure);
        clearInvocations(client);

        assertSame(failure, assertThrows(ClientHttpException.class,
                () -> CaseActionMessage.TENANT_MESSAGE.correlate(client, LEADER, TENANT, REFERENCE)));
        verify(client).newCorrelateMessageCommand();
    }

    private CamundaFuture<CorrelateMessageResponse> actionSend() {
        return client.newCorrelateMessageCommand()
                .messageName("PropertyFlow-Fallaktion")
                .correlationKey(LEADER)
                .tenantId(TENANT)
                .variables(Map.of(ACTION_TYPE, "TENANT_MESSAGE", TENANT_MESSAGE_ID, REFERENCE.toString()))
                .send();
    }
}
