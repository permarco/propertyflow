package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseNotificationBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.ConversationEvaluationBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.FollowUpBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.TenantMessageBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.TenantReplyBpmnConstants;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants.JobType.REGISTER_AUTOMATIC;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants.JobType.START_CLARIFICATION;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.PREPARE_INITIAL;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.PREPARE_PROCESS_KEY;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.JobType.REGISTER_CASE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InitialWorkflowBindingTest {

    private static final Path MODELS = Path.of("src", "main", "resources", "bpmn");
    private static final Map<String, String> NAMED_TYPES = Map.ofEntries(
            Map.entry("process", "PROCESS"), Map.entry("serviceTask", "SERVICE"), Map.entry("userTask", "USERTASK"),
            Map.entry("sendTask", "SENDTASK"), Map.entry("callActivity", "CALL"), Map.entry("subProcess", "SUBPROCESS"),
            Map.entry("startEvent", "START"), Map.entry("endEvent", "END"), Map.entry("intermediateCatchEvent", "CATCH"),
            Map.entry("intermediateThrowEvent", "THROW"), Map.entry("boundaryEvent", "BOUNDARY"),
            Map.entry("exclusiveGateway", "XOR"), Map.entry("eventBasedGateway", "EVENT_GATEWAY"), Map.entry("sequenceFlow", "FLOW"),
            Map.entry("lane", "LANE"), Map.entry("group", "GROUP"));

    @Test
    void javaConstantsMatchTheirCanonicalBpmnModel() throws Exception {
        Map<Class<?>, String> models = Map.of(
                CaseProcessBpmnConstants.class, "propertyflow-fallprozess-entwurf.bpmn",
                AutomaticClarificationBpmnConstants.class, "automatische-abklaerung.bpmn",
                ConversationEvaluationBpmnConstants.class, "gespraech-auswerten.bpmn",
                TenantReplyBpmnConstants.class, "mieterantwort-pruefen.bpmn",
                StaffProcessingBpmnConstants.class, "mitarbeiterbearbeitung.bpmn",
                FollowUpBpmnConstants.class, "wiedervorlage.bpmn",
                TenantMessageBpmnConstants.class, "mieternachricht-verarbeiten.bpmn",
                CaseNotificationBpmnConstants.class, "fallbenachrichtigung-versenden.bpmn");
        XPath xpath = XPathFactory.newInstance().newXPath();
        for (Map.Entry<Class<?>, String> model : models.entrySet()) {
            Document document = factory().newDocumentBuilder().parse(MODELS.resolve(model.getValue()).toFile());
            Class<?> ids = model.getKey();
            assertTrue(ids.getSimpleName().matches("[A-Z][A-Za-z0-9]*BpmnConstants"), ids.getName());
            assertEquals(1, ids.getFields().length, ids.getName());
            assertModelId(xpath, document, "process", ids.getField("PROCESS_ID"));
            for (Class<?> group : ids.getDeclaredClasses()) {
                int modifiers = group.getModifiers();
                assertTrue(Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers), group.getName());
                if (Set.of("Variablen", "JobType", "ErrorCode", "EscalationCode").contains(group.getSimpleName())) {
                    continue;
                }
                String name = group.getSimpleName();
                String elementType = Character.toLowerCase(name.charAt(0)) + name.substring(1);
                for (Field field : group.getFields()) {
                    assertModelId(xpath, document, elementType, field);
                }
            }
        }
    }

    private void assertModelId(XPath xpath, Document document, String elementType, Field field) throws Exception {
        String id = (String) field.get(null);
        String element = "//*[name()='bpmn:" + elementType + "'][@id='" + id + "']";
        assertEquals("1", xpath.evaluate("count(" + element + ")", document), field.toString());
    }

    @Test
    void canonicalModelsUseAsciiNamesAndTypePostfixes() throws Exception {
        List<Path> models;
        try (Stream<Path> files = Files.list(MODELS)) {
            models = files.filter(path -> path.toString().endsWith(".bpmn")).sorted().toList();
        }
        assertTrue(!models.isEmpty(), "BPMN-Modelle fehlen");
        for (Path model : models) {
            Document document = factory().newDocumentBuilder().parse(model.toFile());
            NodeList nodes = document.getElementsByTagName("*");
            Set<String> ids = new HashSet<>();
            for (int index = 0; index < nodes.getLength(); index++) {
                Element element = (Element) nodes.item(index);
                if (!element.hasAttribute("id")) {
                    continue;
                }
                String id = element.getAttribute("id");
                assertTrue(id.matches("[A-Za-z][A-Za-z0-9_]*"), model + ": " + id);
                assertTrue(ids.add(id), model + ": " + id);
                String tag = element.getTagName().replace("bpmn:", "");
                String suffix = NAMED_TYPES.get(tag);
                if (suffix == null) {
                    continue;
                }
                assertTrue(id.endsWith("_" + suffix), model + ": " + id);
                String name = element.getAttribute("name");
                assertTrue(name.chars().noneMatch(character -> "äöüÄÖÜßẞ".indexOf(character) >= 0), model + ": " + name);
                if (!name.isBlank() && !"sequenceFlow".equals(tag)) {
                    String stem = name.replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_|_$", "");
                    assertTrue(id.startsWith(stem + "_"), model + ": " + name + " / " + id);
                }
            }
        }
    }

    @Test
    void canonicalModelsUseExpectedProcessAndWorkerBindings() throws Exception {
        verify("propertyflow-fallprozess-entwurf.bpmn", CaseProcessBpmnConstants.PROCESS_ID,
                Map.of(CaseProcessBpmnConstants.ServiceTask.PREPARE_PROCESS_KEY, PREPARE_PROCESS_KEY,
                        CaseProcessBpmnConstants.ServiceTask.REGISTER_CASE, REGISTER_CASE,
                        CaseProcessBpmnConstants.ServiceTask.PREPARE_INITIAL_CLARIFICATION, PREPARE_INITIAL));
        verify("automatische-abklaerung.bpmn", AutomaticClarificationBpmnConstants.PROCESS_ID,
                Map.of(AutomaticClarificationBpmnConstants.ServiceTask.CHECK_ENTRY, REGISTER_AUTOMATIC,
                        AutomaticClarificationBpmnConstants.ServiceTask.START_CLARIFICATION, START_CLARIFICATION));
    }

    private void verify(String canonical, String processId, Map<String, String> bindings) throws Exception {
        DocumentBuilderFactory factory = factory();
        Document original = factory.newDocumentBuilder().parse(MODELS.resolve(canonical).toFile());
        XPath xpath = XPathFactory.newInstance().newXPath();
        String process = "//*[@id='" + processId + "']/@isExecutable";
        assertEquals("true", xpath.evaluate(process, original));

        for (Map.Entry<String, String> binding : bindings.entrySet()) {
            String expression = "//*[@id='" + binding.getKey() + "']/*[name()='bpmn:extensionElements']/*[name()='zeebe:taskDefinition']/@type";
            assertEquals(binding.getValue(), xpath.evaluate(expression, original));

        }
        if (processId.equals(CaseProcessBpmnConstants.PROCESS_ID)) {
            String call = "//*[@id='" + CaseProcessBpmnConstants.CallActivity.AUTOMATIC_CLARIFICATION_CALL + "']"
                    + "/*[name()='bpmn:extensionElements']/*[name()='zeebe:calledElement']";
            assertEquals(AutomaticClarificationBpmnConstants.PROCESS_ID, xpath.evaluate(call + "/@processId", original));
            assertEquals("deployment", xpath.evaluate(call + "/@bindingType", original));
            assertEquals("false", xpath.evaluate(call + "/@propagateAllChildVariables", original));
            assertEquals("false", xpath.evaluate(call + "/@propagateAllParentVariables", original));
        }
    }

    private DocumentBuilderFactory factory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory;
    }
}
