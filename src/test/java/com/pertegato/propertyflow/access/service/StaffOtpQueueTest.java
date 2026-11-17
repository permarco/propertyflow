package com.pertegato.propertyflow.access.service;

import com.pertegato.propertyflow.access.contract.StaffAuthenticationException;
import com.pertegato.propertyflow.access.persistence.StaffAccountStore.Account;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StaffOtpQueueTest {
    private static final String EMAIL = "queue@example.test";
    private final StaffAccountService accounts = mock(StaffAccountService.class);
    private UUID flow = UUID.randomUUID();
    private final MutableClock clock = new MutableClock();
    private final QueuedExecutor queue = new QueuedExecutor();
    private final List<String> codes = new ArrayList<>();

    @BeforeEach
    void setup() {
        Account account = new Account(UUID.randomUUID(), EMAIL, true, true);
        when(accounts.findActiveByEmail(anyString())).thenReturn(Optional.of(account));
        when(accounts.resolve(any())).thenReturn(Optional.of(account));
    }

    @Test
    void separateBrowserFlowsCannotReplaceEachOthersCode() {
        StaffOtpService login = new StaffOtpService(accounts, (email, code) -> codes.add(code), clock, queue,
                Duration.ofMinutes(10), Duration.ofMinutes(1), 5, 5);
        UUID firstFlow = flow;
        login.requestCode(flow, EMAIL);
        clock.advance(60);
        flow = UUID.randomUUID();
        login.requestCode(flow, EMAIL);
        queue.jobs.get(1).run();
        queue.jobs.get(0).run();
        assertEquals(2, codes.size());
        assertTrue(login.verifyCode(flow, codes.getFirst()).isPresent());
        assertTrue(login.verifyCode(firstFlow, codes.get(1)).isPresent());
    }

    @Test
    void failureOfOlderDeliveryDoesNotRevokeNewerCode() throws Exception {
        CountDownLatch oldDeliveryStarted = new CountDownLatch(1);
        CountDownLatch failOldDelivery = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> latestCode = new AtomicReference<>();
        StaffOtpService login = new StaffOtpService(accounts, (email, code) -> {
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
        }, clock, queue,
                Duration.ofMinutes(10), Duration.ofMinutes(1), 5, 5);
        login.requestCode(flow, EMAIL);
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<?> oldDelivery = executor.submit(queue.jobs.getFirst());
            try {
                assertTrue(oldDeliveryStarted.await(5, TimeUnit.SECONDS));
                clock.advance(60);
        flow = UUID.randomUUID();
                login.requestCode(flow, EMAIL);
                queue.jobs.get(1).run();
            } finally {
                failOldDelivery.countDown();
            }
            oldDelivery.get(5, TimeUnit.SECONDS);
        }
        assertEquals(2, calls.get());
        assertTrue(login.verifyCode(flow, latestCode.get()).isPresent());
    }

    @Test
    void rejectedQueueDoesNotActivateAccountAndStillEnforcesRateLimit() {
        StaffOtpService login = new StaffOtpService(accounts, (email, code) -> codes.add(code), clock, task -> {
            throw new RejectedExecutionException("Queue full");
        }, Duration.ofMinutes(10), Duration.ofMinutes(1), 5, 5);
        assertDoesNotThrow(() -> login.requestCode(flow, EMAIL));
        assertTrue(codes.isEmpty());
        assertTrue(login.verifyCode(flow, "123456").isEmpty());
        assertThrows(StaffAuthenticationException.class, () -> login.requestCode(flow, EMAIL));
    }

    @Test
    void rejectedNewFlowCannotAuthenticateWithPreviousFlowCode() {
        AtomicInteger requests = new AtomicInteger();
        StaffOtpService login = new StaffOtpService(accounts, (email, code) -> codes.add(code), clock, task -> {
            if (requests.incrementAndGet() == 1) {
                task.run();
            } else {
                throw new RejectedExecutionException("Queue full");
            }
        }, Duration.ofMinutes(10), Duration.ofMinutes(1), 5, 5);
        login.requestCode(flow, EMAIL);
        clock.advance(60);
        flow = UUID.randomUUID();
        assertDoesNotThrow(() -> login.requestCode(flow, EMAIL));
        assertTrue(login.verifyCode(flow, codes.getFirst()).isEmpty());
    }

    @Test
    void jobStartedAtExpiryDoesNotSendOrActivateCode() {
        StaffOtpService login = new StaffOtpService(accounts, (email, code) -> codes.add(code), clock, queue,
                Duration.ofMinutes(10), Duration.ofMinutes(1), 5, 5);
        login.requestCode(flow, EMAIL);
        clock.advance(600);
        queue.jobs.getFirst().run();
        assertTrue(codes.isEmpty());
        assertTrue(login.verifyCode(flow, "123456").isEmpty());
    }

    @Test
    void addressLimitRejectsNewAddressesAndRecoversAfterWindowExpires() {
        StaffOtpService login = new StaffOtpService(accounts, (email, code) -> codes.add(code), clock, queue,
                Duration.ofMinutes(10), Duration.ofMinutes(1), 5, 5);
        for (int i = 0; i < 4096; i++) {
            login.requestCode(UUID.randomUUID(), "address-" + i + "@example.test");
        }
        assertThrows(StaffAuthenticationException.class, () -> login.requestCode(flow, EMAIL));
        assertEquals(4096, queue.jobs.size());
        clock.advance(3600);
        login.requestCode(flow, EMAIL);
        queue.jobs.getLast().run();
        assertEquals(1, codes.size());
        assertTrue(login.verifyCode(flow, codes.getFirst()).isPresent());
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
