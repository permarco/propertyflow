package com.pertegato.propertyflow.access.service;

import com.pertegato.propertyflow.access.demo.DemoAccounts;
import com.pertegato.propertyflow.access.demo.DemoAccounts.Account;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("frontend-demo")
public final class StaffLogin {

    private static final Logger LOGGER = LoggerFactory.getLogger(StaffLogin.class);
    private static final String DIGEST_ALGORITHM = "HmacSHA256";
    private static final Pattern CODE_FORMAT = Pattern.compile("[0-9]{6}");
    private static final Duration CODE_LIFETIME = Duration.ofMinutes(10);
    private static final Duration REQUEST_INTERVAL = Duration.ofMinutes(1);
    private static final Duration REQUEST_WINDOW = Duration.ofHours(1);
    private static final int MAX_REQUESTS = 5;
    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_TRACKED_ADDRESSES = 4096;
    private static final int DELIVERY_QUEUE_CAPACITY = 32;

    private final DemoAccounts accounts;
    private final StaffCodeDelivery delivery;
    private final Clock clock;
    private final Executor deliveryExecutor;
    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec digestKey;
    private final Map<String, AddressState> addresses = new HashMap<>();

    @Autowired
    public StaffLogin(DemoAccounts accounts, StaffCodeDelivery delivery) {
        this(accounts, delivery, Clock.systemUTC(), createDeliveryExecutor());
    }

    StaffLogin(DemoAccounts accounts, StaffCodeDelivery delivery, Clock clock, Executor deliveryExecutor) {
        this.accounts = accounts;
        this.delivery = delivery;
        this.clock = clock;
        this.deliveryExecutor = deliveryExecutor;

        byte[] key = new byte[32];
        random.nextBytes(key);
        digestKey = new SecretKeySpec(key, DIGEST_ALGORITHM);
    }

    public void requestCode(String email) {
        requestCode(email, false);
    }

    public void requestRegistrationCode(String email) {
        requestCode(email, true);
    }

    private void requestCode(String email, boolean registration) {
        String normalizedEmail = DemoAccounts.normalizeEmail(email);
        Instant requestedAt = clock.instant();
        reserveRequest(normalizedEmail, requestedAt);

        // SMTP latency and failures are independent of the public request response.
        try {
            deliveryExecutor.execute(() -> issueCode(normalizedEmail, requestedAt, registration));
        } catch (RejectedExecutionException exception) {
            LOGGER.warn("Staff sign-in code delivery is temporarily unavailable.");
        }
    }

    public Optional<UUID> verifyCode(String email, String code) {
        String normalizedEmail;
        try {
            normalizedEmail = DemoAccounts.normalizeEmail(email);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }

        Challenge challenge = consumeCode(normalizedEmail, code);
        if (challenge == null) {
            return Optional.empty();
        }

        if (challenge.accountId == null) {
            return Optional.of(accounts.activateVerifiedTenant(normalizedEmail).id());
        }

        return accounts.findActiveById(challenge.accountId)
                .filter(account -> normalizedEmail.equals(account.email()))
                .map(Account::id);
    }

    @PreDestroy
    void shutdown() {
        if (deliveryExecutor instanceof ExecutorService executorService) {
            executorService.shutdownNow();
        }
    }

    private synchronized void reserveRequest(String email, Instant requestedAt) {
        Instant cutoff = requestedAt.minus(REQUEST_WINDOW);
        addresses.values().removeIf(state -> !state.requests.getLast().isAfter(cutoff));

        AddressState state = addresses.get(email);
        if (state == null) {
            if (addresses.size() >= MAX_TRACKED_ADDRESSES) {
                throw new CodeRateLimitException();
            }
            state = new AddressState();
            addresses.put(email, state);
        }

        if (!state.requests.isEmpty() && requestedAt.isBefore(state.requests.getLast().plus(REQUEST_INTERVAL))) {
            throw new CodeRateLimitException();
        }

        while (!state.requests.isEmpty() && !state.requests.getFirst().isAfter(cutoff)) {
            state.requests.removeFirst();
        }
        if (state.requests.size() >= MAX_REQUESTS) {
            throw new CodeRateLimitException();
        }

        state.requests.addLast(requestedAt);
        state.challenge = null;
    }

    private void issueCode(String email, Instant requestedAt, boolean registration) {
        try {
            Optional<Account> account = accounts.findActiveByEmail(email);
            if (account.isEmpty() && !registration) {
                return;
            }

            String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
            Challenge challenge = new Challenge(account.map(Account::id).orElse(null), digest(email, code), requestedAt.plus(CODE_LIFETIME));
            if (activateCode(email, requestedAt, challenge)) {
                delivery.send(email, code);
            }
        } catch (RuntimeException exception) {
            clearCode(email, requestedAt);
            // Delivery and account lookup exceptions may contain addresses or secrets.
            LOGGER.warn("Staff sign-in code delivery could not be completed.");
        }
    }

    private synchronized boolean activateCode(String email, Instant requestedAt, Challenge challenge) {
        AddressState state = addresses.get(email);
        if (state == null || !state.requests.getLast().equals(requestedAt) || !clock.instant().isBefore(challenge.expiresAt)) {
            return false;
        }
        state.challenge = challenge;
        return true;
    }

    private synchronized void clearCode(String email, Instant requestedAt) {
        AddressState state = addresses.get(email);
        if (state != null && state.requests.getLast().equals(requestedAt)) {
            state.challenge = null;
        }
    }

    private synchronized Challenge consumeCode(String email, String code) {
        AddressState state = addresses.get(email);
        if (state == null || state.challenge == null) {
            return null;
        }

        Challenge challenge = state.challenge;
        if (!clock.instant().isBefore(challenge.expiresAt)) {
            state.challenge = null;
            return null;
        }

        boolean match = code != null && CODE_FORMAT.matcher(code).matches()
                && MessageDigest.isEqual(challenge.digest, digest(email, code));
        challenge.attempts++;
        if (match || challenge.attempts >= MAX_ATTEMPTS) {
            state.challenge = null;
        }
        return match ? challenge : null;
    }

    private byte[] digest(String email, String code) {
        try {
            Mac mac = Mac.getInstance(DIGEST_ALGORITHM);
            mac.init(digestKey);
            return mac.doFinal((email + '\0' + code).getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot authenticate staff codes.", exception);
        }
    }

    private static ExecutorService createDeliveryExecutor() {
        return new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(DELIVERY_QUEUE_CAPACITY), task -> {
            Thread thread = new Thread(task, "staff-code-delivery");
            thread.setDaemon(true);
            return thread;
        });
    }

    public static final class CodeRateLimitException extends RuntimeException {
    }

    private static final class AddressState {

        private final Deque<Instant> requests = new ArrayDeque<>();
        private Challenge challenge;
    }

    private static final class Challenge {

        private final UUID accountId;
        private final byte[] digest;
        private final Instant expiresAt;
        private int attempts;

        private Challenge(UUID accountId, byte[] digest, Instant expiresAt) {
            this.accountId = accountId;
            this.digest = digest;
            this.expiresAt = expiresAt;
        }
    }
}
