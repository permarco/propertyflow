package com.pertegato.propertyflow.access.service;

import com.pertegato.propertyflow.access.demo.DemoAccounts;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantRegistrationTest {

    private static final String EMAIL = "new-tenant@example.test";
    private final DemoAccounts accounts = new DemoAccounts("staff@example.test");
    private final AtomicReference<String> delivered = new AtomicReference<>();
    private final MutableClock clock = new MutableClock();
    private final StaffLogin login = new StaffLogin(accounts, (email, code) -> delivered.set(code), clock, Runnable::run);

    @Test
    void registrationActivatesOnlyAfterVerificationAndDoesNotDuplicateAccounts() {
        login.requestRegistrationCode(" NEW-TENANT@example.test ");
        assertTrue(accounts.findActiveByEmail(EMAIL).isEmpty());
        assertTrue(login.verifyCode(EMAIL, "invalid").isEmpty());
        assertTrue(accounts.findActiveByEmail(EMAIL).isEmpty());

        UUID id = login.verifyCode(EMAIL, delivered.get()).orElseThrow();
        assertTrue(accounts.findActiveById(id).orElseThrow().tenant());
        assertTrue(login.verifyCode(EMAIL, delivered.get()).isEmpty());
        clock.now = clock.now.plusSeconds(60);
        login.requestRegistrationCode(EMAIL);
        assertEquals(Optional.of(id), login.verifyCode(EMAIL, delivered.get()));
        clock.now = clock.now.plusSeconds(60);
        login.requestCode(EMAIL);
        assertEquals(Optional.of(id), login.verifyCode(EMAIL, delivered.get()));
    }

    @Test
    void expiryFailedAttemptsAndDeliveryFailureDoNotCreateAccounts() {
        login.requestRegistrationCode(EMAIL);
        clock.now = clock.now.plusSeconds(600);
        assertTrue(login.verifyCode(EMAIL, delivered.get()).isEmpty());
        assertTrue(accounts.findActiveByEmail(EMAIL).isEmpty());

        login.requestRegistrationCode(EMAIL);
        for (int attempt = 0; attempt < 5; attempt++) {
            assertTrue(login.verifyCode(EMAIL, "invalid").isEmpty());
        }
        assertTrue(login.verifyCode(EMAIL, delivered.get()).isEmpty());
        assertTrue(accounts.findActiveByEmail(EMAIL).isEmpty());

        StaffLogin failedDelivery = new StaffLogin(accounts, (email, code) -> {
            delivered.set(code);
            throw new IllegalStateException("delivery failed");
        }, clock, Runnable::run);
        failedDelivery.requestRegistrationCode(EMAIL);
        assertTrue(failedDelivery.verifyCode(EMAIL, delivered.get()).isEmpty());
        assertTrue(accounts.findActiveByEmail(EMAIL).isEmpty());
    }

    @Test
    void registrationAndLoginShareRateLimitsAndPreserveExistingStaffRole() {
        UUID staffId = accounts.findActiveByEmail("staff@example.test").orElseThrow().id();
        login.requestRegistrationCode("staff@example.test");
        assertEquals(Optional.of(staffId), login.verifyCode("staff@example.test", delivered.get()));
        assertFalse(accounts.findActiveById(staffId).orElseThrow().tenant());
        login.requestRegistrationCode(EMAIL);
        assertThrows(StaffLogin.CodeRateLimitException.class, () -> login.requestCode(EMAIL));
        assertThrows(StaffLogin.CodeRateLimitException.class, () -> login.requestRegistrationCode(EMAIL));
        assertThrows(IllegalArgumentException.class, () -> login.requestRegistrationCode("invalid"));
    }

    @Test
    void concurrentVerificationCreatesExactlyOneAccountAndSessionResult() throws Exception {
        login.requestRegistrationCode(EMAIL);
        String code = delivered.get();
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Optional<UUID>> first = executor.submit(() -> login.verifyCode(EMAIL, code));
            Future<Optional<UUID>> second = executor.submit(() -> login.verifyCode(EMAIL, code));
            assertEquals(1, (first.get().isPresent() ? 1 : 0) + (second.get().isPresent() ? 1 : 0));
        }
        assertTrue(accounts.findActiveByEmail(EMAIL).orElseThrow().tenant());
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-13T08:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
