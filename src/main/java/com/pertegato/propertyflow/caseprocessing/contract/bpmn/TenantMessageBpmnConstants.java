package com.pertegato.propertyflow.caseprocessing.contract.bpmn;

/**
 * Prozesskonstanten für mieternachricht-verarbeiten.bpmn.
 */
public final class TenantMessageBpmnConstants {

    public static final String PROCESS_ID = "Mieternachricht_verarbeiten_PROCESS";

    private TenantMessageBpmnConstants() {
    }

    public static final class CallActivity {

        public static final String EVALUATE_MESSAGE = "Gespraech_auswerten_Mieternachricht_CALL";
        public static final String REFRESH_EVALUATION = "Gespraech_auswerten_Aktualisierung_CALL";

        private CallActivity() {
        }
    }

    public static final class ServiceTask {

        public static final String APPLY_EVALUATION = "Aktuelle_Auswertung_bereitstellen_SERVICE";
        public static final String CHECK_EVALUATION = "Nachrichtenauswertung_pruefen_SERVICE";
        public static final String CHECK_MESSAGE = "Nachricht_und_Fallstand_pruefen_SERVICE";
        public static final String MARK_NEW_MESSAGE = "Neuen_Eingang_vormerken_SERVICE";
        public static final String NOTIFY_FOLLOW_UP = "Kontakt_zur_Wiedervorlage_bereitstellen_SERVICE";
        public static final String RECORD_EVALUATION_FAILURE = "Analysefehler_festhalten_SERVICE";
        public static final String UPDATE_OPEN_REVIEW = "Laufende_Mitarbeiterpruefung_ergaenzen_SERVICE";

        private ServiceTask() {
        }
    }

    /**
     * Namen der ausschliesslich in diesem Prozess verwendeten Variablen.
     */
    public static final class Variablen {

        public static final String CASE_PHASE = "casePhase";
        public static final String TENANT_MESSAGE_ACCEPTED = "tenantMessageAccepted";

        private Variablen() {
        }
    }

    /**
     * BPMN-Fehlercodes für auslösende und empfangende Ereignisse.
     */
    public static final class ErrorCode {

        public static final String TENANT_EVALUATION_STALE = "TENANT_EVALUATION_STALE";

        private ErrorCode() {
        }
    }

    /**
     * Technische Jobtypen für Worker-Bindungen.
     */
    public static final class JobType {

        public static final String CONVERSATION_APPLY = "propertyflow.conversation.apply";
        public static final String CONVERSATION_CONTEXT = "propertyflow.conversation.context";
        public static final String CONVERSATION_RECOMMENDATION = "propertyflow.conversation.recommendation";
        public static final String TENANT_MESSAGE_IDENTIFY = "propertyflow.tenant-message.identify";

        private JobType() {
        }
    }
}
