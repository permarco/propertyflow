package com.pertegato.propertyflow.caseprocessing.workflow;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.CorrelateMessageResponse;
import java.util.Map;
import java.util.UUID;

import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.ACTION_TYPE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.CASE_DATA_COMMAND_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.CLARIFICATION_CLOSURE_COMMAND_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.CLEAR_ANSWER_COMMAND_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.DIRECT_FOLLOW_COMMAND_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.REPLY_CLOSURE_COMMAND_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.Variablen.STAFF_TAKEOVER_COMMAND_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.REVIEW_SIGNAL_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.STAFF_MESSAGE_COMMAND_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.TENANT_MESSAGE_ID;

enum CaseActionMessage {
    TENANT_MESSAGE(TENANT_MESSAGE_ID),
    CLEAR_ANSWER(CLEAR_ANSWER_COMMAND_REF),
    CLOSE_REPLY(REPLY_CLOSURE_COMMAND_REF),
    REVIEW_REQUIRED(REVIEW_SIGNAL_REF),
    STAFF_QUESTION(STAFF_MESSAGE_COMMAND_REF),
    CLOSE_CLARIFICATION(CLARIFICATION_CLOSURE_COMMAND_REF),
    DIRECT_FOLLOW_UP(DIRECT_FOLLOW_COMMAND_REF),
    STAFF_TAKEOVER(STAFF_TAKEOVER_COMMAND_REF),
    CASE_DATA(CASE_DATA_COMMAND_REF);

    static final String MESSAGE_NAME = "PropertyFlow-Fallaktion";
    private final String referenceVariable;

    CaseActionMessage(String referenceVariable) {
        this.referenceVariable = referenceVariable;
    }

    CorrelateMessageResponse correlate(CamundaClient client, String correlationKey, String tenant, UUID reference) {
        return client.newCorrelateMessageCommand()
                .messageName(MESSAGE_NAME)
                .correlationKey(correlationKey)
                .tenantId(tenant)
                .variables(Map.of(ACTION_TYPE, name(), referenceVariable, reference.toString()))
                .send()
                .join();
    }
}
