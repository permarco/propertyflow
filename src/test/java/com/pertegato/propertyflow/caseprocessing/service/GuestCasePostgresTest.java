package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.access.contract.SubmissionContext.Guest;
import com.pertegato.propertyflow.access.contract.SubmissionContext.Tenant;
import com.pertegato.propertyflow.caseprocessing.contract.CaseAccepted;
import com.pertegato.propertyflow.caseprocessing.contract.CaseCreation;
import com.pertegato.propertyflow.caseprocessing.contract.CaseErrorCode;
import com.pertegato.propertyflow.caseprocessing.contract.CaseReading;
import com.pertegato.propertyflow.caseprocessing.contract.CaseServiceException;
import com.pertegato.propertyflow.caseprocessing.contract.CaseStatus;
import com.pertegato.propertyflow.caseprocessing.contract.CreateCaseCommand;
import com.pertegato.propertyflow.caseprocessing.contract.PublicAuthor;
import com.pertegato.propertyflow.caseprocessing.contract.PublicMessage;
import com.pertegato.propertyflow.caseprocessing.contract.TenantCaseView;
import com.pertegato.propertyflow.caseprocessing.delivery.PersonalLinkCodec;
import com.pertegato.propertyflow.intake.contract.CaseSubmission;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Destructive integration tests for the isolated propertyflow_test database, never a development database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("propertyflow-backend")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_DATABASE_URL", matches = ".+")
class GuestCasePostgresTest {

    private static final String ENCRYPTION_KEY = randomKey();
    private static final List<String> TABLES = List.of(
            "pf_case", "pf_case_access", "pf_submission", "pf_process_start_order", "pf_acceptance_mail_order");

    @Autowired
    private CaseCreation creation;

    @Autowired
    private CaseSubmission submission;

    @Autowired
    private CaseReading reading;

    @Autowired
    private PersonalLinkCodec links;

    @Autowired
    private JdbcTemplate database;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("PROPERTYFLOW_TEST_DATABASE_URL"));
        properties.add("spring.datasource.username", () -> environmentOrDefault("PROPERTYFLOW_TEST_DATABASE_USER", "propertyflow_test"));
        properties.add("spring.datasource.password", () -> environmentOrDefault("PROPERTYFLOW_TEST_DATABASE_PASSWORD", "propertyflow_test"));
        properties.add("propertyflow.links.encryption-key", () -> ENCRYPTION_KEY);
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @BeforeEach
    void clearIsolatedDatabase() {
        assertEquals("propertyflow_test", database.queryForObject("select current_database()", String.class),
                "These tests may clear only the explicitly isolated propertyflow_test database");
        database.execute("truncate table pf_case, pf_case_access, pf_submission, pf_process_start_order, pf_acceptance_mail_order cascade");
    }

    @Test
    void acceptsAndReadsCommittedCaseWithoutExternalServices() {
        CreateCaseCommand command = new CreateCaseCommand("first", " Heizung kalt ", " Wohnung 4 ",
                " Seit gestern kalt.\r\n Bitte prüfen. ", " TENANT@example.test ");

        CaseAccepted accepted = submission.submit(guest(), command);
        TenantCaseView view = reading.readByToken(accepted.personalAccessToken());

        assertFalse(accepted.replayed());
        assertEquals(accepted.caseId(), view.state().caseId());
        assertEquals(CaseStatus.RECEIVED, view.state().status());
        assertEquals("Heizung kalt", view.title());
        assertEquals("Wohnung 4", view.originalUnit());
        assertEquals("Seit gestern kalt.\n Bitte prüfen.", view.description());
        assertEquals("t***e.test", view.maskedContact());
        assertTrue(view.assignedUnit().isEmpty());
        assertTrue(view.state().urgency().isEmpty());
        assertTrue(view.urgencySource().isEmpty());
        assertTrue(view.urgencyHistory().isEmpty());
        assertEquals(view.receivedAt(), view.lastTenantContactAt());
        assertEquals(1, view.messages().size());
        PublicMessage message = view.messages().getFirst();
        assertEquals(PublicAuthor.TENANT, message.author());
        assertEquals(view.description(), message.text());
        assertEquals(view.receivedAt(), message.occurredAt());
        assertFalse(message.id().isBlank());
        assertCounts(1);
        assertEquals("PENDING", database.queryForObject("select state from pf_process_start_order", String.class));
        assertEquals("PENDING", database.queryForObject("select state from pf_acceptance_mail_order", String.class));
        assertEquals("tenant@example.test", database.queryForObject("select contact_email from pf_case", String.class));
    }

    @Test
    void identicalRetryReturnsOriginalCaseAndLinkWithoutDuplicateOrders() {
        Guest guest = guest();
        CreateCaseCommand command = command("retry");

        CaseAccepted first = creation.create(guest, command);
        CaseAccepted replay = submission.submit(guest, command);

        assertTrue(replay.replayed());
        assertEquals(first.caseId(), replay.caseId());
        assertTrue(first.personalAccessToken().equals(replay.personalAccessToken()), "Replay must return the same secret without logging it");
        assertCounts(1);
    }

    @Test
    void retryNormalizesLineEndingsAndEmailButPreservesOriginalTitleForComparison() {
        Guest guest = guest();
        CreateCaseCommand original = new CreateCaseCommand("normalization", " Titel ", " Wohnung 4 ", "Text\r\nZeile", " TENANT@example.test ");
        CaseAccepted first = creation.create(guest, original);

        CreateCaseCommand equivalent = new CreateCaseCommand("normalization", " Titel ", " Wohnung 4 ", "Text\nZeile", "tenant@example.test");
        CaseAccepted replay = creation.create(guest, equivalent);
        assertEquals(first.caseId(), replay.caseId());
        assertTrue(replay.replayed());

        CreateCaseCommand trimmedTitle = new CreateCaseCommand("normalization", "Titel", " Wohnung 4 ", "Text\nZeile", "tenant@example.test");
        assertError(CaseErrorCode.IDEMPOTENCY_CONFLICT, () -> creation.create(guest, trimmedTitle));
        assertCounts(1);
    }

    @Test
    void changedSubmissionCannotReuseAnOperation() {
        Guest guest = guest();
        creation.create(guest, command("conflict"));

        CreateCaseCommand changed = new CreateCaseCommand("conflict", "Anderer Titel", "Wohnung 4", "Heizung ist kalt.", "tenant@example.test");
        assertError(CaseErrorCode.IDEMPOTENCY_CONFLICT, () -> creation.create(guest, changed));
        assertCounts(1);
    }

    @Test
    void identicalOperationAndEmailInDifferentGuestScopesCreateIndependentCases() {
        CreateCaseCommand command = command("same-operation");

        CaseAccepted first = creation.create(guest(), command);
        CaseAccepted second = creation.create(guest(), command);

        assertNotEquals(first.caseId(), second.caseId());
        assertFalse(first.personalAccessToken().equals(second.personalAccessToken()));
        assertFalse(first.replayed());
        assertFalse(second.replayed());
        assertCounts(2);
    }

    @Test
    void concurrentIdenticalRequestsCommitOneCaseAndOneOrderOfEachType() throws Exception {
        int requestCount = 8;
        Guest guest = guest();
        CreateCaseCommand command = command("concurrent");
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        List<Future<CaseAccepted>> requests = new ArrayList<>();

        try {
            for (int index = 0; index < requestCount; index++) {
                requests.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS), "Concurrent test start timed out");
                    return creation.create(guest, command);
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<CaseAccepted> results = new ArrayList<>();
            for (Future<CaseAccepted> request : requests) {
                results.add(request.get(30, TimeUnit.SECONDS));
            }

            CaseAccepted first = results.getFirst();
            assertEquals(1, results.stream().filter(result -> !result.replayed()).count());
            assertTrue(results.stream().allMatch(result -> first.caseId().equals(result.caseId())));
            assertTrue(results.stream().allMatch(result -> first.personalAccessToken().equals(result.personalAccessToken())));
            assertCounts(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void invalidServiceInputDoesNotConsumeOperationAndCanBeCorrected() {
        Guest guest = guest();
        CreateCaseCommand invalid = new CreateCaseCommand("correctable", " ", "Wohnung 4", "Heizung ist kalt.", "tenant@example.test");

        CaseServiceException failure = assertError(CaseErrorCode.INVALID_INPUT, () -> creation.create(guest, invalid));
        assertEquals("REQUIRED", failure.fieldErrors().get("title"));
        assertCounts(0);

        assertFalse(creation.create(guest, command("correctable")).replayed());
        assertCounts(1);
    }

    @Test
    void validationRejectsOversizedRawInputBeforeTrimmingAndInvalidGuestEmail() {
        Guest guest = guest();
        CreateCaseCommand oversized = new CreateCaseCommand("limits", " ".repeat(150) + "X", "Wohnung 4", "Text", "tenant@example.test");
        CaseServiceException tooLong = assertError(CaseErrorCode.INVALID_INPUT, () -> creation.create(guest, oversized));
        assertEquals("TOO_LONG", tooLong.fieldErrors().get("title"));

        CreateCaseCommand invalidEmail = new CreateCaseCommand("limits", "Titel", "Wohnung 4", "Text", "invalid");
        CaseServiceException emailFailure = assertError(CaseErrorCode.INVALID_INPUT, () -> creation.create(guest, invalidEmail));
        assertEquals("INVALID", emailFailure.fieldErrors().get("email"));
        assertCounts(0);
    }

    @Test
    void absentOrUnsupportedAccountContextCannotFallBackToGuestCreation() {
        assertError(CaseErrorCode.UNAUTHENTICATED, () -> creation.create(null, command("no-context")));
        assertError(CaseErrorCode.UNAUTHENTICATED, () -> creation.create(new Tenant(UUID.randomUUID()), command("tenant-context")));
        assertCounts(0);
    }

    @Test
    void malformedUnknownAndRevokedTokensAreEquallyUnavailable() {
        assertError(CaseErrorCode.CASE_NOT_AVAILABLE, () -> reading.readByToken(null));
        assertError(CaseErrorCode.CASE_NOT_AVAILABLE, () -> reading.readByToken("not-a-token"));
        assertError(CaseErrorCode.CASE_NOT_AVAILABLE, () -> reading.readByToken(links.generateToken()));

        Guest guest = guest();
        CreateCaseCommand command = command("revoked");
        CaseAccepted accepted = creation.create(guest, command);
        database.update("update pf_case_access set revoked_at = current_timestamp where case_id = ?", accepted.caseId());

        assertError(CaseErrorCode.CASE_NOT_AVAILABLE, () -> reading.readByToken(accepted.personalAccessToken()));
        assertError(CaseErrorCode.REPLAY_ACCESS_UNAVAILABLE, () -> creation.create(guest, command));
        assertCounts(1);
    }

    @Test
    void replayNeverDisclosesTheReplacementLink() {
        Guest guest = guest();
        CreateCaseCommand command = command("replaced");
        CaseAccepted accepted = creation.create(guest, command);
        UUID replacementId = UUID.randomUUID();
        String replacementToken = links.generateToken();
        database.update("update pf_case_access set revoked_at = current_timestamp where case_id = ?", accepted.caseId());
        database.update("insert into pf_case_access (id, case_id, token_hash, encrypted_token) values (?, ?, ?, ?)",
                replacementId, accepted.caseId(), links.hash(replacementToken), links.encrypt(replacementId, replacementToken));

        assertEquals(accepted.caseId(), reading.readByToken(replacementToken).state().caseId());
        assertError(CaseErrorCode.CASE_NOT_AVAILABLE, () -> reading.readByToken(accepted.personalAccessToken()));
        assertError(CaseErrorCode.REPLAY_ACCESS_UNAVAILABLE, () -> creation.create(guest, command));
        assertEquals(1L, count("pf_case"));
        assertEquals(2L, count("pf_case_access"));
        assertEquals(1L, count("pf_submission"));
    }

    @Test
    void readingClosedCaseAndReplayingDoNotReopenItOrIncreaseItsVersion() {
        Guest guest = guest();
        CreateCaseCommand command = command("closed");
        CaseAccepted accepted = creation.create(guest, command);
        database.update("update pf_case set status = 'CLOSED', version = version + 1 where id = ?", accepted.caseId());
        TenantCaseView first = reading.readByToken(accepted.personalAccessToken());

        CaseAccepted replay = creation.create(guest, command);
        TenantCaseView second = reading.readByToken(accepted.personalAccessToken());

        assertTrue(replay.replayed());
        assertEquals(CaseStatus.CLOSED, first.state().status());
        assertEquals(first, second);
        assertEquals(first.version(), database.queryForObject("select version from pf_case where id = ?", Long.class, accepted.caseId()));
        assertCounts(1);
    }

    @Test
    void failureWhilePersistingMailOrderRollsBackTheEntireAcceptance() {
        database.execute("alter table pf_acceptance_mail_order add constraint test_reject_mail_order check (false) not valid");
        try {
            assertError(CaseErrorCode.STORAGE_UNAVAILABLE, () -> creation.create(guest(), command("rollback")));
            assertCounts(0);
        } finally {
            database.execute("alter table pf_acceptance_mail_order drop constraint test_reject_mail_order");
        }
    }

    @Test
    void storedAccessContainsHashAndEncryptedCopyButNoPlaintextToken() {
        CaseAccepted accepted = creation.create(guest(), command("encrypted"));
        Map<String, Object> access = database.queryForMap(
                "select token_hash, encrypted_token from pf_case_access where case_id = ?", accepted.caseId());
        String hash = (String) access.get("token_hash");
        String encrypted = (String) access.get("encrypted_token");

        assertTrue(hash.matches("[0-9a-f]{64}"));
        assertTrue(links.hash(accepted.personalAccessToken()).equals(hash));
        assertFalse(encrypted.contains(accepted.personalAccessToken()));
        assertFalse(new String(Base64.getDecoder().decode(encrypted), StandardCharsets.ISO_8859_1)
                .contains(accepted.personalAccessToken()));
        assertFalse(accepted.toString().contains(accepted.personalAccessToken()));
    }

    private void assertCounts(long expected) {
        for (String table : TABLES) {
            assertEquals(expected, count(table), table);
        }
    }

    private long count(String table) {
        if (!TABLES.contains(table)) {
            throw new IllegalArgumentException("Unknown test table");
        }
        return database.queryForObject("select count(*) from " + table, Long.class);
    }

    private CaseServiceException assertError(CaseErrorCode expected, Runnable action) {
        CaseServiceException failure = assertThrows(CaseServiceException.class, action::run);
        assertEquals(expected, failure.code());
        assertEquals(expected.name(), failure.getMessage());
        return failure;
    }

    private Guest guest() {
        return new Guest(UUID.randomUUID());
    }

    private CreateCaseCommand command(String operation) {
        return new CreateCaseCommand(operation, "Heizung kalt", "Wohnung 4", "Heizung ist kalt.", "tenant@example.test");
    }

    private static String environmentOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String randomKey() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
