package com.pertegato.propertyflow.caseprocessing.contract.bpmn;

/**
 * Gemeinsame Konstanten der BPMN-Prozesse; Variablennamen verbinden Verträge, nicht Instanzwerte.
 */
public final class CommonBpmnConstants {

    private CommonBpmnConstants() {
    }

    /**
     * Prozessübergreifend verwendete Namen für Eingaben, Ergebnisse und Steuerungsreferenzen.
     */
    public static final class Variablen {

        public static final String ACTIVE_FOLLOW_UP_VERSION = "activeFollowUpVersion";
        public static final String ACTIVE_REPLY_CALL_REF = "activeReplyCallRef";
        public static final String ACTIVE_REPLY_ENTRY_REF = "activeReplyEntryRef";
        public static final String ACTIVE_WAIT_REF = "activeWaitRef";
        public static final String ACTIVE_WORK_TASK_REF = "activeWorkTaskRef";
        public static final String AUTOMATIC_CALL_REF = "automaticCallRef";
        public static final String AUTOMATIC_DECISION_REF = "automaticDecisionRef";
        public static final String AUTOMATIC_INTERRUPTION_CALL_REF = "automaticInterruptionCallRef";
        public static final String AUTOMATIC_INTERRUPTION_OUTCOME = "automaticInterruptionOutcome";
        public static final String AUTOMATIC_INTERRUPTION_READY = "automaticInterruptionReady";
        public static final String AUTOMATIC_OUTCOME = "automaticOutcome";
        public static final String CASE_ID = "caseId";
        public static final String CONTACT_MESSAGE_REF = "contactMessageRef";
        public static final String DECISION_REF = "decisionRef";
        public static final String ENTRY_CONTEXT_REF = "entryContextRef";
        public static final String ENTRY_KIND = "entryKind";
        public static final String EVALUATION_CURRENT = "evaluationCurrent";
        public static final String EVALUATION_REF = "evaluationRef";
        public static final String FOLLOW_UP_SCOPE_REF = "followUpScopeRef";
        public static final String LEADING_PROCESS_INSTANCE_KEY = "leadingProcessInstanceKey";
        public static final String OUTCOME = "outcome";
        public static final String PENDING_REVIEW_FOLLOW_UP_VERSION = "pendingReviewFollowUpVersion";
        public static final String PENDING_TENANT_CONTACT_FOLLOW_UP_VERSION = "pendingTenantContactFollowUpVersion";
        public static final String PENDING_WORK_REVIEW_REF = "pendingWorkReviewRef";
        public static final String PHASE_REVIEW_SIGNAL_REF = "phaseReviewSignalRef";
        public static final String REPLY_ENTRY_CONTEXT_REF = "replyEntryContextRef";
        public static final String REVIEW_CONTINUATION_REF = "reviewContinuationRef";
        public static final String REVIEW_NEED_REPLY_CALL_REF = "reviewNeedReplyCallRef";
        public static final String REVIEW_ROUTE = "reviewRoute";
        public static final String REVIEW_SIGNAL_REF = "reviewSignalRef";
        public static final String STAFF_CONTINUATION_OUTCOME = "staffContinuationOutcome";
        public static final String STAFF_DECISION_REF = "staffDecisionRef";
        public static final String STAFF_ENTRY_CONTEXT_REF = "staffEntryContextRef";
        public static final String STAFF_MESSAGE_COMMAND_REF = "staffMessageCommandRef";
        public static final String STAFF_MESSAGE_OUTCOME = "staffMessageOutcome";
        public static final String TENANT_MESSAGE_ID = "tenantMessageId";

        private Variablen() {
        }
    }

    /**
     * BPMN-Fehlercodes für auslösende und empfangende Ereignisse.
     */
    public static final class ErrorCode {

        public static final String ANALYSIS_BASIS_STALE = "ANALYSIS_BASIS_STALE";
        public static final String AUTO_CLOSURE_STALE = "AUTO_CLOSURE_STALE";
        public static final String CLOSURE_MESSAGE_INVALID = "CLOSURE_MESSAGE_INVALID";
        public static final String PUBLICATION_BASIS_STALE = "PUBLICATION_BASIS_STALE";
        public static final String TRIAGE_ANALYSIS_FAILED = "TRIAGE_ANALYSIS_FAILED";

        private ErrorCode() {
        }
    }

    /**
     * Technische Jobtypen für Worker-Bindungen.
     */
    public static final class JobType {

        public static final String STAFF_MESSAGE_PUBLISH = "propertyflow.staff-message.publish";

        private JobType() {
        }
    }
}
