package com.pertegato.propertyflow.access.service;

import com.pertegato.propertyflow.access.contract.StaffAuthenticationException;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.access.persistence.StaffAccountStore.Account;
import com.pertegato.propertyflow.access.persistence.StaffAccountStore;
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
import static org.mockito.Mockito.mock;

class StaffOtpSecurityTest {

    private static final String STAFF_EMAIL = "staff@example.ch";
    private static final String UNKNOWN_EMAIL = "unknown@example.ch";
    private static final UUID STAFF_ID = UUID.fromString("8f58b2c2-ac8c-49f8-a382-d663a93c847b");
    private static final Account STAFF = new Account(STAFF_ID, STAFF_EMAIL, true, true);

    private final UUID flow = UUID.randomUUID();
    private TestStaffAccounts accounts;
    private TestClock clock;
    private AtomicReference<String> deliveredCode;
    private StaffOtpService login;

    @BeforeEach
    void setup() {
        accounts = new TestStaffAccounts();
        clock = new TestClock();
        deliveredCode = new AtomicReference<>();
        login = new StaffOtpService(accounts, (email, code) -> {
            assertEquals(STAFF_EMAIL, email);
            deliveredCode.set(code);
        }, clock, Runnable::run, Duration.ofMinutes(10), Duration.ofMinutes(1), 5, 5);
    }

    @Test
    void codeMatchesOnlyTheIntendedAccountAndCanBeUsedOnce() {
        login.requestCode(flow, " Staff@Example.ch ");

        String code = deliveredCode.get();
        assertTrue(code.matches("[0-9]{6}"));
        assertTrue(login.verifyCode(UUID.randomUUID(), code).isEmpty());
        assertEquals(Optional.of(new WebAccountContext(STAFF_ID)), login.verifyCode(flow, code));
        assertTrue(login.verifyCode(flow, code).isEmpty());
    }

    @Test
    void aCodeIsRejectedAfterTenMinutes() {
        login.requestCode(flow, STAFF_EMAIL);
        clock.advance(Duration.ofMinutes(10));

        assertTrue(login.verifyCode(flow, deliveredCode.get()).isEmpty());
    }

    @Test
    void fiveInvalidAttemptsInvalidateTheCode() {
        login.requestCode(flow, STAFF_EMAIL);
        String code = deliveredCode.get();
        String incorrectCode = code.equals("123456") ? "654321" : "123456";

        for (int attempt = 0; attempt < 5; attempt++) {
            assertTrue(login.verifyCode(flow, incorrectCode).isEmpty());
        }

        assertTrue(login.verifyCode(flow, code).isEmpty());
    }

    @Test
    void unknownAddressesReceiveNoMailAndHaveTheSameResendLimit() {
        login.requestCode(flow, UNKNOWN_EMAIL);
        assertNull(deliveredCode.get());
        assertThrows(StaffAuthenticationException.class, () -> login.requestCode(flow, UNKNOWN_EMAIL));

        login.requestCode(flow, STAFF_EMAIL);
        assertThrows(StaffAuthenticationException.class, () -> login.requestCode(flow, " STAFF@example.ch "));
    }

    @Test
    void noMoreThanFiveCodesCanBeRequestedInARollingHour() {
        for (int request = 0; request < 5; request++) {
            login.requestCode(flow, STAFF_EMAIL);
            clock.advance(Duration.ofMinutes(1));
        }

        assertThrows(StaffAuthenticationException.class, () -> login.requestCode(flow, STAFF_EMAIL));

        clock.advance(Duration.ofMinutes(55));
        login.requestCode(flow, STAFF_EMAIL);
        assertEquals(Optional.of(new WebAccountContext(STAFF_ID)), login.verifyCode(flow, deliveredCode.get()));
    }

    @Test
    void anAccountDisabledAfterIssuanceCannotAuthenticate() {
        login.requestCode(flow, STAFF_EMAIL);
        accounts.active = false;

        assertTrue(login.verifyCode(flow, deliveredCode.get()).isEmpty());
    }

    @Test
    void failedDeliveryRevokesTheCodeAndStillCountsForRateLimiting() {
        login = new StaffOtpService(accounts, (email, code) -> {
            deliveredCode.set(code);
            throw new IllegalStateException("Simulated delivery failure");
        }, clock, Runnable::run, Duration.ofMinutes(10), Duration.ofMinutes(1), 5, 5);

        login.requestCode(flow, STAFF_EMAIL);

        assertTrue(login.verifyCode(flow, deliveredCode.get()).isEmpty());
        assertThrows(StaffAuthenticationException.class, () -> login.requestCode(flow, STAFF_EMAIL));
    }

    @Test
    void simultaneousVerificationConsumesTheCodeOnce() throws Exception {
        login.requestCode(flow, STAFF_EMAIL);
        String code = deliveredCode.get();
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Optional<WebAccountContext>> first = executor.submit(() -> {
                start.await();
                return login.verifyCode(flow, code);
            });
            Future<Optional<WebAccountContext>> second = executor.submit(() -> {
                start.await();
                return login.verifyCode(flow, code);
            });
            start.countDown();

            int successfulVerifications = (first.get(5, TimeUnit.SECONDS).isPresent() ? 1 : 0)
                    + (second.get(5, TimeUnit.SECONDS).isPresent() ? 1 : 0);
            assertEquals(1, successfulVerifications);
        }
    }

    private static final class TestStaffAccounts extends StaffAccountService {

        private boolean active = true;

        private TestStaffAccounts() {
            super(mock(StaffAccountStore.class));
        }

        @Override
        public Optional<Account> findActiveByEmail(String email) {
            return active && STAFF_EMAIL.equals(email) ? Optional.of(STAFF) : Optional.empty();
        }

        @Override
        public Optional<Account> resolve(WebAccountContext context) {
            return active && STAFF_ID.equals(context.accountId()) ? Optional.of(STAFF) : Optional.empty();
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
