package com.pertegato.propertyflow.caseprocessing.contract.bpmn;

/**
 * Prozesskonstanten für propertyflow-fallprozess-entwurf.bpmn.
 */
public final class CaseProcessBpmnConstants {

    public static final String PROCESS_ID = "Fall_bearbeiten_PROCESS";

    private CaseProcessBpmnConstants() {
    }

    public static final class CallActivity {

        public static final String AUTOMATIC_CLARIFICATION_CALL = "Automatische_Abklaerung_CALL";
        public static final String STAFF_PROCESSING_CALL = "Mitarbeiterpruefung_und_Bearbeitung_CALL";
        public static final String TENANT_MESSAGE_CALL = "Mieternachricht_verarbeiten_CALL";
        public static final String TENANT_REPLY_CALL = "Auf_ausgewertete_Mieterantwort_warten_CALL";

        private CallActivity() {
        }
    }

    public static final class EndEvent {

        public static final String UNKNOWN_STAFF_ACTION_END = "Unbekannte_Aktion_Mitarbeiteraktion_ausfuehren_END";

        private EndEvent() {
        }
    }

    public static final class ServiceTask {

        public static final String ACCEPT_AUTOMATIC_RESULT = "Automatik_Ergebnis_uebernehmen_SERVICE";
        public static final String APPLY_CASE_DATA = "Falldaten_und_Aenderungsnachweis_speichern_SERVICE";
        public static final String CHECK_CLOSURE_DURING_REPLY = "Abschlussauftrag_pruefen_SERVICE";
        public static final String CHECK_REVIEW_REASON = "Pruefgrund_und_Fallstand_pruefen_SERVICE";
        public static final String CLAIM_STAFF_CONTINUATION = "Mitarbeiterfortsetzung_uebernehmen_SERVICE";
        public static final String CLEAR_ANSWER_REQUIREMENT = "Antwortbedarf_aufheben_SERVICE";
        public static final String CLOSE_DURING_CLARIFICATION = "Abschluss_in_Abklaerung_speichern_SERVICE";
        public static final String CONTINUE_TO_FOLLOW_UP = "Wiedervorlage_Eintritt_vorbereiten_SERVICE";
        public static final String CONTINUE_WITHOUT_ANSWER = "Fortsetzung_ohne_Antwortbedarf_pruefen_SERVICE";
        public static final String NOTIFY_CLOSURE_DURING_REPLY = "Abschluss_im_Mieterwarten_bereitstellen_SERVICE";
        public static final String NOTIFY_CURRENT_PHASE = "Pruefbedarf_an_aktuelle_Fallphase_melden_SERVICE";
        public static final String NOTIFY_DIRECT_FOLLOW_UP = "Wiedervorlage_zur_Uebernahme_bereitstellen_SERVICE";
        public static final String NOTIFY_REPLY_WAIT = "Pruefbedarf_im_Mieterwarten_melden_SERVICE";
        public static final String NOTIFY_STAFF_QUESTION = "Rueckfrage_zur_Abloesung_bereitstellen_SERVICE";
        public static final String NOTIFY_STAFF_TAKEOVER = "Uebernahme_zur_Abloesung_bereitstellen_SERVICE";
        public static final String PREPARE_ANALYSIS_FAILURE = "Analysefehler_und_Pruefkontext_sichern_SERVICE";
        public static final String PREPARE_AUTOMATIC_CLOSURE = "Automatischen_Abschluss_vorbereiten_SERVICE";
        public static final String PREPARE_CONFIRMED_INTERRUPTION = "Bestaetigte_Abloesung_pruefen_SERVICE";
        public static final String PREPARE_INITIAL_CLARIFICATION = "Erste_Abklaerung_vorbereiten_SERVICE";
        public static final String PREPARE_PROCESS_KEY = "Prozessschluessel_bereitstellen_SERVICE";
        public static final String PREPARE_QUESTION_ROUND = "Erneute_Katalogrueckfrage_vorbereiten_SERVICE";
        public static final String PREPARE_STAFF_REVIEW = "Pruefkontext_fuer_Mitarbeiter_bereitstellen_SERVICE";
        public static final String RECONCILE_STAFF_CONTINUATION = "Ueberholte_Fortsetzung_abgleichen_SERVICE";
        public static final String REGISTER_CASE = "Fuehrenden_Prozess_zuordnen_SERVICE";
        public static final String REOPEN_CASE = "Wiedereroeffnung_pruefen_und_zuordnen_SERVICE";
        public static final String SAVE_DIRECT_FOLLOW_UP = "Direkte_Wiedervorlage_speichern_SERVICE";
        public static final String SAVE_STAFF_CLOSURE = "Mitarbeiterabschluss_pruefen_und_speichern_SERVICE";
        public static final String SAVE_STAFF_MESSAGE = "Mitarbeiternachricht_speichern_SERVICE";
        public static final String SAVE_STAFF_TAKEOVER = "Mitarbeiteruebernahme_speichern_SERVICE";
        public static final String UPDATE_OPEN_REVIEW = "Offene_Pruefung_ergaenzen_SERVICE";
        public static final String UPDATE_STAFF_CONTINUATION = "Bestehende_Bearbeitung_ergaenzen_SERVICE";

        private ServiceTask() {
        }
    }

    public static final class StartEvent {

        public static final String ACTION_RECEIVER = "Auf_Fallaktion_warten_START";

        private StartEvent() {
        }
    }

    /**
     * Namen der ausschliesslich in diesem Prozess verwendeten Variablen.
     */
    public static final class Variablen {

        public static final String ACTION_TYPE = "actionType";
        public static final String ANSWER_CANCELLATION_REF = "answerCancellationRef";
        public static final String AUTOMATIC_ENTRY_CONTEXT_REF = "automaticEntryContextRef";
        public static final String CASE_DATA_COMMAND_REF = "caseDataCommandRef";
        public static final String CLARIFICATION_CLOSURE_COMMAND_REF = "clarificationClosureCommandRef";
        public static final String CLARIFICATION_CLOSURE_OUTCOME = "clarificationClosureOutcome";
        public static final String CLEAR_ANSWER_COMMAND_REF = "clearAnswerCommandRef";
        public static final String CLEAR_ANSWER_OUTCOME = "clearAnswerOutcome";
        public static final String CLEARED_ANSWER_CALL_REF = "clearedAnswerCallRef";
        public static final String CLEARED_AUTOMATIC_CALL_REF = "clearedAutomaticCallRef";
        public static final String CLEARED_REPLY_ENTRY_REF = "clearedReplyEntryRef";
        public static final String DIRECT_FOLLOW_COMMAND_REF = "directFollowCommandRef";
        public static final String DIRECT_FOLLOW_CONTINUATION_REF = "directFollowContinuationRef";
        public static final String DIRECT_FOLLOW_OUTCOME = "directFollowOutcome";
        public static final String MANUAL_QUESTION_CALL_REF = "manualQuestionCallRef";
        public static final String MANUAL_QUESTION_PREVIOUS_REPLY_ENTRY_REF = "manualQuestionPreviousReplyEntryRef";
        public static final String PROCESS_INSTANCE_KEY = "processInstanceKey";
        public static final String REPLY_CLOSURE_COMMAND_REF = "replyClosureCommandRef";
        public static final String REPLY_CLOSURE_OUTCOME = "replyClosureOutcome";
        public static final String REPLY_CONTINUATION_OUTCOME = "replyContinuationOutcome";
        public static final String REPLY_CONTINUATION_SOURCE = "replyContinuationSource";
        public static final String REPLY_DECISION_REF = "replyDecisionRef";
        public static final String REPLY_EXIT_REASON = "replyExitReason";
        public static final String REPLY_OUTCOME = "replyOutcome";
        public static final String STAFF_CLOSE_REPLY_CALL_REF = "staffCloseReplyCallRef";
        public static final String STAFF_CLOSE_REPLY_ENTRY_REF = "staffCloseReplyEntryRef";
        public static final String STAFF_FOLLOW_AUTOMATIC_CALL_REF = "staffFollowAutomaticCallRef";
        public static final String STAFF_FOLLOW_COMMAND_REF = "staffFollowCommandRef";
        public static final String STAFF_FOLLOW_REPLY_CALL_REF = "staffFollowReplyCallRef";
        public static final String STAFF_FOLLOW_REPLY_ENTRY_REF = "staffFollowReplyEntryRef";
        public static final String STAFF_OUTCOME = "staffOutcome";
        public static final String STAFF_REPLY_CLOSURE_DECISION_REF = "staffReplyClosureDecisionRef";
        public static final String STAFF_TAKEOVER_CALL_REF = "staffTakeoverCallRef";
        public static final String STAFF_TAKEOVER_COMMAND_REF = "staffTakeoverCommandRef";
        public static final String STAFF_TAKEOVER_OUTCOME = "staffTakeoverOutcome";
        public static final String STAFF_TAKEOVER_REPLY_ENTRY_REF = "staffTakeoverReplyEntryRef";
        public static final String START_KIND = "startKind";
        public static final String START_ORDER_REF = "startOrderRef";

        private Variablen() {
        }
    }

    /**
     * BPMN-Fehlercodes für auslösende und empfangende Ereignisse.
     */
    public static final class ErrorCode {

        public static final String REOPEN_REJECTED = "REOPEN_REJECTED";
        public static final String STAFF_CLOSURE_STALE = "STAFF_CLOSURE_STALE";
        public static final String UNKNOWN_CASE_ACTION = "UNKNOWN_CASE_ACTION";

        private ErrorCode() {
        }
    }

    /**
     * BPMN-Eskalationscodes für auslösende und empfangende Ereignisse.
     */
    public static final class EscalationCode {

        public static final String STAFF_CLARIFICATION_CLOSURE_COMMITTED = "STAFF_CLARIFICATION_CLOSURE_COMMITTED";

        private EscalationCode() {
        }
    }

    /**
     * Technische Jobtypen für Worker-Bindungen.
     */
    public static final class JobType {

        public static final String ANSWER_CLEAR = "propertyflow.answer.clear";
        public static final String ANSWER_CONTINUE = "propertyflow.answer.continue";
        public static final String AUTOMATIC_ACCEPT_RESULT = "propertyflow.automatic.accept-result";
        public static final String AUTOMATIC_PREPARE_CLOSE_ROUND = "propertyflow.automatic.prepare-close-round";
        public static final String AUTOMATIC_PREPARE_QUESTION_ROUND = "propertyflow.automatic.prepare-question-round";
        public static final String CASE_DATA_APPLY = "propertyflow.case-data.apply";
        public static final String CASE_REOPEN = "propertyflow.case.reopen";
        public static final String CLARIFICATION_CLOSE = "propertyflow.clarification.close";
        public static final String CLARIFICATION_QUESTION_SIGNAL = "propertyflow.clarification-question.signal";
        public static final String DIRECT_FOLLOW_UP_APPLY = "propertyflow.direct-follow-up.apply";
        public static final String DIRECT_FOLLOW_UP_CONTINUE = "propertyflow.direct-follow-up.continue";
        public static final String DIRECT_FOLLOW_UP_SIGNAL = "propertyflow.direct-follow-up.signal";
        public static final String PREPARE_INITIAL = "propertyflow-prepare-initial";
        public static final String PREPARE_PROCESS_KEY = "propertyflow-prepare-process-key";
        public static final String REGISTER_CASE = "propertyflow-register-case";
        public static final String REPLY_CLOSURE_CHECK = "propertyflow.reply-closure.check";
        public static final String REPLY_CLOSURE_SIGNAL = "propertyflow.reply-closure.signal";
        public static final String REPLY_PREPARE_REVIEW = "propertyflow.reply.prepare-review";
        public static final String REPLY_WAKE_REVIEW = "propertyflow.reply.wake-review";
        public static final String REVIEW_ANALYSIS_FAILURE = "propertyflow.review.analysis-failure";
        public static final String REVIEW_CHECK = "propertyflow.review.check";
        public static final String REVIEW_INTERRUPT_AUTOMATIC = "propertyflow.review.interrupt-automatic";
        public static final String REVIEW_MERGE = "propertyflow.review.merge";
        public static final String REVIEW_PREPARE_STAFF = "propertyflow.review.prepare-staff";
        public static final String STAFF_CLAIM = "propertyflow.staff.claim";
        public static final String STAFF_CLOSE = "propertyflow.staff.close";
        public static final String STAFF_MERGE_CONTINUATION = "propertyflow.staff.merge-continuation";
        public static final String STAFF_RECONCILE_CONTINUATION = "propertyflow.staff.reconcile-continuation";
        public static final String TAKEOVER_APPLY = "propertyflow.takeover.apply";
        public static final String TAKEOVER_SIGNAL = "propertyflow.takeover.signal";

        private JobType() {
        }
    }
}
