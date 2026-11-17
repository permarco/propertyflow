package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.caseprocessing.contract.CaseActionException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FollowUpDateTest {

    @Test
    void calendarDaysFollowSwissDaylightSavingAndDoNotSkipWeekends() {
        Clock spring = clock("2026-03-28T12:00:00Z");
        assertEquals(Instant.parse("2026-03-29T06:00:00Z"), FollowUpDate.calculate(spring, 1, null));
        Clock autumn = clock("2026-10-24T12:00:00Z");
        assertEquals(Instant.parse("2026-10-25T07:00:00Z"), FollowUpDate.calculate(autumn, 1, null));
    }

    @Test
    void todayIsAllowedOnlyBeforeEightAndExactlyOneInputIsRequired() {
        LocalDate date = LocalDate.of(2026, 11, 15);
        assertEquals(Instant.parse("2026-11-15T07:00:00Z"), FollowUpDate.calculate(clock("2026-11-15T06:59:59Z"), null, date));
        assertThrows(CaseActionException.class, () -> FollowUpDate.calculate(clock("2026-11-15T07:00:00Z"), null, date));
        assertThrows(CaseActionException.class, () -> FollowUpDate.calculate(clock("2026-11-15T06:00:00Z"), 1, date));
        assertThrows(CaseActionException.class, () -> FollowUpDate.calculate(clock("2026-11-15T06:00:00Z"), null, null));
        assertThrows(CaseActionException.class, () -> FollowUpDate.calculate(clock("2026-11-15T06:00:00Z"), -1, null));
    }

    private Clock clock(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    }
}
