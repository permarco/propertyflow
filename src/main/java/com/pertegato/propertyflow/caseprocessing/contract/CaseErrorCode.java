package com.pertegato.propertyflow.caseprocessing.contract;

public enum CaseErrorCode {
    INVALID_INPUT,
    UNAUTHENTICATED,
    FORBIDDEN,
    CASE_NOT_AVAILABLE,
    IDEMPOTENCY_CONFLICT,
    REPLAY_ACCESS_UNAVAILABLE,
    STORAGE_UNAVAILABLE,
    INTERNAL_FAILURE
}
