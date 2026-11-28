package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.access.contract.SubmissionContext;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.access.web.StaffWebSession;
import com.pertegato.propertyflow.caseprocessing.contract.AccountCaseReading;
import com.pertegato.propertyflow.caseprocessing.contract.CaseAccepted;
import com.pertegato.propertyflow.caseprocessing.contract.CaseActionException;
import com.pertegato.propertyflow.caseprocessing.contract.CaseCreation;
import com.pertegato.propertyflow.caseprocessing.contract.CaseReading;
import com.pertegato.propertyflow.caseprocessing.contract.CreateCaseCommand;
import com.pertegato.propertyflow.caseprocessing.contract.StaffCaseDataCommands;
import com.pertegato.propertyflow.caseprocessing.contract.StaffCaseListing;
import com.pertegato.propertyflow.caseprocessing.contract.StaffCaseQuery;
import com.pertegato.propertyflow.caseprocessing.contract.Urgency;
import com.pertegato.propertyflow.caseprocessing.contract.UrgencySource;
import com.pertegato.propertyflow.caseprocessing.delivery.CaseProcessGateway;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseDataStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.Entry;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.StartOrder;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "propertyflow.workflow.enabled=false")
@ActiveProfiles("propertyflow-backend")
@Import(CaseDataPostgresTest.AvailabilityConfiguration.class)
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_DATABASE_URL", matches = ".+")
class CaseDataPostgresTest {

    @Autowired
    private CaseCreation creation;
    @Autowired
    private CaseReading tenant;
    @Autowired
    private AccountCaseReading staff;
    @Autowired
    private StaffCaseListing listing;
    @Autowired
    private CaseWorkflowStore workflow;
    @Autowired
    private CaseWorkflowService initial;
    @Autowired
    private CaseDataService service;
    @Autowired
    private CaseDataStore store;
    @Autowired
    private Availability availability;
    @Autowired
    private JdbcTemplate database;
    @Autowired
    private WebApplicationContext web;

    private CaseAccepted accepted;
    private WebAccountContext actor;
    private MockMvc browser;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("PROPERTYFLOW_TEST_DATABASE_URL"));
        properties.add("spring.datasource.username", () -> "propertyflow_test");
        properties.add("spring.datasource.password", () -> "propertyflow_test");
        properties.add("propertyflow.links.encryption-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
    }

    @BeforeEach
    void setup() {
        assertEquals("propertyflow_test", database.queryForObject("SELECT current_database()", String.class));
        database.execute("TRUNCATE pf_case CASCADE");
        actor = new WebAccountContext(UUID.randomUUID());
        database.update("""
                INSERT INTO pf_staff_account (id, name, email, active, staff_role, account_administration)
                VALUES (?, 'Mitarbeiter', ?, TRUE, TRUE, FALSE)
                """,
                actor.accountId(), actor.accountId() + "@example.test");
        accepted = creation.create(new SubmissionContext.Guest(UUID.randomUUID()),
                new CreateCaseCommand("memo", "Heizung", "Wohnung", "Kalt", "tenant@example.test"));
        StartOrder order = workflow.claim(100, "<default>", Duration.ofMinutes(2)).orElseThrow();
        initial.register(accepted.caseId(), order.id(), 200, 100, "<default>");
        Entry entry = initial.prepareInitial(accepted.caseId(), 200, 300);
        initial.registerAutomatic(accepted.caseId(), entry.id(), entry.callRef(), 200, 400, 350);
        initial.startClarification(accepted.caseId(), entry.id(), entry.callRef(), 200, 400);
        availability.active = true;
        availability.correlationFails = false;
        availability.correlationRejected = false;
        availability.deliveries = 0;
        availability.onDeliver = null;
        browser = MockMvcBuilders.webAppContextSetup(web).build();
    }

    @Test
    void memoIsStaffOnlySearchableAndIdempotentEvenAfterClosure() throws Exception {
        UUID operation = UUID.randomUUID();
        assertEquals("PENDING", service.submit(actor, accepted.caseId(), operation, 1, "Interne Offertenprüfung <script>").state());
        assertTrue(staff.readForStaff(actor, accepted.caseId()).internalMemos().isEmpty());
        UUID command = store.find(accepted.caseId(), actor.accountId(), operation).orElseThrow().id();
        service.apply(command, 200, "<default>");
        service.apply(command, 200, "<default>");
        assertEquals(1, staff.readForStaff(actor, accepted.caseId()).internalMemos().size());
        assertEquals(1, tenant.readByToken(accepted.personalAccessToken()).messages().size());
        assertEquals(1, listing.listForStaff(actor, new StaffCaseQuery("offertenprüfung", "", false, "default", false, 1)).total());
        assertEquals(1, database.queryForObject("SELECT COUNT(*) FROM pf_case_mail_order", Integer.class));
        assertEquals("UNDER_CLARIFICATION", database.queryForObject("SELECT status FROM pf_case", String.class));
        assertEquals(database.queryForObject("SELECT received_at FROM pf_case", java.sql.Timestamp.class),
                database.queryForObject("SELECT last_tenant_contact_at FROM pf_case", java.sql.Timestamp.class));
        browser.perform(signed(get("/mitarbeiter/faelle/" + accepted.caseId())))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Interne Offertenprüfung &lt;script&gt;")))
                .andExpect(content().string(not(containsString("Interne Offertenprüfung <script>"))));
        database.update("UPDATE pf_case SET status = 'CLOSED'");
        availability.active = false;
        assertEquals("APPLIED", service.submit(actor, accepted.caseId(), operation, 1, "Interne Offertenprüfung <script>").state());
        assertEquals("APPLIED", service.submit(actor, accepted.caseId(), operation, 2, "Interne Offertenprüfung <script>").state());
        assertThrows(CaseActionException.class, () -> service.submit(actor, accepted.caseId(), operation, 1, "Anderer Text"));
    }

    @Test
    void revokedRoleAndChangedCaseRejectPendingEffect() {
        UUID first = UUID.randomUUID();
        service.submit(actor, accepted.caseId(), first, 1, "Memo");
        database.update("UPDATE pf_staff_account SET staff_role = FALSE WHERE id = ?", actor.accountId());
        service.apply(store.find(accepted.caseId(), actor.accountId(), first).orElseThrow().id(), 200, "<default>");
        assertEquals("FORBIDDEN", store.find(accepted.caseId(), actor.accountId(), first).orElseThrow().code());
        assertThrows(CaseActionException.class, () -> service.read(actor, accepted.caseId(), first));
        database.update("UPDATE pf_staff_account SET staff_role = TRUE WHERE id = ?", actor.accountId());
        UUID second = UUID.randomUUID();
        service.submit(actor, accepted.caseId(), second, 1, "Zweites Memo");
        database.update("UPDATE pf_case SET version = version + 1");
        service.apply(store.find(accepted.caseId(), actor.accountId(), second).orElseThrow().id(), 200, "<default>");
        assertEquals("CASE_CHANGED", service.read(actor, accepted.caseId(), second).code());
        assertEquals(0, database.queryForObject("SELECT COUNT(*) FROM pf_internal_memo", Integer.class));
    }

    @Test
    void unavailableCamundaAndClosedCaseDoNotAcceptNewActions() {
        availability.active = false;
        assertFalse(service.available(actor, accepted.caseId()));
        assertEquals("WORKFLOW_UNAVAILABLE", assertThrows(CaseActionException.class,
                () -> service.submit(actor, accepted.caseId(), UUID.randomUUID(), 1, "Memo")).code());
        availability.active = true;
        database.update("UPDATE pf_case SET status = 'CLOSED'");
        assertFalse(service.available(actor, accepted.caseId()));
        assertThrows(CaseActionException.class, () -> service.submit(actor, accepted.caseId(), UUID.randomUUID(), 1, "Memo"));
        assertEquals(0, database.queryForObject("SELECT COUNT(*) FROM pf_case_data_command", Integer.class));
    }

    @Test
    void concurrentSameOperationHasOneCommandAndOneEffect() throws Exception {
        UUID operation = UUID.randomUUID();
        try (ExecutorService threads = Executors.newFixedThreadPool(2)) {
            Future<StaffCaseDataCommands.CaseDataResult> first = threads.submit(() -> service.submit(actor, accepted.caseId(), operation, 1, "Memo"));
            Future<StaffCaseDataCommands.CaseDataResult> second = threads.submit(() -> service.submit(actor, accepted.caseId(), operation, 1, "Memo"));
            assertEquals(first.get(), second.get());
            UUID id = store.find(accepted.caseId(), actor.accountId(), operation).orElseThrow().id();
            Future<?> a = threads.submit(() -> service.apply(id, 200, "<default>"));
            Future<?> b = threads.submit(() -> service.apply(id, 200, "<default>"));
            a.get();
            b.get();
        }
        assertEquals(1, database.queryForObject("SELECT COUNT(*) FROM pf_internal_memo", Integer.class));
    }

    @Test
    void httpRequiresCookieSessionCsrfAndServerOwnedIdentity() throws Exception {
        String path = "/mitarbeiter/faelle/" + accepted.caseId() + "/memos";
        browser.perform(post(path)).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control", "no-store"));
        browser.perform(signed(post(path)).param("csrf", "wrong")).andExpect(status().isForbidden());
        UUID operation = UUID.randomUUID();
        browser.perform(signed(post(path)).param("csrf", "csrf-test").param("operationId", operation.toString())
                .param("version", "1").param("text", "Memo").param("actorId", UUID.randomUUID().toString()).param("processInstanceKey", "999"))
                .andExpect(status().isAccepted()).andExpect(content().string(containsString("PENDING")));
        assertEquals(200, store.find(accepted.caseId(), actor.accountId(), operation).orElseThrow().leader());
        browser.perform(signed(get(path + "/auftraege/" + UUID.randomUUID()))).andExpect(status().isNotFound());
        browser.perform(signed(post(path)).param("csrf", "csrf-test").param("operationId", operation.toString())
                .param("version", "1").param("text", "Changed"))
                .andExpect(status().isConflict()).andExpect(content().string(containsString("IDEMPOTENCY_CONFLICT")));
    }

    @Test
    void manualUrgencyPublishesOnlyApprovedHistoryAndExactlyOneMail() throws Exception {
        UUID operation = UUID.randomUUID();
        service.submitUrgency(actor, accepted.caseId(), operation, 1, Urgency.CRITICAL, "Interne Kostenabklärung");
        UUID id = store.find(accepted.caseId(), actor.accountId(), operation).orElseThrow().id();
        service.apply(id, 200, "<default>");
        service.apply(id, 200, "<default>");
        assertEquals(Urgency.CRITICAL, tenant.readByToken(accepted.personalAccessToken()).state().urgency().orElseThrow());
        assertEquals(UrgencySource.STAFF, tenant.readByToken(accepted.personalAccessToken()).urgencySource().orElseThrow());
        assertEquals(1, tenant.readByToken(accepted.personalAccessToken()).urgencyHistory().size());
        assertEquals(1, database.queryForObject("SELECT COUNT(*) FROM pf_urgency_mail_order", Integer.class));
        assertEquals(1, database.queryForObject("SELECT COUNT(*) FROM pf_case_review_signal WHERE reason = 'CRITICAL_URGENCY'", Integer.class));
        assertEquals("UNDER_CLARIFICATION", database.queryForObject("SELECT status FROM pf_case", String.class));
        assertTrue(staff.readForStaff(actor, accepted.caseId()).reviewReasons().stream()
                .anyMatch(reason -> !reason.automatic() && reason.text().contains("Kritische")));
        assertEquals(1, listing.listForStaff(actor, new StaffCaseQuery("", "Kritisch", false, "urgency", false, 1)).total());
        assertEquals(0, listing.listForStaff(actor, new StaffCaseQuery("", "Noch nicht bewertet", false, "default", false, 1)).total());
        browser.perform(get("/mieter/fall/zugang/" + accepted.personalAccessToken()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Kritisch")))
                .andExpect(content().string(not(containsString("Interne Kostenabklärung"))));
    }

    @Test
    void unchangedManualUrgencyKeepsVersionAndDiscardsReasonButProtectsRetryIdentity() {
        setUrgency(Urgency.HIGH, 1, "Erste Begründung");
        UUID noop = UUID.randomUUID();
        service.submitUrgency(actor, accepted.caseId(), noop, 2, Urgency.HIGH, "Nicht zusätzlich speichern");
        UUID command = store.find(accepted.caseId(), actor.accountId(), noop).orElseThrow().id();
        service.apply(command, 200, "<default>");
        assertEquals(2L, service.read(actor, accepted.caseId(), noop).version());
        assertEquals(1, database.queryForObject("SELECT COUNT(*) FROM pf_urgency_event", Integer.class));
        assertEquals(1, database.queryForObject("SELECT COUNT(*) FROM pf_urgency_mail_order", Integer.class));
        assertEquals(null, store.get(command).reason());
        assertEquals("APPLIED", service.submitUrgency(actor, accepted.caseId(), noop, 2, Urgency.HIGH, "Nicht zusätzlich speichern").state());
        assertThrows(CaseActionException.class, () -> service.submitUrgency(actor, accepted.caseId(), noop, 2, Urgency.HIGH, "Andere Begründung"));
    }

    @Test
    void firstManualConfirmationOfSystemLevelChangesSourceWithoutPublicEventOrMail() {
        database.update("UPDATE pf_case SET urgency = 'NORMAL', urgency_source = 'SYSTEM'");
        setUrgency(Urgency.NORMAL, 1, "Manuell bestätigt");
        assertEquals(UrgencySource.STAFF, tenant.readByToken(accepted.personalAccessToken()).urgencySource().orElseThrow());
        assertEquals(2, database.queryForObject("SELECT version FROM pf_case", Long.class));
        assertEquals(0, tenant.readByToken(accepted.personalAccessToken()).urgencyHistory().size());
        assertEquals(0, database.queryForObject("SELECT COUNT(*) FROM pf_urgency_mail_order", Integer.class));
        assertEquals("Manuell bestätigt", database.queryForObject("SELECT internal_reason FROM pf_urgency_event", String.class));
    }

    @Test
    void urgencyConflictAndLateClosureRejectWithoutAuditOrMail() {
        UUID operation = UUID.randomUUID();
        service.submitUrgency(actor, accepted.caseId(), operation, 1, Urgency.LOW, "Begründung");
        database.update("UPDATE pf_case SET status = 'CLOSED', version = version + 1");
        service.apply(store.find(accepted.caseId(), actor.accountId(), operation).orElseThrow().id(), 200, "<default>");
        assertEquals("REJECTED", service.read(actor, accepted.caseId(), operation).state());
        assertEquals(0, database.queryForObject("SELECT COUNT(*) FROM pf_urgency_event", Integer.class));
        assertEquals(0, database.queryForObject("SELECT COUNT(*) FROM pf_urgency_mail_order", Integer.class));
        assertTrue(tenant.readByToken(accepted.personalAccessToken()).state().urgency().isEmpty());
    }

    @Test
    void unknownCorrelationDoesNotConfirmActionAndRetryUsesSameDurableCommand() {
        UUID operation = UUID.randomUUID();
        availability.correlationFails = true;
        assertEquals("WORKFLOW_UNAVAILABLE", assertThrows(CaseActionException.class,
                () -> service.submit(actor, accepted.caseId(), operation, 1, "Memo")).code());
        UUID id = store.find(accepted.caseId(), actor.accountId(), operation).orElseThrow().id();
        assertEquals("PENDING", service.read(actor, accepted.caseId(), operation).state());
        assertEquals(0, database.queryForObject("SELECT COUNT(*) FROM pf_internal_memo", Integer.class));
        availability.correlationFails = false;
        assertEquals("PENDING", service.submit(actor, accepted.caseId(), operation, 1, "Memo").state());
        assertEquals(id, store.find(accepted.caseId(), actor.accountId(), operation).orElseThrow().id());
        service.apply(id, 200, "<default>");
        availability.correlationFails = true;
        assertEquals("APPLIED", service.submit(actor, accepted.caseId(), operation, 1, "Memo").state());
    }

    @Test
    void assignmentKeepsOriginalAndAccessWhileUpdatingListSearchAndAnalysisBasis() throws Exception {
        UUID operation = UUID.randomUUID();
        service.submitAssignment(actor, accepted.caseId(), operation, 1, "  Lindenhof  12 / Wohnung 3  ");
        assertTrue(tenant.readByToken(accepted.personalAccessToken()).assignedUnit().isEmpty());
        service.apply(store.find(accepted.caseId(), actor.accountId(), operation).orElseThrow().id(), 200, "<default>");
        assertEquals("Lindenhof  12 / Wohnung 3", tenant.readByToken(accepted.personalAccessToken()).assignedUnit().orElseThrow());
        assertEquals("Wohnung", tenant.readByToken(accepted.personalAccessToken()).originalUnit());
        assertEquals("tenant@example.test", staff.readForStaff(actor, accepted.caseId()).contactEmail());
        assertEquals(1, listing.listForStaff(actor, new StaffCaseQuery("Lindenhof", "", false, "unit", false, 1)).total());
        assertEquals("Lindenhof  12 / Wohnung 3", listing.listForStaff(actor,
                new StaffCaseQuery("", "", false, "unit", false, 1)).rows().getFirst().unit());
        assertEquals(1, database.queryForObject("SELECT analysis_version FROM pf_case", Long.class));
        assertEquals(1, database.queryForObject("SELECT COUNT(*) FROM pf_assignment_event", Integer.class));
        assertEquals(1, database.queryForObject("SELECT COUNT(*) FROM pf_case_mail_order", Integer.class));
        assertEquals(database.queryForObject("SELECT received_at FROM pf_case", java.sql.Timestamp.class),
                database.queryForObject("SELECT last_tenant_contact_at FROM pf_case", java.sql.Timestamp.class));
        assertEquals("APPLIED", service.submitAssignment(actor, accepted.caseId(), operation, 1, "Lindenhof  12 / Wohnung 3").state());
        browser.perform(signed(get("/mitarbeiter/faelle/" + accepted.caseId() + "/zuordnung/vorschlaege").param("q", "linden")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Lindenhof  12 / Wohnung 3")));
        browser.perform(get("/mitarbeiter/faelle/" + accepted.caseId() + "/zuordnung/vorschlaege"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void assignmentConfirmationOfOriginalIsExplicitAndCorrectionDoesNotFinishReview() {
        UUID first = UUID.randomUUID();
        service.submitAssignment(actor, accepted.caseId(), first, 1, "Wohnung");
        service.apply(store.find(accepted.caseId(), actor.accountId(), first).orElseThrow().id(), 200, "<default>");
        database.update("UPDATE pf_case SET status = 'STAFF_REVIEW_REQUIRED'");
        UUID second = UUID.randomUUID();
        service.submitAssignment(actor, accepted.caseId(), second, 2, "Andere Wohnung");
        service.apply(store.find(accepted.caseId(), actor.accountId(), second).orElseThrow().id(), 200, "<default>");
        assertEquals("STAFF_REVIEW_REQUIRED", database.queryForObject("SELECT status FROM pf_case", String.class));
        assertEquals("Wohnung", database.queryForObject("SELECT previous_assignment FROM pf_assignment_event WHERE id = ?", String.class,
                store.find(accepted.caseId(), actor.accountId(), second).orElseThrow().id()));
        UUID same = UUID.randomUUID();
        service.submitAssignment(actor, accepted.caseId(), same, 3, "Andere Wohnung");
        service.apply(store.find(accepted.caseId(), actor.accountId(), same).orElseThrow().id(), 200, "<default>");
        assertEquals(3L, service.read(actor, accepted.caseId(), same).version());
        assertEquals(2, database.queryForObject("SELECT COUNT(*) FROM pf_assignment_event", Integer.class));
    }

    @Test
    void invalidAssignmentStructuredReferenceAndVersionConflictCannotChangeStoredValue() throws Exception {
        for (String invalid : new String[] {"   ", "x".repeat(201)}) {
            assertThrows(CaseActionException.class, () -> service.submitAssignment(actor, accepted.caseId(), UUID.randomUUID(), 1, invalid));
        }
        String url = "/mitarbeiter/faelle/" + accepted.caseId() + "/zuordnung";
        browser.perform(signed(post(url).param("operationId", UUID.randomUUID().toString()).param("version", "1")
                        .param("assignment", "Wohnung").param("unitId", "unverified-master-id").param("csrf", "csrf-test")))
                .andExpect(status().isBadRequest());
        UUID stale = UUID.randomUUID();
        service.submitAssignment(actor, accepted.caseId(), stale, 1, "Zuordnung");
        database.update("UPDATE pf_case SET version = version + 1");
        service.apply(store.find(accepted.caseId(), actor.accountId(), stale).orElseThrow().id(), 200, "<default>");
        assertEquals("CASE_CHANGED", service.read(actor, accepted.caseId(), stale).code());
        assertEquals(0, database.queryForObject("SELECT COUNT(*) FROM pf_assignment_event", Integer.class));
        assertTrue(tenant.readByToken(accepted.personalAccessToken()).assignedUnit().isEmpty());
    }

    @Test
    void canonicalUrgencyUsesOpaqueOperationAndAcknowledgesCorrelationWithoutRepeatingIt() throws Exception {
        String path = "/mitarbeiter/faelle/" + accepted.caseId() + "/dringlichkeit-aendern";
        String operation = "Dringlichkeit / bestätigter Auftrag";
        browser.perform(signed(post(path)).param("csrf", "csrf-test").param("operation", operation)
                        .param("version", "1").param("urgency", "Hoch").param("reason", "Nur intern"))
                .andExpect(status().isOk()).andExpect(content().string(""));
        assertEquals(1, availability.deliveries);
        assertEquals("PENDING", service.read(actor, accepted.caseId(), operation).state());
        availability.correlationFails = true;
        browser.perform(signed(post(path)).param("csrf", "csrf-test").param("operation", operation)
                        .param("version", "1").param("urgency", "Hoch").param("reason", "Nur intern"))
                .andExpect(status().isOk()).andExpect(content().string(""));
        assertEquals(1, availability.deliveries);
        browser.perform(signed(post(path)).param("csrf", "csrf-test").param("operation", operation)
                        .param("version", "1").param("urgency", "Normal").param("reason", "Nur intern"))
                .andExpect(status().isBadRequest());
        service.apply(store.find(accepted.caseId(), actor.accountId(), operation).orElseThrow().id(), 200, "<default>");
        assertEquals("HIGH", database.queryForObject("SELECT urgency FROM pf_case", String.class));
    }

    @Test
    void canonicalMemoOnlyConfirmsDurableStorageAndRetainsIdentityAfterUnknownOutcome() throws Exception {
        String path = "/mitarbeiter/faelle/" + accepted.caseId() + "/internes-memo-speichern";
        String operation = "Memo / Absende-ID";
        browser.perform(signed(post(path)).param("csrf", "csrf-test").param("operation", operation).param("text", "Interner Text"))
                .andExpect(status().isInternalServerError());
        assertEquals(0, database.queryForObject("SELECT COUNT(*) FROM pf_internal_memo", Integer.class));
        assertEquals(1, availability.deliveries);
        service.apply(store.find(accepted.caseId(), actor.accountId(), operation).orElseThrow().id(), 200, "<default>");
        database.update("UPDATE pf_case SET status = 'CLOSED'");
        availability.active = false;
        browser.perform(signed(post(path)).param("csrf", "csrf-test").param("operation", operation).param("text", "Interner Text"))
                .andExpect(status().isOk()).andExpect(content().string(""));
        assertEquals(1, availability.deliveries);
        assertEquals(1, database.queryForObject("SELECT COUNT(*) FROM pf_internal_memo", Integer.class));
        browser.perform(signed(post(path)).param("csrf", "csrf-test").param("operation", operation).param("text", "Geändert"))
                .andExpect(status().isConflict());
    }

    @Test
    void canonicalMemoConfirmsWorkerCommitAndRejectsClientOwnedInternalMetadata() throws Exception {
        availability.onDeliver = id -> service.apply(id, 200, "<default>");
        String path = "/mitarbeiter/faelle/" + accepted.caseId() + "/internes-memo-speichern";
        browser.perform(signed(post(path)).param("csrf", "csrf-test").param("operation", "Memo-1").param("text", "Genauer Text "))
                .andExpect(status().isOk()).andExpect(content().string(""));
        assertEquals("Genauer Text ", database.queryForObject("SELECT text FROM pf_internal_memo", String.class));
        browser.perform(signed(post(path)).param("csrf", "csrf-test").param("operation", "Memo-2")
                        .param("text", "Memo").param("visibility", "EXTERNAL"))
                .andExpect(status().isBadRequest());
        assertEquals(1, database.queryForObject("SELECT COUNT(*) FROM pf_internal_memo", Integer.class));
    }

    @Test
    void canonicalAssignmentValidatesVersionReferenceAndUtf16OperationLimit() throws Exception {
        String path = "/mitarbeiter/faelle/" + accepted.caseId() + "/objekt-wohnung-zuordnen";
        browser.perform(signed(post(path)).param("csrf", "csrf-test").param("operation", "x".repeat(79) + "😀")
                        .param("version", "1").param("assignment", "Wohnung 2"))
                .andExpect(status().isBadRequest());
        browser.perform(signed(post(path)).param("csrf", "csrf-test").param("operation", "Zuordnung")
                        .param("version", "0").param("assignment", "Wohnung 2"))
                .andExpect(status().isBadRequest());
        browser.perform(signed(post(path)).param("csrf", "csrf-test").param("operation", "Zuordnung")
                        .param("version", "1").param("propertyId", "Objekt").param("unitId", "Fremde Wohnung"))
                .andExpect(status().isBadRequest());
        browser.perform(signed(post(path)).param("csrf", "csrf-test").param("operation", "Zuordnung")
                        .param("version", "1").param("assignment", "  Haus 2 / Wohnung 3  "))
                .andExpect(status().isOk()).andExpect(content().string(""));
        assertEquals("Haus 2 / Wohnung 3", store.find(accepted.caseId(), actor.accountId(), "Zuordnung").orElseThrow().text());
    }

    @Test
    void definitivelyRejectedCorrelationReleasesOperationWithoutRevivingOldWorker() throws Exception {
        String path = "/mitarbeiter/faelle/" + accepted.caseId() + "/dringlichkeit-aendern";
        availability.correlationRejected = true;
        browser.perform(signed(post(path)).param("csrf", "csrf-test").param("operation", "Korrigierbarer Auftrag")
                        .param("version", "1").param("urgency", "Hoch"))
                .andExpect(status().isBadRequest());
        UUID rejected = store.latest(accepted.caseId(), "Korrigierbarer Auftrag").orElseThrow().id();
        assertTrue(store.find(accepted.caseId(), actor.accountId(), "Korrigierbarer Auftrag").isEmpty());
        availability.correlationRejected = false;
        browser.perform(signed(post(path)).param("csrf", "csrf-test").param("operation", "Korrigierbarer Auftrag")
                        .param("version", "1").param("urgency", "Normal"))
                .andExpect(status().isOk());
        service.apply(rejected, 200, "<default>");
        assertTrue(tenant.readByToken(accepted.personalAccessToken()).state().urgency().isEmpty());
        service.apply(store.find(accepted.caseId(), actor.accountId(), "Korrigierbarer Auftrag").orElseThrow().id(), 200, "<default>");
        assertEquals("NORMAL", database.queryForObject("SELECT urgency FROM pf_case", String.class));
        assertEquals(1, database.queryForObject("SELECT COUNT(*) FROM pf_urgency_event", Integer.class));
    }

    private void setUrgency(Urgency urgency, long version, String reason) {
        UUID operation = UUID.randomUUID();
        service.submitUrgency(actor, accepted.caseId(), operation, version, urgency, reason);
        service.apply(store.find(accepted.caseId(), actor.accountId(), operation).orElseThrow().id(), 200, "<default>");
    }

    private MockHttpServletRequestBuilder signed(MockHttpServletRequestBuilder request) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(StaffWebSession.ACCOUNT, actor.accountId());
        session.setAttribute(StaffWebSession.CSRF, "csrf-test");
        return request.session(session).with(servlet -> {
            servlet.setRequestedSessionId(session.getId());
            servlet.setRequestedSessionIdFromCookie(true);
            return servlet;
        });
    }

    static class Availability implements CaseProcessGateway {
        private volatile boolean active;
        private volatile boolean correlationFails;
        private volatile boolean correlationRejected;
        private volatile int deliveries;
        private Consumer<UUID> onDeliver;

        @Override
        public boolean active(long key, String tenant) {
            return active;
        }

        @Override
        public void deliverCaseData(UUID command, long key, String tenant) {
            deliveries++;
            if (correlationRejected) {
                throw new CaseActionException("CASE_NOT_ACTIVE");
            }
            if (correlationFails || !active) {
                throw new IllegalStateException("TEST_CORRELATION_UNAVAILABLE");
            }
            if (onDeliver != null) {
                onDeliver.accept(command);
            }
        }
    }

    @TestConfiguration
    static class AvailabilityConfiguration {
        @Bean
        @Primary
        Availability availability() {
            return new Availability();
        }
    }
}
