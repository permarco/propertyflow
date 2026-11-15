package com.pertegato.propertyflow.caseprocessing.contract;

import com.pertegato.propertyflow.access.contract.EmailAddress;
import com.pertegato.propertyflow.intake.contract.CaseDescription;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared boundary checks; preserves significant input whitespace for the idempotency comparison.
 */
public final class CaseInputValidation {

    private CaseInputValidation() {
    }

    public static CreateCaseCommand validateGuest(CreateCaseCommand command) {
        if (command == null) {
            throw new CaseServiceException(CaseErrorCode.INVALID_INPUT, Map.of("operation", "REQUIRED"));
        }
        Map<String, String> errors = new LinkedHashMap<>();
        String description = CaseDescription.normalizeLineEndings(command.description());
        check(errors, "operation", command.operationId(), 80);
        check(errors, "title", command.title(), 150);
        check(errors, "unit", command.unit(), 200);
        check(errors, "description", description, CaseDescription.MAX_LENGTH);
        String email = null;
        try {
            email = EmailAddress.normalize(command.guestEmail());
        } catch (IllegalArgumentException exception) {
            errors.put("email", command.guestEmail() == null || command.guestEmail().isBlank() ? "REQUIRED" : "INVALID");
        }
        if (!errors.isEmpty()) {
            throw new CaseServiceException(CaseErrorCode.INVALID_INPUT, errors);
        }
        return new CreateCaseCommand(command.operationId(), command.title(), command.unit(), description, email);
    }

    private static void check(Map<String, String> errors, String field, String value, int limit) {
        if (value == null || value.isBlank()) {
            errors.put(field, "REQUIRED");
        } else if (value.length() > limit) {
            errors.put(field, "TOO_LONG");
        } else if (value.indexOf('\0') >= 0) {
            errors.put(field, "INVALID");
        }
    }
}
