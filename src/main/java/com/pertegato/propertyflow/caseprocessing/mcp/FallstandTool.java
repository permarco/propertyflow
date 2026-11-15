package com.pertegato.propertyflow.caseprocessing.mcp;

import com.pertegato.propertyflow.access.demo.DemoAccounts;
import com.pertegato.propertyflow.access.demo.DemoAccounts.Account;
import com.pertegato.propertyflow.caseprocessing.demo.DemoCases;
import com.pertegato.propertyflow.caseprocessing.demo.DemoCases.CaseStatus;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class FallstandTool {

    private static final Logger AUDIT = LoggerFactory.getLogger(FallstandTool.class);
    private static final String NAME = "fallstand_abrufen";
    private static final String CASE_ID = "caseId";
    private static final String CASE_ID_FORMAT = "^[A-Za-z0-9_-]{1,80}$";
    private static final Pattern VALID_CASE_ID = Pattern.compile(CASE_ID_FORMAT);
    private static final String NOT_AVAILABLE = "Der Fall ist nicht verfügbar.";

    private final DemoAccounts accounts;
    private final DemoCases cases;

    FallstandTool(DemoAccounts accounts, DemoCases cases) {
        this.accounts = accounts;
        this.cases = cases;
    }

    Tool definition() {
        return Tool.builder(NAME, objectSchema(Map.of(CASE_ID, Map.of("type", "string", "pattern", CASE_ID_FORMAT))))
                .title("Fallstand abrufen")
                .description("Liest den gespeicherten Bearbeitungsstatus und die wirksame Dringlichkeit eines eigenen Falls anhand der Case-ID.")
                .annotations(ToolAnnotations.builder().readOnlyHint(true).openWorldHint(false).build())
                .outputSchema(objectSchema(Map.of(CASE_ID, Map.of("type", "string"), "status", Map.of("type", "string"),
                        "urgency", Map.of("type", "string"))))
                .build();
    }

    CallToolResult call(McpTransportContext context, CallToolRequest request) {
        Object principal = context.get(McpConfiguration.TENANT_ID);
        Optional<Account> tenant = principal instanceof UUID tenantId
                ? accounts.findActiveById(tenantId).filter(Account::tenant)
                : Optional.empty();
        if (tenant.isEmpty()) {
            audit(null, null, "denied");
            return error(NOT_AVAILABLE);
        }

        Account account = tenant.orElseThrow();
        Map<String, Object> arguments = request.arguments();
        if (arguments == null || arguments.size() != 1 || !(arguments.get(CASE_ID) instanceof String id)
                || !VALID_CASE_ID.matcher(id).matches()) {
            audit(account.id(), null, "invalid-arguments");
            return error("Die Case-ID ist ungültig.");
        }

        try {
            CaseStatus status = cases.tenantCaseStatus(id, account.email());
            audit(account.id(), id, "allowed");
            return CallToolResult.builder().structuredContent(status).build();
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
                audit(account.id(), id, "denied");
                return error(NOT_AVAILABLE);
            }
            audit(account.id(), id, "error");
            return error("Der Fallstand konnte nicht abgerufen werden.");
        }
    }

    private Map<String, Object> objectSchema(Map<String, Object> properties) {
        return Map.of("type", "object", "properties", properties, "required", List.copyOf(properties.keySet()),
                "additionalProperties", false);
    }

    private CallToolResult error(String message) {
        return CallToolResult.builder().addTextContent(message).isError(true).build();
    }

    private void audit(UUID accountId, String caseId, String outcome) {
        AUDIT.info("mcp_audit tool={} account={} case={} outcome={}", NAME, accountId, caseId, outcome);
    }
}
