package com.pertegato.propertyflow.caseprocessing.contract;

/**
 * Untrusted input. Validation and normalization belong to the service, including nullable form fields.
 */
public record CreateCaseCommand(String operationId, String title, String unit, String description, String guestEmail) {
    @Override
    public String toString() {
        return "CreateCaseCommand[redacted]";
    }
}
