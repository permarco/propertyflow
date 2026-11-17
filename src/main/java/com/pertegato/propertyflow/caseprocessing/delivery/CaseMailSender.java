package com.pertegato.propertyflow.caseprocessing.delivery;

import java.util.UUID;

public interface CaseMailSender {

    SendResult send(Mail mail);

    enum SendResult {
        ACCEPTED,
        RETRYABLE,
        REJECTED,
        UNKNOWN
    }

    record Mail(UUID orderId, UUID attemptId, String recipient, String caseId, String information, String personalUrl) {
        @Override
        public String toString() {
            return "Case mail " + orderId + " [content redacted]";
        }
    }
}
