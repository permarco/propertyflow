package com.pertegato.propertyflow.caseprocessing.workflow;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.DeploymentEvent;

/**
 * Deployt die unveränderten Anwendungsmodelle gemeinsam, damit deployment-gebundene Calls dieselbe Version verwenden.
 */
final class WorkflowTestModels {

    private WorkflowTestModels() {
    }

    static DeploymentEvent deploy(CamundaClient engine) {
        return engine.newDeployResourceCommand()
                .addResourceFromClasspath("bpmn/propertyflow-fallprozess-entwurf.bpmn")
                .addResourceFromClasspath("bpmn/automatische-abklaerung.bpmn")
                .addResourceFromClasspath("bpmn/gespraech-auswerten.bpmn")
                .addResourceFromClasspath("bpmn/mitarbeiterbearbeitung.bpmn")
                .addResourceFromClasspath("bpmn/mieterantwort-pruefen.bpmn")
                .addResourceFromClasspath("bpmn/mieternachricht-verarbeiten.bpmn")
                .addResourceFromClasspath("bpmn/wiedervorlage.bpmn")
                .addResourceFromClasspath("bpmn/fallbenachrichtigung-versenden.bpmn")
                .send().join();
    }
}
