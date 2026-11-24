package com.pertegato.propertyflow.caseprocessing.contract.bpmn;

/**
 * Prozesskonstanten für fallbenachrichtigung-versenden.bpmn.
 */
public final class CaseNotificationBpmnConstants {

    public static final String PROCESS_ID = "Fallbenachrichtigung_versenden_PROCESS";

    private CaseNotificationBpmnConstants() {
    }

    public static final class SendTask {

        public static final String SEND_ACCEPTANCE_OR_MESSAGE_MAIL = "Annahme_oder_Nachrichtenmail_versenden_SENDTASK";
        public static final String SEND_STATUS_MAIL = "Statusmail_versenden_SENDTASK";

        private SendTask() {
        }
    }

    public static final class ServiceTask {

        public static final String PREPARE_MAIL = "Mailauftrag_pruefen_SERVICE";
        public static final String RECORD_MAIL_PROBLEM = "Versandproblem_festhalten_SERVICE";

        private ServiceTask() {
        }
    }

    /**
     * Namen der ausschliesslich in diesem Prozess verwendeten Variablen.
     */
    public static final class Variablen {

        public static final String MAIL_ORDER_REF = "mailOrderRef";
        public static final String MAIL_ROUTE = "mailRoute";

        private Variablen() {
        }
    }

    /**
     * BPMN-Fehlercodes für auslösende und empfangende Ereignisse.
     */
    public static final class ErrorCode {

        public static final String MAIL_CLARIFICATION_REQUIRED = "MAIL_CLARIFICATION_REQUIRED";

        private ErrorCode() {
        }
    }

    /**
     * Technische Jobtypen für Worker-Bindungen.
     */
    public static final class JobType {

        public static final String MAIL_PREPARE = "propertyflow.mail.prepare";
        public static final String MAIL_RECORD_PROBLEM = "propertyflow.mail.record-problem";
        public static final String MAIL_SEND = "propertyflow.mail.send";

        private JobType() {
        }
    }
}
