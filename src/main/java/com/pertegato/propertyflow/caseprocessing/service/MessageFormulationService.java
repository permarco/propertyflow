package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.access.contract.StaffAccessDecision;
import com.pertegato.propertyflow.access.contract.StaffAccountAccess;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.caseprocessing.contract.CaseActionException;
import com.pertegato.propertyflow.caseprocessing.contract.StaffCaseDataCommands;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseRepository;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@Service
@Profile("propertyflow-backend")
public class MessageFormulationService {

    public static final int MAX_TEXT_LENGTH = 5000;
    private final StaffAccountAccess access;
    private final CaseRepository cases;
    private final StaffCaseDataCommands commands;
    private final MessageFormulationModel model;
    private final Duration timeout;
    private final Semaphore capacity = new Semaphore(32);

    public MessageFormulationService(StaffAccountAccess access, CaseRepository cases, StaffCaseDataCommands commands,
            MessageFormulationModel model, @Value("${propertyflow.formulation.timeout:60s}") Duration timeout) {
        this.access = access;
        this.cases = cases;
        this.commands = commands;
        this.model = model;
        this.timeout = timeout;
    }

    public Attempt prepare(WebAccountContext actor, Attempts attempts, String caseId, UUID attemptId, String text) {
        authorize(actor, caseId);
        if (text == null || text.isBlank() || text.length() > MAX_TEXT_LENGTH) {
            throw new CaseActionException("INVALID_INPUT");
        }
        if (!commands.available(actor, caseId)) {
            throw new CaseActionException("CASE_NOT_ACTIVE");
        }
        return attempts.reserve(caseId, attemptId);
    }

    public void cancel(WebAccountContext actor, Attempts attempts, String caseId, UUID attemptId) {
        // Abbrechen bleibt auch nach Fallabschluss oder Engineausfall möglich.
        authorize(actor, caseId);
        attempts.cancel(caseId, attemptId);
    }

    public void stream(Attempt attempt, String instruction, Consumer<Event> output) {
        if (!attempt.connect(output)) {
            return;
        }
        if (!capacity.tryAcquire()) {
            attempt.finish("error");
            return;
        }
        attempt.deadline.update(Schedulers.parallel().schedule(() -> attempt.finish("error"), timeout.toMillis(), TimeUnit.MILLISECONDS));
        attempt.subscription.update(Flux.defer(() -> attempt.finished() ? Flux.empty() : model.formulate(instruction))
                .subscribeOn(Schedulers.boundedElastic())
                .doFinally(signal -> capacity.release())
                .subscribe(attempt::delta, ignored -> attempt.finish("error"), () -> attempt.finish("complete")));
    }

    private void authorize(WebAccountContext actor, String caseId) {
        StaffAccessDecision decision = access.checkStaffAccess(actor);
        if (decision != StaffAccessDecision.ALLOWED) {
            throw new CaseActionException(decision == StaffAccessDecision.UNAUTHENTICATED ? "UNAUTHENTICATED" : "FORBIDDEN");
        }
        if (!cases.existsById(caseId)) {
            throw new CaseActionException("CASE_NOT_AVAILABLE");
        }
    }

    public record Event(String name, Map<String, String> data) {
    }

    /**
     * Nur technische Versuchsreferenzen, bis die zugehörige Sitzung endet.
     */
    public static final class Attempts implements AutoCloseable {

        private final Map<Key, Attempt> entries = new HashMap<>();
        private boolean closed;

        synchronized Attempt reserve(String caseId, UUID id) {
            Key key = new Key(caseId, id);
            if (entries.containsKey(key)) {
                throw new CaseActionException("IDEMPOTENCY_CONFLICT");
            }
            checkCapacity();
            if (entries.values().stream().filter(attempt -> !attempt.finished()).count() >= 4) {
                throw new CaseActionException("FORMULATION_CAPACITY");
            }
            Attempt attempt = new Attempt();
            entries.put(key, attempt);
            return attempt;
        }

        synchronized void cancel(String caseId, UUID id) {
            Key key = new Key(caseId, id);
            Attempt attempt = entries.get(key);
            if (attempt == null) {
                checkCapacity();
                attempt = new Attempt();
                entries.put(key, attempt);
            }
            attempt.finish("aborted");
        }

        private void checkCapacity() {
            if (closed || entries.size() >= 512) {
                throw new CaseActionException("FORMULATION_CAPACITY");
            }
        }

        @Override
        public synchronized void close() {
            closed = true;
            entries.values().forEach(attempt -> attempt.finish("aborted"));
            entries.clear();
        }

        private record Key(String caseId, UUID id) {
        }
    }

    public static final class Attempt {

        private final Disposable.Swap subscription = Disposables.swap();
        private final Disposable.Swap deadline = Disposables.swap();
        private Consumer<Event> output;
        private String terminal;
        private int length;
        private boolean hasText;

        synchronized boolean connect(Consumer<Event> listener) {
            if (output != null) {
                return false;
            }
            if (terminal != null) {
                listener.accept(event(terminal));
                return false;
            }
            output = listener;
            return true;
        }

        synchronized void delta(String text) {
            if (terminal != null) {
                return;
            }
            length += text.length();
            if (length > MAX_TEXT_LENGTH) {
                finish("error");
                return;
            }
            hasText |= !text.isBlank();
            try {
                output.accept(new Event("delta", Map.of("text", text)));
            } catch (RuntimeException disconnected) {
                finish("aborted");
            }
        }

        public synchronized void finish(String state) {
            if (terminal != null) {
                return;
            }
            terminal = "complete".equals(state) && !hasText ? "error" : state;
            deadline.dispose();
            subscription.dispose();
            if (output != null) {
                try {
                    output.accept(event(terminal));
                } catch (RuntimeException disconnected) {
                    // Die Verbindung ist bereits beendet; keine weitere Ausgabe versuchen.
                } finally {
                    output = null;
                }
            }
        }

        synchronized boolean finished() {
            return terminal != null;
        }

        private Event event(String state) {
            return new Event(state, "error".equals(state) ? Map.of("code", "formulierung-fehlgeschlagen") : Map.of());
        }
    }
}
