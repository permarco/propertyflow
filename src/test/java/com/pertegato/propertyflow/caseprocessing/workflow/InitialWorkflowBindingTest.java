package com.pertegato.propertyflow.caseprocessing.workflow;

import java.nio.file.Path;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InitialWorkflowBindingTest {

    @Test
    void executableFixtureUsesCanonicalProcessAndWorkerBindings() throws Exception {
        verify("propertyflow-fallprozess-entwurf.bpmn", "initial-case.bpmn", InitialCaseWorkers.CASE_PROCESS,
                Map.of("PrepareProcessKey", InitialCaseWorkers.PREPARE_KEY, "Register", InitialCaseWorkers.REGISTER,
                        "PrepareInitialAuto", InitialCaseWorkers.PREPARE_INITIAL));
        verify("automatische-abklaerung.bpmn", "initial-automatic.bpmn", InitialCaseWorkers.AUTOMATIC_PROCESS,
                Map.of("AEntry", InitialCaseWorkers.REGISTER_AUTOMATIC, "Clarification", InitialCaseWorkers.CLARIFICATION));
    }

    private void verify(String canonical, String fixture, String processId, Map<String, String> bindings) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        Document original = factory.newDocumentBuilder().parse(Path.of("src", "main", "resources", "bpmn", canonical).toFile());
        Document tested = factory.newDocumentBuilder().parse(Path.of("src", "test", "resources", "workflow", fixture).toFile());
        XPath xpath = XPathFactory.newInstance().newXPath();
        String process = "//*[@id='" + processId + "']/@isExecutable";
        assertEquals("true", xpath.evaluate(process, original));
        assertEquals("true", xpath.evaluate(process, tested));
        for (Map.Entry<String, String> binding : bindings.entrySet()) {
            String expression = "//*[@id='" + binding.getKey() + "']/*[name()='bpmn:extensionElements']/*[name()='zeebe:taskDefinition']/@type";
            assertEquals(binding.getValue(), xpath.evaluate(expression, original));
            assertEquals(binding.getValue(), xpath.evaluate(expression, tested));
        }
        if (processId.equals(InitialCaseWorkers.CASE_PROCESS)) {
            String call = "//*[@id='Automatic']/*[name()='bpmn:extensionElements']/*[name()='zeebe:calledElement']";
            for (String attribute : new String[] {"processId", "bindingType", "propagateAllChildVariables", "propagateAllParentVariables"}) {
                assertEquals(xpath.evaluate(call + "/@" + attribute, original), xpath.evaluate(call + "/@" + attribute, tested));
            }
        }
    }
}
