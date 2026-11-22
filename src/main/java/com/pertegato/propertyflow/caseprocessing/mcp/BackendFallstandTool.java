package com.pertegato.propertyflow.caseprocessing.mcp;

import com.pertegato.propertyflow.access.contract.McpAccountContext;
import com.pertegato.propertyflow.caseprocessing.contract.CaseServiceException;
import com.pertegato.propertyflow.caseprocessing.contract.CaseStatus;
import com.pertegato.propertyflow.caseprocessing.contract.CaseStatusView;
import com.pertegato.propertyflow.caseprocessing.contract.McpCaseReading;
import com.pertegato.propertyflow.caseprocessing.contract.Urgency;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import java.util.List;
import java.util.Map;

final class BackendFallstandTool {

    private static final String CASE_ID = "caseId";
    private static final String STATUS = "status";
    private static final String URGENCY = "urgency";
    private static final String NOT_AVAILABLE = "Der Fall ist nicht verfügbar.";
    private static final String INVALID_CASE_ID = "Die Case-ID ist ungültig.";
    private final McpCaseReading cases;

    BackendFallstandTool(McpCaseReading cases) {
        this.cases = cases;
    }

    Tool definition() {
        Map<String, String> string = Map.of("type", "string");
        return Tool.builder("fallstand_abrufen", schema(Map.of(CASE_ID, Map.of("type", "string", "pattern", McpCaseReading.CASE_ID_PATTERN))))
                .title("Fallstand abrufen")
                .description("Liest den Status und die wirksame Dringlichkeit eines eigenen Falls.")
                .annotations(ToolAnnotations.builder().readOnlyHint(true).openWorldHint(false).build())
                .outputSchema(schema(Map.of(CASE_ID, string, STATUS, string, URGENCY, string)))
                .build();
    }

    CallToolResult call(McpTransportContext context, CallToolRequest request) {
        if (!(context.get(BackendMcpConfiguration.ACCOUNT_CONTEXT) instanceof McpAccountContext account)) {
            return error(NOT_AVAILABLE);
        }
        Map<String, Object> arguments = request.arguments();
        if (arguments == null || arguments.size() != 1 || !(arguments.get(CASE_ID) instanceof String caseId)) {
            return error(INVALID_CASE_ID);
        }
        try {
            CaseStatusView view = cases.readStatusForMcp(account, caseId);
            return CallToolResult.builder()
                    .structuredContent(Map.of(CASE_ID, view.caseId(), STATUS, status(view.status()), URGENCY,
                            view.urgency().map(BackendFallstandTool::urgency).orElse("Noch nicht bewertet")))
                    .build();
        } catch (CaseServiceException exception) {
            return switch (exception.code()) {
                case CASE_NOT_AVAILABLE, UNAUTHENTICATED, FORBIDDEN -> error(NOT_AVAILABLE);
                case INVALID_INPUT -> error(INVALID_CASE_ID);
                default -> error("Der Fallstand konnte nicht abgerufen werden.");
            };
        }
    }

    private static Map<String, Object> schema(Map<String, ?> properties) {
        return Map.of("type", "object", "properties", properties, "required", List.copyOf(properties.keySet()), "additionalProperties", false);
    }

    private static CallToolResult error(String message) {
        return CallToolResult.builder().addTextContent(message).isError(true).build();
    }

    static String status(CaseStatus value) {
        return switch (value) {
            case RECEIVED -> "Eingegangen";
            case UNDER_CLARIFICATION -> "In Abklärung";
            case WAITING_FOR_TENANT -> "Wartet auf Mieterantwort";
            case STAFF_REVIEW_REQUIRED -> "Mitarbeiterprüfung erforderlich";
            case IN_PROGRESS -> "In Bearbeitung";
            case WAITING_FOR_EXTERNAL -> "Wartet auf externe Rückmeldung";
            case WAITING_FOR_FOLLOW_UP -> "Wartet auf Wiedervorlage";
            case CLOSED -> "Abgeschlossen";
        };
    }

    static String urgency(Urgency value) {
        return switch (value) {
            case CRITICAL -> "Kritisch";
            case HIGH -> "Hoch";
            case NORMAL -> "Normal";
            case LOW -> "Niedrig";
        };
    }
}
