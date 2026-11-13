package com.pertegato.propertyflow.access.service;

import com.pertegato.propertyflow.access.demo.DemoAccounts;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffLoginQueueTest {
    private static final String EMAIL = "queue@example.test";
    private final DemoAccounts accounts = new DemoAccounts("");
    private final MutableClock clock = new MutableClock();
    private final QueuedExecutor queue = new QueuedExecutor();
    private final List<String> codes = new ArrayList<>();

    @Test
    void supersededQueuedRequestCannotSendOrReplaceTheNewCode() {
        StaffLogin login = new StaffLogin(accounts, (email, code) -> codes.add(code), clock, queue);
        login.requestRegistrationCode(EMAIL);
        clock.advance(60);
        login.requestRegistrationCode(EMAIL);
        queue.jobs.get(1).run();
        queue.jobs.get(0).run();
        assertEquals(1, codes.size());
        assertTrue(accounts.findActiveByEmail(EMAIL).isEmpty());
        assertTrue(login.verifyCode(EMAIL, codes.getFirst()).isPresent());
    }

    @Test
    void failureOfOlderDeliveryDoesNotRevokeNewerCode() throws Exception {
        CountDownLatch oldDeliveryStarted = new CountDownLatch(1);
        CountDownLatch failOldDelivery = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> latestCode = new AtomicReference<>();
        StaffLogin login = new StaffLogin(accounts, (email, code) -> {
            if (calls.incrementAndGet() == 1) {
                oldDeliveryStarted.countDown();
                try {
                    assertTrue(failOldDelivery.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                throw new IllegalStateException("Older delivery failed");
            }
            latestCode.set(code);
        }, clock, queue);
        login.requestRegistrationCode(EMAIL);
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<?> oldDelivery = executor.submit(queue.jobs.getFirst());
            try {
                assertTrue(oldDeliveryStarted.await(5, TimeUnit.SECONDS));
                clock.advance(60);
                login.requestRegistrationCode(EMAIL);
                queue.jobs.get(1).run();
            } finally {
                failOldDelivery.countDown();
            }
            oldDelivery.get(5, TimeUnit.SECONDS);
        }
        assertEquals(2, calls.get());
        assertTrue(accounts.findActiveByEmail(EMAIL).isEmpty());
        assertTrue(login.verifyCode(EMAIL, latestCode.get()).isPresent());
    }

    @Test
    void rejectedQueueDoesNotActivateAccountAndStillEnforcesRateLimit() {
        StaffLogin login = new StaffLogin(accounts, (email, code) -> codes.add(code), clock, task -> {
            throw new RejectedExecutionException("Queue full");
        });
        assertDoesNotThrow(() -> login.requestRegistrationCode(EMAIL));
        assertTrue(codes.isEmpty());
        assertTrue(login.verifyCode(EMAIL, "123456").isEmpty());
        assertTrue(accounts.findActiveByEmail(EMAIL).isEmpty());
        assertThrows(StaffLogin.CodeRateLimitException.class, () -> login.requestRegistrationCode(EMAIL));
    }

    @Test
    void rejectedReplacementRevokesThePreviousCode() {
        AtomicInteger requests = new AtomicInteger();
        StaffLogin login = new StaffLogin(accounts, (email, code) -> codes.add(code), clock, task -> {
            if (requests.incrementAndGet() == 1) {
                task.run();
            } else {
                throw new RejectedExecutionException("Queue full");
            }
        });
        login.requestRegistrationCode(EMAIL);
        clock.advance(60);
        assertDoesNotThrow(() -> login.requestRegistrationCode(EMAIL));
        assertTrue(login.verifyCode(EMAIL, codes.getFirst()).isEmpty());
        assertTrue(accounts.findActiveByEmail(EMAIL).isEmpty());
    }

    @Test
    void jobStartedAtExpiryDoesNotSendOrActivateCode() {
        StaffLogin login = new StaffLogin(accounts, (email, code) -> codes.add(code), clock, queue);
        login.requestRegistrationCode(EMAIL);
        clock.advance(600);
        queue.jobs.getFirst().run();
        assertTrue(codes.isEmpty());
        assertTrue(login.verifyCode(EMAIL, "123456").isEmpty());
        assertTrue(accounts.findActiveByEmail(EMAIL).isEmpty());
    }

    @Test
    void addressLimitRejectsNewAddressesAndRecoversAfterWindowExpires() {
        StaffLogin login = new StaffLogin(accounts, (email, code) -> codes.add(code), clock, queue);
        for (int i = 0; i < 4096; i++) {
            login.requestRegistrationCode("address-" + i + "@example.test");
        }
        assertThrows(StaffLogin.CodeRateLimitException.class, () -> login.requestRegistrationCode(EMAIL));
        assertEquals(4096, queue.jobs.size());
        clock.advance(3600);
        login.requestRegistrationCode(EMAIL);
        queue.jobs.getLast().run();
        assertEquals(1, codes.size());
        assertTrue(login.verifyCode(EMAIL, codes.getFirst()).isPresent());
    }

    private static final class QueuedExecutor implements Executor {
        private final List<Runnable> jobs = new ArrayList<>();

        @Override
        public void execute(Runnable task) {
            jobs.add(task);
        }
    }

    private static final class MutableClock extends Clock {
        private volatile Instant now = Instant.parse("2026-11-13T08:00:00Z");

        private void advance(long seconds) {
            now = now.plusSeconds(seconds);
        }

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
