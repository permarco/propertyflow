package com.pertegato.propertyflow.access.service;

import com.pertegato.propertyflow.access.demo.DemoAccounts;
import com.pertegato.propertyflow.access.demo.DemoAccounts.Account;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffLoginTest {

    private static final String STAFF_EMAIL = "staff@example.ch";
    private static final String UNKNOWN_EMAIL = "unknown@example.ch";
    private static final UUID STAFF_ID = UUID.fromString("8f58b2c2-ac8c-49f8-a382-d663a93c847b");
    private static final Account STAFF = new Account(STAFF_ID, STAFF_EMAIL);

    private TestDemoAccounts accounts;
    private TestClock clock;
    private AtomicReference<String> deliveredCode;
    private StaffLogin login;

    @BeforeEach
    void setup() {
        accounts = new TestDemoAccounts();
        clock = new TestClock();
        deliveredCode = new AtomicReference<>();
        login = new StaffLogin(accounts, (email, code) -> {
            assertEquals(STAFF_EMAIL, email);
            deliveredCode.set(code);
        }, clock, Runnable::run);
    }

    @Test
    void codeMatchesOnlyTheIntendedAccountAndCanBeUsedOnce() {
        login.requestCode(" Staff@Example.ch ");

        String code = deliveredCode.get();
        assertTrue(code.matches("[0-9]{6}"));
        assertTrue(login.verifyCode(UNKNOWN_EMAIL, code).isEmpty());
        assertEquals(Optional.of(STAFF_ID), login.verifyCode(STAFF_EMAIL, code));
        assertTrue(login.verifyCode(STAFF_EMAIL, code).isEmpty());
    }

    @Test
    void aCodeIsRejectedAfterTenMinutes() {
        login.requestCode(STAFF_EMAIL);
        clock.advance(Duration.ofMinutes(10));

        assertTrue(login.verifyCode(STAFF_EMAIL, deliveredCode.get()).isEmpty());
    }

    @Test
    void fiveInvalidAttemptsInvalidateTheCode() {
        login.requestCode(STAFF_EMAIL);
        String code = deliveredCode.get();
        String incorrectCode = code.equals("123456") ? "654321" : "123456";

        for (int attempt = 0; attempt < 5; attempt++) {
            assertTrue(login.verifyCode(STAFF_EMAIL, incorrectCode).isEmpty());
        }

        assertTrue(login.verifyCode(STAFF_EMAIL, code).isEmpty());
    }

    @Test
    void unknownAddressesReceiveNoMailAndHaveTheSameResendLimit() {
        login.requestCode(UNKNOWN_EMAIL);
        assertNull(deliveredCode.get());
        assertThrows(StaffLogin.CodeRateLimitException.class, () -> login.requestCode(UNKNOWN_EMAIL));

        login.requestCode(STAFF_EMAIL);
        assertThrows(StaffLogin.CodeRateLimitException.class, () -> login.requestCode(" STAFF@example.ch "));
    }

    @Test
    void noMoreThanFiveCodesCanBeRequestedInARollingHour() {
        for (int request = 0; request < 5; request++) {
            login.requestCode(STAFF_EMAIL);
            clock.advance(Duration.ofMinutes(1));
        }

        assertThrows(StaffLogin.CodeRateLimitException.class, () -> login.requestCode(STAFF_EMAIL));

        clock.advance(Duration.ofMinutes(55));
        login.requestCode(STAFF_EMAIL);
        assertEquals(Optional.of(STAFF_ID), login.verifyCode(STAFF_EMAIL, deliveredCode.get()));
    }

    @Test
    void anAccountDisabledAfterIssuanceCannotAuthenticate() {
        login.requestCode(STAFF_EMAIL);
        accounts.active = false;

        assertTrue(login.verifyCode(STAFF_EMAIL, deliveredCode.get()).isEmpty());
    }

    @Test
    void failedDeliveryRevokesTheCodeAndStillCountsForRateLimiting() {
        login = new StaffLogin(accounts, (email, code) -> {
            deliveredCode.set(code);
            throw new IllegalStateException("Simulated delivery failure");
        }, clock, Runnable::run);

        login.requestCode(STAFF_EMAIL);

        assertTrue(login.verifyCode(STAFF_EMAIL, deliveredCode.get()).isEmpty());
        assertThrows(StaffLogin.CodeRateLimitException.class, () -> login.requestCode(STAFF_EMAIL));
    }

    @Test
    void simultaneousVerificationConsumesTheCodeOnce() throws Exception {
        login.requestCode(STAFF_EMAIL);
        String code = deliveredCode.get();
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Optional<UUID>> first = executor.submit(() -> {
                start.await();
                return login.verifyCode(STAFF_EMAIL, code);
            });
            Future<Optional<UUID>> second = executor.submit(() -> {
                start.await();
                return login.verifyCode(STAFF_EMAIL, code);
            });
            start.countDown();

            int successfulVerifications = (first.get(5, TimeUnit.SECONDS).isPresent() ? 1 : 0)
                    + (second.get(5, TimeUnit.SECONDS).isPresent() ? 1 : 0);
            assertEquals(1, successfulVerifications);
        }
    }

    private static final class TestDemoAccounts extends DemoAccounts {

        private boolean active = true;

        private TestDemoAccounts() {
            super("");
        }

        @Override
        public Optional<Account> findActiveByEmail(String email) {
            return active && STAFF_EMAIL.equals(email) ? Optional.of(STAFF) : Optional.empty();
        }

        @Override
        public Optional<Account> findActiveById(UUID id) {
            return active && STAFF_ID.equals(id) ? Optional.of(STAFF) : Optional.empty();
        }
    }

    private static final class TestClock extends Clock {

        private Instant instant = Instant.parse("2026-11-12T08:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
