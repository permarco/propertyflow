package com.pertegato.propertyflow.caseprocessing.contract.bpmn;

/**
 * Prozesskonstanten für mitarbeiterbearbeitung.bpmn.
 */
public final class StaffProcessingBpmnConstants {

    public static final String PROCESS_ID = "Mitarbeiterbearbeitung_PROCESS";

    private StaffProcessingBpmnConstants() {
    }

    public static final class CallActivity {

        public static final String FOLLOW_UP_CALL = "Wiedervorlage_verwalten_CALL";

        private CallActivity() {
        }
    }

    public static final class ServiceTask {

        public static final String CHECK_ENTRY = "Mitarbeitereintritt_pruefen_SERVICE";
        public static final String CONTINUE_WORK = "Bearbeitung_fortsetzen_SERVICE";
        public static final String PUBLISH_QUESTION = "Mitarbeiterrueckfrage_veroeffentlichen_SERVICE";
        public static final String PUBLISH_STAFF_MESSAGE = "Nachricht_und_Versandauftrag_speichern_SERVICE";
        public static final String START_EXTERNAL_WAIT = "Externe_Wartephase_speichern_SERVICE";
        public static final String START_REVIEW = "Mitarbeiterpruefung_festhalten_SERVICE";
        public static final String STOP_FOLLOW_UP = "Wiedervorlage_aufheben_und_Pruefung_starten_SERVICE";

        private ServiceTask() {
        }
    }

    public static final class UserTask {

        public static final String EXTERNAL_WAIT_TASK = "Externe_Rueckmeldung_abwarten_USERTASK";
        public static final String REVIEW_TASK = "Fall_pruefen_und_Vorgehen_entscheiden_USERTASK";
        public static final String WORK_TASK = "Naechste_Mitarbeiteraktion_abwarten_USERTASK";

        private UserTask() {
        }
    }

    /**
     * Namen der ausschliesslich in diesem Prozess verwendeten Variablen.
     */
    public static final class Variablen {

        public static final String ACTIVE_EXTERNAL_WAIT_REF = "activeExternalWaitRef";
        public static final String EXTERNAL_WAIT_REVIEW_REQUIRED = "externalWaitReviewRequired";
        public static final String FOLLOW_UP_DECISION_REF = "followUpDecisionRef";
        public static final String FOLLOW_UP_ENTRY_CONTEXT_REF = "followUpEntryContextRef";
        public static final String FOLLOW_UP_OUTCOME = "followUpOutcome";
        public static final String REVIEW_WAIT_REF = "reviewWaitRef";
        public static final String STAFF_DECISION = "staffDecision";
        public static final String STAFF_ENTRY_KIND = "staffEntryKind";

        private Variablen() {
        }
    }

    /**
     * BPMN-Fehlercodes für auslösende und empfangende Ereignisse.
     */
    public static final class ErrorCode {

        public static final String STAFF_DECISION_STALE = "STAFF_DECISION_STALE";

        private ErrorCode() {
        }
    }

    /**
     * Technische Jobtypen für Worker-Bindungen.
     */
    public static final class JobType {

        public static final String FOLLOW_UP_STOP_CONTACT = "propertyflow.follow-up.stop-contact";
        public static final String STAFF_PROGRESS = "propertyflow.staff.progress";
        public static final String STAFF_QUESTION_PUBLISH = "propertyflow.staff-question.publish";
        public static final String STAFF_REGISTER = "propertyflow.staff.register";
        public static final String STAFF_START_REVIEW = "propertyflow.staff.start-review";
        public static final String STAFF_WAIT_EXTERNAL = "propertyflow.staff.wait-external";

        private JobType() {
        }
    }
}
