package com.pertegato.propertyflow.caseprocessing.contract.bpmn;

/**
 * Prozesskonstanten für automatische-abklaerung.bpmn.
 */
public final class AutomaticClarificationBpmnConstants {

    public static final String PROCESS_ID = "Automatische_Abklaerung_PROCESS";

    private AutomaticClarificationBpmnConstants() {
    }

    public static final class CallActivity {

        public static final String EVALUATE_CONVERSATION = "Gespraech_auswerten_CALL";

        private CallActivity() {
        }
    }

    public static final class ServiceTask {

        public static final String CHECK_ENTRY = "Eintritt_und_aktuellen_Fallstand_pruefen_SERVICE";
        public static final String CHECK_EVALUATION = "Auswertung_und_Pruefbedarf_pruefen_SERVICE";
        public static final String CONFIRM_CLOSURE = "Gespeicherten_Abschluss_bestaetigen_SERVICE";
        public static final String PREPARE_REPLY_WAIT = "Mieterwarte_Eintritt_bereitstellen_SERVICE";
        public static final String PREPARE_REVIEW = "Pruefkontext_bereitstellen_SERVICE";
        public static final String PUBLISH_CATALOG_QUESTION = "Katalogrueckfrage_veroeffentlichen_SERVICE";
        public static final String CHECK_CLOSURE_MESSAGE = "Abschlussnachricht_pruefen_SERVICE";
        public static final String SAVE_AUTOMATIC_CLOSURE = "Automatischen_Abschluss_speichern_SERVICE";
        public static final String START_CLARIFICATION = "Status_speichern_In_Abklaerung_SERVICE";
        public static final String VALIDATE_AUTOMATIC_ACTION = "Zulaessige_Automatikaktion_pruefen_SERVICE";
        public static final String WRITE_CLOSURE_MESSAGE = "Abschlussnachricht_schreiben_SERVICE";

        private ServiceTask() {
        }
    }

    /**
     * Namen der ausschliesslich in diesem Prozess verwendeten Variablen.
     */
    public static final class Variablen {

        public static final String AUTOMATIC_ACTION = "automaticAction";
        public static final String AUTOMATIC_CLOSURE_REF = "automaticClosureRef";
        public static final String CLOSURE_DRAFT_RECOVERIES = "closureDraftRecoveries";
        public static final String EVALUATION_REFRESH = "evaluationRefresh";
        public static final String QUESTION_BATCH_REF = "questionBatchRef";

        private Variablen() {
        }
    }

    /**
     * BPMN-Fehlercodes für auslösende und empfangende Ereignisse.
     */
    public static final class ErrorCode {

        public static final String CLOSURE_DRAFT_MISSING = "CLOSURE_DRAFT_MISSING";

        private ErrorCode() {
        }
    }

    /**
     * Technische Jobtypen für Worker-Bindungen.
     */
    public static final class JobType {

        public static final String AUTOMATIC_CHECK_CLOSURE_MESSAGE = "propertyflow.automatic.check-closure-message";
        public static final String AUTOMATIC_CLOSE = "propertyflow.automatic.close";
        public static final String AUTOMATIC_CONFIRM_CLOSURE = "propertyflow.automatic.confirm-closure";
        public static final String AUTOMATIC_PREPARE_RESULT = "propertyflow.automatic.prepare-result";
        public static final String AUTOMATIC_PUBLISH_QUESTIONS = "propertyflow.automatic.publish-questions";
        public static final String AUTOMATIC_RECOMMENDATION = "propertyflow.automatic.recommendation";
        public static final String AUTOMATIC_VALIDATE_QUESTIONS = "propertyflow.automatic.validate-questions";
        public static final String AUTOMATIC_WRITE_CLOSURE_MESSAGE = "propertyflow.automatic.write-closure-message";
        public static final String REGISTER_AUTOMATIC = "propertyflow-register-automatic";
        public static final String START_CLARIFICATION = "propertyflow-start-clarification";

        private JobType() {
        }
    }
}
