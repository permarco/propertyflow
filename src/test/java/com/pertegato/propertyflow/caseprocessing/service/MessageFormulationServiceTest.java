package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.access.contract.StaffAccessDecision;
import com.pertegato.propertyflow.access.contract.StaffAccountAccess;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.caseprocessing.contract.CaseActionException;
import com.pertegato.propertyflow.caseprocessing.contract.StaffCaseDataCommands;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseRepository;
import com.pertegato.propertyflow.caseprocessing.service.MessageFormulationService.Attempt;
import com.pertegato.propertyflow.caseprocessing.service.MessageFormulationService.Attempts;
import com.pertegato.propertyflow.caseprocessing.service.MessageFormulationService.Event;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MessageFormulationServiceTest {

    private static final WebAccountContext ACTOR = new WebAccountContext(UUID.randomUUID());
    private final StaffAccountAccess access = mock(StaffAccountAccess.class);
    private final StaffCaseDataCommands commands = mock(StaffCaseDataCommands.class);
    private final CaseRepository cases = mock(CaseRepository.class);
    private final MessageFormulationModel model = mock(MessageFormulationModel.class);
    private final Attempts attempts = new Attempts();
    private MessageFormulationService service;

    @BeforeEach
    void prepare() {
        when(access.checkStaffAccess(ACTOR)).thenReturn(StaffAccessDecision.ALLOWED);
        when(cases.existsById(anyString())).thenReturn(true);
        when(commands.available(any(), anyString())).thenReturn(true);
        service = new MessageFormulationService(access, cases, commands, model, Duration.ofMillis(250));
    }

    @Test
    void streamsOnlyExplicitInstructionAndNeverLoadsCaseContent() throws Exception {
        String instruction = "Techniker kommt Dienstag. Bitte Zugang ermöglichen.";
        when(model.formulate(instruction)).thenReturn(Flux.just("Guten Tag, ", "<b>am Dienstag</b>"));
        List<Event> events = run(instruction);
        assertThat(events).extracting(Event::name).containsExactly("delta", "delta", "complete");
        assertThat(events.get(1).data()).containsEntry("text", "<b>am Dienstag</b>");
        verify(model).formulate(instruction);
        verify(cases).existsById("CASE-A");
        org.mockito.Mockito.verifyNoMoreInteractions(cases);
    }

    @Test
    void earlyAbortAndReplayDoNotStartAnotherProviderCall() {
        UUID id = UUID.randomUUID();
        service.cancel(ACTOR, attempts, "CASE-A", id);
        service.cancel(ACTOR, attempts, "CASE-A", id);
        assertThatThrownBy(() -> service.prepare(ACTOR, attempts, "CASE-A", id, "Text"))
                .isInstanceOf(CaseActionException.class).hasMessage("IDEMPOTENCY_CONFLICT");
        UUID started = UUID.randomUUID();
        service.prepare(ACTOR, attempts, "CASE-A", started, "Text");
        assertThatThrownBy(() -> service.prepare(ACTOR, attempts, "CASE-A", started, "Anderer Text"))
                .hasMessage("IDEMPOTENCY_CONFLICT");
        verifyNoInteractions(model);
    }

    @Test
    void cancellationIsBoundToSessionAndCase() {
        UUID id = UUID.randomUUID();
        service.cancel(ACTOR, attempts, "CASE-A", id);
        assertThat(service.prepare(ACTOR, attempts, "CASE-B", id, "Text")).isNotNull();
        assertThat(service.prepare(ACTOR, new Attempts(), "CASE-A", id, "Text")).isNotNull();
    }

    @Test
    void cancelAndSessionEndDisposeProviderAndDiscardLateOutput() throws Exception {
        CountDownLatch subscribed = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);
        when(model.formulate("Text")).thenReturn(Flux.<String>create(sink -> {
            sink.onCancel(cancelled::countDown);
            sink.next("Teiltext");
            subscribed.countDown();
        }));
        Attempt attempt = service.prepare(ACTOR, attempts, "CASE-A", UUID.randomUUID(), "Text");
        List<Event> events = new CopyOnWriteArrayList<>();
        service.stream(attempt, "Text", events::add);
        assertThat(subscribed.await(2, TimeUnit.SECONDS)).isTrue();
        attempts.close();
        assertThat(cancelled.await(2, TimeUnit.SECONDS)).isTrue();
        attempt.delta("Zu spät");
        attempt.finish("complete");
        assertThat(events).extracting(Event::name).containsExactly("delta", "aborted");
    }

    @Test
    void totalTimeoutCancelsEvenAStreamWithContinuousChunks() throws Exception {
        CountDownLatch cancelled = new CountDownLatch(1);
        when(model.formulate("Text")).thenReturn(Flux.interval(Duration.ofMillis(20)).map(index -> "a")
                .doOnCancel(cancelled::countDown));
        List<Event> events = run("Text");
        assertThat(events.getLast().name()).isEqualTo("error");
        assertThat(cancelled.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void providerFailureDoesNotExposeDiagnosticOrInstruction() throws Exception {
        when(model.formulate("Text")).thenReturn(Flux.error(new IllegalStateException("secret provider diagnostic")));
        assertThat(run("Text")).containsExactly(new Event("error", java.util.Map.of("code", "formulierung-fehlgeschlagen")));
    }

    @Test
    void emptyAndOversizedOutputsNeverComplete() throws Exception {
        when(model.formulate("Text")).thenReturn(Flux.just(" "));
        assertThat(run("Text").getLast().name()).isEqualTo("error");
        when(model.formulate("Text")).thenReturn(Flux.just("a".repeat(5001)));
        assertThat(run("Text")).extracting(Event::name).containsExactly("error");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n\t"})
    void blankInstructionIsRejectedBeforeProvider(String text) {
        assertThatThrownBy(() -> service.prepare(ACTOR, attempts, "CASE-A", UUID.randomUUID(), text))
                .hasMessage("INVALID_INPUT");
        verifyNoInteractions(model);
    }

    @Test
    void authorizationAndProcessAvailabilityAreCheckedBeforeEveryStart() {
        when(access.checkStaffAccess(ACTOR)).thenReturn(StaffAccessDecision.FORBIDDEN);
        assertThatThrownBy(() -> service.prepare(ACTOR, attempts, "CASE-A", UUID.randomUUID(), "Text")).hasMessage("FORBIDDEN");
        when(access.checkStaffAccess(ACTOR)).thenReturn(StaffAccessDecision.ALLOWED);
        when(commands.available(ACTOR, "CASE-A")).thenReturn(false);
        assertThatThrownBy(() -> service.prepare(ACTOR, attempts, "CASE-A", UUID.randomUUID(), "Text")).hasMessage("CASE_NOT_ACTIVE");
        service.cancel(ACTOR, attempts, "CASE-A", UUID.randomUUID());
        verifyNoInteractions(model);
    }

    @Test
    void boundsConcurrentAttemptsWithoutEvictingReplayProtection() {
        for (int i = 0; i < 4; i++) {
            service.prepare(ACTOR, attempts, "CASE-A", UUID.randomUUID(), "Text");
        }
        assertThatThrownBy(() -> service.prepare(ACTOR, attempts, "CASE-A", UUID.randomUUID(), "Text"))
                .hasMessage("FORMULATION_CAPACITY");
    }

    @Test
    void concurrentSameIdHasExactlyOneWinner() throws Exception {
        UUID id = UUID.randomUUID();
        AtomicInteger accepted = new AtomicInteger();
        Runnable start = () -> {
            try {
                service.prepare(ACTOR, attempts, "CASE-A", id, "Text");
                accepted.incrementAndGet();
            } catch (CaseActionException expected) {
                assertThat(expected.code()).isEqualTo("IDEMPOTENCY_CONFLICT");
            }
        };
        Thread first = Thread.startVirtualThread(start);
        Thread second = Thread.startVirtualThread(start);
        first.join();
        second.join();
        assertThat(accepted).hasValue(1);
    }

    private List<Event> run(String instruction) throws Exception {
        Attempt attempt = service.prepare(ACTOR, attempts, "CASE-A", UUID.randomUUID(), instruction);
        List<Event> events = new CopyOnWriteArrayList<>();
        CountDownLatch finished = new CountDownLatch(1);
        service.stream(attempt, instruction, event -> {
            events.add(event);
            if (!"delta".equals(event.name())) {
                finished.countDown();
            }
        });
        assertThat(finished.await(3, TimeUnit.SECONDS)).isTrue();
        return events;
    }
}
