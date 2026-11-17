package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.access.contract.SubmissionContext;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.caseprocessing.contract.AccountCaseReading;
import com.pertegato.propertyflow.caseprocessing.contract.CaseAccepted;
import com.pertegato.propertyflow.caseprocessing.contract.CaseActionException;
import com.pertegato.propertyflow.caseprocessing.contract.CaseCreation;
import com.pertegato.propertyflow.caseprocessing.contract.CaseReading;
import com.pertegato.propertyflow.caseprocessing.contract.CreateCaseCommand;
import com.pertegato.propertyflow.caseprocessing.contract.StaffCaseListing;
import com.pertegato.propertyflow.caseprocessing.contract.StaffCaseQuery;
import com.pertegato.propertyflow.caseprocessing.delivery.CaseMailSender;
import com.pertegato.propertyflow.caseprocessing.delivery.CaseProcessGateway;
import com.pertegato.propertyflow.caseprocessing.delivery.TenantMessageGateway;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseAnalysisStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.Entry;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.StartOrder;
import com.pertegato.propertyflow.caseprocessing.persistence.MailDeliveryStore;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantMessageStore;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
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
@Import(TenantMessagePostgresTest.AvailabilityConfiguration.class)
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_DATABASE_URL", matches = ".+")
class TenantMessagePostgresTest {

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
    private TenantMessageService service;

    @Autowired
    private TenantMessageStore messages;

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
        availability.delivered.clear();
        browser = MockMvcBuilders.webAppContextSetup(web).build();
    }

    @Autowired
    private CaseAnalysisStore analyses;

    @Autowired
    private MailDeliveryStore mail;

    @Autowired
    private CaseMailService mailService;

    @Autowired
    private MailCapture sender;

    @Test
    void messageMailSurvivesClosureAndContainsNoConversationText() {
        UUID id = service.send(accepted.personalAccessToken(), UUID.randomUUID(), "Vertraulicher Nachrichtentext");
        database.update("UPDATE pf_case SET status = 'CLOSED'");
        mail.discoverOrders();
        database.update("UPDATE pf_mail_delivery SET definition_key = 1000, tenant_id = '<default>' WHERE id = ?", id);
        assertEquals("MESSAGE", mailService.prepare(id, 900, 1000, "<default>"));
        assertEquals(CaseMailService.MailOutcome.SENT, mailService.send(id, 900));
        assertEquals(CaseMailService.MailOutcome.SENT, mailService.send(id, 900));
        assertEquals("Ihre Nachricht wurde im Fallverlauf gespeichert.", sender.sent.information());
        assertFalse(sender.sent.toString().contains("Vertraulicher Nachrichtentext"));
        assertEquals("SENT", database.queryForObject("SELECT state FROM pf_message_mail_order WHERE id = ?", String.class, id));
    }

    @Test
    void acceptedMessagePublishesOnceAndInvalidatesOldAnalysisWithoutChangingPhase() {
        UUID operation = UUID.randomUUID();
        UUID id = service.send(accepted.personalAccessToken(), operation, "Neue Ergänzung <script>");
        assertEquals(id, service.send(accepted.personalAccessToken(), operation, "Neue Ergänzung <script>"));
        assertEquals(2, tenant.readByToken(accepted.personalAccessToken()).messages().size());
        assertEquals("Neue Ergänzung <script>", tenant.readByToken(accepted.personalAccessToken()).messages().getLast().text());
        assertEquals(2, staff.readForStaff(actor, accepted.caseId()).publicView().messages().size());
        assertEquals(1, listing.listForStaff(actor, new StaffCaseQuery("Ergänzung", "", false, "default", false, 1)).total());
        assertEquals(1L, database.queryForObject("SELECT analysis_version FROM pf_case", Long.class));
        assertEquals(2L, database.queryForObject("SELECT version FROM pf_case", Long.class));
        assertEquals("UNDER_CLARIFICATION", database.queryForObject("SELECT status FROM pf_case", String.class));
        assertTrue(analyses.input(accepted.caseId()).text().contains("Neue Ergänzung <script>"));
        assertEquals(id, analyses.input(accepted.caseId()).messageId());
        assertEquals(1, count("pf_message_mail_order"));
        mail.discoverOrders();
        mail.discoverOrders();
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_mail_delivery WHERE notification_type = 'TENANT_MESSAGE'", Integer.class));
        assertThrows(CaseActionException.class, () -> service.send(accepted.personalAccessToken(), operation, "Changed"));
        database.update("UPDATE pf_case SET status = 'CLOSED'");
        assertEquals(id, service.send(accepted.personalAccessToken(), operation, "Neue Ergänzung <script>"));
        assertThrows(CaseActionException.class, () -> service.send(accepted.personalAccessToken(), UUID.randomUUID(), "Zu spät"));
        assertEquals(1, count("pf_tenant_message"));
    }

    @Test
    void unavailableEngineDoesNotSaveButLostCorrelationRetainsSameMessageAndOrder() {
        UUID operation = UUID.randomUUID();
        availability.active = false;
        assertThrows(IllegalStateException.class, () -> service.send(accepted.personalAccessToken(), operation, "Ergänzung"));
        assertEquals(0, count("pf_tenant_message"));
        availability.active = true;
        availability.correlationFails = true;
        assertThrows(IllegalStateException.class, () -> service.send(accepted.personalAccessToken(), operation, "Ergänzung"));
        assertEquals(1, count("pf_tenant_message"));
        assertEquals(2, tenant.readByToken(accepted.personalAccessToken()).messages().size());
        UUID original = availability.delivered.getFirst();
        availability.correlationFails = false;
        assertEquals(original, service.send(accepted.personalAccessToken(), operation, "Ergänzung"));
        assertEquals(List.of(original, original), availability.delivered);
        assertEquals(1, count("pf_message_mail_order"));
    }

    @Test
    void receptionClaimsOneActualChildAndKeepsNewerMessagesIndependent() {
        UUID first = service.send(accepted.personalAccessToken(), UUID.randomUUID(), "Erste Ergänzung");
        UUID second = service.send(accepted.personalAccessToken(), UUID.randomUUID(), "Zweite Ergänzung");
        assertTrue(service.receive(accepted.caseId(), first, 200, 800, 750, "<default>"));
        assertTrue(service.receive(accepted.caseId(), first, 200, 800, 750, "<default>"));
        assertFalse(service.receive(accepted.caseId(), first, 200, 801, 751, "<default>"));
        assertTrue(service.receive(accepted.caseId(), second, 200, 802, 752, "<default>"));
        assertEquals(800L, messages.get(first).child());
        assertEquals(802L, messages.get(second).child());
        assertThrows(WorkflowRejectedException.class, () -> service.receive(accepted.caseId(), first, 201, 803, 753, "<default>"));
        database.update("UPDATE pf_case SET status = 'CLOSED'");
        assertFalse(service.receive(accepted.caseId(), second, 200, 802, 752, "<default>"));
        assertEquals(2, count("pf_message_mail_order"));
    }

    @Test
    void textLimitUsesNormalizedUtf16AndRevokedAccessCannotReplay() {
        String limit = "a".repeat(1998) + "\r\n" + "b";
        UUID operation = UUID.randomUUID();
        service.send(accepted.personalAccessToken(), operation, limit);
        assertEquals(2000, tenant.readByToken(accepted.personalAccessToken()).messages().getLast().text().length());
        assertThrows(CaseActionException.class, () -> service.send(accepted.personalAccessToken(), UUID.randomUUID(), "a".repeat(1999) + "😀"));
        assertEquals(1, count("pf_tenant_message"));
        database.update("UPDATE pf_case_access SET revoked_at = CURRENT_TIMESTAMP");
        assertThrows(CaseActionException.class, () -> service.send(accepted.personalAccessToken(), operation, limit));
    }

    @Test
    void concurrentSameOperationHasOneMessageAndOneMail() throws Exception {
        UUID operation = UUID.randomUUID();
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<UUID> first = executor.submit(() -> service.send(accepted.personalAccessToken(), operation, "Parallel"));
            Future<UUID> second = executor.submit(() -> service.send(accepted.personalAccessToken(), operation, "Parallel"));
            assertEquals(first.get(), second.get());
        }
        assertEquals(1, count("pf_tenant_message"));
        assertEquals(1, count("pf_message_mail_order"));
    }

    @Test
    void httpRequiresCookieCsrfAndRejectsVisibilityInjectionWhileRenderingTextSafely() throws Exception {
        String path = "/mieter/fall/zugang/" + accepted.personalAccessToken();
        MvcResult page = browser.perform(get(path)).andReturn();
        MockHttpSession session = (MockHttpSession) page.getRequest().getSession();
        String csrf = (String) session.getAttribute("propertyflow.tenant-message.csrf");
        String operation = "tenant-operation-one";
        browser.perform(post(path + "/nachrichten").param("operation", operation).param("text", "Test").param("csrf", csrf))
                .andExpect(status().isForbidden());
        browser.perform(signed(post(path + "/nachrichten"), session).param("operation", operation).param("text", "Test")
                        .param("csrf", csrf).param("visibility", "INTERNAL"))
                .andExpect(status().isBadRequest());
        browser.perform(signed(post(path + "/nachrichten"), session).param("operation", operation).param("text", "<script>tenant</script>")
                        .param("csrf", csrf)).andExpect(status().isSeeOther())
                .andExpect(header().string("Location", path));
        browser.perform(get(path)).andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;tenant&lt;/script&gt;")))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
        assertEquals(1, count("pf_tenant_message"));
        availability.correlationFails = true;
        browser.perform(signed(post(path + "/nachrichten"), session).param("operation", "lost-confirmation")
                        .param("text", "<script>Erhaltene Eingabe</script>").param("csrf", csrf))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(containsString("&lt;script&gt;Erhaltene Eingabe&lt;/script&gt;")))
                .andExpect(content().string(containsString("lost-confirmation")));
        assertEquals(2, count("pf_tenant_message"));
    }

    private MockHttpServletRequestBuilder signed(MockHttpServletRequestBuilder request, MockHttpSession session) {
        return request.session(session).with(servlet -> {
            servlet.setRequestedSessionId(session.getId());
            servlet.setRequestedSessionIdFromCookie(true);
            return servlet;
        });
    }

    private int count(String table) {
        return database.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    static class Availability implements CaseProcessGateway, TenantMessageGateway {
        private boolean active;
        private boolean correlationFails;
        private final List<UUID> delivered = new CopyOnWriteArrayList<>();

        @Override
        public boolean active(long instanceKey, String tenantId) {
            return active;
        }

        @Override
        public void deliverCaseData(UUID command, long key, String tenant) {
        }

        @Override
        public void correlate(UUID id, long leader, String tenant) {
            assertEquals(200, leader);
            assertEquals("<default>", tenant);
            delivered.add(id);
            if (correlationFails) {
                throw new IllegalStateException("TEST_CORRELATION_LOST");
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

        @Bean
        @Primary
        MailCapture mailCapture() {
            return new MailCapture();
        }
    }

    static class MailCapture implements CaseMailSender {
        private Mail sent;

        @Override
        public SendResult send(Mail mail) {
            sent = mail;
            return SendResult.ACCEPTED;
        }
    }
}
