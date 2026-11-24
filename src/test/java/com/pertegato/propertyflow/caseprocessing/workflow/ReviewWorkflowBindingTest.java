package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.ConversationEvaluationBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.FollowUpBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.StaffProcessingBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.TenantMessageBpmnConstants;
import com.pertegato.propertyflow.caseprocessing.contract.bpmn.TenantReplyBpmnConstants;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
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

import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants.ErrorCode.CLOSURE_DRAFT_MISSING;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants.JobType.AUTOMATIC_CHECK_CLOSURE_MESSAGE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.AutomaticClarificationBpmnConstants.JobType.AUTOMATIC_WRITE_CLOSURE_MESSAGE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CaseProcessBpmnConstants.ErrorCode.UNKNOWN_CASE_ACTION;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.ErrorCode.CLOSURE_MESSAGE_INVALID;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.ErrorCode.PUBLICATION_BASIS_STALE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.ConversationEvaluationBpmnConstants.JobType.AUTOMATIC_ADVISE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.ConversationEvaluationBpmnConstants.JobType.AUTOMATIC_ANALYZE;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.ConversationEvaluationBpmnConstants.JobType.AUTOMATIC_ASSESS_CLOSURE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewWorkflowBindingTest {

    private static final String CASE_ACTION_MESSAGE = "//*[@id='PropertyFlow_Fallaktion_MESSAGE']";
    private static final String SUBSCRIPTION_KEY = "//*[name()='zeebe:subscription']/@correlationKey";

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
        for (String path : new String[] {"src/main/resources/bpmn/wiedervorlage.bpmn"}) {
            Document document = read(path);
            assertEquals("1", xpath.evaluate("count(//*[name()='bpmn:sequenceFlow'][@targetRef='Auf_erstes_Ereignis_warten_EVENT_GATEWAY'])", document));
            assertEquals("1", xpath.evaluate("count(//*[@id='Auf_erstes_Ereignis_warten_EVENT_GATEWAY']/*[name()='bpmn:incoming'])", document));
            for (String flow : new String[] {"Fehler_bei_Neuen_zu_RECALCULATE_Wiedervorlage_verwalten_THROW_FLOW", "Fehler_bei_Timer_zu_RECALCULATE_Wiedervorlage_verwalten_4_FLOW", "Fehler_bei_Wiedervorlage_zu_RECALCULATE_Wiedervorlage_verwalten_3_FLOW"}) {
                String merge = targetAfterLink(xpath, document, flow);
                assertEquals("bpmn:exclusiveGateway", xpath.evaluate("name(//*[@id='" + merge + "'])", document));
                assertEquals("1", xpath.evaluate("count(//*[name()='bpmn:sequenceFlow'][@sourceRef='" + merge + "'])", document));
                assertEquals("Auf_erstes_Ereignis_warten_EVENT_GATEWAY", xpath.evaluate("//*[name()='bpmn:sequenceFlow'][@sourceRef='" + merge + "']/@targetRef", document));
            }
        }
    }

    @Test
    void directFollowUpUsesOneCommandAndCallBoundInterruptionWithExistingStaffHierarchy() throws Exception {
        Document model = read("src/main/resources/bpmn/propertyflow-fallprozess-entwurf.bpmn");
        Document relatedModel = read("src/main/resources/bpmn/mitarbeiterbearbeitung.bpmn");
        assertValue(model,
                "//*[@id='Direkte_Wiedervorlage_speichern_SERVICE']//*[name()='zeebe:taskDefinition']/@type",
                CaseProcessBpmnConstants.JobType.DIRECT_FOLLOW_UP_APPLY);
        assertValue(model,
                "//*[@id='Wiedervorlage_zur_Uebernahme_bereitstellen_SERVICE']//*[name()='zeebe:taskDefinition']/@type",
                CaseProcessBpmnConstants.JobType.DIRECT_FOLLOW_UP_SIGNAL);
        assertValue(model,
                "//*[@id='Wiedervorlage_Eintritt_vorbereiten_SERVICE']//*[name()='zeebe:taskDefinition']/@type",
                CaseProcessBpmnConstants.JobType.DIRECT_FOLLOW_UP_CONTINUE);
        assertFeel(model,
                "//*[@id='Terminsetzung_gespeichert_XOR_Ja_bereits_uebernommen_zu_Wiedervorlage_zur_Uebernahme_bereitstellen_FLOW']/*[name()='bpmn:conditionExpression']/text()",
                "= directFollowOutcome = \"APPLIED\"");
        assertFeel(model,
                "//*[@id='Terminsetzung_gespeichert_XOR_Nein_zu_Abgelehnt_aktuellen_Stand_anzeigen_FLOW']/*[name()='bpmn:conditionExpression']/text()",
                "= directFollowOutcome = \"REJECTED\"");
        assertValue(relatedModel,
                "//*[@id='Wiedervorlage_verwalten_CALL']//*[name()='zeebe:calledElement']/@processId",
                "Wiedervorlage_verwalten_PROCESS");
    }

    @Test
    void replyClosureUsesTheBoundCallAndTheSameStaffClosureContract() throws Exception {
        Document model = read("src/main/resources/bpmn/propertyflow-fallprozess-entwurf.bpmn");
        assertValue(model,
                "//*[@id='Abschlussauftrag_pruefen_SERVICE']//*[name()='zeebe:taskDefinition']/@type",
                CaseProcessBpmnConstants.JobType.REPLY_CLOSURE_CHECK);
        assertValue(model,
                "//*[@id='Abschluss_im_Mieterwarten_bereitstellen_SERVICE']//*[name()='zeebe:taskDefinition']/@type",
                CaseProcessBpmnConstants.JobType.REPLY_CLOSURE_SIGNAL);
        assertValue(model,
                "//*[@id='Mitarbeiterabschluss_pruefen_und_speichern_SERVICE']//*[name()='zeebe:taskDefinition']/@type",
                CaseProcessBpmnConstants.JobType.STAFF_CLOSE);
        assertFeel(model,
                "//*[@id='Auftrag_uebernommen_XOR_Ja_zu_Abschluss_im_Mieterwarten_bereitstellen_FLOW']/*[name()='bpmn:conditionExpression']/text()",
                "= replyClosureOutcome = \"ACCEPTED\"");
        assertFeel(model,
                "//*[@id='Auftrag_uebernommen_XOR_Nein_Konflikt_nicht_zulaessig_zu_Abgelehnt_Aktueller_Pfad_bleibt_FLOW']/*[name()='bpmn:conditionExpression']/text()",
                "= replyClosureOutcome = \"REJECTED\"");
    }

    @Test
    void obsoleteContinuationUsesAnExplicitOutcomeAndANonTerminatingEnd() throws Exception {
        Document model = read("src/main/resources/bpmn/propertyflow-fallprozess-entwurf.bpmn");
        assertValue(model,
                "//*[@id='Ueberholte_Fortsetzung_abgleichen_SERVICE']//*[name()='zeebe:taskDefinition']/@type",
                CaseProcessBpmnConstants.JobType.STAFF_RECONCILE_CONTINUATION);
        assertFeel(model,
                "//*[@id='Wer_fuehrt_weiter_SUPERSEDED_ueberholt_zu_Ueberholte_Fortsetzung_abgleichen_SERVICE_FLOW']/*[name()='bpmn:conditionExpression']/text()",
                "= staffContinuationOutcome = \"SUPERSEDED\"");
        assertValue(model,
                "count(//*[@id='Nur_redundante_Fortsetzung_beendet_Aktiven_Fall_bearbeiten_2_END']/*[name()='bpmn:terminateEventDefinition'])",
                "0");
    }

    @Test
    void directClarificationClosureKeepsItsDecisionLocalAndEscalatesOnlyAfterCommit() throws Exception {
        Document model = read("src/main/resources/bpmn/propertyflow-fallprozess-entwurf.bpmn");
        assertValue(model,
                "//*[@id='Abschluss_in_Abklaerung_speichern_SERVICE']//*[name()='zeebe:taskDefinition']/@type",
                CaseProcessBpmnConstants.JobType.CLARIFICATION_CLOSE);
        assertValue(model,
                "//*[@id='Mitarbeiterabschluss_aus_Abklaerung_gespeichert_ESCALATION']/@escalationCode",
                "STAFF_CLARIFICATION_CLOSURE_COMMITTED");
        assertValue(model,
                "//*[@id='Mitarbeiterabschluss_aus_Abklaerung_gespeichert_BOUNDARY']/@attachedToRef",
                "Aktiven_Fall_bearbeiten_SUBPROCESS");
        assertValue(model,
                "//*[@id='Fall_in_Abklaerung_abschliessen_SUBPROCESS']//*[name()='zeebe:input'][@target='clarificationClosureOutcome']/@source",
                "= null");
        assertFeel(model,
                "//*[@id='Abschluss_gespeichert_XOR_Ja_identischer_Erfolg_zu_Gespeichert_aktive_Bearbeitung_beenden_FLOW']/*[name()='bpmn:conditionExpression']/text()",
                "= clarificationClosureOutcome = \"APPLIED\"");
        assertFeel(model,
                "//*[@id='Abschluss_gespeichert_XOR_Nein_Konflikt_nicht_zulaessig_zu_Nicht_abgeschlossen_Aktuellen_Stand_FLOW']/*[name()='bpmn:conditionExpression']/text()",
                "= clarificationClosureOutcome = \"REJECTED\"");
        assertValue(model,
                "//*[@id='Aktionsrundlauf_SUBPROCESS']/*[name()='bpmn:extensionElements']//*[name()='zeebe:input'][@target='clarificationClosureCommandRef']/@source",
                "= null");
        assertValue(model,
                "//*[@id='Fall_in_Abklaerung_abschliessen_SUBPROCESS']/*[name()='bpmn:extensionElements']//*[name()='zeebe:input'][@target='clarificationClosureCommandRef']/@source",
                "= clarificationClosureCommandRef");
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
    void automaticCatalogUsesPublicationAndResultContracts() throws Exception {
        Document model = read("src/main/resources/bpmn/automatische-abklaerung.bpmn");
        Document relatedModel = read("src/main/resources/bpmn/propertyflow-fallprozess-entwurf.bpmn");
        assertValue(model,
                "//*[@id='Zulaessige_Automatikaktion_pruefen_SERVICE']//*[name()='zeebe:taskDefinition']/@type",
                AutomaticClarificationBpmnConstants.JobType.AUTOMATIC_VALIDATE_QUESTIONS);
        assertValue(model,
                "//*[@id='Katalogrueckfrage_veroeffentlichen_SERVICE']//*[name()='zeebe:taskDefinition']/@type",
                AutomaticClarificationBpmnConstants.JobType.AUTOMATIC_PUBLISH_QUESTIONS);
        assertValue(model,
                "//*[@id='Mieterwarte_Eintritt_bereitstellen_SERVICE']//*[name()='zeebe:taskDefinition']/@type",
                AutomaticClarificationBpmnConstants.JobType.AUTOMATIC_PREPARE_RESULT);
        assertValue(model,
                "//*[@id='Pruefkontext_bereitstellen_SERVICE']//*[name()='zeebe:taskDefinition']/@type",
                AutomaticClarificationBpmnConstants.JobType.AUTOMATIC_PREPARE_RESULT);
        assertFeel(model,
                "//*[@id='Geprueftes_Vorgehen_XOR_Katalogrueckfrage_zulaessig_zu_Katalogrueckfrage_veroeffentlichen_SERVICE_FLOW']/*[name()='bpmn:conditionExpression']/text()",
                "= automaticAction = \"QUESTION\"");
        assertFeel(model,
                "//*[@id='Geprueftes_Vorgehen_XOR_Offene_Fragen_keine_weitere_Aktion_zu_Mieterwarte_Eintritt_bereitstellen_zusammenfuehren_FLOW']/*[name()='bpmn:conditionExpression']/text()",
                "= automaticAction = \"WAIT\"");
        assertFeel(model,
                "//*[@id='Geprueftes_Vorgehen_XOR_Pruefbedarf_unklar_zu_Pruefkontext_bereitstellen_zusammenfuehren_XOR_FLOW']/*[name()='bpmn:conditionExpression']/text()",
                "= automaticAction = \"REVIEW\"");
        assertValue(relatedModel,
                "//*[@id='Automatik_Ergebnis_uebernehmen_SERVICE']//*[name()='zeebe:taskDefinition']/@type",
                CaseProcessBpmnConstants.JobType.AUTOMATIC_ACCEPT_RESULT);
        assertFeel(relatedModel,
                "//*[@id='Fortsetzung_Aktiven_Fall_Auf_Mieterantwort_warten_zu_Auf_ausgewertete_Mieterantwort_warten_FLOW']/*[name()='bpmn:conditionExpression']/text()",
                "= automaticOutcome = \"WAIT_FOR_REPLY\"");
        assertFeel(relatedModel,
                "//*[@id='Fortsetzung_Aktiven_Fall_REVIEW_zu_REVIEW_THROW_FLOW']/*[name()='bpmn:conditionExpression']/text()",
                "= automaticOutcome = \"REVIEW\"");
        assertValue(relatedModel,
                "//*[@id='Automatische_Abklaerung_CALL']//*[name()='zeebe:output'][@target='automaticOutcome']/@source",
                "= outcome");
        assertValue(relatedModel,
                "//*[@id='Automatische_Abklaerung_CALL']//*[name()='zeebe:output'][@target='automaticDecisionRef']/@source",
                "= decisionRef");
    }

    @Test
    void automaticClosureWritesThenChecksBeforeClosingAndKeepsErrorRoutes() throws Exception {
        XPath xpath = XPathFactory.newInstance().newXPath();
        Document automatic = read("src/main/resources/bpmn/automatische-abklaerung.bpmn");
        String write = "Abschlussnachricht_schreiben_SERVICE";
        String check = "Abschlussnachricht_pruefen_SERVICE";
        String close = AutomaticClarificationBpmnConstants.ServiceTask.SAVE_AUTOMATIC_CLOSURE;
        for (Map.Entry<String, String> stage : Map.of(write, AUTOMATIC_WRITE_CLOSURE_MESSAGE,
                check, AUTOMATIC_CHECK_CLOSURE_MESSAGE).entrySet()) {
            String definition = "//*[@id='" + stage.getKey() + "']//*[name()='zeebe:taskDefinition']";
            assertEquals(stage.getValue(), xpath.evaluate(definition + "/@type", automatic));
            assertEquals("3", xpath.evaluate(definition + "/@retries", automatic));
            assertClosureMessageBoundary(xpath, automatic, stage.getKey(), PUBLICATION_BASIS_STALE);
            assertClosureMessageBoundary(xpath, automatic, stage.getKey(), CLOSURE_MESSAGE_INVALID);
        }
        assertEquals(check, xpath.evaluate("//*[name()='bpmn:sequenceFlow'][@sourceRef='" + write + "']/@targetRef", automatic));
        assertEquals(close, xpath.evaluate("//*[name()='bpmn:sequenceFlow'][@sourceRef='" + check + "']/@targetRef", automatic));
        assertClosureMessageBoundary(xpath, automatic, check, CLOSURE_DRAFT_MISSING);
    }

    @Test
    void followUpClosureUsesVersionedSubscriptionAndCommonStaffReturn() throws Exception {
        Document model = read("src/main/resources/bpmn/wiedervorlage.bpmn");
        Document relatedModel = read("src/main/resources/bpmn/mitarbeiterbearbeitung.bpmn");
        assertValue(model,
                "//*[@id='Wiedervorlage_beenden_SERVICE']//*[name()='zeebe:taskDefinition']/@type",
                FollowUpBpmnConstants.JobType.FOLLOW_UP_CLOSE);
        assertValue(model,
                "//*[@id='Fehler_bei_Wiedervorlage_beenden_BOUNDARY']/@attachedToRef",
                "Wiedervorlage_beenden_SERVICE");
        assertValue(model,
                "//*[@id='Fehler_bei_Wiedervorlage_beenden_BOUNDARY']//*[name()='bpmn:errorEventDefinition']/@errorRef",
                "Error_FollowUpChangeStale_ERROR");
        assertValue(relatedModel,
                "//*[@id='Wiedervorlage_verwalten_CALL']//*[name()='zeebe:output'][@target='outcome']/@source",
                "= outcome");
        assertValue(relatedModel,
                "//*[@id='Wiedervorlage_verwalten_CALL']//*[name()='zeebe:output'][@target='decisionRef']/@source",
                "= decisionRef");
    }

    @Test
    void reviewUsesOriginalWorkerMessageConditionAndScopeContracts() throws Exception {
        Document model = read("src/main/resources/bpmn/propertyflow-fallprozess-entwurf.bpmn");
        assertFeel(model,
                "//*[@id='Bestaetigte_Abloesung_BOUNDARY']//*[name()='bpmn:condition']/text()",
                "= automaticInterruptionReady = true and automaticInterruptionCallRef = automaticCallRef");
        assertFeel(model,
                "//*[@id='Aktuelle_Situation_XOR_Pruefung_bereits_offen_zu_Offene_Pruefung_ergaenzen_SERVICE_FLOW']/*[name()='bpmn:conditionExpression']/text()",
                "= reviewRoute = \"EXISTING\"");
        assertFeel(model,
                "//*[@id='Aktuelle_Situation_XOR_Wiederholung_beruecksichtigt_abgeschlossen_zu_Keine_neue_Pruefung_aus_FLOW']/*[name()='bpmn:conditionExpression']/text()",
                "= reviewRoute = \"NONE\"");
        assertFeel(model,
                "//*[@id='Aktuelle_Situation_XOR_Andere_aktive_Phase_zu_Pruefbedarf_an_aktuelle_Fallphase_FLOW']/*[name()='bpmn:conditionExpression']/text()",
                "= reviewRoute = \"OTHER\"");
        assertFeel(model,
                "//*[@id='Wer_fuehrt_weiter_START_neuer_Aufruf_zu_Mitarbeiterpruefung_und_Bearbeitung_CALL_FLOW']/*[name()='bpmn:conditionExpression']/text()",
                "= staffContinuationOutcome = \"START\"");
        assertFeel(model,
                "//*[@id='Wer_fuehrt_weiter_EXISTING_bereits_aktiv_zu_Bestehende_Bearbeitung_ergaenzen_SERVICE_FLOW']/*[name()='bpmn:conditionExpression']/text()",
                "= staffContinuationOutcome = \"EXISTING\"");
        assertValue(model,
                "count(//*[@id='Neuen_Pruefbedarf_bearbeiten_SUBPROCESS']/*[name()='bpmn:extensionElements']//*[name()='zeebe:input'])",
                "3");
        assertValue(model,
                "count(//*[@id='Falldaten_ergaenzen_SUBPROCESS']/*[name()='bpmn:extensionElements']//*[name()='zeebe:input'])",
                "1");
        assertValue(model,
                "//*[@id='Mitarbeiterpruefung_und_Bearbeitung_CALL']//*[name()='zeebe:calledElement']/@propagateAllChildVariables",
                "false");
        assertValue(model,
                "//*[@id='Mitarbeiterpruefung_und_Bearbeitung_CALL']//*[name()='zeebe:calledElement']/@propagateAllParentVariables",
                "false");
        assertValue(model,
                "//*[@id='Mitarbeiterpruefung_und_Bearbeitung_CALL']//*[name()='zeebe:calledElement']/@bindingType",
                "deployment");
    }

    @Test
    void staffEntryUsesRealCamundaUserTaskAndMatchingReviewRoute() throws Exception {
        Document model = read("src/main/resources/bpmn/mitarbeiterbearbeitung.bpmn");
        assertValue(model,
                "//*[@id='Mitarbeitereintritt_pruefen_SERVICE']//*[name()='zeebe:taskDefinition']/@type",
                StaffProcessingBpmnConstants.JobType.STAFF_REGISTER);
        assertValue(model,
                "//*[@id='Mitarbeiterpruefung_festhalten_SERVICE']//*[name()='zeebe:taskDefinition']/@type",
                StaffProcessingBpmnConstants.JobType.STAFF_START_REVIEW);
        assertFeel(model,
                "//*[@id='Gepruefte_Fortsetzung_XOR_Ja_regulaerer_Eintritt_zu_Mitarbeiterpruefung_festhalten_zusammenfuehren_XOR_FLOW']/*[name()='bpmn:conditionExpression']/text()",
                "= staffEntryKind = \"REVIEW\"");
        assertValue(model,
                "count(//*[@id='Fall_pruefen_und_Vorgehen_entscheiden_USERTASK']//*[name()='zeebe:userTask'])",
                "1");
    }

    @Test
    void staffMessagesUseLocalReferencesAndEntryCorrelationAvailableBeforeChildStartup() throws Exception {
        Document model = read("src/main/resources/bpmn/propertyflow-fallprozess-entwurf.bpmn");
        Document relatedModel = read("src/main/resources/bpmn/mitarbeiterbearbeitung.bpmn");
        assertValue(model,
                "//*[@id='Mitarbeiternachricht_speichern_SERVICE']//*[name()='zeebe:taskDefinition']/@type",
                CommonBpmnConstants.JobType.STAFF_MESSAGE_PUBLISH);
        assertFeel(model,
                "//*[@id='Gespeichertes_Ergebnis_XOR_INFORMATION_ohne_Antwortbedarf_zu_Information_gespeichert_Automatik_bleibt_FLOW']/*[name()='bpmn:conditionExpression']/text()",
                "=staffMessageOutcome = \"INFORMATION\"");
        assertFeel(model,
                "//*[@id='Gespeichertes_Ergebnis_XOR_QUESTION_Rueckfrage_gespeichert_zu_Rueckfrage_zur_Abloesung_bereitstellen_FLOW']/*[name()='bpmn:conditionExpression']/text()",
                "=staffMessageOutcome = \"QUESTION\"");
        assertValue(relatedModel,
                "//*[@id='Mitarbeiternachricht_als_Nebenaktion_senden_MESSAGE']//*[name()='zeebe:subscription']/@correlationKey",
                "=entryContextRef");
        assertValue(relatedModel,
                "//*[@id='Nachricht_und_Versandauftrag_speichern_SERVICE']//*[name()='zeebe:taskDefinition']/@type",
                CommonBpmnConstants.JobType.STAFF_MESSAGE_PUBLISH);
        assertValue(relatedModel,
                "//*[@id='Nachricht_bei_offener_Aufgabe_senden_SUBPROCESS']/@triggeredByEvent",
                "true");
        assertValue(relatedModel,
                "//*[@id='Sendeauftrag_eingegangen_START']/@isInterrupting",
                "false");
        assertValue(relatedModel,
                "//*[@id='Sendeauftrag_eingegangen_START']/*[name()='bpmn:messageEventDefinition']/@messageRef",
                "Mitarbeiternachricht_als_Nebenaktion_senden_MESSAGE");
        assertValue(relatedModel,
                "//*[@id='Nachricht_bei_offener_Aufgabe_senden_SUBPROCESS']/*[name()='bpmn:extensionElements']//*[name()='zeebe:input'][@target='staffMessageCommandRef']/@source",
                "=null");
        assertValue(relatedModel,
                "//*[@id='Nachricht_bei_offener_Aufgabe_senden_SUBPROCESS']/*[name()='bpmn:extensionElements']//*[name()='zeebe:input'][@target='staffMessageOutcome']/@source",
                "=null");
        assertValue(model,
                "//*[@id='Mitarbeiternachricht_in_Abklaerung_senden_SUBPROCESS']/../@id",
                "Mitarbeiteraktion_ausfuehren_SUBPROCESS");
        XPath xpath = XPathFactory.newInstance().newXPath();
        String mainPlane = "//*[name()='bpmndi:BPMNPlane'][@bpmnElement='Mitarbeiterbearbeitung_PROCESS']";
        String detailPlane = "//*[@id='Nachricht_bei_offener_Aufgabe_senden_SUBPROCESS_DIAGRAM']/*[name()='bpmndi:BPMNPlane'][@bpmnElement='Nachricht_bei_offener_Aufgabe_senden_SUBPROCESS']";
        assertEquals("false", xpath.evaluate(mainPlane + "/*[name()='bpmndi:BPMNShape'][@bpmnElement='Nachricht_bei_offener_Aufgabe_senden_SUBPROCESS']/@isExpanded", relatedModel));
        assertEquals("1", xpath.evaluate("count(" + detailPlane + ")", relatedModel));
        for (String id : new String[] {"Sendeauftrag_eingegangen_START", "Nachricht_und_Versandauftrag_speichern_SERVICE", "Nur_Nebenaktion_beendet_END", "Sendeauftrag_eingegangen_START_zu_Nachricht_und_Versandauftrag_speichern_FLOW", "Nachricht_und_Versandauftrag_zu_Nur_Nebenaktion_beendet_END_FLOW"}) {
            assertEquals("0", xpath.evaluate("count(" + mainPlane + "/*[@bpmnElement='" + id + "'])", relatedModel));
            assertEquals("1", xpath.evaluate("count(" + detailPlane + "/*[@bpmnElement='" + id + "'])", relatedModel));
        }
    }

    @Test
    void oneStaffFollowUpBoundaryUsesBothVersionBoundPredicatesAndTheExistingStop() throws Exception {
        Document model = read("src/main/resources/bpmn/mitarbeiterbearbeitung.bpmn");
        XPath xpath = XPathFactory.newInstance().newXPath();
        String boundary = "//*[name()='bpmn:boundaryEvent'][@attachedToRef='Wiedervorlage_verwalten_CALL']";
        assertEquals("1", xpath.evaluate("count(" + boundary + ")", model));
        assertEquals("Wiedervorlage_unterbrochen_BOUNDARY", xpath.evaluate(boundary + "/@id", model));
        assertTrue(!"false".equals(xpath.evaluate(boundary + "/@cancelActivity", model)));
        assertValue(model,
                "//*[@id='Wiedervorlage_aufheben_und_Pruefung_starten_SERVICE']//*[name()='zeebe:taskDefinition']/@type",
                StaffProcessingBpmnConstants.JobType.FOLLOW_UP_STOP_CONTACT);
        assertValue(model,
                "count(//*[name()='bpmn:boundaryEvent'][@attachedToRef='Wiedervorlage_verwalten_CALL'])",
                "1");
        assertFeel(model,
                "//*[@id='Wiedervorlage_unterbrochen_BOUNDARY']/*[name()='bpmn:conditionalEventDefinition']/*[name()='bpmn:condition']/text()",
                "= (pendingTenantContactFollowUpVersion!=nullandpendingTenantContactFollowUpVersion=activeFollowUpVersion) or (pendingReviewFollowUpVersion!=nullandpendingReviewFollowUpVersion=activeFollowUpVersion)");
        assertValue(model,
                "//*[@id='Wiedervorlage_unterbrochen_BOUNDARY_zu_Wiedervorlage_aufheben_und_Pruefung_FLOW']/@sourceRef",
                "Wiedervorlage_unterbrochen_BOUNDARY");
        assertValue(model,
                "//*[@id='Wiedervorlage_unterbrochen_BOUNDARY_zu_Wiedervorlage_aufheben_und_Pruefung_FLOW']/@targetRef",
                "Wiedervorlage_aufheben_und_Pruefung_starten_SERVICE");
    }

    @Test
    void replyUsesWorkerBindingsAndExplicitCallOutputs() throws Exception {
        Document model = read("src/main/resources/bpmn/mieterantwort-pruefen.bpmn");
        Document relatedModel = read("src/main/resources/bpmn/propertyflow-fallprozess-entwurf.bpmn");
        Document model3 = read("src/main/resources/bpmn/mitarbeiterbearbeitung.bpmn");
        assertValue(model,
                "//*[@id='Mieterwarten_registrieren_SERVICE']//*[name()='zeebe:taskDefinition']/@type",
                TenantReplyBpmnConstants.JobType.REPLY_REGISTER);
        assertValue(model,
                "//*[@id='Fortsetzung_nach_Mieterantwort_pruefen_SERVICE']//*[name()='zeebe:taskDefinition']/@type",
                TenantReplyBpmnConstants.JobType.REPLY_VALIDATE);
        assertValue(model,
                "//*[@id='Nachricht_im_Mieterwarten_speichern_SERVICE']//*[name()='zeebe:taskDefinition']/@type",
                CommonBpmnConstants.JobType.STAFF_MESSAGE_PUBLISH);
        assertValue(model,
                "//*[@id='Antwortauswertung_uebernehmen_SERVICE']//*[name()='zeebe:taskDefinition']/@type",
                TenantReplyBpmnConstants.JobType.REPLY_APPLY_EVALUATION);
        assertValue(model,
                "//*[@id='Mitarbeiternachricht_waehrend_Mieterwartephase_senden_MESSAGE']//*[name()='zeebe:subscription']/@correlationKey",
                "= entryContextRef");
        assertValue(relatedModel,
                "//*[@id='Auf_ausgewertete_Mieterantwort_warten_CALL']//*[name()='zeebe:calledElement']/@propagateAllChildVariables",
                "false");
        assertValue(relatedModel,
                "count(//*[@id='Auf_ausgewertete_Mieterantwort_warten_CALL']//*[name()='zeebe:output'][@target!='replyContinuationSource'])",
                "3");
        assertValue(relatedModel,
                "//*[@id='Mitarbeiterpruefung_und_Bearbeitung_CALL']//*[name()='zeebe:calledElement']/@propagateAllChildVariables",
                "false");
        assertValue(relatedModel,
                "count(//*[@id='Mitarbeiterpruefung_und_Bearbeitung_CALL']//*[name()='zeebe:output'][@target!='replyContinuationSource'])",
                "3");
        assertValue(relatedModel,
                "//*[@id='Auf_ausgewertete_Mieterantwort_warten_CALL']//*[name()='zeebe:output'][@target='replyOutcome']/@source",
                "= outcome");
        assertValue(relatedModel,
                "//*[@id='Auf_ausgewertete_Mieterantwort_warten_CALL']//*[name()='zeebe:output'][@target='replyDecisionRef']/@source",
                "= decisionRef");
        assertValue(relatedModel,
                "//*[@id='Auf_ausgewertete_Mieterantwort_warten_CALL']//*[name()='zeebe:output'][@target='staffEntryContextRef']/@source",
                "= if outcome = \"REVIEW\" then decisionRef else staffEntryContextRef");
        assertFeel(relatedModel,
                "//*[@id='Fortsetzung_Aktiven_Fall_Rueckfrage_veroeffentlicht_zu_Auf_ausgewertete_Mieterantwort_warten_FLOW']/*[name()='bpmn:conditionExpression']/text()",
                "= staffOutcome = \"WAIT_FOR_REPLY\"");
        assertFeel(relatedModel,
                "//*[@id='Wie_geht_der_Antwortbedarf_offen_erneut_warten_zu_Erneut_warten_zusammenfuehren_XOR_FLOW']/*[name()='bpmn:conditionExpression']/text()",
                "= replyOutcome = \"WAIT\"");
        assertFeel(relatedModel,
                "//*[@id='Wie_geht_der_Pruefung_erforderlich_zu_Mitarbeiterbearbeitung_zusammenfuehren_XOR_FLOW']/*[name()='bpmn:conditionExpression']/text()",
                "= replyOutcome = \"REVIEW\"");
        assertValue(relatedModel,
                "//*[@id='Pruefkontext_fuer_Mitarbeiter_bereitstellen_SERVICE']//*[name()='zeebe:taskDefinition']/@type",
                CaseProcessBpmnConstants.JobType.REPLY_PREPARE_REVIEW);
        assertValue(relatedModel,
                "//*[@id='Pruefbedarf_im_Mieterwarten_melden_SERVICE']//*[name()='zeebe:taskDefinition']/@type",
                CaseProcessBpmnConstants.JobType.REPLY_WAKE_REVIEW);
        assertValue(relatedModel,
                "//*[@id='Antwortbedarf_aufheben_SERVICE']//*[name()='zeebe:taskDefinition']/@type",
                CaseProcessBpmnConstants.JobType.ANSWER_CLEAR);
        assertValue(relatedModel,
                "//*[@id='Fortsetzung_ohne_Antwortbedarf_pruefen_SERVICE']//*[name()='zeebe:taskDefinition']/@type",
                CaseProcessBpmnConstants.JobType.ANSWER_CONTINUE);
        assertValue(model3,
                "//*[@id='Mitarbeiterrueckfrage_veroeffentlichen_SERVICE']//*[name()='zeebe:taskDefinition']/@type",
                StaffProcessingBpmnConstants.JobType.STAFF_QUESTION_PUBLISH);
        assertFeel(model3,
                "//*[@id='Naechste_Aktion_XOR_Rueckfrage_zu_Mitarbeiterrueckfrage_veroeffentlichen_SERVICE_FLOW']/*[name()='bpmn:conditionExpression']/text()",
                "= staffDecision = \"QUESTION\"");
        assertValue(model3,
                "//*[@id='Fehler_bei_Mitarbeiterrueckfrage_veroeffentlichen_BOUNDARY']/*[name()='bpmn:errorEventDefinition']/@errorRef",
                "Bestaetigter_Fallstand_geaendert_ERROR");
    }

    @Test
    void questionReentryMatchesOriginalPreparationAndExplicitReplyDecision() throws Exception {
        Document model = read("src/main/resources/bpmn/propertyflow-fallprozess-entwurf.bpmn");
        assertValue(model,
                "//*[@id='Erneute_Katalogrueckfrage_vorbereiten_SERVICE']//*[name()='zeebe:taskDefinition']/@type",
                CaseProcessBpmnConstants.JobType.AUTOMATIC_PREPARE_QUESTION_ROUND);
        assertFeel(model,
                "//*[@id='Wie_geht_der_Weitere_Katalogfrage_zulaessig_zu_Erneute_Katalogrueckfrage_vorbereiten_SERVICE_FLOW']/*[name()='bpmn:conditionExpression']/text()",
                "= replyOutcome = \"QUESTION\"");
        assertValue(model,
                "//*[@id='Auf_ausgewertete_Mieterantwort_warten_CALL']//*[name()='zeebe:output'][@target='replyDecisionRef']/@source",
                "= decisionRef");
    }

    @Test
    void takeoverHasLocalCommandExplicitOutcomeAndHandoverBoundaries() throws Exception {
        Document model = read("src/main/resources/bpmn/propertyflow-fallprozess-entwurf.bpmn");
        assertValue(model,
                "//*[@id='Mitarbeiteruebernahme_speichern_SERVICE']//*[name()='zeebe:taskDefinition']/@type",
                CaseProcessBpmnConstants.JobType.TAKEOVER_APPLY);
        assertValue(model,
                "//*[@id='Uebernahme_zur_Abloesung_bereitstellen_SERVICE']//*[name()='zeebe:taskDefinition']/@type",
                CaseProcessBpmnConstants.JobType.TAKEOVER_SIGNAL);
        assertFeel(model,
                "//*[@id='Uebernahme_gespeichert_XOR_Ja_bereits_uebernommen_zu_Uebernahme_zur_Abloesung_bereitstellen_FLOW']/*[name()='bpmn:conditionExpression']/text()",
                "= staffTakeoverOutcome = \"APPLIED\"");
        assertFeel(model,
                "//*[@id='Uebernahme_gespeichert_XOR_Nein_zu_Abgelehnt_aktuellen_Stand_anzeigen_FLOW']/*[name()='bpmn:conditionExpression']/text()",
                "= staffTakeoverOutcome = \"REJECTED\"");
        assertValue(model,
                "//*[@id='Aktionsrundlauf_SUBPROCESS']/*[name()='bpmn:extensionElements']//*[name()='zeebe:input'][@target='staffTakeoverCommandRef']/@source",
                "= null");
        assertValue(model,
                "//*[@id='Bearbeitung_in_Abklaerung_uebernehmen_SUBPROCESS']/*[name()='bpmn:extensionElements']//*[name()='zeebe:input'][@target='staffTakeoverCommandRef']/@source",
                "= staffTakeoverCommandRef");
    }

    @Test
    void manualClarificationQuestionUsesExplicitInterruptionAndWaitOutcome() throws Exception {
        Document model = read("src/main/resources/bpmn/propertyflow-fallprozess-entwurf.bpmn");
        assertValue(model,
                "//*[@id='Rueckfrage_zur_Abloesung_bereitstellen_SERVICE']//*[name()='zeebe:taskDefinition']/@type",
                CaseProcessBpmnConstants.JobType.CLARIFICATION_QUESTION_SIGNAL);
        assertFeel(model,
                "//*[@id='Gepruefte_Fortsetzung_XOR_Veroeffentlichte_Rueckfrage_zu_Erneut_warten_zusammenfuehren_XOR_FLOW']/*[name()='bpmn:conditionExpression']/text()",
                "= automaticInterruptionOutcome = \"WAIT_FOR_REPLY\"");
        assertFeel(model,
                "//*[@id='Gepruefte_Fortsetzung_XOR_Pruefung_Bearbeitung_Wiedervorlage_zu_Mitarbeiterbearbeitung_zusammenfuehren_XOR_FLOW']/*[name()='bpmn:conditionExpression']/text()",
                "= automaticInterruptionOutcome = \"STAFF\"");
        assertFeel(model,
                "//*[@id='Gespeichertes_Ergebnis_XOR_QUESTION_Rueckfrage_gespeichert_zu_Rueckfrage_zur_Abloesung_bereitstellen_FLOW']/*[name()='bpmn:conditionExpression']/text()",
                "=staffMessageOutcome = \"QUESTION\"");
        assertFeel(model,
                "//*[@id='Gespeichertes_Ergebnis_XOR_INFORMATION_ohne_Antwortbedarf_zu_Information_gespeichert_Automatik_bleibt_FLOW']/*[name()='bpmn:conditionExpression']/text()",
                "=staffMessageOutcome = \"INFORMATION\"");
    }

    @Test
    void oneAutomaticResultInterruptionKeepsThePreviousPredicatesAndPriority() throws Exception {
        Document model = read("src/main/resources/bpmn/propertyflow-fallprozess-entwurf.bpmn");
        XPath xpath = XPathFactory.newInstance().newXPath();
        String boundary = "//*[name()='bpmn:boundaryEvent'][@attachedToRef='Automatik_Ergebnis_uebernehmen_SERVICE']";
        assertEquals("1", xpath.evaluate("count(" + boundary + ")", model));
        assertEquals("Uebergabe_abgeloest_BOUNDARY", xpath.evaluate(boundary + "/@id", model));
        assertTrue(!"false".equals(xpath.evaluate(boundary + "/@cancelActivity", model)));
        String interruptedFlow = "//*[@id='Uebergabe_abgeloest_BOUNDARY_zu_Fortsetzung_pruefen_Aktiven_Fall_FLOW']";
        assertEquals("Uebergabe_abgeloest_BOUNDARY", xpath.evaluate(interruptedFlow + "/@sourceRef", model));
        String continuationThrowId = xpath.evaluate(interruptedFlow + "/@targetRef", model);
        String linkDefinition = "*[name()='bpmn:linkEventDefinition']";
        String continuationThrow = "//*[@id='Aktiven_Fall_bearbeiten_SUBPROCESS']/*[name()='bpmn:intermediateThrowEvent'][@id='"
                + continuationThrowId + "']";
        assertEquals("REPLY_CONTINUATION", xpath.evaluate(continuationThrow + "/" + linkDefinition + "/@name", model));
        String continuationCatch = "//*[@id='Aktiven_Fall_bearbeiten_SUBPROCESS']/*[name()='bpmn:intermediateCatchEvent']["
                + linkDefinition + "/@name='REPLY_CONTINUATION']";
        assertEquals("1", xpath.evaluate("count(" + continuationCatch + ")", model));
        String catchFlow = xpath.evaluate(continuationCatch + "/*[name()='bpmn:outgoing']/text()", model);
        assertEquals("Fortsetzung_bestimmen_zusammenfuehren_XOR", xpath.evaluate("//*[@id='" + catchFlow + "']/@targetRef", model));

        assertValue(model,
                "count(//*[name()='bpmn:boundaryEvent'][@attachedToRef='Automatik_Ergebnis_uebernehmen_SERVICE'])",
                "1");
        assertFeel(model,
                "//*[@id='Uebergabe_abgeloest_BOUNDARY']/*[name()='bpmn:conditionalEventDefinition']/*[name()='bpmn:condition']/text()",
                "=(manualQuestionCallRef!=nullandmanualQuestionCallRef=automaticCallRef)or((staffTakeoverCallRef!=nullandstaffTakeoverCallRef=automaticCallRef)or(staffFollowAutomaticCallRef!=nullandstaffFollowAutomaticCallRef=automaticCallRef))or(clearedAutomaticCallRef!=nullandclearedAutomaticCallRef=automaticCallRef)");
        assertFeel(model,
                "//*[@id='Uebergabe_abgeloest_BOUNDARY']//*[name()='zeebe:output'][@target='replyContinuationSource']/@source",
                "=if(manualQuestionCallRef!=nullandmanualQuestionCallRef=automaticCallRef)or((staffTakeoverCallRef!=nullandstaffTakeoverCallRef=automaticCallRef)or(staffFollowAutomaticCallRef!=nullandstaffFollowAutomaticCallRef=automaticCallRef))then\"AUTOMATIC\"elseif(clearedAutomaticCallRef!=nullandclearedAutomaticCallRef=automaticCallRef)then\"CLEAR\"elsenull");
    }

    @Test
    void oneReplyExitKeepsEveryCallBoundPredicateAndItsContinuation() throws Exception {
        Document model = read("src/main/resources/bpmn/propertyflow-fallprozess-entwurf.bpmn");
        XPath xpath = XPathFactory.newInstance().newXPath();
        String boundary = "//*[name()='bpmn:boundaryEvent'][@attachedToRef='Auf_ausgewertete_Mieterantwort_warten_CALL']";
        assertEquals("1", xpath.evaluate("count(" + boundary + ")", model));
        assertEquals("Wartephase_abgeloest_BOUNDARY", xpath.evaluate(boundary + "/@id", model));
        assertTrue(!"false".equals(xpath.evaluate(boundary + "/@cancelActivity", model)));
        assertEquals("Wartephase_abgeloest_BOUNDARY", xpath.evaluate("//*[@id='Wartephase_abgeloest_BOUNDARY_zu_Fortsetzung_bestimmen_zusammenfuehren_XOR_FLOW']/@sourceRef", model));
        for (String entry : new String[] {"Wartephase_abgeloest_BOUNDARY_zu_Fortsetzung_bestimmen_zusammenfuehren_XOR_FLOW", "Auf_ausgewertete_Mieterantwort_Antwort_ausgewertet_zu_Fortsetzung_bestimmen_zusammenfuehren_XOR_FLOW"}) {
            assertEquals("Fortsetzung_bestimmen_zusammenfuehren_XOR", xpath.evaluate("//*[@id='" + entry + "']/@targetRef", model));
        }
        assertEquals("Fortsetzung_bestimmen_SUBPROCESS", xpath.evaluate("//*[name()='bpmn:sequenceFlow'][@sourceRef='Fortsetzung_bestimmen_zusammenfuehren_XOR']/@targetRef", model));
        assertEquals("bpmn:exclusiveGateway", xpath.evaluate("name(//*[@id='Warum_endet_das_Warten_XOR'])", model));
        assertEquals("", xpath.evaluate("//*[@id='Warum_endet_das_Warten_XOR']/@default", model));
        assertEquals("Fortsetzung_bestimmen_SUBPROCESS", xpath.evaluate("//*[@id='Warum_endet_das_Warten_XOR']/../@id", model));

        assertValue(model,
                "//*[@id='Warum_endet_das_Uebernahme_oder_Rueckfrage_bestaetigt_zu_Bestaetigte_Abloesung_pruefen_zusammenfuehren_FLOW']/@sourceRef",
                "Warum_endet_das_Warten_XOR");
        assertValue(model,
                "//*[@id='Warum_endet_das_Uebernahme_oder_Rueckfrage_bestaetigt_zu_Bestaetigte_Abloesung_pruefen_zusammenfuehren_FLOW']/@targetRef",
                "Bestaetigte_Abloesung_pruefen_zusammenfuehren_XOR");
        assertValue(model,
                "//*[@id='Warum_endet_das_Neuer_Pruefbedarf_zu_Pruefkontext_fuer_Mitarbeiter_bereitstellen_FLOW']/@sourceRef",
                "Warum_endet_das_Warten_XOR");
        assertValue(model,
                "//*[@id='Warum_endet_das_Neuer_Pruefbedarf_zu_Pruefkontext_fuer_Mitarbeiter_bereitstellen_FLOW']/@targetRef",
                "Pruefkontext_fuer_Mitarbeiter_bereitstellen_SERVICE");
        assertValue(model,
                "//*[@id='Warum_endet_das_Wiedervorlage_bestaetigt_zu_Wiedervorlage_Eintritt_vorbereiten_SERVICE_FLOW']/@sourceRef",
                "Warum_endet_das_Warten_XOR");
        assertValue(model,
                "//*[@id='Warum_endet_das_Wiedervorlage_bestaetigt_zu_Wiedervorlage_Eintritt_vorbereiten_SERVICE_FLOW']/@targetRef",
                "Wiedervorlage_Eintritt_vorbereiten_SERVICE");
        assertValue(model,
                "//*[@id='Warum_endet_das_Mitarbeiterabschluss_bestaetigt_zu_Mitarbeiterabschluss_pruefen_END_FLOW']/@sourceRef",
                "Warum_endet_das_Warten_XOR");
        assertValue(model,
                "//*[@id='Warum_endet_das_Mitarbeiterabschluss_bestaetigt_zu_Mitarbeiterabschluss_pruefen_END_FLOW']/@targetRef",
                "Mitarbeiterabschluss_pruefen_END");
        assertValue(model,
                "//*[@id='Warum_endet_das_Antwortbedarf_aufgehoben_zu_Fortsetzung_ohne_Antwortbedarf_pruefen_FLOW']/@sourceRef",
                "Warum_endet_das_Warten_XOR");
        assertValue(model,
                "//*[@id='Warum_endet_das_Antwortbedarf_aufgehoben_zu_Fortsetzung_ohne_Antwortbedarf_pruefen_FLOW']/@targetRef",
                "Fortsetzung_ohne_Antwortbedarf_pruefen_zusammenfuehren_XOR");
        assertValue(model,
                "count(//*[name()='bpmn:boundaryEvent'][@attachedToRef='Auf_ausgewertete_Mieterantwort_warten_CALL'])",
                "1");
        assertFeel(model,
                "//*[@id='Wartephase_abgeloest_BOUNDARY']/*[name()='bpmn:conditionalEventDefinition']/*[name()='bpmn:condition']/text()",
                "=(staffTakeoverReplyEntryRef!=nullandstaffTakeoverReplyEntryRef!=\"\"andstaffTakeoverReplyEntryRef=replyEntryContextRef)or(reviewNeedReplyCallRef!=nullandreviewNeedReplyCallRef=activeReplyCallRefandactiveReplyEntryRef!=nullandactiveReplyEntryRef=replyEntryContextRef)or(manualQuestionPreviousReplyEntryRef!=nullandmanualQuestionPreviousReplyEntryRef!=\"\"andmanualQuestionPreviousReplyEntryRef=replyEntryContextRef)or(staffFollowReplyEntryRef!=nullandstaffFollowReplyEntryRef!=\"\"andstaffFollowReplyEntryRef=replyEntryContextRefand(staffFollowReplyCallRef=\"\"orstaffFollowReplyCallRef=activeReplyCallRef))or(staffCloseReplyCallRef!=nullandstaffCloseReplyCallRef=activeReplyCallRefandstaffCloseReplyEntryRef=replyEntryContextRef)or((clearedAnswerCallRef!=nullandclearedAnswerCallRef=activeReplyCallRefandactiveReplyEntryRef!=nullandactiveReplyEntryRef=replyEntryContextRef)or(clearedReplyEntryRef!=nullandclearedReplyEntryRef=replyEntryContextRef))");
        assertFeel(model,
                "//*[@id='Wartephase_abgeloest_BOUNDARY']//*[name()='zeebe:output'][@target='replyExitReason']/@source",
                "=if(staffTakeoverReplyEntryRef!=nullandstaffTakeoverReplyEntryRef!=\"\"andstaffTakeoverReplyEntryRef=replyEntryContextRef)then\"TAKEOVER\"elseif(reviewNeedReplyCallRef!=nullandreviewNeedReplyCallRef=activeReplyCallRefandactiveReplyEntryRef!=nullandactiveReplyEntryRef=replyEntryContextRef)then\"REVIEW\"elseif(manualQuestionPreviousReplyEntryRef!=nullandmanualQuestionPreviousReplyEntryRef!=\"\"andmanualQuestionPreviousReplyEntryRef=replyEntryContextRef)then\"MANUAL_QUESTION\"elseif(staffFollowReplyEntryRef!=nullandstaffFollowReplyEntryRef!=\"\"andstaffFollowReplyEntryRef=replyEntryContextRefand(staffFollowReplyCallRef=\"\"orstaffFollowReplyCallRef=activeReplyCallRef))then\"FOLLOW_UP\"elseif(staffCloseReplyCallRef!=nullandstaffCloseReplyCallRef=activeReplyCallRefandstaffCloseReplyEntryRef=replyEntryContextRef)then\"CLOSE\"elseif((clearedAnswerCallRef!=nullandclearedAnswerCallRef=activeReplyCallRefandactiveReplyEntryRef!=nullandactiveReplyEntryRef=replyEntryContextRef)or(clearedReplyEntryRef!=nullandclearedReplyEntryRef=replyEntryContextRef))then\"CLEAR_ANSWER\"elsenull");
        assertFeel(model,
                "//*[@id='Wartephase_abgeloest_BOUNDARY']//*[name()='zeebe:output'][@target='staffOutcome']/@source",
                "=ifreplyExitReason=\"CLOSE\"then\"CLOSE\"elsestaffOutcome");
        assertFeel(model,
                "//*[@id='Wartephase_abgeloest_BOUNDARY']//*[name()='zeebe:output'][@target='staffDecisionRef']/@source",
                "=ifreplyExitReason=\"CLOSE\"thenstaffReplyClosureDecisionRefelsestaffDecisionRef");
        assertFeel(model,
                "//*[@id='Wartephase_abgeloest_BOUNDARY']//*[name()='zeebe:output'][@target='directFollowContinuationRef']/@source",
                "=ifreplyExitReason=\"FOLLOW_UP\"thenstaffFollowCommandRefelsedirectFollowContinuationRef");
    }

    @Test
    void replyContinuationHidesReasonRoutingBehindSharedBusinessOutcomes() throws Exception {
        XPath xpath = XPathFactory.newInstance().newXPath();
        Document model = read("src/main/resources/bpmn/propertyflow-fallprozess-entwurf.bpmn");
        String mainPlane = "//*[name()='bpmndi:BPMNPlane'][@bpmnElement='Aktiven_Fall_bearbeiten_SUBPROCESS']";
        String detailPlane = "//*[name()='bpmndi:BPMNPlane'][@bpmnElement='Fortsetzung_bestimmen_SUBPROCESS']";
        String shape = "/*[name()='bpmndi:BPMNShape']";
        assertEquals("false", xpath.evaluate(mainPlane + shape + "[@bpmnElement='Fortsetzung_bestimmen_SUBPROCESS']/@isExpanded", model));
        assertEquals("1", xpath.evaluate("count(" + detailPlane + ")", model));
        for (String id : new String[] {"Wie_geht_der_Fall_weiter_XOR", "Warum_endet_das_Warten_XOR", "Erneute_Katalogrueckfrage_vorbereiten_SERVICE", "Automatischen_Abschluss_vorbereiten_SERVICE",
                "Fortsetzung_ohne_Antwortbedarf_pruefen_SERVICE", "Bestaetigte_Abloesung_pruefen_SERVICE", "Pruefkontext_fuer_Mitarbeiter_bereitstellen_SERVICE", "Wiedervorlage_Eintritt_vorbereiten_SERVICE"}) {
            assertEquals("Fortsetzung_bestimmen_SUBPROCESS", xpath.evaluate("//*[@id='" + id + "']/../@id", model));
            assertEquals("0", xpath.evaluate("count(" + mainPlane + shape + "[@bpmnElement='" + id + "'])", model));
            assertEquals("1", xpath.evaluate("count(" + detailPlane + shape + "[@bpmnElement='" + id + "'])", model));
        }
        assertEquals("Naechste_Fallphase_XOR",
                xpath.evaluate("//*[name()='bpmn:sequenceFlow'][@sourceRef='Fortsetzung_bestimmen_SUBPROCESS']/@targetRef", model));
        for (Map.Entry<String, String> outcome : Map.of(
                "Wait", "Auf_ausgewertete_Mieterantwort_warten_zusammenfuehren_XOR", "Staff", "Aktiven_Fall_bearbeiten_verzweigen_XOR", "Automatic", "Automatische_Abklaerung_zusammenfuehren_XOR", "Close", "Mitarbeiterabschluss_pruefen_und_speichern_zusammenfuehren_XOR").entrySet()) {
            String flow = switch (outcome.getKey()) {
                case "Wait" -> "Naechste_Fallphase_XOR_Erneut_warten_zu_Auf_ausgewertete_Mieterantwort_warten_FLOW";
                case "Staff" -> "Naechste_Fallphase_XOR_zu_Mitarbeiterbearbeitung_THROW_FLOW";
                case "Automatic" -> "Naechste_Fallphase_XOR_zu_Automatik_fortsetzen_THROW_FLOW";
                case "Close" -> "Naechste_Fallphase_XOR_zu_Fall_abschliessen_THROW_FLOW";
                default -> throw new AssertionError(outcome.getKey());
            };
            String sequence = "//*[@id='" + flow + "']";
            assertEquals("Naechste_Fallphase_XOR", xpath.evaluate(sequence + "/@sourceRef", model));
            String expression = xpath.evaluate(sequence + "/*[name()='bpmn:conditionExpression']/text()", model);
            assertEquals("replyContinuationOutcome=\"" + outcome.getKey().toUpperCase(Locale.ROOT) + "\"",
                    normalizedFeel(expression), outcome.getKey());
            assertEquals(outcome.getValue(), targetAfterLink(xpath, model, flow), outcome.getKey());
        }
    }

    @Test
    void sharedEvaluationUsesSeparateWorkersAndKeepsParentErrorBoundary() throws Exception {
        XPath xpath = XPathFactory.newInstance().newXPath();
        Document parent = read("src/main/resources/bpmn/propertyflow-fallprozess-entwurf.bpmn");
        assertValue(parent, "//*[@id='Analysefehler_und_Pruefkontext_sichern_SERVICE']//*[name()='zeebe:taskDefinition']/@type", CaseProcessBpmnConstants.JobType.REVIEW_ANALYSIS_FAILURE);
        assertValue(parent, "//*[@id='Fachlicher_Analysefehler_BOUNDARY']/@attachedToRef", "Automatische_Abklaerung_CALL");
        assertValue(parent, "//*[@id='Fachlicher_Analysefehler_BOUNDARY']/*[name()='bpmn:errorEventDefinition']/@errorRef", "KI_Analyse_fehlgeschlagen_ERROR");
        assertValue(parent, "//*[@id='KI_Analyse_fehlgeschlagen_ERROR']/@errorCode", "TRIAGE_ANALYSIS_FAILED");
        Document automatic = read("src/main/resources/bpmn/automatische-abklaerung.bpmn");
        Document tenant = read("src/main/resources/bpmn/mieternachricht-verarbeiten.bpmn");
        Document reply = read("src/main/resources/bpmn/mieterantwort-pruefen.bpmn");
        Document evaluation = read("src/main/resources/bpmn/gespraech-auswerten.bpmn");
        String binding = "//*[name()='zeebe:taskDefinition']/@type";
        Map<String, String> stages = Map.of(
                ConversationEvaluationBpmnConstants.ServiceTask.ANALYZE_CONVERSATION, AUTOMATIC_ANALYZE,
                ConversationEvaluationBpmnConstants.ServiceTask.RECOMMEND_PROCEDURE, AUTOMATIC_ADVISE,
                ConversationEvaluationBpmnConstants.ServiceTask.ASSESS_CLOSURE, AUTOMATIC_ASSESS_CLOSURE);
        for (Map.Entry<String, String> stage : stages.entrySet()) {
            String jobType = "//*[name()='zeebe:taskDefinition'][@type='" + stage.getValue() + "']";
            assertEquals(stage.getValue(), xpath.evaluate("//*[@id='" + stage.getKey() + "']" + binding, evaluation));
            assertEquals("1", xpath.evaluate("count(" + jobType + ")", evaluation), stage.getKey());
            assertEquals("0", xpath.evaluate("count(" + jobType + ")", automatic), stage.getKey());
            assertEquals("0", xpath.evaluate("count(" + jobType + ")", tenant), stage.getKey());
            assertEquals("0", xpath.evaluate("count(" + jobType + ")", reply), stage.getKey());
        }
        assertSharedEvaluationCall(xpath, automatic, AutomaticClarificationBpmnConstants.CallActivity.EVALUATE_CONVERSATION);
        assertSharedEvaluationCall(xpath, tenant, TenantMessageBpmnConstants.CallActivity.EVALUATE_MESSAGE);
        assertSharedEvaluationCall(xpath, tenant, TenantMessageBpmnConstants.CallActivity.REFRESH_EVALUATION);
        assertSharedEvaluationCall(xpath, reply, TenantReplyBpmnConstants.CallActivity.EVALUATE_CONVERSATION);
        String replyCallId = TenantReplyBpmnConstants.CallActivity.EVALUATE_CONVERSATION;
        String replyInput = "//*[@id='" + replyCallId + "']//*[name()='zeebe:input']";
        assertEquals("3", xpath.evaluate("count(" + replyInput + ")", reply));
        for (String variable : new String[] {"caseId", "leadingProcessInstanceKey", "entryContextRef"}) {
            assertEquals("= " + variable, xpath.evaluate(replyInput + "[@target='" + variable + "']/@source", reply), variable);
        }
    }

    @Test
    void progressUsesNativeWorkTaskAndExplicitStaleDecisionBoundary() throws Exception {
        Document model = read("src/main/resources/bpmn/mitarbeiterbearbeitung.bpmn");
        assertValue(model,
                "//*[@id='Bearbeitung_fortsetzen_SERVICE']//*[name()='zeebe:taskDefinition']/@type",
                StaffProcessingBpmnConstants.JobType.STAFF_PROGRESS);
        assertFeel(model,
                "//*[@id='Bestaetigte_Aktion_XOR_Bearbeitung_fortsetzen_zu_Bearbeitung_fortsetzen_zusammenfuehren_XOR_FLOW']/*[name()='bpmn:conditionExpression']/text()",
                "= staffDecision = \"CONTINUE\"");
        assertValue(model,
                "//*[@id='Fallstand_geaendert_BOUNDARY']/@attachedToRef",
                "Bearbeitung_fortsetzen_SERVICE");
        assertValue(model,
                "//*[@id='Fallstand_geaendert_BOUNDARY']/*[name()='bpmn:errorEventDefinition']/@errorRef",
                "Bestaetigter_Fallstand_geaendert_ERROR");
        assertValue(model,
                "//*[@id='Bestaetigter_Fallstand_geaendert_ERROR']/@errorCode",
                "STAFF_DECISION_STALE");
        assertValue(model,
                "count(//*[@id='Naechste_Mitarbeiteraktion_abwarten_USERTASK']//*[name()='zeebe:userTask'])",
                "1");
    }

    @Test
    void closureUsesOriginalOutcomeMappingConflictScopeAndTerminatingEnd() throws Exception {
        Document model = read("src/main/resources/bpmn/propertyflow-fallprozess-entwurf.bpmn");
        Document relatedModel = read("src/main/resources/bpmn/mitarbeiterbearbeitung.bpmn");
        assertValue(model,
                "//*[@id='Mitarbeiterabschluss_pruefen_und_speichern_SERVICE']//*[name()='zeebe:taskDefinition']/@type",
                CaseProcessBpmnConstants.JobType.STAFF_CLOSE);
        assertFeel(model,
                "//*[@id='Fortsetzung_Aktiven_Fall_Abschluss_bestaetigt_zu_Mitarbeiterabschluss_pruefen_und_speichern_FLOW']/*[name()='bpmn:conditionExpression']/text()",
                "= staffOutcome = \"CLOSE\"");
        assertValue(model,
                "//*[@id='Mitarbeiterentscheidung_veraltet_BOUNDARY']/@attachedToRef",
                "Mitarbeiterabschluss_pruefen_und_speichern_SERVICE");
        assertValue(model,
                "//*[@id='Mitarbeiterentscheidung_veraltet_ERROR']/@errorCode",
                "STAFF_CLOSURE_STALE");
        assertValue(model,
                "//*[@id='Mitarbeiterpruefung_und_Bearbeitung_CALL']//*[name()='zeebe:output'][@target='staffOutcome']/@source",
                "= outcome");
        assertValue(model,
                "//*[@id='Mitarbeiterpruefung_und_Bearbeitung_CALL']//*[name()='zeebe:output'][@target='staffDecisionRef']/@source",
                "= decisionRef");
        assertValue(model,
                "//*[@id='Mitarbeiterentscheidung_veraltet_BOUNDARY']//*[name()='zeebe:output'][@target='staffEntryContextRef']/@source",
                "= staffEntryContextRef");
        assertFeel(relatedModel,
                "//*[@id='Bestaetigte_Aktion_XOR_Abschliessen_zu_Abschluss_bestaetigt_zusammenfuehren_XOR_FLOW']/*[name()='bpmn:conditionExpression']/text()",
                "= staffDecision = \"CLOSE\"");
        assertFeel(relatedModel,
                "//*[@id='Naechste_Aktion_XOR_Abschliessen_zu_Abschluss_bestaetigt_zusammenfuehren_XOR_FLOW']/*[name()='bpmn:conditionExpression']/text()",
                "= staffDecision = \"CLOSE\"");
        assertValue(model,
                "count(//*[@id='Aktive_Bearbeitung_beendet_Aktiven_Fall_bearbeiten_END']/*[name()='bpmn:terminateEventDefinition'])",
                "1");
    }

    @Test
    void tenantMessageReceptionHasLocalInputAndExplicitChildContract() throws Exception {
        Document model = read("src/main/resources/bpmn/propertyflow-fallprozess-entwurf.bpmn");
        Document relatedModel = read("src/main/resources/bpmn/mieternachricht-verarbeiten.bpmn");
        assertValue(model,
                "//*[@id='Mieternachricht_verarbeiten_CALL']//*[name()='zeebe:input'][@target='caseId']/@source",
                "= caseId");
        assertValue(model,
                "//*[@id='Mieternachricht_verarbeiten_CALL']//*[name()='zeebe:input'][@target='leadingProcessInstanceKey']/@source",
                "= processInstanceKey");
        assertValue(model,
                "//*[@id='Mieternachricht_verarbeiten_CALL']//*[name()='zeebe:input'][@target='tenantMessageId']/@source",
                "= tenantMessageId");
        assertValue(relatedModel,
                "//*[@id='Nachricht_und_Fallstand_pruefen_SERVICE']//*[name()='zeebe:taskDefinition']/@type",
                TenantMessageBpmnConstants.JobType.TENANT_MESSAGE_IDENTIFY);
        assertFeel(relatedModel,
                "//*[@id='Neu_zu_verarbeiten_Ja_zu_Aktuelle_Bearbeitung_XOR_FLOW']/*[name()='bpmn:conditionExpression']/text()",
                "= tenantMessageAccepted = true");
        assertValue(model,
                "count(//*[@id='Mieternachricht_verarbeiten_CALL']//*[name()='zeebe:output'])",
                "0");
        assertValue(model,
                "//*[@id='Aktionsrundlauf_SUBPROCESS']/*[name()='bpmn:extensionElements']//*[name()='zeebe:input'][@target='tenantMessageId']/@source",
                "= null");
        assertValue(model,
                "//*[@id='Mieternachricht_mit_LLM_verarbeiten_SUBPROCESS']/*[name()='bpmn:extensionElements']//*[name()='zeebe:input'][@target='tenantMessageId']/@source",
                "= tenantMessageId");
    }

    @Test
    void allNineActionsUseOneReceptionAndFourVisibleBranchesBeforeTheCommonEnd() throws Exception {
        XPath xpath = XPathFactory.newInstance().newXPath();
        Document model = read("src/main/resources/bpmn/propertyflow-fallprozess-entwurf.bpmn");
        String actionLoop = "//*[@id='Aktionsrundlauf_SUBPROCESS']";
        String directEventSubprocesses = "//*[@id='Aktiven_Fall_bearbeiten_SUBPROCESS']/*[name()='bpmn:subProcess'][@triggeredByEvent='true']";
        assertEquals("Aktionsrundlauf_SUBPROCESS", xpath.evaluate(directEventSubprocesses + "/@id", model));
        assertEquals("1", xpath.evaluate("count(" + directEventSubprocesses + ")", model));
        assertEquals("1", xpath.evaluate("count(" + actionLoop + "/*[name()='bpmn:startEvent'])", model));
        assertEquals("Aktionsrundlauf_SUBPROCESS", xpath.evaluate("//*[@id='Auf_Fallaktion_warten_START']/../@id", model));
        assertEquals("false", xpath.evaluate("//*[@id='Auf_Fallaktion_warten_START']/@isInterrupting", model));
        assertEquals("PropertyFlow_Fallaktion_MESSAGE", xpath.evaluate("//*[@id='Auf_Fallaktion_warten_START']/*[name()='bpmn:messageEventDefinition']/@messageRef", model));
        assertEquals(CaseActionMessage.MESSAGE_NAME, xpath.evaluate(CASE_ACTION_MESSAGE + "/@name", model));
        assertEquals("=processInstanceKey",
                xpath.evaluate(CASE_ACTION_MESSAGE + SUBSCRIPTION_KEY, model).replaceAll("\\s+", ""));

        Map<CaseActionMessage, ActionBranch> actions = Map.of(
                CaseActionMessage.TENANT_MESSAGE, new ActionBranch("Mieternachricht_mit_LLM_verarbeiten_SUBPROCESS", "tenantMessageId", ActionScope.DIRECT),
                CaseActionMessage.REVIEW_REQUIRED, new ActionBranch("Neuen_Pruefbedarf_bearbeiten_SUBPROCESS", "reviewSignalRef", ActionScope.DIRECT),
                CaseActionMessage.CLEAR_ANSWER, new ActionBranch("Antwortbedarf_aufheben_SUBPROCESS", "clearAnswerCommandRef", ActionScope.STAFF),
                CaseActionMessage.STAFF_QUESTION, new ActionBranch("Mitarbeiternachricht_in_Abklaerung_senden_SUBPROCESS", "staffMessageCommandRef", ActionScope.STAFF),
                CaseActionMessage.DIRECT_FOLLOW_UP, new ActionBranch("Direkte_Wiedervorlage_setzen_SUBPROCESS", "directFollowCommandRef", ActionScope.STAFF),
                CaseActionMessage.STAFF_TAKEOVER, new ActionBranch("Bearbeitung_in_Abklaerung_uebernehmen_SUBPROCESS", "staffTakeoverCommandRef", ActionScope.STAFF),
                CaseActionMessage.CASE_DATA, new ActionBranch("Falldaten_ergaenzen_SUBPROCESS", "caseDataCommandRef", ActionScope.STAFF),
                CaseActionMessage.CLOSE_REPLY, new ActionBranch("Fall_in_Mieterwartephase_abschliessen_SUBPROCESS", "replyClosureCommandRef", ActionScope.CLOSE),
                CaseActionMessage.CLOSE_CLARIFICATION, new ActionBranch(
                        "Fall_in_Abklaerung_abschliessen_SUBPROCESS", "clarificationClosureCommandRef", ActionScope.CLOSE));

        String route = ActionScope.DIRECT.flowsFromRouter();
        assertEquals("=null", xpath.evaluate(actionLoop
                + "/*[name()='bpmn:extensionElements']//*[name()='zeebe:input'][@target='actionType']/@source", model).replaceAll("\\s+", ""));
        assertEquals("Welche_Fallaktion_XOR", xpath.evaluate(actionLoop + "/*[name()='bpmn:sequenceFlow'][@sourceRef='Auf_Fallaktion_warten_START']/@targetRef", model));
        assertEquals("bpmn:exclusiveGateway", xpath.evaluate("name(//*[@id='Welche_Fallaktion_XOR'])", model));
        assertEquals("Welche_Fallaktion_XOR_zu_Mitarbeiteraktion_ausfuehren_SUBPROCESS_FLOW", xpath.evaluate("//*[@id='Welche_Fallaktion_XOR']/@default", model));
        assertEquals("Mitarbeiteraktion_ausfuehren_SUBPROCESS", xpath.evaluate(route + "[@id='Welche_Fallaktion_XOR_zu_Mitarbeiteraktion_ausfuehren_SUBPROCESS_FLOW']/@targetRef", model));
        assertEquals("0", xpath.evaluate("count(" + route + "[@id='Welche_Fallaktion_XOR_zu_Mitarbeiteraktion_ausfuehren_SUBPROCESS_FLOW']/*[name()='bpmn:conditionExpression'])", model));
        assertEquals("Fall_abschliessen_SUBPROCESS", xpath.evaluate(route + "[@id='Welche_Fallaktion_XOR_zu_Fall_abschliessen_SUBPROCESS_FLOW']/@targetRef", model));
        String closeCondition = xpath.evaluate(route + "[@id='Welche_Fallaktion_XOR_zu_Fall_abschliessen_SUBPROCESS_FLOW']/*[name()='bpmn:conditionExpression']/text()", model);
        Set<String> closeCodes = Set.of(normalizedFeel(closeCondition).replace("(", "").replace(")", "").split("or"));
        assertEquals(Set.of("actionType=\"CLOSE_REPLY\"", "actionType=\"CLOSE_CLARIFICATION\""), closeCodes);
        assertEquals("4", xpath.evaluate("count(" + route + ")", model));
        assertEquals("4", xpath.evaluate("count(" + actionLoop + "/*[name()='bpmn:subProcess'])", model));
        assertEquals("4", xpath.evaluate("count(" + actionLoop + "/*[name()='bpmn:sequenceFlow'][@targetRef='Aktion_verarbeitet_zusammenfuehren_XOR'])", model));
        assertActionGroupCompletion(xpath, model, ActionScope.DIRECT);
        assertActionGroup(xpath, model, ActionScope.STAFF, "Mitarbeiteraktion_ausfuehren_SUBPROCESS_zu_Aktion_verarbeitet_zusammenfuehren_XOR_FLOW", 5);
        assertActionGroup(xpath, model, ActionScope.CLOSE, "Fall_abschliessen_SUBPROCESS_zu_Aktion_verarbeitet_zusammenfuehren_XOR_FLOW", 2);

        for (Map.Entry<CaseActionMessage, ActionBranch> action : actions.entrySet()) {
            String branchId = action.getValue().id();
            String reference = action.getValue().reference();
            ActionScope scope = action.getValue().scope();
            String branch = scope.path() + "/*[name()='bpmn:subProcess'][@id='" + branchId + "']";
            String entry = scope.flowsFromRouter() + "[@targetRef='" + branchId + "']";
            String expression = xpath.evaluate(entry + "/*[name()='bpmn:conditionExpression']/text()", model);
            assertEquals(branchId, xpath.evaluate(entry + "/@targetRef", model));
            assertEquals("actionType=\"" + action.getKey().name() + "\"", normalizedFeel(expression), branchId);
            String exit = scope.path() + "/*[name()='bpmn:sequenceFlow'][@sourceRef='" + branchId + "']";
            assertEquals(scope.merge, xpath.evaluate(exit + "/@targetRef", model), branchId);
            assertEquals("1", xpath.evaluate("count(" + scope.plane() + "/*[@bpmnElement='" + branchId + "'])", model), branchId);
            if (scope != ActionScope.DIRECT) {
                assertEquals("0", xpath.evaluate("count(" + ActionScope.DIRECT.plane() + "/*[@bpmnElement='" + branchId + "'])", model), branchId);
            }
            assertEquals("=null", xpath.evaluate(actionLoop + "/*[name()='bpmn:extensionElements']"
                    + "//*[name()='zeebe:input'][@target='" + reference + "']/@source", model).replaceAll("\\s+", ""), branchId);
            assertEquals("=" + reference, xpath.evaluate(branch + "/*[name()='bpmn:extensionElements']"
                    + "//*[name()='zeebe:input'][@target='" + reference + "']/@source", model).replaceAll("\\s+", ""), branchId);
            assertEquals("0", xpath.evaluate("count(" + branch + "/*[name()='bpmn:startEvent']/*[name()='bpmn:messageEventDefinition'])", model), branchId);
            assertEquals("0", xpath.evaluate("count(" + branch + "/*[name()='bpmn:extensionElements']//*[name()='zeebe:output'])", model), branchId);
        }
    }

    private void assertClosureMessageBoundary(XPath xpath, Document automatic, String task, String errorCode) throws Exception {
        String errorId = xpath.evaluate("//*[name()='bpmn:error'][@errorCode='" + errorCode + "']/@id", automatic);
        assertTrue(!errorId.isBlank(), errorCode);
        String boundary = "//*[name()='bpmn:boundaryEvent'][@attachedToRef='" + task + "']"
                + "[./*[name()='bpmn:errorEventDefinition'][@errorRef='" + errorId + "']]";
        assertEquals("1", xpath.evaluate("count(" + boundary + ")", automatic), task + ": " + errorCode);
    }

    private String normalizedFeel(String expression) {
        return expression.strip().replaceFirst("^=", "").replaceAll("\\s+", "");
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

    private void assertSharedEvaluationCall(XPath xpath, Document caller, String id) throws Exception {
        String call = "//*[name()='bpmn:callActivity'][@id='" + id + "']";
        assertEquals("Gespraech auswerten", xpath.evaluate(call + "/@name", caller), id);
        String element = call + "//*[name()='zeebe:calledElement']";
        assertEquals(ConversationEvaluationBpmnConstants.PROCESS_ID, xpath.evaluate(element + "/@processId", caller), id);
        assertEquals("deployment", xpath.evaluate(element + "/@bindingType", caller), id);
        assertEquals("false", xpath.evaluate(element + "/@propagateAllParentVariables", caller), id);
        assertEquals("false", xpath.evaluate(element + "/@propagateAllChildVariables", caller), id);
        String output = call + "//*[name()='zeebe:output']";
        assertEquals("2", xpath.evaluate("count(" + output + ")", caller), id);
        for (String variable : new String[] {"evaluationRef", "evaluationCurrent"}) {
            assertEquals("= " + variable, xpath.evaluate(output + "[@target='" + variable + "']/@source", caller), id);
        }
    }

    private Document read(String path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        Path file = Path.of(path);
        Document document = factory.newDocumentBuilder().parse(file.toFile());
        return document;
    }

    private void assertActionGroup(XPath xpath, Document model, ActionScope group, String exitFlow, int actionCount) throws Exception {
        String shape = ActionScope.DIRECT.plane() + "/*[name()='bpmndi:BPMNShape'][@bpmnElement='" + group.id + "']";
        assertEquals("false", xpath.evaluate(shape + "/@isExpanded", model));
        assertEquals("1", xpath.evaluate("count(" + group.plane() + ")", model));
        assertEquals("Aktionsrundlauf_SUBPROCESS", xpath.evaluate(group.path() + "/../@id", model));
        assertEquals("0", xpath.evaluate("count(" + group.path() + "/*[name()='bpmn:extensionElements']/*[name()='zeebe:ioMapping'])", model));
        assertEquals("bpmn:exclusiveGateway", xpath.evaluate("name(//*[@id='" + group.router + "'])", model));
        String start = group.path() + "/*[name()='bpmn:startEvent']";
        assertEquals("1", xpath.evaluate("count(" + start + ")", model));
        String startId = xpath.evaluate(start + "/@id", model);
        assertEquals(group.router, xpath.evaluate(group.path() + "/*[name()='bpmn:sequenceFlow'][@sourceRef='" + startId + "']/@targetRef", model));
        String groupExit = "//*[@id='" + exitFlow + "']";
        assertEquals(group.id, xpath.evaluate(groupExit + "/@sourceRef", model));
        assertEquals("Aktion_verarbeitet_zusammenfuehren_XOR", xpath.evaluate(groupExit + "/@targetRef", model));
        assertEquals(Integer.toString(actionCount), xpath.evaluate("count(" + group.path() + "/*[name()='bpmn:subProcess'])", model));
        assertEquals(Integer.toString(actionCount + 1), xpath.evaluate("count(" + group.flowsFromRouter() + ")", model));
        String defaultFlow = xpath.evaluate("//*[@id='" + group.router + "']/@default", model);
        String errorEnd = xpath.evaluate(group.flowsFromRouter() + "[@id='" + defaultFlow + "']/@targetRef", model);
        String errorRef = xpath.evaluate(group.path() + "/*[name()='bpmn:endEvent'][@id='" + errorEnd
                + "']/*[name()='bpmn:errorEventDefinition']/@errorRef", model);
        assertEquals(UNKNOWN_CASE_ACTION, xpath.evaluate("//*[@id='" + errorRef + "']/@errorCode", model));
        assertActionGroupCompletion(xpath, model, group);
    }

    private void assertActionGroupCompletion(XPath xpath, Document model, ActionScope scope) throws Exception {
        String flow = scope.path() + "/*[name()='bpmn:sequenceFlow'][@sourceRef='" + scope.merge + "']";
        assertEquals("1", xpath.evaluate("count(" + flow + ")", model));
        String end = scope.path() + "/*[name()='bpmn:endEvent'][@id='" + xpath.evaluate(flow + "/@targetRef", model) + "']";
        assertEquals("1", xpath.evaluate("count(" + end + ")", model));
        assertEquals("0", xpath.evaluate("count(" + end + "/*[contains(name(), 'EventDefinition')])", model));
    }

    private enum ActionScope {
        DIRECT("Aktionsrundlauf_SUBPROCESS", "Welche_Fallaktion_XOR", "Aktion_verarbeitet_zusammenfuehren_XOR"),
        STAFF("Mitarbeiteraktion_ausfuehren_SUBPROCESS", "Welche_Mitarbeiteraktion_XOR", "Mitarbeiteraktion_ausfuehren_zusammenfuehren_XOR"),
        CLOSE("Fall_abschliessen_SUBPROCESS", "In_welcher_Fallphase_XOR", "Fall_abschliessen_zusammenfuehren_XOR");

        private final String id;
        private final String router;
        private final String merge;

        ActionScope(String id, String router, String merge) {
            this.id = id;
            this.router = router;
            this.merge = merge;
        }

        private String path() {
            return "//*[@id='" + id + "']";
        }

        private String flowsFromRouter() {
            return path() + "/*[name()='bpmn:sequenceFlow'][@sourceRef='" + router + "']";
        }

        private String plane() {
            return "//*[name()='bpmndi:BPMNPlane'][@bpmnElement='" + id + "']";
        }
    }

    private record ActionBranch(String id, String reference, ActionScope scope) {
    }

    private void assertValue(Document model, String expression, String expected) throws Exception {
        assertEquals(expected, XPathFactory.newInstance().newXPath().evaluate(expression, model).strip(), expression);
    }

    private void assertFeel(Document model, String expression, String expected) throws Exception {
        assertEquals(normalizedFeel(expected),
                normalizedFeel(XPathFactory.newInstance().newXPath().evaluate(expression, model)), expression);
    }
}
