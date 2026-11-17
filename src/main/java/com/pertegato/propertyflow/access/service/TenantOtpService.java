package com.pertegato.propertyflow.access.service;

import com.pertegato.propertyflow.access.contract.EmailAddress;
import com.pertegato.propertyflow.access.contract.StaffAuthenticationErrorCode;
import com.pertegato.propertyflow.access.contract.StaffAuthenticationException;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
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
import java.util.Objects;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("propertyflow-backend & !frontend-demo")
public class TenantOtpService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TenantOtpService.class);
    private static final String DIGEST_ALGORITHM = "HmacSHA256";
    private static final Pattern CODE_FORMAT = Pattern.compile("[0-9]{6}");
    private static final Duration REQUEST_WINDOW = Duration.ofHours(1);
    private static final int MAX_TRACKED_ADDRESSES = 4096;
    private static final int DELIVERY_QUEUE_CAPACITY = 32;

    private final TenantAccountService accounts;
    private final StaffCodeDelivery delivery;
    private final Clock clock;
    private final Executor deliveryExecutor;
    private final Duration lifetime;
    private final Duration requestInterval;
    private final int maxAttempts;
    private final int requestsPerHour;
    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec digestKey;
    private final Map<String, Deque<Request>> requests = new HashMap<>();
    private final Map<UUID, Challenge> challenges = new HashMap<>();

    @Autowired
    public TenantOtpService(TenantAccountService accounts, StaffCodeDelivery delivery,
            @Value("${propertyflow.tenant.otp.lifetime:10m}") Duration lifetime,
            @Value("${propertyflow.tenant.otp.resend-interval:1m}") Duration requestInterval,
            @Value("${propertyflow.tenant.otp.max-attempts:5}") int maxAttempts,
            @Value("${propertyflow.tenant.otp.requests-per-hour:5}") int requestsPerHour) {
        this(accounts, delivery, Clock.systemUTC(), createDeliveryExecutor(), lifetime, requestInterval, maxAttempts, requestsPerHour);
    }

    TenantOtpService(TenantAccountService accounts, StaffCodeDelivery delivery, Clock clock, Executor deliveryExecutor,
            Duration lifetime, Duration requestInterval, int maxAttempts, int requestsPerHour) {
        if (lifetime.isNegative() || lifetime.isZero() || lifetime.compareTo(REQUEST_WINDOW) > 0
                || requestInterval.isNegative() || requestInterval.compareTo(REQUEST_WINDOW) > 0 || maxAttempts < 1 || requestsPerHour < 1) {
            throw new IllegalArgumentException("Invalid staff OTP settings.");
        }
        this.accounts = accounts;
        this.delivery = delivery;
        this.clock = clock;
        this.deliveryExecutor = deliveryExecutor;
        this.lifetime = lifetime;
        this.requestInterval = requestInterval;
        this.maxAttempts = maxAttempts;
        this.requestsPerHour = requestsPerHour;
        byte[] key = new byte[32];
        random.nextBytes(key);
        digestKey = new SecretKeySpec(key, DIGEST_ALGORITHM);
    }

    public void requestCode(UUID loginFlowId, String email, boolean registration) {
        Objects.requireNonNull(loginFlowId, "loginFlowId");
        String address;
        try {
            address = EmailAddress.normalize(email);
        } catch (IllegalArgumentException exception) {
            throw new StaffAuthenticationException(StaffAuthenticationErrorCode.INVALID_EMAIL);
        }
        Request request = new Request(loginFlowId, clock.instant());
        reserveRequest(address, request);

        boolean permitted;
        try {
            permitted = registration || accounts.exists(address);
        } catch (RuntimeException exception) {
            releaseRequest(address, request);
            throw exception;
        }
        // Gleiche asynchrone Antwort für bekannte und unbekannte Adressen.
        try {
            deliveryExecutor.execute(() -> {
                if (permitted) {
                    deliverCode(loginFlowId, address, registration, request.at().plus(lifetime));
                }
            });
        } catch (RejectedExecutionException exception) {
            LOGGER.warn("Tenant sign-in code delivery is temporarily unavailable.");
        }
    }

    public Optional<WebAccountContext> verifyCode(UUID loginFlowId, String code) {
        Challenge challenge = matchingChallenge(loginFlowId, code);
        if (challenge == null) {
            return Optional.empty();
        }
        if (!consumeChallenge(loginFlowId, challenge)) {
            return Optional.empty();
        }
        return accounts.authenticate(challenge.email, challenge.registration).map(WebAccountContext::new);
    }

    private void deliverCode(UUID loginFlowId, String email, boolean registration, Instant expiresAt) {
        Challenge challenge = null;
        try {
            String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
            challenge = new Challenge(email, registration, digest(loginFlowId, code), expiresAt);
            if (activateChallenge(loginFlowId, challenge)) {
                delivery.send(email, code);
            }
        } catch (RuntimeException exception) {
            if (challenge != null) {
                consumeChallenge(loginFlowId, challenge);
            }
            // SMTP-Fehler können Nachrichtentext und Empfänger enthalten; deshalb nur neutral protokollieren.
            LOGGER.warn("Tenant sign-in code delivery could not be completed.");
        }
    }

    private synchronized void reserveRequest(String address, Request request) {
        Instant cutoff = request.at().minus(REQUEST_WINDOW);
        requests.values().removeIf(queue -> !queue.getLast().at().isAfter(cutoff));
        challenges.values().removeIf(challenge -> !request.at().isBefore(challenge.expiresAt));

        Deque<Request> queue = requests.get(address);
        if (queue == null) {
            if (requests.size() >= MAX_TRACKED_ADDRESSES) {
                throw new StaffAuthenticationException(StaffAuthenticationErrorCode.RATE_LIMITED);
            }
            queue = new ArrayDeque<>();
            requests.put(address, queue);
        }
        if (!queue.isEmpty() && request.at().isBefore(queue.getLast().at().plus(requestInterval))) {
            throw new StaffAuthenticationException(StaffAuthenticationErrorCode.RATE_LIMITED);
        }
        while (!queue.isEmpty() && !queue.getFirst().at().isAfter(cutoff)) {
            queue.removeFirst();
        }
        if (queue.size() >= requestsPerHour) {
            throw new StaffAuthenticationException(StaffAuthenticationErrorCode.RATE_LIMITED);
        }
        queue.addLast(request);
    }

    private synchronized void releaseRequest(String address, Request request) {
        Deque<Request> queue = requests.get(address);
        if (queue != null) {
            queue.remove(request);
            if (queue.isEmpty()) {
                requests.remove(address);
            }
        }
    }

    private synchronized boolean activateChallenge(UUID loginFlowId, Challenge challenge) {
        return clock.instant().isBefore(challenge.expiresAt) && challenges.putIfAbsent(loginFlowId, challenge) == null;
    }

    private synchronized Challenge matchingChallenge(UUID loginFlowId, String code) {
        Challenge challenge = challenges.get(loginFlowId);
        if (challenge == null || !clock.instant().isBefore(challenge.expiresAt)) {
            challenges.remove(loginFlowId);
            return null;
        }
        if (code != null && CODE_FORMAT.matcher(code).matches()
                && MessageDigest.isEqual(challenge.digest, digest(loginFlowId, code))) {
            return challenge;
        }
        challenge.failedAttempts++;
        if (challenge.failedAttempts >= maxAttempts) {
            challenges.remove(loginFlowId);
        }
        return null;
    }

    private synchronized boolean consumeChallenge(UUID loginFlowId, Challenge challenge) {
        return challenges.remove(loginFlowId, challenge) && clock.instant().isBefore(challenge.expiresAt);
    }

    private byte[] digest(UUID loginFlowId, String code) {
        try {
            Mac mac = Mac.getInstance(DIGEST_ALGORITHM);
            mac.init(digestKey);
            return mac.doFinal((loginFlowId + ":" + code).getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot authenticate tenant codes.", exception);
        }
    }

    private static Executor createDeliveryExecutor() {
        return new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(DELIVERY_QUEUE_CAPACITY), task -> {
            Thread thread = new Thread(task, "tenant-otp-delivery");
            thread.setDaemon(true);
            return thread;
        });
    }

    @PreDestroy
    void shutdown() {
        if (deliveryExecutor instanceof ExecutorService executorService) {
            executorService.shutdownNow();
        }
    }

    private record Request(UUID flowId, Instant at) {
    }

    private static final class Challenge {

        private final String email;
        private final boolean registration;
        private final byte[] digest;
        private final Instant expiresAt;
        private int failedAttempts;

        private Challenge(String email, boolean registration, byte[] digest, Instant expiresAt) {
            this.email = email;
            this.registration = registration;
            this.digest = digest;
            this.expiresAt = expiresAt;
        }
    }
}
