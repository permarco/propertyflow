package com.pertegato.propertyflow.caseprocessing.contract.bpmn;

/**
 * Prozesskonstanten für gespraech-auswerten.bpmn.
 */
public final class ConversationEvaluationBpmnConstants {

    public static final String PROCESS_ID = "Gespraech_auswerten_PROCESS";

    private ConversationEvaluationBpmnConstants() {
    }

    public static final class ServiceTask {

        public static final String ANALYZE_CONVERSATION = "Gespraech_analysieren_SERVICE";
        public static final String ASSESS_CLOSURE = "Fallabschluss_beurteilen_SERVICE";
        public static final String RECOMMEND_PROCEDURE = "Vorgehen_empfehlen_SERVICE";

        private ServiceTask() {
        }
    }

    /**
     * Namen der ausschliesslich in diesem Prozess verwendeten Variablen.
     */
    public static final class Variablen {

        public static final String EVALUATION_ATTEMPT = "evaluationAttempt";
        public static final String EVALUATION_RESTART = "evaluationRestart";
        public static final String NEEDS_EVALUATION_ADVICE = "needsEvaluationAdvice";
        public static final String NEEDS_EVALUATION_CLOSURE = "needsEvaluationClosure";

        private Variablen() {
        }
    }

    /**
     * Technische Jobtypen für Worker-Bindungen.
     */
    public static final class JobType {

        public static final String AUTOMATIC_ADVISE = "propertyflow.automatic.advise";
        public static final String AUTOMATIC_ANALYZE = "propertyflow.automatic.analyze";
        public static final String AUTOMATIC_ASSESS_CLOSURE = "propertyflow.automatic.assess-closure";

        private JobType() {
        }
    }
}
