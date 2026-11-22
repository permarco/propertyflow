package com.pertegato.propertyflow.caseprocessing.mcp;

import com.pertegato.propertyflow.access.contract.McpAccountContext;
import com.pertegato.propertyflow.access.contract.McpScope;
import com.pertegato.propertyflow.access.contract.McpTenantAuthorization;
import com.pertegato.propertyflow.caseprocessing.contract.CaseServiceException;
import com.pertegato.propertyflow.caseprocessing.contract.CaseStatus;
import com.pertegato.propertyflow.caseprocessing.contract.CaseStatusView;
import com.pertegato.propertyflow.caseprocessing.contract.McpCaseReading;
import com.pertegato.propertyflow.caseprocessing.contract.McpCaseReading.CaseStateFilter;
import com.pertegato.propertyflow.caseprocessing.contract.McpCaseReading.StaffCaseDetail;
import com.pertegato.propertyflow.caseprocessing.contract.PublicMessage;
import com.pertegato.propertyflow.caseprocessing.contract.StaffCasePage;
import com.pertegato.propertyflow.caseprocessing.contract.TenantCaseView;
import com.pertegato.propertyflow.caseprocessing.contract.Urgency;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

final class BackendMcpTools {

    static final String CASE_ID = "caseId";
    static final String TEXT = "text";
    static final Map<String, String> STRING = Map.of("type", "string");
    static final Map<String, String> CASE_ID_SCHEMA = Map.of("type", "string", "pattern", McpCaseReading.CASE_ID_PATTERN);

    private static final int PAGE_SIZE = 50;
    private static final String STATE = "state";
    private static final String STATUS = "status";
    private static final String URGENCY = "urgency";
    private static final String UNASSESSED = "Noch nicht bewertet";
    private static final String TITLE = "title";
    private static final String DESCRIPTION = "description";
    private static final String CURSOR = "cursor";
    private static final String MESSAGE_CURSOR = "messageCursor";
    private static final String ITEMS = "items";
    private static final String NEXT_CURSOR = "nextCursor";
    private static final String MESSAGES = "messages";
    private static final String NEXT_MESSAGE_CURSOR = "nextMessageCursor";
    private static final String LAST_CONTACT = "lastTenantContactAt";
    private static final Map<String, Object> STATE_SCHEMA = Map.of("type", "string", "enum", List.of("active", "closed", "all"));
    private static final Pattern EMAIL_ADDRESS = Pattern.compile(
            "[\\p{L}\\p{N}.!#$%&'*+/=?^_{|}~-]+@[\\p{L}\\p{N}-]+(?:\\.[\\p{L}\\p{N}-]+)+");
    private static final Pattern PERSONAL_CASE_LINK = Pattern.compile(
            "(?i)(?:https?://[^\\s<>\"']+)?/mieter/fall/zugang/[A-Za-z0-9_-]+");

    private final McpTenantAuthorization authorization;
    private final McpCaseReading cases;
    private final McpCursor cursors = new McpCursor();
    private final List<ReadTool> reads;

    BackendMcpTools(McpTenantAuthorization authorization, McpCaseReading cases) {
        this.authorization = authorization;
        this.cases = cases;
        BackendFallstandTool fallstand = new BackendFallstandTool(cases);
        Tool fallstandDefinition = fallstand.definition();
        reads = List.of(
                new ReadTool(fallstandDefinition, McpScope.TENANT_CASES,
                        (account, arguments) -> fallstand.call(McpTransportContext.create(Map.of(BackendMcpConfiguration.ACCOUNT_CONTEXT, account)),
                                new CallToolRequest(fallstandDefinition.name(), arguments))),
                new ReadTool(tenantListDefinition(), McpScope.TENANT_CASES, this::tenantList),
                new ReadTool(staffListDefinition(), McpScope.STAFF_CASES, this::staffList),
                new ReadTool(staffDetailDefinition(), McpScope.STAFF_CASES, this::staffDetail),
                new ReadTool(detailDefinition(), null, this::detail),
                new ReadTool(historyDefinition(), null, this::history));
    }

    List<Tool> definitions(McpAccountContext account) {
        return reads.stream().filter(tool -> allowed(account, tool)).map(ReadTool::definition).toList();
    }

    CallToolResult read(McpAccountContext account, String name, Map<String, Object> arguments) {
        ReadTool tool = reads.stream().filter(candidate -> candidate.definition().name().equals(name)).findFirst().orElse(null);
        if (tool == null || !allowed(account, tool)) {
            return error("Das Werkzeug ist für diese Verbindung nicht verfügbar.");
        }
        try {
            Map<?, ?> properties = (Map<?, ?>) tool.definition().inputSchema().get("properties");
            if (!properties.keySet().containsAll(arguments.keySet())) {
                return error("Die Eingaben sind ungültig.");
            }
            return tool.call().apply(account, arguments);
        } catch (IllegalArgumentException exception) {
            return error("Die Eingaben oder der Fortsetzungswert sind ungültig.");
        } catch (CaseServiceException exception) {
            return switch (exception.code()) {
                case CASE_NOT_AVAILABLE, UNAUTHENTICATED, FORBIDDEN -> error("Der Fallzugriff ist nicht verfügbar.");
                case INVALID_INPUT -> error("Die Eingaben sind ungültig.");
                default -> error("Der Fallzugriff ist derzeit nicht möglich.");
            };
        }
    }

    private boolean allowed(McpAccountContext account, ReadTool tool) {
        return tool.scope() == null
                ? authorization.allows(account, McpScope.TENANT_CASES) || authorization.allows(account, McpScope.STAFF_CASES)
                : authorization.allows(account, tool.scope());
    }

    private CallToolResult detail(McpAccountContext account, Map<String, Object> arguments) {
        String caseId = requiredText(arguments, CASE_ID);
        TenantCaseView view = cases.readPublicForMcp(account, caseId, authorization.allows(account, McpScope.STAFF_CASES));
        return result(Map.of(CASE_ID, view.state().caseId(), TITLE, publicText(view.title()),
                DESCRIPTION, publicText(view.description()), STATUS, BackendFallstandTool.status(view.state().status()),
                URGENCY, urgency(view.state())));
    }

    private CallToolResult history(McpAccountContext account, Map<String, Object> arguments) {
        String caseId = requiredText(arguments, CASE_ID);
        String query = "history:" + caseId;
        int offset = cursors.offset(account, query, optionalText(arguments, MESSAGE_CURSOR));
        TenantCaseView view = cases.readPublicForMcp(account, caseId, authorization.allows(account, McpScope.STAFF_CASES));
        Map<String, Object> output = new LinkedHashMap<>();
        output.put(CASE_ID, view.state().caseId());
        addMessages(output, account, query, offset, view.messages());
        return result(output);
    }

    private CallToolResult tenantList(McpAccountContext account, Map<String, Object> arguments) {
        CaseStateFilter requested = state(arguments);
        int offset = cursors.offset(account, "tenant:" + requested, optionalText(arguments, CURSOR));
        List<Map<String, String>> rows = cases.listForMcp(account).stream()
                .filter(row -> requested == CaseStateFilter.ALL || (row.state().status() == CaseStatus.CLOSED) == (requested == CaseStateFilter.CLOSED))
                .skip(offset)
                .limit(PAGE_SIZE + 1)
                .map(row -> Map.of(CASE_ID, row.state().caseId(), TITLE, publicText(row.title()),
                        STATUS, BackendFallstandTool.status(row.state().status())))
                .toList();
        return page(account, "tenant:" + requested, offset, rows, ITEMS, NEXT_CURSOR);
    }

    private CallToolResult staffList(McpAccountContext account, Map<String, Object> arguments) {
        CaseStateFilter state = state(arguments);
        String statusValue = optionalText(arguments, STATUS);
        CaseStatus status = statusValue == null ? null : status(statusValue);
        String urgencyValue = optionalText(arguments, URGENCY);
        String urgency = urgencyValue == null ? "" : urgency(urgencyValue);
        String query = "staff:" + state + ":" + status + ":" + urgency;
        int offset = cursors.offset(account, query, optionalText(arguments, CURSOR));
        int pageNumber = offset / PAGE_SIZE + 1;
        StaffCasePage page = cases.listForMcpStaff(account, state, status, urgency, pageNumber);
        List<Map<String, Object>> rows = page.page() != pageNumber ? List.of() : page.rows().stream()
                .map(row -> Map.<String, Object>of(CASE_ID, row.state().caseId(), TITLE, publicText(row.title()),
                        STATUS, BackendFallstandTool.status(row.state().status()), URGENCY, urgency(row.state()),
                        LAST_CONTACT, row.lastTenantContactAt().toString()))
                .toList();
        return result(pageNumber < page.totalPages()
                ? Map.of(ITEMS, rows, NEXT_CURSOR, cursors.issue(account, query, Math.addExact(offset, PAGE_SIZE)))
                : Map.of(ITEMS, rows));
    }

    private CallToolResult staffDetail(McpAccountContext account, Map<String, Object> arguments) {
        String caseId = requiredText(arguments, CASE_ID);
        String query = "messages:" + caseId;
        int offset = cursors.offset(account, query, optionalText(arguments, MESSAGE_CURSOR));
        StaffCaseDetail detail = cases.readForMcpStaff(account, caseId);
        Map<String, Object> output = new LinkedHashMap<>(Map.of(CASE_ID, detail.state().caseId(),
                TITLE, publicText(detail.title()), STATUS, BackendFallstandTool.status(detail.state().status()), URGENCY, urgency(detail.state())));
        addMessages(output, account, query, offset, detail.messages());
        return result(output);
    }

    private void addMessages(Map<String, Object> output, McpAccountContext account, String query, int offset, List<PublicMessage> history) {
        List<Map<String, Object>> messages = history.stream()
                .skip(offset)
                .limit(PAGE_SIZE + 1)
                .map(message -> Map.<String, Object>of("sentAt", message.occurredAt().toString(),
                        "authorRole", message.author().name().toLowerCase(Locale.ROOT), TEXT, publicText(message.text()),
                        "answerRequired", message.answerState() != PublicMessage.AnswerState.NONE))
                .toList();
        output.put(MESSAGES, messages.stream().limit(PAGE_SIZE).toList());
        if (messages.size() > PAGE_SIZE) {
            output.put(NEXT_MESSAGE_CURSOR, cursors.issue(account, query, Math.addExact(offset, PAGE_SIZE)));
        }
    }

    private CallToolResult page(McpAccountContext account, String query, int offset, List<?> rows, String items, String next) {
        return result(rows.size() > PAGE_SIZE
                ? Map.of(items, rows.subList(0, PAGE_SIZE), next, cursors.issue(account, query, Math.addExact(offset, PAGE_SIZE)))
                : Map.of(items, rows));
    }

    private static Tool tenantListDefinition() {
        Map<String, Object> item = object(Map.of(CASE_ID, STRING, TITLE, STRING, STATUS, STRING), List.of(CASE_ID, TITLE, STATUS));
        return tool("eigene_faelle_auflisten", "Eigene Fälle auflisten", "Listet die eigenen aktiven, abgeschlossenen oder alle Fälle.",
                object(Map.of(STATE, STATE_SCHEMA, CURSOR, STRING), List.of()),
                object(Map.of(ITEMS, array(item), NEXT_CURSOR, STRING), List.of(ITEMS)), true);
    }

    private static Tool staffListDefinition() {
        Map<String, Object> item = object(Map.of(CASE_ID, STRING, TITLE, STRING, STATUS, STRING, URGENCY, STRING,
                LAST_CONTACT, Map.of("type", "string", "format", "date-time")), List.of(CASE_ID, TITLE, STATUS, URGENCY, LAST_CONTACT));
        return tool("mitarbeiter_faelle_auflisten", "Fälle der Bewirtschaftung auflisten",
                "Listet als Mitarbeiter die Fälle mit optionalen Filtern nach Status und Dringlichkeit.",
                object(Map.of(STATE, STATE_SCHEMA, STATUS, STRING, URGENCY, STRING, CURSOR, STRING), List.of()),
                object(Map.of(ITEMS, array(item), NEXT_CURSOR, STRING), List.of(ITEMS)), true);
    }

    private static Tool staffDetailDefinition() {
        return tool("mitarbeiter_fall_abrufen", "Fall für die Bewirtschaftung abrufen",
                "Liest Fallstand und den bereits veröffentlichten Nachrichtenverlauf.",
                object(Map.of(CASE_ID, CASE_ID_SCHEMA, MESSAGE_CURSOR, STRING), List.of(CASE_ID)),
                object(Map.of(CASE_ID, STRING, TITLE, STRING, STATUS, STRING, URGENCY, STRING, MESSAGES, messagesSchema(), NEXT_MESSAGE_CURSOR, STRING),
                        List.of(CASE_ID, TITLE, STATUS, URGENCY, MESSAGES)), true);
    }

    private static Tool detailDefinition() {
        return tool("falldetails_abrufen", "Falldetails abrufen", "Liest die vollständige ursprüngliche Problembeschreibung eines berechtigten Falls.",
                object(Map.of(CASE_ID, CASE_ID_SCHEMA), List.of(CASE_ID)),
                object(Map.of(CASE_ID, STRING, TITLE, STRING, DESCRIPTION, STRING, STATUS, STRING, URGENCY, STRING),
                        List.of(CASE_ID, TITLE, DESCRIPTION, STATUS, URGENCY)), true);
    }

    private static Tool historyDefinition() {
        return tool("fallhistorie_abrufen", "Fallhistorie abrufen", "Zeigt die bisherige veröffentlichte Kommunikation im Fall.",
                object(Map.of(CASE_ID, CASE_ID_SCHEMA, MESSAGE_CURSOR, STRING), List.of(CASE_ID)),
                object(Map.of(CASE_ID, STRING, MESSAGES, messagesSchema(), NEXT_MESSAGE_CURSOR, STRING), List.of(CASE_ID, MESSAGES)), true);
    }

    private static Map<String, Object> messagesSchema() {
        return array(object(Map.of("sentAt", Map.of("type", "string", "format", "date-time"),
                "authorRole", STRING, TEXT, STRING, "answerRequired", Map.of("type", "boolean")),
                List.of("sentAt", "authorRole", TEXT, "answerRequired")));
    }

    static Tool tool(String name, String title, String description, Map<String, Object> input, Map<String, Object> output, boolean readOnly) {
        return Tool.builder(name, input)
                .title(title)
                .description(description)
                .outputSchema(output)
                .annotations(ToolAnnotations.builder().readOnlyHint(readOnly).openWorldHint(!readOnly).build())
                .build();
    }

    static Map<String, Object> object(Map<String, ?> properties, List<String> required) {
        return Map.of("type", "object", "properties", properties, "required", required, "additionalProperties", false);
    }

    private static Map<String, Object> array(Map<String, ?> item) {
        return Map.of("type", "array", "items", item, "maxItems", PAGE_SIZE);
    }

    static String requiredText(Map<String, Object> arguments, String key) {
        if (!(arguments.get(key) instanceof String value) || value.isBlank()) {
            throw new IllegalArgumentException("Ungültige Eingabe.");
        }
        return value;
    }

    private static String optionalText(Map<String, Object> arguments, String key) {
        return arguments.containsKey(key) ? requiredText(arguments, key) : null;
    }

    private static CaseStateFilter state(Map<String, Object> arguments) {
        String value = optionalText(arguments, STATE);
        return switch (value == null ? "active" : value) {
            case "active" -> CaseStateFilter.ACTIVE;
            case "closed" -> CaseStateFilter.CLOSED;
            case "all" -> CaseStateFilter.ALL;
            default -> throw new IllegalArgumentException();
        };
    }

    private static String publicText(String value) {
        String withoutLinks = PERSONAL_CASE_LINK.matcher(value).replaceAll("[persönlicher Fall-Link entfernt]");
        return EMAIL_ADDRESS.matcher(withoutLinks).replaceAll("[E-Mail-Adresse entfernt]");
    }

    private static CaseStatus status(String value) {
        return Arrays.stream(CaseStatus.values())
                .filter(status -> status.name().equals(value) || BackendFallstandTool.status(status).equals(value))
                .findFirst()
                .orElseThrow(IllegalArgumentException::new);
    }

    private static String urgency(String value) {
        if (UNASSESSED.equals(value)) {
            return value;
        }
        return Arrays.stream(Urgency.values())
                .filter(urgency -> urgency.name().equals(value) || BackendFallstandTool.urgency(urgency).equals(value))
                .map(BackendFallstandTool::urgency)
                .findFirst()
                .orElseThrow(IllegalArgumentException::new);
    }

    private static String urgency(CaseStatusView state) {
        return state.urgency().map(BackendFallstandTool::urgency).orElse(UNASSESSED);
    }

    static CallToolResult result(Map<String, ?> value) {
        return CallToolResult.builder().structuredContent(value).build();
    }

    static CallToolResult error(String message) {
        return CallToolResult.builder().addTextContent(message).isError(true).build();
    }

    private record ReadTool(Tool definition, McpScope scope, BiFunction<McpAccountContext, Map<String, Object>, CallToolResult> call) {
    }
}
