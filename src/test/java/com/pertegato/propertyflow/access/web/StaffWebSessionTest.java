package com.pertegato.propertyflow.access.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StaffWebSessionTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "https://example.invalid/mitarbeiter/faelle", "//example.invalid/mitarbeiter/faelle",
            "javascript:alert(1)", "/mitarbeiter/faelle#fragment", "/mitarbeiter/faelle/../admin",
            "/mitarbeiter/faelle/%2e%2e", "/mitarbeiter/faelle%2f..", "/mieter/faelle", "\\\\example.invalid",
            "/mitarbeiter/faelle\r\nLocation: https://example.invalid", "/mitarbeiter/faelle?bad=%zz"})
    void unsafeLoginTargetsFallBackToTheStaffList(String target) {
        assertEquals("/mitarbeiter/faelle", StaffWebSession.safeTarget(target));
    }

    @Test
    void loginTargetLengthBoundaryPreservesOnlyAllowedLocalTargets() {
        String target = "/mitarbeiter/faelle/REQ-2026-001?q=";
        String maximum = target + "a".repeat(2048 - target.length());
        assertEquals(maximum, StaffWebSession.safeTarget(maximum));
        assertEquals("/mitarbeiter/faelle", StaffWebSession.safeTarget(maximum + "a"));
    }
}
