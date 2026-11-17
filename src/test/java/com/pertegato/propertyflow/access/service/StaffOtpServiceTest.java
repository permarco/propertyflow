package com.pertegato.propertyflow.access.service;

import com.pertegato.propertyflow.access.contract.StaffAccessDecision;
import com.pertegato.propertyflow.access.contract.StaffAuthenticationErrorCode;
import com.pertegato.propertyflow.access.contract.StaffAuthenticationException;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.access.persistence.StaffAccountStore;
import com.pertegato.propertyflow.access.persistence.StaffAccountStore.Account;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
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
import org.springframework.dao.DataAccessResourceFailureException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StaffOtpServiceTest {

    private static final String EMAIL = "staff@example.test";
    private static final UUID ID = UUID.fromString("b2cb1350-374b-4a8f-a926-4b79f6e24bf6");
    private static final Account ACTIVE = new Account(ID, EMAIL, true, true);
    private static final WebAccountContext CONTEXT = new WebAccountContext(ID);

    private final TestClock clock = new TestClock();
    private final AtomicReference<String> delivered = new AtomicReference<>();

    private StaffAccountStore store;
    private StaffAccountService accounts;
    private StaffOtpService otp;

    @BeforeEach
    void setUp() {
        store = mock(StaffAccountStore.class);
        accounts = new StaffAccountService(store);
        when(store.findByEmail(EMAIL)).thenReturn(Optional.of(ACTIVE));
        when(store.findById(ID)).thenReturn(Optional.of(ACTIVE));
        otp = new StaffOtpService(accounts, (address, code) -> {
            assertEquals(EMAIL, address);
            delivered.set(code);
        }, clock, Runnable::run, Duration.ofMinutes(10), Duration.ofMinutes(1), 5, 5);
    }

    @Test
    void bindsCodeToOriginalFlowAndConsumesItOnce() {
        UUID flow = UUID.randomUUID();
        otp.requestCode(flow, " Staff@Example.test ");
        String code = delivered.get();

        assertTrue(code.matches("[0-9]{6}"));
        assertTrue(otp.verifyCode(UUID.randomUUID(), code).isEmpty());
        assertEquals(Optional.of(CONTEXT), otp.verifyCode(flow, code));
        assertTrue(otp.verifyCode(flow, code).isEmpty());
    }

    @Test
    void codeExpiresAtItsLimit() {
        UUID flow = UUID.randomUUID();
        otp.requestCode(flow, EMAIL);
        clock.advance(Duration.ofMinutes(10));

        assertTrue(otp.verifyCode(flow, delivered.get()).isEmpty());
    }

    @Test
    void retryLimitAppliesToKnownAndUnknownAddresses() {
        otp.requestCode(UUID.randomUUID(), "unknown@example.test");
        assertNull(delivered.get());
        assertProblem(StaffAuthenticationErrorCode.RATE_LIMITED, () -> otp.requestCode(UUID.randomUUID(), "unknown@example.test"));
        otp.requestCode(UUID.randomUUID(), EMAIL);
        assertProblem(StaffAuthenticationErrorCode.RATE_LIMITED, () -> otp.requestCode(UUID.randomUUID(), " STAFF@example.test "));
        assertProblem(StaffAuthenticationErrorCode.INVALID_EMAIL, () -> otp.requestCode(UUID.randomUUID(), "invalid"));

        for (int request = 1; request < 5; request++) {
            clock.advance(Duration.ofMinutes(1));
            otp.requestCode(UUID.randomUUID(), EMAIL);
        }
        clock.advance(Duration.ofMinutes(1));
        assertProblem(StaffAuthenticationErrorCode.RATE_LIMITED, () -> otp.requestCode(UUID.randomUUID(), EMAIL));
        clock.advance(Duration.ofMinutes(55));
        otp.requestCode(UUID.randomUUID(), EMAIL);
    }

    @Test
    void wrongCodesUseUpAllAttempts() {
        UUID flow = UUID.randomUUID();
        otp.requestCode(flow, EMAIL);
        String correct = delivered.get();
        String wrong = "111111".equals(correct) ? "222222" : "111111";
        for (int attempt = 0; attempt < 5; attempt++) {
            assertTrue(otp.verifyCode(flow, wrong).isEmpty());
        }
        assertTrue(otp.verifyCode(flow, correct).isEmpty());
    }

    @Test
    void roleRevocationBetweenIssuanceAndVerificationRejectsAndConsumesTheCode() {
        UUID flow = UUID.randomUUID();
        otp.requestCode(flow, EMAIL);
        when(store.findById(ID)).thenReturn(Optional.of(new Account(ID, EMAIL, true, false)));

        assertEquals(StaffAccessDecision.FORBIDDEN, accounts.checkStaffAccess(CONTEXT));
        assertTrue(otp.verifyCode(flow, delivered.get()).isEmpty());
        when(store.findById(ID)).thenReturn(Optional.of(ACTIVE));
        assertEquals(StaffAccessDecision.ALLOWED, accounts.checkStaffAccess(CONTEXT));
        assertTrue(otp.verifyCode(flow, delivered.get()).isEmpty());

        when(store.findById(ID)).thenReturn(Optional.empty());
        assertEquals(StaffAccessDecision.UNAUTHENTICATED, accounts.checkStaffAccess(CONTEXT));
    }

    @Test
    void transientReadFailureIsDistinctFromAnInvalidCodeAndDoesNotConsumeTheChallenge() {
        UUID flow = UUID.randomUUID();
        otp.requestCode(flow, EMAIL);
        when(store.findById(ID)).thenThrow(new DataAccessResourceFailureException("synthetic failure"))
                .thenThrow(new DataAccessResourceFailureException("synthetic failure"))
                .thenReturn(Optional.of(ACTIVE));

        assertProblem(StaffAuthenticationErrorCode.SERVICE_UNAVAILABLE, () -> otp.verifyCode(flow, delivered.get()));
        assertProblem(StaffAuthenticationErrorCode.SERVICE_UNAVAILABLE, () -> accounts.checkStaffAccess(CONTEXT));

        assertEquals(Optional.of(CONTEXT), otp.verifyCode(flow, delivered.get()));
    }

    @Test
    void aRejectedRequestDoesNotReplaceAUsableChallenge() {
        UUID original = UUID.randomUUID();
        otp.requestCode(original, EMAIL);
        assertProblem(StaffAuthenticationErrorCode.RATE_LIMITED, () -> otp.requestCode(UUID.randomUUID(), EMAIL));

        assertEquals(Optional.of(CONTEXT), otp.verifyCode(original, delivered.get()));
    }

    @Test
    void delayedDeliveryFailureDoesNotInvalidateAnotherFlowForTheSameAddress() {
        Deque<Runnable> queue = new ArrayDeque<>();
        UUID failedFlow = UUID.randomUUID();
        UUID activeFlow = UUID.randomUUID();
        AtomicReference<String> latestCode = new AtomicReference<>();
        AtomicReference<String> failedCode = new AtomicReference<>();
        StaffOtpService asynchronous = new StaffOtpService(accounts, (address, code) -> {
            if (latestCode.get() != null) {
                failedCode.set(code);
                throw new IllegalStateException("synthetic delayed delivery failure");
            }
            latestCode.set(code);
        }, clock, queue::addLast, Duration.ofMinutes(10), Duration.ZERO, 5, 5);

        asynchronous.requestCode(failedFlow, EMAIL);
        asynchronous.requestCode(activeFlow, EMAIL);
        queue.removeLast().run();
        queue.removeFirst().run();

        assertTrue(asynchronous.verifyCode(failedFlow, failedCode.get()).isEmpty());
        assertEquals(Optional.of(CONTEXT), asynchronous.verifyCode(activeFlow, latestCode.get()));
    }

    @Test
    void oneOfTwoParallelVerificationsConsumesTheCode() throws Exception {
        UUID flow = UUID.randomUUID();
        otp.requestCode(flow, EMAIL);
        String code = delivered.get();
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService threads = Executors.newFixedThreadPool(2)) {
            Future<Optional<WebAccountContext>> first = threads.submit(() -> {
                start.await();
                return otp.verifyCode(flow, code);
            });
            Future<Optional<WebAccountContext>> second = threads.submit(() -> {
                start.await();
                return otp.verifyCode(flow, code);
            });
            start.countDown();
            assertTrue(first.get(5, TimeUnit.SECONDS).isPresent() ^ second.get(5, TimeUnit.SECONDS).isPresent());
        }
    }

    private static void assertProblem(StaffAuthenticationErrorCode code, Runnable action) {
        assertEquals(code, assertThrows(StaffAuthenticationException.class, action::run).code());
    }

    private static final class TestClock extends Clock {

        private Instant now = Instant.parse("2026-11-14T14:00:00Z");

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

        private void advance(Duration duration) {
            now = now.plus(duration);
        }
    }
}
