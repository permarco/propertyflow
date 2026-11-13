package com.pertegato.propertyflow.caseprocessing.demo;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;

class DemoCasesBoundaryTest {
    private final DemoCases cases = new DemoCases();

    static Stream<Arguments> invalidSubmissions() {
        return Stream.concat(
                Stream.of(0, 1, 2, 3, 4).flatMap(field -> Stream.of("", "   ", (String) null)
                        .map(value -> Arguments.of(field, value))),
                Stream.of(Arguments.of(0, "t".repeat(151)), Arguments.of(1, "u".repeat(201)),
                        Arguments.of(2, "invalid"), Arguments.of(2, "a@b@c.ch"),
                        Arguments.of(2, "a".repeat(250) + "@b.ch"), Arguments.of(3, "d".repeat(2001)),
                        Arguments.of(4, "o".repeat(81))));
    }

    @ParameterizedTest
    @MethodSource("invalidSubmissions")
    void invalidSubmissionLeavesNoCaseAndDoesNotConsumeOperation(int field, String value) {
        String[] input = {"Titel", "Wohnung", "a@b.ch", "Beschreibung", "operation"};
        input[field] = value;
        DemoCases.Page before = cases.list("", "", true, "default", false, 1);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> cases.create("scope", input[0], input[1], input[2], input[3], input[4]));
        assertEquals(BAD_REQUEST, error.getStatusCode());
        assertEquals(before, cases.list("", "", true, "default", false, 1));
        String token = cases.create("scope", "Titel", "Wohnung", "a@b.ch", "Beschreibung", "operation");
        assertEquals("Titel", cases.tenant(token).title());
        assertEquals(before.total() + 1, cases.list("", "", true, "default", false, 1).total());
    }

    @Test
    void submissionAcceptsExactMaximumLengths() {
        String email = "a".repeat(249) + "@b.ch";
        String description = "d".repeat(1997) + "\n\uD83D\uDE80";
        String formDescription = description.replace("\n", "\r\n");
        String token = cases.create("scope", "t".repeat(150), "u".repeat(200), email, formDescription, "o".repeat(80));
        DemoCases.View stored = cases.tenant(token);
        assertEquals("t".repeat(150), stored.title());
        assertEquals("u".repeat(200), stored.originalUnit());
        assertEquals(email, stored.email());
        assertEquals(description, stored.description());
        assertEquals(1, stored.entries().size());
        assertEquals(description, stored.entries().getFirst().text());
        assertEquals(token, cases.create("scope", "t".repeat(150), "u".repeat(200), email, description, "o".repeat(80)));
    }

    static Stream<Arguments> invalidMessages() {
        return Stream.of(Arguments.of("", "operation"), Arguments.of("   ", "operation"),
                Arguments.of("x".repeat(2001), "operation"), Arguments.of("Text", ""),
                Arguments.of("Text", "   "), Arguments.of("Text", "o".repeat(81)));
    }

    @ParameterizedTest
    @MethodSource("invalidMessages")
    void invalidMessageLeavesEntireCaseUnchangedAndAllowsCorrectedRetry(String text, String operation) {
        DemoCases.View before = cases.get("REQ-2026-001");
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> cases.message(null, "demo-aktiver-fall", text, false, false, operation));
        assertEquals(BAD_REQUEST, error.getStatusCode());
        assertEquals(before, cases.get(before.id()));
        cases.message(null, "demo-aktiver-fall", "Korrigiert", false, false, "operation");
        assertEquals(before.entries().size() + 1, cases.get(before.id()).entries().size());
    }

    @Test
    void tenantMessageAcceptsExactMaximumAfterNewlineNormalizationAndIdenticalRetryHasNoEffect() {
        String text = "m".repeat(1997) + "\n\uD83D\uDE80";
        String operation = "o".repeat(80);
        cases.message(null, "demo-aktiver-fall", text.replace("\n", "\r\n"), false, false, operation);
        DemoCases.View saved = cases.get("REQ-2026-001");
        assertEquals(text, saved.entries().getLast().text());
        cases.message(null, "demo-aktiver-fall", text, false, false, operation);
        assertEquals(saved, cases.get(saved.id()));
    }

    @Test
    void staffMessageAcceptsItsOwnMaximumLength() {
        String id = "REQ-2026-001";
        String text = "m".repeat(5000);
        cases.message(id, null, text, false, false, "staff-message");
        DemoCases.View saved = cases.get(id);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> cases.message(id, null, text + "m", false, false, "over-staff-limit"));
        assertEquals(BAD_REQUEST, error.getStatusCode());
        assertEquals(text, saved.entries().getLast().text());
        assertEquals(saved, cases.get(id));
    }

    @ParameterizedTest
    @ValueSource(strings = {"text", "internal", "answer", "tenant"})
    void changedMessagePayloadConflictsWithoutChangingAnyCaseData(String field) {
        String id = "REQ-2026-001";
        cases.message(id, null, "Original", false, false, "same-operation");
        DemoCases.View saved = cases.get(id);
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> cases.message(
                field.equals("tenant") ? null : id, field.equals("tenant") ? "demo-aktiver-fall" : null,
                field.equals("text") ? "Geändert" : "Original", field.equals("internal"), field.equals("answer"), "same-operation"));
        assertEquals(CONFLICT, error.getStatusCode());
        assertEquals(saved, cases.get(id));
    }

    static Stream<Arguments> invalidChanges() {
        return Stream.of(Arguments.of("", ""), Arguments.of("   ", ""), Arguments.of("o".repeat(81), ""),
                Arguments.of("operation", "r".repeat(1001)));
    }

    @ParameterizedTest
    @MethodSource("invalidChanges")
    void invalidChangeLeavesEntireCaseUnchangedAndAllowsCorrectedRetry(String operation, String reason) {
        DemoCases.View before = cases.get("REQ-2026-001");
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> cases.change(before.id(), "unit", "Neue Wohnung", reason, before.version(), operation));
        assertEquals(BAD_REQUEST, error.getStatusCode());
        assertEquals(before, cases.get(before.id()));
        cases.change(before.id(), "unit", "Neue Wohnung", "", before.version(), "operation");
        assertEquals("Neue Wohnung", cases.get(before.id()).unit());
        assertEquals(before.entries().size() + 1, cases.get(before.id()).entries().size());
    }

    @Test
    void changeAcceptsExactMaximumLengthsAndDoesNotDuplicateHistoryOnRetry() {
        DemoCases.View before = cases.get("REQ-2026-001");
        String unit = "u".repeat(200);
        String reason = "r".repeat(1000);
        String operation = "o".repeat(80);
        cases.change(before.id(), "unit", unit, reason, before.version(), operation);
        DemoCases.View after = cases.get(before.id());
        assertEquals(unit, after.unit());
        assertEquals(reason, after.entries().getLast().reason());
        cases.change(before.id(), "unit", unit, reason, before.version(), operation);
        assertEquals(after, cases.get(before.id()));
    }

    @Test
    void concurrentIdenticalSubmissionsCreateExactlyOneCase() throws Exception {
        long before = cases.list("", "", true, "default", false, 1).total();
        List<String> tokens = race(
                () -> cases.create("scope", "Titel", "Wohnung", "a@b.ch", "Text", "same"),
                () -> cases.create("scope", "Titel", "Wohnung", "a@b.ch", "Text", "same"));
        assertEquals(tokens.getFirst(), tokens.getLast());
        assertEquals(before + 1, cases.list("", "", true, "default", false, 1).total());
        assertEquals(1, cases.tenant(tokens.getFirst()).entries().size());
    }

    @Test
    void concurrentChangesWithSameVersionHaveExactlyOneWinner() throws Exception {
        DemoCases.View before = cases.get("REQ-2026-001");
        List<Integer> results = race(() -> changeStatus(before, "In Bearbeitung", "first"),
                () -> changeStatus(before, "Wartet auf externe Rückmeldung", "second"));
        assertEquals(1, results.stream().filter(status -> status == 200).count());
        assertEquals(1, results.stream().filter(status -> status == 409).count());
        DemoCases.View after = cases.get(before.id());
        assertEquals(before.version() + 1, after.version());
        assertEquals(before.entries().size() + 1, after.entries().size());
        assertEquals(before.entries(), after.entries().subList(0, before.entries().size()));
        assertEquals(results.getFirst() == 200 ? "In Bearbeitung" : "Wartet auf externe Rückmeldung", after.status());
        assertEquals(results.getFirst() == 200 ? "first" : "second", after.entries().getLast().reason());
    }

    private int changeStatus(DemoCases.View before, String status, String operation) {
        try {
            cases.change(before.id(), "status", status, operation, before.version(), operation);
            return 200;
        } catch (ResponseStatusException exception) {
            return exception.getStatusCode().value();
        }
    }

    private static <T> List<T> race(Callable<T> first, Callable<T> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<T> firstResult = executor.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return first.call();
            });
            Future<T> secondResult = executor.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return second.call();
            });
            try {
                assertTrue(ready.await(5, TimeUnit.SECONDS));
            } finally {
                start.countDown();
            }
            return List.of(firstResult.get(5, TimeUnit.SECONDS), secondResult.get(5, TimeUnit.SECONDS));
        }
    }
}
