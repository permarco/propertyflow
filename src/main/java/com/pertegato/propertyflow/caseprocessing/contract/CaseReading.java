package com.pertegato.propertyflow.caseprocessing.contract;

/**
 * Resolves and authorizes the personal link on every call without changing the case.
 */
public interface CaseReading {
    TenantCaseView readByToken(String token);
}
