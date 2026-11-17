package com.pertegato.propertyflow.caseprocessing.workflow;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewWorkflowBindingTest {

    @Test
    void allCanonicalAlternativeMergesUseSeparateExclusiveGateways() throws Exception {
        XPath xpath = XPathFactory.newInstance().newXPath();
        try (DirectoryStream<Path> paths = Files.newDirectoryStream(Path.of("src/main/resources/bpmn"), "*.bpmn")) {
            for (Path path : paths) {
                Document document = read(path.toString());
                NodeList flows = document.getElementsByTagName("bpmn:sequenceFlow");
                Map<String, Integer> incoming = new HashMap<>();
                Map<String, Integer> outgoing = new HashMap<>();
                for (int index = 0; index < flows.getLength(); index++) {
                    Element flow = (Element) flows.item(index);
                    incoming.merge(flow.getAttribute("targetRef"), 1, Integer::sum);
                    outgoing.merge(flow.getAttribute("sourceRef"), 1, Integer::sum);
                }
                for (Map.Entry<String, Integer> entry : incoming.entrySet()) {
                    String selector = "//*[@id='" + entry.getKey() + "']";
                    String label = path.getFileName() + ": " + entry.getKey();
                    assertEquals(entry.getValue().toString(),
                            xpath.evaluate("count(" + selector + "/*[name()='bpmn:incoming'])", document), label);
                    if (entry.getValue() > 1) {
                        assertEquals("bpmn:exclusiveGateway", xpath.evaluate("name(" + selector + ")", document), label);
                        assertEquals(1, outgoing.get(entry.getKey()), label);
                    }
                }
            }
        }
    }

    @Test
    void staleFollowUpCommandsMergeBeforeTheNextEventRace() throws Exception {
        XPath xpath = XPathFactory.newInstance().newXPath();
        for (String path : new String[] {"src/main/resources/bpmn/wiedervorlage.bpmn", "src/test/resources/workflow/follow-up.bpmn"}) {
            Document document = read(path);
            assertEquals("1", xpath.evaluate("count(//*[name()='bpmn:sequenceFlow'][@targetRef='WEvents'])", document));
            assertEquals("1", xpath.evaluate("count(//*[@id='WEvents']/*[name()='bpmn:incoming'])", document));
            for (String flow : new String[] {"WUpdateRetry", "WDeleteTaskRetry", "WCloseTaskRetry"}) {
                String merge = targetAfterLink(xpath, document, flow);
                assertEquals("bpmn:exclusiveGateway", xpath.evaluate("name(//*[@id='" + merge + "'])", document));
                assertEquals("1", xpath.evaluate("count(//*[name()='bpmn:sequenceFlow'][@sourceRef='" + merge + "'])", document));
                assertEquals("WEvents", xpath.evaluate("//*[name()='bpmn:sequenceFlow'][@sourceRef='" + merge + "']/@targetRef", document));
            }
        }
    }

    @Test
    void directFollowUpUsesOneCommandAndCallBoundInterruptionWithExistingStaffHierarchy() throws Exception {
        XPath xpath = XPathFactory.newInstance().newXPath();
        Document original = read("src/main/resources/bpmn/propertyflow-fallprozess-entwurf.bpmn");
        for (String path : new String[] {"src/test/resources/workflow/catalog-case.bpmn", "src/test/resources/workflow/reply-case.bpmn"}) {
            Document fixture = read(path);
            for (String id : new String[] {"StaffFollowPublish", "StaffFollowSignal", "StaffFollowContinue"}) {
                equal(xpath, original, fixture, "//*[@id='" + id + "']//*[name()='zeebe:taskDefinition']/@type");
            }
            equal(xpath, original, fixture, "//*[@id='Message_StaffFollowClarification']//*[name()='zeebe:subscription']/@correlationKey");
            equal(xpath, original, fixture, "//*[@id='StaffFollowAtReply']//*[name()='bpmn:condition']/text()");
            equal(xpath, original, fixture, "//*[@id='StaffFollowAtReply']//*[name()='zeebe:output']/@target");
            for (String flow : new String[] {"SF03", "SF05"}) {
                equal(xpath, original, fixture, "//*[@id='" + flow + "']/*[name()='bpmn:conditionExpression']/text()");
            }
        }
        assertEquals(DirectFollowUpWorkers.APPLY, xpath.evaluate("//*[@id='StaffFollowPublish']//*[name()='zeebe:taskDefinition']/@type", original));
        equal(xpath, read("src/main/resources/bpmn/mitarbeiterbearbeitung.bpmn"), read("src/test/resources/workflow/reply-staff.bpmn"),
                "//*[@id='FollowUp']//*[name()='zeebe:calledElement']/@processId");
    }

    @Test
    void replyClosureUsesTheBoundCallAndTheSameStaffClosureContract() throws Exception {
        XPath xpath = XPathFactory.newInstance().newXPath();
        Document original = read("src/main/resources/bpmn/propertyflow-fallprozess-entwurf.bpmn");
        Document fixture = read("src/test/resources/workflow/reply-case.bpmn");
        for (String id : new String[] {"StaffCloseReplyCheck", "StaffCloseReplySignal", "Close"}) {
            equal(xpath, original, fixture, "//*[@id='" + id + "']//*[name()='zeebe:taskDefinition']/@type");
        }
        equal(xpath, original, fixture, "//*[@id='Message_StaffCloseReply']//*[name()='zeebe:subscription']/@correlationKey");
        equal(xpath, original, fixture, "//*[@id='ReplyStaffClose']//*[name()='bpmn:condition']/text()");
        for (String target : new String[] {"staffOutcome", "staffDecisionRef"}) {
            equal(xpath, original, fixture, "//*[@id='ReplyStaffClose']//*[name()='zeebe:output'][@target='" + target + "']/@source");
        }
        for (String id : new String[] {"CR03", "CR04"}) {
            equal(xpath, original, fixture, "//*[@id='" + id + "']/*[name()='bpmn:conditionExpression']/text()");
        }
    }

    @Test
    void obsoleteContinuationUsesAnExplicitOutcomeAndANonTerminatingEnd() throws Exception {
        XPath xpath = XPathFactory.newInstance().newXPath();
        Document original = read("src/main/resources/bpmn/propertyflow-fallprozess-entwurf.bpmn");
        Document fixture = read("src/test/resources/workflow/review-case.bpmn");
        equal(xpath, original, fixture, "//*[@id='StaffContinuationReconcile']//*[name()='zeebe:taskDefinition']/@type");
        equal(xpath, original, fixture, "//*[@id='SCSuperseded']/*[name()='bpmn:conditionExpression']/text()");
        for (Document document : new Document[] {original, fixture}) {
            assertEquals("0", xpath.evaluate("count(//*[@id='StaffContinuationObsolete']/*[name()='bpmn:terminateEventDefinition'])", document));
        }
    }

    @Test
    void directClarificationClosureKeepsItsDecisionLocalAndEscalatesOnlyAfterCommit() throws Exception {
        XPath xpath = XPathFactory.newInstance().newXPath();
        Document original = read("src/main/resources/bpmn/propertyflow-fallprozess-entwurf.bpmn");
        Document fixture = read("src/test/resources/workflow/catalog-case.bpmn");
        equal(xpath, original, fixture, "//*[@id='StaffCloseClarificationApply']//*[name()='zeebe:taskDefinition']/@type");
        equal(xpath, original, fixture, "//*[@id='Message_StaffCloseClarification']//*[name()='zeebe:subscription']/@correlationKey");
        equal(xpath, original, fixture, "//*[@id='Escalation_StaffCloseCommitted']/@escalationCode");
        equal(xpath, original, fixture, "//*[@id='StaffClosureCommitted']/@attachedToRef");
        for (String target : new String[] {"clarificationClosureCommandRef", "clarificationClosureOutcome"}) {
            String path = "//*[@id='StaffCloseClarificationSide']//*[name()='zeebe:input'][@target='" + target + "']/@source";
            assertEquals("= null", xpath.evaluate(path, original));
            equal(xpath, original, fixture, path);
        }
        for (String id : new String[] {"SC03", "SC04"}) {
            equal(xpath, original, fixture, "//*[@id='" + id + "']/*[name()='bpmn:conditionExpression']/text()");
        }
    }

    @Test
    void originalModelsHaveUniqueIdsAndExistingFlowTargetsAcrossAllDiagramPlanes() throws Exception {
        try (DirectoryStream<Path> models = Files.newDirectoryStream(Path.of("src/main/resources/bpmn"), "*.bpmn")) {
            for (Path model : models) {
                Document document = read(model.toString());
                NodeList elements = document.getElementsByTagName("*");
                Set<String> ids = new HashSet<>();
                for (int index = 0; index < elements.getLength(); index++) {
                    Element element = (Element) elements.item(index);
                    if (element.hasAttribute("id")) {
                        assertTrue(ids.add(element.getAttribute("id")), model + ": doppelte ID " + element.getAttribute("id"));
                    }
                }
                for (int index = 0; index < elements.getLength(); index++) {
                    Element element = (Element) elements.item(index);
                    for (String attribute : new String[] {"sourceRef", "targetRef", "attachedToRef", "bpmnElement"}) {
                        if (element.hasAttribute(attribute)) {
                            assertTrue(ids.contains(element.getAttribute(attribute)), model + ": unbekannter Bezug " + element.getAttribute(attribute));
                        }
                    }
                }
            }
        }
    }

    @Test
    void automaticCatalogFixtureSharesPublicationAndResultContracts() throws Exception {
        XPath xpath = XPathFactory.newInstance().newXPath();
        Document automatic = read("src/main/resources/bpmn/automatische-abklaerung.bpmn");
        Document fixture = read("src/test/resources/workflow/catalog-automatic.bpmn");
        for (String id : new String[] {"Validate", "Question", "AWait", "AReview"}) {
            equal(xpath, automatic, fixture, "//*[@id='" + id + "']//*[name()='zeebe:taskDefinition']/@type");
        }
        for (String id : new String[] {"F07", "A_RefreshWait", "A_RouteReview"}) {
            equal(xpath, automatic, fixture, "//*[@id='" + id + "']/*[name()='bpmn:conditionExpression']/text()");
        }
        Document root = read("src/main/resources/bpmn/propertyflow-fallprozess-entwurf.bpmn");
        Document rootFixture = read("src/test/resources/workflow/catalog-case.bpmn");
        equal(xpath, root, rootFixture, "//*[@id='AutomaticResult']//*[name()='zeebe:taskDefinition']/@type");
        for (String id : new String[] {"AParentWait", "AParentReview"}) {
            equal(xpath, root, rootFixture, "//*[@id='" + id + "']/*[name()='bpmn:conditionExpression']/text()");
        }
        for (String output : new String[] {"automaticOutcome", "automaticDecisionRef"}) {
            equal(xpath, root, rootFixture, "//*[@id='Automatic']//*[name()='zeebe:output'][@target='" + output + "']/@source");
        }
    }

    @Test
    void followUpClosureUsesVersionedSubscriptionAndCommonStaffReturn() throws Exception {
        XPath xpath = XPathFactory.newInstance().newXPath();
        Document original = read("src/main/resources/bpmn/wiedervorlage.bpmn");
        Document fixture = read("src/test/resources/workflow/follow-up.bpmn");
        for (String expression : new String[] {
                "//*[@id='WCloseTask']//*[name()='zeebe:taskDefinition']/@type",
                "//*[@id='WCloseTaskStale']/@attachedToRef",
                "//*[@id='WCloseTaskStale']//*[name()='bpmn:errorEventDefinition']/@errorRef"}) {
            equal(xpath, original, fixture, expression);
        }
        assertEquals(targetAfterLink(xpath, fixture, "WCloseTaskRetry"), targetAfterLink(xpath, original, "WCloseTaskRetry"));
        assertEquals(FollowUpCommandWorkers.CLOSE,
                xpath.evaluate("//*[@id='WCloseTask']//*[name()='zeebe:taskDefinition']/@type", original));
        assertEquals("=activeWaitRef", xpath.evaluate("//*[@id='Message_StaffClose']//*[name()='zeebe:subscription']/@correlationKey", original));
        Document staff = read("src/main/resources/bpmn/mitarbeiterbearbeitung.bpmn");
        Document staffFixture = read("src/test/resources/workflow/wait-staff.bpmn");
        for (String output : new String[] {"outcome", "decisionRef"}) {
            String expression = "//*[@id='FollowUp']//*[name()='zeebe:output'][@target='" + output + "']/@source";
            assertEquals("= " + output, xpath.evaluate(expression, staff));
            equal(xpath, staff, staffFixture, expression);
        }
    }

    @Test
    void reviewFixtureSharesOriginalWorkerMessageConditionAndScopeContracts() throws Exception {
        Document original = read("src/main/resources/bpmn/propertyflow-fallprozess-entwurf.bpmn");
        Document fixture = read("src/test/resources/workflow/review-case.bpmn");
        XPath xpath = XPathFactory.newInstance().newXPath();
        for (Map.Entry<String, String> binding : Map.of(
                "ReviewNeedCheck", "propertyflow.review.check", "ReviewNeedOther", "propertyflow.review.interrupt-automatic",
                "AutomaticInterruptionPrepare", "propertyflow.review.prepare-staff", "StaffContinuationClaim", "propertyflow.staff.claim",
                "ReviewNeedExisting", "propertyflow.review.merge", "StaffContinuationMerge", "propertyflow.staff.merge-continuation",
                "CaseDataApply", "propertyflow.case-data.apply").entrySet()) {
            String expression = "//*[@id='" + binding.getKey() + "']/*[name()='bpmn:extensionElements']/*[name()='zeebe:taskDefinition']/@type";
            assertEquals(binding.getValue(), xpath.evaluate(expression, original));
            assertEquals(binding.getValue(), xpath.evaluate(expression, fixture));
        }
        for (String message : new String[] {"Message_ReviewRequired", "Message_CaseDataChange"}) {
            assertEquals(xpath.evaluate("//*[@id='" + message + "']/@name", original), xpath.evaluate("//*[@id='" + message + "']/@name", fixture));
            equal(xpath, original, fixture, "//*[@id='" + message + "']//*[name()='zeebe:subscription']/@correlationKey");
        }
        equal(xpath, original, fixture, "//*[@id='AutomaticInterrupted']//*[name()='bpmn:condition']/text()");
        for (String flow : new String[] {"PN03", "PN05", "PN08", "SCStart", "SCExisting"}) {
            equal(xpath, original, fixture, "//*[@id='" + flow + "']/*[name()='bpmn:conditionExpression']/text()");
        }
        for (String scope : new String[] {"ReviewNeedSide", "CaseDataSide"}) {
            equal(xpath, original, fixture, "count(//*[@id='" + scope + "']/*[name()='bpmn:extensionElements']//*[name()='zeebe:input'])");
        }
        for (String flag : new String[] {"propagateAllChildVariables", "propagateAllParentVariables", "bindingType"}) {
            equal(xpath, original, fixture, "//*[@id='Staff']//*[name()='zeebe:calledElement']/@" + flag);
        }
    }

    @Test
    void staffEntryUsesRealCamundaUserTaskAndMatchingReviewRoute() throws Exception {
        Document original = read("src/main/resources/bpmn/mitarbeiterbearbeitung.bpmn");
        Document fixture = read("src/test/resources/workflow/review-staff.bpmn");
        XPath xpath = XPathFactory.newInstance().newXPath();
        for (String id : new String[] {"SEntryCheck", "SReviewStatus"}) {
            equal(xpath, original, fixture, "//*[@id='" + id + "']//*[name()='zeebe:taskDefinition']/@type");
        }
        assertEquals("1", xpath.evaluate("count(//*[@id='SReview']//*[name()='zeebe:userTask'])", original));
        assertEquals("1", xpath.evaluate("count(//*[@id='SReview']//*[name()='zeebe:userTask'])", fixture));
        equal(xpath, original, fixture, "//*[@id='S_EntryReview']/*[name()='bpmn:conditionExpression']/text()");
    }

    @Test
    void staffMessagesUseLocalReferencesAndEntryCorrelationAvailableBeforeChildStartup() throws Exception {
        XPath xpath = XPathFactory.newInstance().newXPath();
        Document original = read("src/main/resources/bpmn/propertyflow-fallprozess-entwurf.bpmn");
        Document fixture = read("src/test/resources/workflow/staff-message-case.bpmn");
        equal(xpath, original, fixture, "//*[@id='Message_StaffQuestionClarification']//*[name()='zeebe:subscription']/@correlationKey");
        equal(xpath, original, fixture, "//*[@id='StaffQuestionPublish']//*[name()='zeebe:taskDefinition']/@type");
        assertEquals("ActiveCase", xpath.evaluate("//*[@id='StaffQuestionSide']/../@id", fixture));
        for (String flow : new String[] {"SQInfo", "SQ03"}) {
            equal(xpath, original, fixture, "//*[@id='" + flow + "']/*[name()='bpmn:conditionExpression']/text()");
        }
        Document staff = read("src/main/resources/bpmn/mitarbeiterbearbeitung.bpmn");
        Document staffFixture = read("src/test/resources/workflow/staff-message-staff.bpmn");
        String subscription = "//*[@id='Message_StaffReviewSend']//*[name()='zeebe:subscription']/@correlationKey";
        assertEquals("=entryContextRef", xpath.evaluate(subscription, staff));
        equal(xpath, staff, staffFixture, subscription);
        equal(xpath, staff, staffFixture, "//*[@id='SMessagePublish']//*[name()='zeebe:taskDefinition']/@type");
        for (String local : new String[] {"staffMessageCommandRef", "staffMessageOutcome"}) {
            String expression = "//*[@id='SMessageSide']/*[name()='bpmn:extensionElements']//*[name()='zeebe:input'][@target='" + local + "']/@source";
            assertEquals("=null", xpath.evaluate(expression, staff));
            equal(xpath, staff, staffFixture, expression);
        }
    }

    @Test
    void replyFixtureMatchesWorkerBindingsAndExplicitCallOutputs() throws Exception {
        XPath xpath = XPathFactory.newInstance().newXPath();
        Document original = read("src/main/resources/bpmn/mieterantwort-pruefen.bpmn");
        Document fixture = read("src/test/resources/workflow/reply-processing.bpmn");
        for (String id : new String[] {"RStatus", "REvaluate", "RCurrentAnalysis", "RValidate", "RStaffMessagePublish"}) {
            equal(xpath, original, fixture, "//*[@id='" + id + "']//*[name()='zeebe:taskDefinition']/@type");
        }
        equal(xpath, original, fixture, "//*[@id='Message_ReplyStaffSend']//*[name()='zeebe:subscription']/@correlationKey");
        original = read("src/main/resources/bpmn/propertyflow-fallprozess-entwurf.bpmn");
        fixture = read("src/test/resources/workflow/reply-case.bpmn");
        for (String call : new String[] {"Reply", "Staff"}) {
            equal(xpath, original, fixture, "//*[@id='" + call + "']//*[name()='zeebe:calledElement']/@propagateAllChildVariables");
            equal(xpath, original, fixture, "count(//*[@id='" + call + "']//*[name()='zeebe:output'])");
        }
        for (String flow : new String[] {"F17", "F19", "F21"}) {
            equal(xpath, original, fixture, "//*[@id='" + flow + "']/*[name()='bpmn:conditionExpression']/text()");
        }
        for (String task : new String[] {"ReviewNeedPrepare", "ReviewNeedWakeReply"}) {
            equal(xpath, original, fixture, "//*[@id='" + task + "']//*[name()='zeebe:taskDefinition']/@type");
        }
        for (String task : new String[] {"ClearAnswerApply", "ClearAnswerContinue"}) {
            equal(xpath, original, fixture, "//*[@id='" + task + "']//*[name()='zeebe:taskDefinition']/@type");
        }
        equal(xpath, original, fixture, "//*[@id='Message_ClearAnswer']//*[name()='zeebe:subscription']/@correlationKey");
        equal(xpath, original, fixture, "//*[@id='ReplyCleared']/*[name()='bpmn:conditionalEventDefinition']/*[name()='bpmn:condition']/text()");
        original = read("src/main/resources/bpmn/mitarbeiterbearbeitung.bpmn");
        fixture = read("src/test/resources/workflow/reply-staff.bpmn");
        equal(xpath, original, fixture, "//*[@id='SQuestion']//*[name()='zeebe:taskDefinition']/@type");
        equal(xpath, original, fixture, "//*[@id='S09']/*[name()='bpmn:conditionExpression']/text()");
        equal(xpath, original, fixture, "//*[@id='SQuestionStale']/*[name()='bpmn:errorEventDefinition']/@errorRef");
    }

    @Test
    void questionReentryMatchesOriginalPreparationAndExplicitReplyDecision() throws Exception {
        XPath xpath = XPathFactory.newInstance().newXPath();
        Document original = read("src/main/resources/bpmn/propertyflow-fallprozess-entwurf.bpmn");
        Document fixture = read("src/test/resources/workflow/catalog-case.bpmn");
        equal(xpath, original, fixture, "//*[@id='PrepareQuestionAuto']//*[name()='zeebe:taskDefinition']/@type");
        equal(xpath, original, fixture, "//*[@id='F18']/*[name()='bpmn:conditionExpression']/text()");
        equal(xpath, original, fixture, "//*[@id='Reply']//*[name()='zeebe:output'][@target='replyDecisionRef']/@source");
        assertEquals(AutomaticQuestionRoundWorkers.PREPARE,
                xpath.evaluate("//*[@id='PrepareQuestionAuto']//*[name()='zeebe:taskDefinition']/@type", original));
    }

    @Test
    void takeoverHasLocalCommandExplicitOutcomeAndHandoverBoundaries() throws Exception {
        XPath xpath = XPathFactory.newInstance().newXPath();
        Document original = read("src/main/resources/bpmn/propertyflow-fallprozess-entwurf.bpmn");
        Document fixture = read("src/test/resources/workflow/catalog-case.bpmn");
        for (String task : new String[] {"StaffTakeoverPublish", "StaffTakeoverSignal"}) {
            equal(xpath, original, fixture, "//*[@id='" + task + "']//*[name()='zeebe:taskDefinition']/@type");
        }
        equal(xpath, original, fixture, "//*[@id='Message_StaffTakeover']//*[name()='zeebe:subscription']/@correlationKey");
        for (String flow : new String[] {"ST03", "ST05"}) {
            equal(xpath, original, fixture, "//*[@id='" + flow + "']/*[name()='bpmn:conditionExpression']/text()");
        }
        for (String boundary : new String[] {"AutomaticResultTakenOver", "ReplyTakenOver"}) {
            equal(xpath, original, fixture, "//*[@id='" + boundary + "']//*[name()='bpmn:condition']/text()");
        }
        assertEquals("= null", xpath.evaluate("//*[@id='StaffTakeoverSide']/*[name()='bpmn:extensionElements']"
                + "//*[name()='zeebe:input'][@target='staffTakeoverCommandRef']/@source", original));
    }

    @Test
    void manualClarificationQuestionUsesExplicitInterruptionAndWaitOutcome() throws Exception {
        XPath xpath = XPathFactory.newInstance().newXPath();
        Document original = read("src/main/resources/bpmn/propertyflow-fallprozess-entwurf.bpmn");
        Document fixture = read("src/test/resources/workflow/catalog-case.bpmn");
        equal(xpath, original, fixture, "//*[@id='StaffQuestionSignal']//*[name()='zeebe:taskDefinition']/@type");
        for (String flow : new String[] {"AIWait", "AIStaff", "SQ03", "SQInfo"}) {
            equal(xpath, original, fixture, "//*[@id='" + flow + "']/*[name()='bpmn:conditionExpression']/text()");
        }
        for (String boundary : new String[] {"AutomaticResultManualQuestion", "ReplyManualQuestion"}) {
            equal(xpath, original, fixture, "//*[@id='" + boundary + "']//*[name()='bpmn:condition']/text()");
        }
    }

    private String targetAfterLink(XPath xpath, Document document, String flow) throws Exception {
        String target = xpath.evaluate("//*[@id='" + flow + "']/@targetRef", document);
        String link = xpath.evaluate("//*[@id='" + target + "']/*[name()='bpmn:linkEventDefinition']/@name", document);
        if (link.isEmpty()) {
            return target;
        }
        // Link-Ereignisse ersetzen nur die gezeichnete Verbindung innerhalb desselben Scopes.
        String receiver = "//*[@id='" + target + "']/../*[name()='bpmn:intermediateCatchEvent']"
                + "[*[name()='bpmn:linkEventDefinition'][@name='" + link + "']]";
        assertEquals("1", xpath.evaluate("count(" + receiver + ")", document));
        String receiverId = xpath.evaluate(receiver + "/@id", document);
        String outgoing = "//*[name()='bpmn:sequenceFlow'][@sourceRef='" + receiverId + "']";
        assertEquals("1", xpath.evaluate("count(" + outgoing + ")", document));
        return xpath.evaluate(outgoing + "/@targetRef", document);
    }

    private void equal(XPath xpath, Document original, Document fixture, String expression) throws Exception {
        assertEquals(xpath.evaluate(expression, original).strip(), xpath.evaluate(expression, fixture).strip(), expression);
    }

    @Test
    void sharedEvaluationUsesOriginalWorkerAndParentErrorBoundary() throws Exception {
        XPath xpath = XPathFactory.newInstance().newXPath();
        Document parent = read("src/main/resources/bpmn/propertyflow-fallprozess-entwurf.bpmn");
        Document fixture = read("src/test/resources/workflow/analysis-case.bpmn");
        equal(xpath, parent, fixture, "//*[@id='AutomaticFailurePrepare']//*[name()='zeebe:taskDefinition']/@type");
        equal(xpath, parent, fixture, "//*[@id='AutomaticFailure']/@attachedToRef");
        equal(xpath, parent, fixture, "//*[@id='AutomaticFailure']/*[name()='bpmn:errorEventDefinition']/@errorRef");
        equal(xpath, parent, fixture, "//*[@id='Error_TriageFailure']/@errorCode");
        equal(xpath, read("src/main/resources/bpmn/automatische-abklaerung.bpmn"),
                read("src/test/resources/workflow/conversation-automatic.bpmn"), "//*[@id='Triage']//*[name()='zeebe:taskDefinition']/@type");
    }

    private Document read(String path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(Path.of(path).toFile());
    }

    @Test
    void progressFixtureUsesNativeWorkTaskAndExplicitStaleDecisionBoundary() throws Exception {
        XPath xpath = XPathFactory.newInstance().newXPath();
        Document original = read("src/main/resources/bpmn/mitarbeiterbearbeitung.bpmn");
        Document fixture = read("src/test/resources/workflow/progress-staff.bpmn");
        equal(xpath, original, fixture, "//*[@id='SProgress']//*[name()='zeebe:taskDefinition']/@type");
        equal(xpath, original, fixture, "//*[@id='S04']/*[name()='bpmn:conditionExpression']/text()");
        equal(xpath, original, fixture, "//*[@id='SProgressStale']/@attachedToRef");
        equal(xpath, original, fixture, "//*[@id='SProgressStale']/*[name()='bpmn:errorEventDefinition']/@errorRef");
        equal(xpath, original, fixture, "//*[@id='Error_StaffDecisionStale']/@errorCode");
        assertEquals("1", xpath.evaluate("count(//*[@id='SWork']//*[name()='zeebe:userTask'])", original));
        assertEquals("1", xpath.evaluate("count(//*[@id='SWork']//*[name()='zeebe:userTask'])", fixture));
    }

    @Test
    void closureFixtureUsesOriginalOutcomeMappingConflictScopeAndTerminatingEnd() throws Exception {
        XPath xpath = XPathFactory.newInstance().newXPath();
        Document original = read("src/main/resources/bpmn/propertyflow-fallprozess-entwurf.bpmn");
        Document fixture = read("src/test/resources/workflow/closure-case.bpmn");
        equal(xpath, original, fixture, "//*[@id='Close']//*[name()='zeebe:taskDefinition']/@type");
        equal(xpath, original, fixture, "//*[@id='F16']/*[name()='bpmn:conditionExpression']/text()");
        equal(xpath, original, fixture, "//*[@id='CloseConflict']/@attachedToRef");
        equal(xpath, original, fixture, "//*[@id='Error_StaffClosureStale']/@errorCode");
        for (String target : new String[] {"staffOutcome", "staffDecisionRef"}) {
            equal(xpath, original, fixture, "//*[@id='Staff']//*[name()='zeebe:output'][@target='" + target + "']/@source");
        }
        String mapping = "//*[@id='CloseConflict']//*[name()='zeebe:output'][@target='staffEntryContextRef']/@source";
        assertEquals("= staffEntryContextRef", xpath.evaluate(mapping, original));
        equal(xpath, original, fixture, mapping);
        assertEquals("1", xpath.evaluate("count(//*[@id='ActiveEnd']/*[name()='bpmn:terminateEventDefinition'])", original));
        assertEquals("1", xpath.evaluate("count(//*[@id='ActiveEnd']/*[name()='bpmn:terminateEventDefinition'])", fixture));
        for (String flow : new String[] {"S08", "S11"}) {
            equal(xpath, read("src/main/resources/bpmn/mitarbeiterbearbeitung.bpmn"), read("src/test/resources/workflow/closure-staff.bpmn"),
                    "//*[@id='" + flow + "']/*[name()='bpmn:conditionExpression']/text()");
        }
    }

    @Test
    void tenantMessageReceptionHasLocalInputAndExplicitChildContract() throws Exception {
        XPath xpath = XPathFactory.newInstance().newXPath();
        Document original = read("src/main/resources/bpmn/propertyflow-fallprozess-entwurf.bpmn");
        Document fixture = read("src/test/resources/workflow/message-case.bpmn");
        equal(xpath, original, fixture, "//*[@id='Message_TenantMessage']//*[name()='zeebe:subscription']/@correlationKey");
        assertEquals("= null", xpath.evaluate("//*[@id='TenantInbox']/*[name()='bpmn:extensionElements']//*[name()='zeebe:input']/@source", original));
        equal(xpath, original, fixture, "//*[@id='TenantInbox']/*[name()='bpmn:extensionElements']//*[name()='zeebe:input']/@source");
        assertEquals("0", xpath.evaluate("count(//*[@id='ProcessTenantMessage']//*[name()='zeebe:output'])", original));
        for (String target : new String[] {"caseId", "leadingProcessInstanceKey", "tenantMessageId"}) {
            equal(xpath, original, fixture, "//*[@id='ProcessTenantMessage']//*[name()='zeebe:input'][@target='" + target + "']/@source");
        }
        Document child = read("src/main/resources/bpmn/mieternachricht-verarbeiten.bpmn");
        Document childFixture = read("src/test/resources/workflow/message-processing.bpmn");
        assertEquals(TenantMessageWorkers.IDENTIFY, xpath.evaluate("//*[@id='NIdentify']//*[name()='zeebe:taskDefinition']/@type", child));
        equal(xpath, child, childFixture, "//*[@id='NIdentify']//*[name()='zeebe:taskDefinition']/@type");
        equal(xpath, child, childFixture, "//*[@id='N04']/*[name()='bpmn:conditionExpression']/text()");
    }
}
