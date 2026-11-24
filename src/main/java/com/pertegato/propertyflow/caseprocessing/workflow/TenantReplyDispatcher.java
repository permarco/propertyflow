package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.contract.bpmn.TenantReplyBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantReplyStore;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantReplyStore.Call;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantReplyStore.Delivery;
import com.pertegato.propertyflow.caseprocessing.service.TenantReplyService;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.ProcessInstance;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.REVIEW_NEED_REPLY_CALL_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.TenantReplyBpmnConstants.Variablen.TENANT_EVALUATION_VERSION;

@Component
@Profile("propertyflow-backend & !frontend-demo")
@ConditionalOnProperty(name = "propertyflow.workflow.enabled", havingValue = "true")
@RequiredArgsConstructor
public class TenantReplyDispatcher {

    private final TenantReplyStore replies;
    private final TenantReplyService service;
    private final CamundaClient engine;

    @Scheduled(fixedDelayString = "${propertyflow.workflow.poll-delay-ms:5000}",
            initialDelayString = "${propertyflow.workflow.initial-delay-ms:5000}")
    public void dispatch() {
        for (int index = 0; index < 20; index++) {
            Delivery delivery = replies.claim().orElse(null);
            if (delivery == null) {
                return;
            }
            try {
                Call call = delivery.call();
                ProcessInstance child = engine.newProcessInstanceGetRequest(call.child())
                        .send()
                        .join();
                if (!TenantReplyBpmnConstants.PROCESS_ID.equals(child.getProcessDefinitionId())
                        || !Long.valueOf(call.leader()).equals(child.getParentProcessInstanceKey())
                        || !Long.valueOf(call.parent()).equals(child.getParentElementInstanceKey())
                        || !call.tenant().equals(child.getTenantId()) || !"ACTIVE".equals(child.getState().name())) {
                    continue;
                }
                if (delivery.review()) {
                    if ("REVIEW".equals(service.review(call.id()).state())) {
                        engine.newSetVariablesCommand(call.leader())
                                .variables(Map.of(REVIEW_NEED_REPLY_CALL_REF, call.id().toString()))
                                .send()
                                .join();
                        replies.reviewDelivered(call.id());
                    }
                } else if (delivery.version() > call.consumed()) {
                    // Die Zielinstanz und der Verbrauchsstand verhindern verlorene frühe und doppelte späte Ergebnisse.
                    engine.newSetVariablesCommand(call.child())
                            .variables(Map.of(TENANT_EVALUATION_VERSION, delivery.version()))
                            .local(true)
                            .send()
                            .join();
                }
            } catch (RuntimeException exception) {
                // Die dauerhafte Zustellung bleibt offen; ein unklarer Engine-Empfang wird erneut abgeglichen.
            }
        }
    }
}
