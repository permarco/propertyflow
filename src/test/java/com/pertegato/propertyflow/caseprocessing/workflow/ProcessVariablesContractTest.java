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
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessVariablesContractTest {

    private static final Path MODELS = Path.of("src/main/resources/bpmn");
    private static final Map<Class<?>, String> CONTRACTS = Map.of(
            CaseProcessBpmnConstants.Variablen.class, "propertyflow-fallprozess-entwurf.bpmn",
            AutomaticClarificationBpmnConstants.Variablen.class, "automatische-abklaerung.bpmn",
            ConversationEvaluationBpmnConstants.Variablen.class, "gespraech-auswerten.bpmn",
            TenantReplyBpmnConstants.Variablen.class, "mieterantwort-pruefen.bpmn",
            StaffProcessingBpmnConstants.Variablen.class, "mitarbeiterbearbeitung.bpmn",
            FollowUpBpmnConstants.Variablen.class, "wiedervorlage.bpmn",
            TenantMessageBpmnConstants.Variablen.class, "mieternachricht-verarbeiten.bpmn",
            CaseNotificationBpmnConstants.Variablen.class, "fallbenachrichtigung-versenden.bpmn");
    private static final Pattern IDENTIFIER = Pattern.compile("\\b[a-z][A-Za-z0-9]*\\b");
    private static final Pattern STRING = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"");
    private static final Set<String> FEEL_WORDS = Set.of(
            "if", "then", "else", "and", "or", "not", "null", "true", "false",
            "string", "contains", "duration", "context", "get", "value");

    @Test
    void modelMappingsAndExpressionsUseDeclaredVariableNames() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        for (Map.Entry<Class<?>, String> contract : CONTRACTS.entrySet()) {
            Set<String> declared = variableNames(contract.getKey());
            declared.addAll(variableNames(CommonBpmnConstants.Variablen.class));
            Set<String> referenced = new HashSet<>();
            NodeList nodes = factory.newDocumentBuilder().parse(MODELS.resolve(contract.getValue()).toFile())
                    .getElementsByTagName("*");
            for (int index = 0; index < nodes.getLength(); index++) {
                Element element = (Element) nodes.item(index);
                if (Set.of("input", "output").contains(element.getLocalName())) {
                    referenced.add(element.getAttribute("target"));
                }
                NamedNodeMap attributes = element.getAttributes();
                for (int attribute = 0; attribute < attributes.getLength(); attribute++) {
                    String expression = attributes.item(attribute).getNodeValue();
                    if (expression.startsWith("=")) {
                        addExpressionVariables(referenced, expression);
                    }
                }
                if (Set.of("conditionExpression", "timeDate", "timeDuration", "timeCycle").contains(element.getLocalName())) {
                    addExpressionVariables(referenced, element.getTextContent());
                }
            }
            referenced.removeAll(declared);
            assertTrue(referenced.isEmpty(), contract.getValue() + ": nicht deklarierte Variablen " + referenced);
        }
    }

    @Test
    void workflowAdaptersUseConstantsInsteadOfVariableNameLiterals() throws Exception {
        Set<String> variables = variableNames(CommonBpmnConstants.Variablen.class);
        for (Class<?> contract : CONTRACTS.keySet()) {
            variables.addAll(variableNames(contract));
        }
        Path directory = Path.of("src/main/java/com/pertegato/propertyflow/caseprocessing/workflow");
        List<Path> sources;
        try (Stream<Path> files = Files.list(directory)) {
            sources = files.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
        assertTrue(!sources.isEmpty(), "Workflow-Quellen fehlen");
        for (Path source : sources) {
            Matcher strings = STRING.matcher(Files.readString(source));
            while (strings.find()) {
                String literal = strings.group();
                String value = literal.substring(1, literal.length() - 1);
                assertTrue(!variables.contains(value), source + ": Variablenkonstante statt " + literal + " verwenden");
            }
        }
    }

    @Test
    void sharedAndProcessSpecificNamesHaveExactlyOneDefinition() throws Exception {
        Set<String> defined = variableNames(CommonBpmnConstants.Variablen.class);
        for (Class<?> contract : CONTRACTS.keySet()) {
            for (String name : variableNames(contract)) {
                assertTrue(defined.add(name), contract.getName() + ": bereits zentral definierter Variablenname " + name);
            }
        }
    }

    private Set<String> variableNames(Class<?> contract) throws Exception {
        Set<String> names = new HashSet<>();
        for (Field field : contract.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            assertTrue(Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers), field.toString());
            assertEquals(String.class, field.getType(), field.toString());
            assertTrue(field.getName().matches("[A-Z][A-Z0-9_]*"), field.toString());
            String value = (String) field.get(null);
            assertTrue(value.matches("[a-z][A-Za-z0-9]*"), field.toString());
            assertTrue(names.add(value), contract.getName() + ": doppelte Variable " + value);
        }
        assertTrue(!names.isEmpty(), contract.getName());
        return names;
    }

    private void addExpressionVariables(Set<String> variables, String expression) {
        // Für die vorhandenen einfachen FEEL-Ausdrücke werden Literale und Schlüsselwörter ausgeblendet.
        Matcher identifiers = IDENTIFIER.matcher(STRING.matcher(expression).replaceAll(""));
        while (identifiers.find()) {
            String identifier = identifiers.group();
            if (!FEEL_WORDS.contains(identifier)) {
                variables.add(identifier);
            }
        }
    }
}
