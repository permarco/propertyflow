package com.pertegato.propertyflow.caseprocessing.contract.bpmn;

/**
 * Prozesskonstanten für mieterantwort-pruefen.bpmn.
 */
public final class TenantReplyBpmnConstants {

    public static final String PROCESS_ID = "Mieterantwort_pruefen_PROCESS";

    private TenantReplyBpmnConstants() {
    }

    public static final class CallActivity {

        public static final String EVALUATE_CONVERSATION = "Gespraech_auswerten_CALL";

        private CallActivity() {
        }
    }

    public static final class ServiceTask {

        public static final String APPLY_EVALUATION = "Antwortauswertung_uebernehmen_SERVICE";
        public static final String PUBLISH_STAFF_MESSAGE = "Nachricht_im_Mieterwarten_speichern_SERVICE";
        public static final String REGISTER_REPLY_WAIT = "Mieterwarten_registrieren_SERVICE";
        public static final String VALIDATE_CONTINUATION = "Fortsetzung_nach_Mieterantwort_pruefen_SERVICE";

        private ServiceTask() {
        }
    }

    /**
     * Namen der ausschliesslich in diesem Prozess verwendeten Variablen.
     */
    public static final class Variablen {

        public static final String CONSUMED_TENANT_EVALUATION_VERSION = "consumedTenantEvaluationVersion";
        public static final String TENANT_EVALUATION_VERSION = "tenantEvaluationVersion";

        private Variablen() {
        }
    }

    /**
     * Technische Jobtypen für Worker-Bindungen.
     */
    public static final class JobType {

        public static final String REPLY_APPLY_EVALUATION = "propertyflow.reply.apply-evaluation";
        public static final String REPLY_REGISTER = "propertyflow.reply.register";
        public static final String REPLY_VALIDATE = "propertyflow.reply.validate";

        private JobType() {
        }
    }
}
