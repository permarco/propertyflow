package com.pertegato.propertyflow.access.service;

import com.pertegato.propertyflow.access.contract.StaffAuthenticationException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TenantOtpServiceTest {
    @Test
    void expiredAndUndeliveredCodesNeverCreateAccountsAndResendRemainsLimited() {
        TenantAccountService accounts = mock(TenantAccountService.class);
        AtomicReference<String> sent = new AtomicReference<>();
        MutableClock clock = new MutableClock();
        TenantOtpService service = new TenantOtpService(accounts, (email, code) -> sent.set(code), clock, Runnable::run,
                Duration.ofMinutes(10), Duration.ofMinutes(1), 5, 5);
        UUID flow = UUID.randomUUID();
        service.requestCode(flow, "tenant@example.test", true);
        assertThrows(StaffAuthenticationException.class,
                () -> service.requestCode(UUID.randomUUID(), "tenant@example.test", false));
        clock.now = clock.now.plusSeconds(600);
        assertTrue(service.verifyCode(flow, sent.get()).isEmpty());
        TenantOtpService failing = new TenantOtpService(accounts, (email, code) -> {
            sent.set(code);
            throw new IllegalStateException("Testzustellung fehlgeschlagen");
        }, clock, Runnable::run, Duration.ofMinutes(10), Duration.ofMinutes(1), 5, 5);
        failing.requestCode(flow, "tenant@example.test", true);
        assertTrue(failing.verifyCode(flow, sent.get()).isEmpty());
        verifyNoInteractions(accounts);
    }

    @Test
    void concurrentVerificationAuthenticatesExactlyOnce() throws Exception {
        TenantAccountService accounts = mock(TenantAccountService.class);
        when(accounts.authenticate("tenant@example.test", true)).thenReturn(Optional.of(UUID.randomUUID()));
        AtomicReference<String> sent = new AtomicReference<>();
        TenantOtpService service = service(accounts, sent);
        UUID flow = UUID.randomUUID();
        service.requestCode(flow, "tenant@example.test", true);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> attempt = () -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return service.verifyCode(flow, sent.get()).isPresent();
            };
            Future<Boolean> first = executor.submit(attempt);
            Future<Boolean> second = executor.submit(attempt);
            start.countDown();
            assertEquals(1, (first.get(5, TimeUnit.SECONDS) ? 1 : 0) + (second.get(5, TimeUnit.SECONDS) ? 1 : 0));
        }
        org.mockito.Mockito.verify(accounts).authenticate("tenant@example.test", true);
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-16T10:00:00Z");

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

    @Test
    void registrationActivatesOnlyAfterCorrectCodeAndCannotReplay() {
        TenantAccountService accounts = mock(TenantAccountService.class);
        AtomicReference<String> sent = new AtomicReference<>();
        UUID id = UUID.randomUUID();
        when(accounts.authenticate("tenant@example.test", true)).thenReturn(Optional.of(id));
        TenantOtpService codes = service(accounts, sent);
        UUID flow = UUID.randomUUID();
        codes.requestCode(flow, " Tenant@Example.test ", true);
        verifyNoInteractions(accounts);
        assertTrue(codes.verifyCode(UUID.randomUUID(), sent.get()).isEmpty());
        assertEquals(id, codes.verifyCode(flow, sent.get()).orElseThrow().accountId());
        assertTrue(codes.verifyCode(flow, sent.get()).isEmpty());
    }

    @Test
    void fiveInvalidAttemptsInvalidateEvenCorrectCode() {
        TenantAccountService accounts = mock(TenantAccountService.class);
        AtomicReference<String> sent = new AtomicReference<>();
        TenantOtpService codes = service(accounts, sent);
        UUID flow = UUID.randomUUID();
        codes.requestCode(flow, "tenant@example.test", true);
        for (int index = 0; index < 5; index++) {
            assertTrue(codes.verifyCode(flow, "wrong").isEmpty());
        }
        assertTrue(codes.verifyCode(flow, sent.get()).isEmpty());
        verifyNoInteractions(accounts);
    }

    @Test
    void loginDoesNotRegisterUnknownAddress() {
        TenantAccountService accounts = mock(TenantAccountService.class);
        AtomicReference<String> sent = new AtomicReference<>();
        TenantOtpService codes = service(accounts, sent);
        codes.requestCode(UUID.randomUUID(), "unknown@example.test", false);
        assertEquals(null, sent.get());
    }

    private TenantOtpService service(TenantAccountService accounts, AtomicReference<String> sent) {
        return new TenantOtpService(accounts, (address, code) -> sent.set(code),
                Clock.fixed(Instant.parse("2026-11-16T10:00:00Z"), ZoneOffset.UTC), Runnable::run,
                Duration.ofMinutes(10), Duration.ofMinutes(1), 5, 5);
    }
}
