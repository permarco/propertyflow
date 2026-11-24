package com.pertegato.propertyflow.caseprocessing.contract.bpmn;

/**
 * Prozesskonstanten für wiedervorlage.bpmn.
 */
public final class FollowUpBpmnConstants {

    public static final String PROCESS_ID = "Wiedervorlage_verwalten_PROCESS";

    private FollowUpBpmnConstants() {
    }

    public static final class ServiceTask {

        public static final String CLOSE_FOLLOW_UP = "Wiedervorlage_beenden_SERVICE";
        public static final String DELETE_FOLLOW_UP = "Timer_und_Termin_entfernen_SERVICE";
        public static final String PUBLISH_STAFF_MESSAGE = "Nachricht_waehrend_Wiedervorlage_speichern_SERVICE";
        public static final String RESOLVE_DUE_TIMER = "Faelligen_Termin_erledigen_SERVICE";
        public static final String SAVE_FOLLOW_UP = "Wiedervorlagetermin_speichern_SERVICE";
        public static final String UPDATE_FOLLOW_UP = "Neuen_Wiedervorlagetermin_speichern_SERVICE";

        private ServiceTask() {
        }
    }

    /**
     * Namen der ausschliesslich in diesem Prozess verwendeten Variablen.
     */
    public static final class Variablen {

        public static final String FOLLOW_UP_COMMAND_REF = "followUpCommandRef";
        public static final String FOLLOW_UP_DUE_AT = "followUpDueAt";
        public static final String WAIT_ACTIVE = "waitActive";

        private Variablen() {
        }
    }

    /**
     * BPMN-Fehlercodes für auslösende und empfangende Ereignisse.
     */
    public static final class ErrorCode {

        public static final String FOLLOW_UP_CHANGE_STALE = "FOLLOW_UP_CHANGE_STALE";

        private ErrorCode() {
        }
    }

    /**
     * Technische Jobtypen für Worker-Bindungen.
     */
    public static final class JobType {

        public static final String FOLLOW_UP_CHANGE = "propertyflow.follow-up.change";
        public static final String FOLLOW_UP_CLOSE = "propertyflow.follow-up.close";
        public static final String FOLLOW_UP_SAVE = "propertyflow.follow-up.save";
        public static final String FOLLOW_UP_TIMER = "propertyflow.follow-up.timer";
        private JobType() {
        }
    }
}
