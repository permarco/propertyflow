package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseNotificationBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.ConversationEvaluationBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.FollowUpBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.TenantMessageBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.TenantReplyBpmnConstants;
import io.camunda.client.annotation.JobWorker;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BpmnTechnicalConstantsTest {

    private static final Path MODELS = Path.of("src/main/resources/bpmn");
    private static final Path WORKERS = Path.of("src/main/java/com/pertegato/propertyflow/caseprocessing/workflow");
    private static final Map<Class<?>, String> CONTRACTS = Map.of(
            CaseProcessBpmnConstants.class, "propertyflow-fallprozess-entwurf.bpmn",
            AutomaticClarificationBpmnConstants.class, "automatische-abklaerung.bpmn",
            ConversationEvaluationBpmnConstants.class, "gespraech-auswerten.bpmn",
            TenantReplyBpmnConstants.class, "mieterantwort-pruefen.bpmn",
            StaffProcessingBpmnConstants.class, "mitarbeiterbearbeitung.bpmn",
            FollowUpBpmnConstants.class, "wiedervorlage.bpmn",
            TenantMessageBpmnConstants.class, "mieternachricht-verarbeiten.bpmn",
            CaseNotificationBpmnConstants.class, "fallbenachrichtigung-versenden.bpmn");
    private static final Map<String, String> ATTRIBUTES = Map.of(
            "JobType", "type", "ErrorCode", "errorCode", "EscalationCode", "escalationCode");
    private static final Map<String, String> ELEMENTS = Map.of(
            "JobType", "taskDefinition", "ErrorCode", "error", "EscalationCode", "escalation");

    @Test
    void technicalConstantsMatchTheirModelsAndSharedValuesHaveOneOwner() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        for (String group : ATTRIBUTES.keySet()) {
            Map<String, Integer> usage = new HashMap<>();
            Set<String> declared = new HashSet<>(values(CommonBpmnConstants.class, group));
            for (Map.Entry<Class<?>, String> contract : CONTRACTS.entrySet()) {
                Set<String> actual = new HashSet<>();
                NodeList elements = factory.newDocumentBuilder().parse(MODELS.resolve(contract.getValue()).toFile())
                        .getElementsByTagNameNS("*", ELEMENTS.get(group));
                for (int index = 0; index < elements.getLength(); index++) {
                    Element element = (Element) elements.item(index);
                    actual.add(element.getAttribute(ATTRIBUTES.get(group)));
                }
                for (String value : actual) {
                    usage.merge(value, 1, Integer::sum);
                }
                Set<String> local = values(contract.getKey(), group);
                for (String value : local) {
                    assertTrue(actual.contains(value), contract.getValue() + ": unbekannter Wert " + value);
                    assertTrue(declared.add(value), group + ": doppelte Definition " + value);
                }
                Set<String> available = new HashSet<>(local);
                available.addAll(values(CommonBpmnConstants.class, group));
                assertTrue(available.containsAll(actual), contract.getValue() + ": Konstanten fehlen für " + actual);
            }
            assertEquals(usage.keySet(), declared, group + ": Modell und Konstanten müssen übereinstimmen");
            Set<String> shared = new HashSet<>();
            usage.forEach((value, count) -> {
                if (count > 1) {
                    shared.add(value);
                }
            });
            assertEquals(shared, values(CommonBpmnConstants.class, group), group + ": gemeinsame Zuständigkeit");
        }
    }

    @Test
    void allWorkerBindingsUseDeclaredJobTypes() throws Exception {
        Set<String> jobTypes = values(CommonBpmnConstants.class, "JobType");
        for (Class<?> contract : CONTRACTS.keySet()) {
            jobTypes.addAll(values(contract, "JobType"));
        }
        int checked = 0;
        for (Path source : workerSources()) {
            String name = source.getFileName().toString().replace(".java", "");
            Class<?> worker = Class.forName(getClass().getPackageName() + "." + name, false, getClass().getClassLoader());
            for (Method method : worker.getDeclaredMethods()) {
                JobWorker binding = method.getAnnotation(JobWorker.class);
                if (binding != null) {
                    assertTrue(jobTypes.contains(binding.type()), method + ": Jobtyp nicht zentral definiert");
                    checked++;
                }
            }
        }
        assertTrue(checked > 0, "Keine Worker-Bindung geprüft");
    }

    @Test
    void workflowAdaptersDoNotDefineTechnicalBindingStringsLocally() throws Exception {
        Set<String> literals = new HashSet<>();
        for (String group : ATTRIBUTES.keySet()) {
            literals.addAll(values(CommonBpmnConstants.class, group));
            for (Class<?> contract : CONTRACTS.keySet()) {
                literals.addAll(values(contract, group));
            }
        }
        Set<String> names = new HashSet<>();
        Set<Class<?>> owners = new HashSet<>(CONTRACTS.keySet());
        owners.add(CommonBpmnConstants.class);
        for (Class<?> owner : owners) {
            for (Class<?> group : owner.getDeclaredClasses()) {
                if (ATTRIBUTES.containsKey(group.getSimpleName())) {
                    for (Field field : group.getDeclaredFields()) {
                        names.add(field.getName());
                    }
                }
            }
        }
        Pattern alias = Pattern.compile("\\bstatic\\s+final\\s+String\\s+\\w+\\s*=\\s*(?:[\\w.]+\\.)?(?:"
                + String.join("|", names) + ")\\s*;");
        for (Path source : workerSources()) {
            String code = Files.readString(source);
            assertFalse(alias.matcher(code).find(), source + ": lokale Alias-Konstante für einen BPMN-Bindungswert");
            for (String literal : literals) {
                assertFalse(code.contains("\"" + literal + "\""), source + ": lokale Definition von " + literal);
            }
        }
    }

    private Set<String> values(Class<?> contract, String groupName) throws Exception {
        Set<String> result = new HashSet<>();
        for (Class<?> group : contract.getDeclaredClasses()) {
            if (!group.getSimpleName().equals(groupName)) {
                continue;
            }
            for (Field field : group.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                assertTrue(Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers), field.toString());
                assertEquals(String.class, field.getType(), field.toString());
                assertTrue(result.add((String) field.get(null)), field.toString());
            }
        }
        return result;
    }

    private List<Path> workerSources() throws Exception {
        try (Stream<Path> files = Files.list(WORKERS)) {
            return files.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
    }
}
