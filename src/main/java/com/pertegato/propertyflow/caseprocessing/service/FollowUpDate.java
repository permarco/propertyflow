package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.caseprocessing.contract.CaseActionException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

public final class FollowUpDate {

    public static final ZoneId ZONE = ZoneId.of("Europe/Zurich");
    private static final LocalTime DUE_TIME = LocalTime.of(8, 0);

    private FollowUpDate() {
    }

    public static Instant calculate(Clock clock, Integer days, LocalDate date) {
        if ((days == null) == (date == null) || days != null && days < 0) {
            throw new CaseActionException("INVALID_FOLLOW_UP_DATE");
        }
        try {
            LocalDate selected = date == null ? LocalDate.now(clock.withZone(ZONE)).plusDays(days) : date;
            Instant due = selected.atTime(DUE_TIME).atZone(ZONE).toInstant();
            if (!due.isAfter(clock.instant())) {
                throw new CaseActionException("INVALID_FOLLOW_UP_DATE");
            }
            return due;
        } catch (DateTimeException exception) {
            throw new CaseActionException("INVALID_FOLLOW_UP_DATE");
        }
    }
}
