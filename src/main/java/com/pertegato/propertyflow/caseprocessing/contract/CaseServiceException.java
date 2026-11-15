package com.pertegato.propertyflow.caseprocessing.contract;

import java.util.Map;
import java.util.Objects;

/**
 * Transport-independent failure containing codes only, never rejected values or provider messages.
 */
public final class CaseServiceException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final CaseErrorCode code;
    private final Map<String, String> fieldErrors;

    public CaseServiceException(CaseErrorCode code, Map<String, String> fieldErrors) {
        super(Objects.requireNonNull(code, "code").name());
        this.code = code;
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public CaseErrorCode code() {
        return code;
    }

    public Map<String, String> fieldErrors() {
        return fieldErrors;
    }
}
