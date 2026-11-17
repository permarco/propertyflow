package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.caseprocessing.contract.TenantCaseView;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseEntity;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CaseProjectionsTest {

    @ParameterizedTest
    @CsvSource({"mieter@example.ch,m***ple.ch", "a@b.ch,a***.ch", "a@b,a***", "tenant@example.test,t***e.test"})
    void tenantProjectionHidesTheMiddleAndNeverExposesTheCompleteDomain(String email, String expected) {
        CaseEntity entity = new CaseEntity("case", "Anliegen", "Wohnung", "Beschreibung", email, Instant.EPOCH);
        TenantCaseView view = CaseProjections.tenantView(entity, List.of());
        assertEquals(expected, view.maskedContact());
        assertFalse(view.maskedContact().contains(email.substring(email.indexOf('@'))));
        assertEquals(email, entity.getContactEmail());
    }
}
