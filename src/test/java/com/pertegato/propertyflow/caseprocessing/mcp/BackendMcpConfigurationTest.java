package com.pertegato.propertyflow.caseprocessing.mcp;

import com.pertegato.propertyflow.access.contract.McpAccountContext;
import com.pertegato.propertyflow.access.contract.McpScope;
import com.pertegato.propertyflow.access.contract.McpTenantAuthorization;
import com.pertegato.propertyflow.caseprocessing.contract.CaseStatus;
import com.pertegato.propertyflow.caseprocessing.contract.CaseStatusView;
import com.pertegato.propertyflow.caseprocessing.contract.McpCaseReading;
import com.pertegato.propertyflow.caseprocessing.contract.McpMessageConfirmations;
import com.pertegato.propertyflow.caseprocessing.contract.McpMessageConfirmations.Change;
import com.pertegato.propertyflow.caseprocessing.contract.McpMessageConfirmations.NewCase;
import com.pertegato.propertyflow.caseprocessing.contract.McpMessageConfirmations.Status;
import com.pertegato.propertyflow.caseprocessing.contract.McpMessageConfirmations.Transition;
import com.pertegato.propertyflow.caseprocessing.contract.McpMessageConfirmations.View;
import com.pertegato.propertyflow.caseprocessing.contract.PublicAuthor;
import com.pertegato.propertyflow.caseprocessing.contract.PublicMessage;
import com.pertegato.propertyflow.caseprocessing.contract.StaffCasePage;
import com.pertegato.propertyflow.caseprocessing.contract.StaffCaseSummary;
import com.pertegato.propertyflow.caseprocessing.contract.TenantCaseView;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.json.JsonMapper;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(BackendMcpConfigurationTest.Web.class)
@ActiveProfiles("propertyflow-backend")
@TestPropertySource(properties = "propertyflow.mcp.backend.enabled=true")
class BackendMcpConfigurationTest {

    private static final String PROTOCOL = "2025-11-25";
    private static final String CLIENT_TOKEN = "test-credential";
    private static final String LIST = """
            {"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}
            """;

    @Autowired
    private WebApplicationContext web;

    @Autowired
    private McpTenantAuthorization auth;

    @Autowired
    private McpCaseReading cases;

    @Autowired
    private McpMessageConfirmations confirmations;

    @BeforeEach
    void resetMocks() {
        reset(auth, cases, confirmations);
        when(auth.resourceUri()).thenReturn(URI.create("https://propertyflow.example.test/mcp"));
        when(auth.resourceMetadataUri()).thenReturn(URI.create("https://propertyflow.example.test/.well-known/oauth-protected-resource/mcp"));
    }

    @Test
    void guardsDiscoveryAndWiresAuthenticatedFallstandCall() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(web).build();
        mvc.perform(request(LIST))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(result -> assertTrue(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)
                        .contains("https://propertyflow.example.test/.well-known/oauth-protected-resource/mcp")));
        mvc.perform(request(LIST).header(HttpHeaders.ORIGIN, "https://other.example.test"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(cases);

        McpAccountContext account = new McpAccountContext(UUID.randomUUID(), UUID.randomUUID(), "test-client");
        when(auth.authenticate(CLIENT_TOKEN)).thenReturn(Optional.of(account));
        when(auth.allows(account, McpScope.TENANT_CASES)).thenReturn(true);
        when(cases.readStatusForMcp(account, "case-a"))
                .thenReturn(new CaseStatusView("case-a", CaseStatus.RECEIVED, Optional.empty()));

        mvc.perform(authenticated(LIST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.tools[0].name").value("fallstand_abrufen"));
        mvc.perform(authenticated("""
                {"jsonrpc":"2.0","id":2,"method":"tools/call", "params":{
                  "name":"fallstand_abrufen","arguments":{"caseId":"case-a"}}}
                """))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.result.structuredContent.caseId").value("case-a"))
                .andExpect(jsonPath("$.result.structuredContent.status").value("Eingegangen"))
                .andExpect(jsonPath("$.result.structuredContent.urgency").value("Noch nicht bewertet"))
                .andExpect(jsonPath("$.result.structuredContent.title").doesNotExist());

        when(auth.authenticate(CLIENT_TOKEN)).thenReturn(Optional.empty());
        mvc.perform(authenticated(LIST)).andExpect(status().isUnauthorized());
    }

    @Test
    void discoversModernServerAndListsOnlyToolsForCurrentDelegation() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(web).build();
        McpAccountContext account = new McpAccountContext(UUID.randomUUID(), UUID.randomUUID(), "test-client");
        when(auth.authenticate(CLIENT_TOKEN)).thenReturn(Optional.of(account));
        when(auth.allows(account, McpScope.TENANT_CASES)).thenReturn(true);
        when(auth.allows(account, McpScope.TENANT_MESSAGES)).thenReturn(true);
        CaseStatusView state = new CaseStatusView("case-a", CaseStatus.RECEIVED, Optional.empty());
        when(cases.listForMcp(account)).thenReturn(List.of(new McpCaseReading.TenantCaseSummary(state, "Fenster")));

        mvc.perform(modern("server/discover", null, ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.resultType").value("complete"))
                .andExpect(jsonPath("$.result.supportedVersions[0]").value("2026-07-28"))
                .andExpect(jsonPath("$.result._meta['io.modelcontextprotocol/serverInfo'].name").value("propertyflow"));
        mvc.perform(modern("tools/list", null, ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.tools.length()").value(6))
                .andExpect(jsonPath("$.result.tools[1].name").value("eigene_faelle_auflisten"))
                .andExpect(jsonPath("$.result.tools[*].name", hasItem("fall_kommentar_senden")));
        mvc.perform(modern("tools/call", "eigene_faelle_auflisten", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.structuredContent.items[0].title").value("Fenster"));
        mvc.perform(modern("tools/call", "mitarbeiter_fall_abrufen", """
                ,"arguments":{"caseId":"case-a"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isError").value(true));
        mvc.perform(modern("resources/list", null, ""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(-32601));
    }

    @Test
    void readsStaffCasesAndRequiresBrowserConfirmationForBothWrites() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(web).build();
        McpAccountContext account = new McpAccountContext(UUID.randomUUID(), UUID.randomUUID(), "test-client");
        when(auth.authenticate(CLIENT_TOKEN)).thenReturn(Optional.of(account));
        when(auth.allows(account, McpScope.STAFF_CASES)).thenReturn(true);
        when(auth.allows(account, McpScope.STAFF_MESSAGES)).thenReturn(true);
        when(auth.allows(account, McpScope.TENANT_MESSAGES)).thenReturn(true);
        CaseStatusView state = new CaseStatusView("case-a", CaseStatus.RECEIVED, Optional.empty());
        when(cases.listForMcpStaff(account, McpCaseReading.CaseStateFilter.ACTIVE, null, "", 1))
                .thenReturn(new StaffCasePage(List.of(new StaffCaseSummary(state, "Fenster", "Einheit", Instant.EPOCH, Instant.EPOCH,
                        Optional.empty())), 1, 1, 1));
        when(cases.readForMcpStaff(account, "case-a")).thenReturn(new McpCaseReading.StaffCaseDetail(state, "Fenster", List.of()));
        mvc.perform(modern("tools/call", "mitarbeiter_faelle_auflisten", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.structuredContent.items[0].caseId").value("case-a"))
                .andExpect(jsonPath("$.result.structuredContent.items[0].unit").doesNotExist());
        mvc.perform(modern("tools/call", "mitarbeiter_fall_abrufen", """
                ,"arguments":{"caseId":"case-a"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.structuredContent.caseId").value("case-a"))
                .andExpect(jsonPath("$.result.structuredContent.internalMemos").doesNotExist());

        UUID tenantOperation = UUID.randomUUID();
        UUID staffOperation = UUID.randomUUID();
        when(confirmations.prepare(account, McpScope.TENANT_MESSAGES, "case-a", "Geprüft", false)).thenReturn(tenantOperation);
        when(confirmations.prepare(account, McpScope.STAFF_MESSAGES, "case-a", "Geprüft", true)).thenReturn(staffOperation);
        String tenantArguments = """
                ,"arguments":{"caseId":"case-a","text":"Geprüft"}
                """;
        mvc.perform(modern("tools/call", "fall_kommentar_senden", tenantArguments))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.resultType").value("input_required"))
                .andExpect(jsonPath("$.result.requestState").value(tenantOperation.toString()));
        mvc.perform(modern("tools/call", "mitarbeiter_fallnachricht_veroeffentlichen", """
                ,"arguments":{"caseId":"case-a","text":"Geprüft","kind":"question"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.inputRequests.propertyflow_confirmation.params.url")
                        .value("https://propertyflow.example.test/mitarbeiter/mcp/bestaetigen/" + staffOperation));
        when(confirmations.continuation(account, McpScope.TENANT_MESSAGES, tenantOperation, "case-a", "Geprüft", false, false))
                .thenReturn(new View(tenantOperation, "case-a", "Fenster", "Geprüft", false, Status.WAITING, null));
        mvc.perform(modern("tools/call", "fall_kommentar_senden", tenantArguments + """
                ,"requestState":"%s","inputResponses":{"propertyflow_confirmation":{"action":"accept"}}
                """.formatted(tenantOperation)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.resultType").value("input_required"));
        verify(confirmations).continuation(account, McpScope.TENANT_MESSAGES, tenantOperation, "case-a", "Geprüft", false, false);
    }

    private static MockHttpServletRequestBuilder modern(String method, String name, String params) {
        String nameField = name == null ? "" : ",\"name\":" + JsonMapper.shared().writeValueAsString(name);
        MockHttpServletRequestBuilder request = post("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + CLIENT_TOKEN)
                .header("MCP-Protocol-Version", "2026-07-28")
                .header("Mcp-Method", method)
                .content("""
                        {"jsonrpc":"2.0","id":1,"method":"%s","params":{"_meta":{
                         "io.modelcontextprotocol/protocolVersion":"2026-07-28",
                         "io.modelcontextprotocol/clientCapabilities":{"elicitation":{"url":{}}}
                        }%s%s}}
                        """.formatted(method, nameField, params));
        return name == null ? request : request.header("Mcp-Name", name);
    }

    @Test
    void readsProblemDescriptionAndCommunicationWithTenantAccess() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(web).build();
        McpAccountContext account = new McpAccountContext(UUID.randomUUID(), UUID.randomUUID(), "test-client");
        when(auth.authenticate(CLIENT_TOKEN)).thenReturn(Optional.of(account));
        when(auth.allows(account, McpScope.TENANT_CASES)).thenReturn(true);
        CaseStatusView state = new CaseStatusView("case-a", CaseStatus.RECEIVED, Optional.empty());
        TenantCaseView view = new TenantCaseView(state, "Fenster", "Die Dichtung ist defekt.\nEs zieht stark.",
                "Wohnung 7", Optional.empty(), "t***", Instant.EPOCH, Instant.EPOCH, 1, Optional.empty(),
                List.of(new PublicMessage("1", PublicAuthor.STAFF, "Kontaktiere privat@example.test.", Instant.EPOCH)), List.of());
        when(cases.readPublicForMcp(account, "case-a", false)).thenReturn(view);
        String params = """
                ,"arguments":{"caseId":"case-a"}
                """;
        mvc.perform(modern("tools/call", "falldetails_abrufen", params))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.structuredContent.description").value(view.description()))
                .andExpect(jsonPath("$.result.structuredContent.maskedContact").doesNotExist());
        mvc.perform(modern("tools/call", "fallhistorie_abrufen", params))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.structuredContent.messages[0].authorRole").value("staff"))
                .andExpect(jsonPath("$.result.structuredContent.messages[0].text").value("Kontaktiere [E-Mail-Adresse entfernt]."));
    }

    @Test
    void exposesAndPreparesOnlyDelegatedWriteActions() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(web).build();
        McpAccountContext account = new McpAccountContext(UUID.randomUUID(), UUID.randomUUID(), "test-client");
        when(auth.authenticate(CLIENT_TOKEN)).thenReturn(Optional.of(account));
        when(auth.allows(account, McpScope.TENANT_INTAKE)).thenReturn(true);
        when(auth.allows(account, McpScope.TENANT_MESSAGES)).thenReturn(true);
        NewCase intake = new NewCase("Fenster", "Wohnung 7", "Die Dichtung ist defekt.");
        Transition transition = new Transition("case-a", Change.FORTSETZEN);
        UUID intakeId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        UUID transitionId = UUID.randomUUID();
        when(confirmations.prepareCaseAction(account, intake)).thenReturn(intakeId);
        when(confirmations.prepare(account, McpScope.TENANT_MESSAGES, "case-a", "Geprüft", false)).thenReturn(commentId);
        String intakeParams = """
                ,"arguments":{"title":"Fenster","unit":"Wohnung 7","description":"Die Dichtung ist defekt."}
                """;
        String statusParams = """
                ,"arguments":{"caseId":"case-a","action":"fortsetzen"}
                """;
        mvc.perform(modern("tools/call", "meldung_erstellen", intakeParams))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.resultType").value("input_required"))
                .andExpect(jsonPath("$.result.requestState").value(intakeId.toString()));
        mvc.perform(modern("tools/call", "kommentar_hinzufuegen", """
                ,"arguments":{"caseId":"case-a","text":"Geprüft"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.requestState").value(commentId.toString()));
        mvc.perform(modern("tools/call", "fallstatus_aendern", statusParams))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isError").value(true));
        verify(confirmations).auditDenied(account, McpScope.STAFF_TRANSITIONS, "SCOPE_DENIED");

        when(auth.allows(account, McpScope.STAFF_TRANSITIONS)).thenReturn(true);
        when(confirmations.prepareCaseAction(account, transition)).thenReturn(transitionId);
        mvc.perform(modern("tools/call", "fallstatus_aendern", statusParams))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.inputRequests.propertyflow_confirmation.params.url")
                        .value("https://propertyflow.example.test/mitarbeiter/mcp/bestaetigen/" + transitionId));
        when(confirmations.continueCaseAction(account, transitionId, transition, false))
                .thenReturn(new View(transitionId, "case-a", "Fenster", "", false, Status.SUBMITTED, null));
        mvc.perform(modern("tools/call", "fallstatus_aendern", statusParams + """
                ,"requestState":"%s","inputResponses":{"propertyflow_confirmation":{"action":"accept"}}
                """.formatted(transitionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.structuredContent.outcome").value("submitted"));
    }

    private static MockHttpServletRequestBuilder request(String payload) {
        return post("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .header("MCP-Protocol-Version", PROTOCOL)
                .content(payload);
    }

    private static MockHttpServletRequestBuilder authenticated(String payload) {
        return request(payload).header(HttpHeaders.AUTHORIZATION, "Bearer " + CLIENT_TOKEN);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import(BackendMcpConfiguration.class)
    static class Web {

        @Bean
        McpTenantAuthorization mcpAuthorization() {
            return mock(McpTenantAuthorization.class);
        }

        @Bean
        McpCaseReading mcpCaseReading() {
            return mock(McpCaseReading.class);
        }

        @Bean
        McpMessageConfirmations confirmations() {
            return mock(McpMessageConfirmations.class);
        }
    }
}
