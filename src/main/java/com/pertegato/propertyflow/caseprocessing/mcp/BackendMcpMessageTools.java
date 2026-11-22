package com.pertegato.propertyflow.caseprocessing.mcp;

import com.pertegato.propertyflow.access.contract.McpAccountContext;
import com.pertegato.propertyflow.access.contract.McpScope;
import com.pertegato.propertyflow.access.contract.McpTenantAuthorization;
import com.pertegato.propertyflow.access.web.McpConfirmationLogin;
import com.pertegato.propertyflow.caseprocessing.contract.CaseActionException;
import com.pertegato.propertyflow.caseprocessing.contract.McpMessageConfirmations;
import com.pertegato.propertyflow.caseprocessing.contract.McpMessageConfirmations.CaseRequest;
import com.pertegato.propertyflow.caseprocessing.contract.McpMessageConfirmations.Change;
import com.pertegato.propertyflow.caseprocessing.contract.McpMessageConfirmations.NewCase;
import com.pertegato.propertyflow.caseprocessing.contract.McpMessageConfirmations.Transition;
import com.pertegato.propertyflow.caseprocessing.contract.McpMessageConfirmations.View;
import com.pertegato.propertyflow.intake.contract.CaseDescription;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

import static com.pertegato.propertyflow.caseprocessing.mcp.BackendMcpTools.CASE_ID;
import static com.pertegato.propertyflow.caseprocessing.mcp.BackendMcpTools.CASE_ID_SCHEMA;
import static com.pertegato.propertyflow.caseprocessing.mcp.BackendMcpTools.STRING;
import static com.pertegato.propertyflow.caseprocessing.mcp.BackendMcpTools.TEXT;

final class BackendMcpMessageTools {

    private static final String TENANT_TOOL = "fall_kommentar_senden";
    private static final String STAFF_TOOL = "mitarbeiter_fallnachricht_veroeffentlichen";
    private static final String COMMENT_TOOL = "kommentar_hinzufuegen";
    private static final String INTAKE_TOOL = "meldung_erstellen";
    private static final String TRANSITION_TOOL = "fallstatus_aendern";
    private static final String TITLE = "title";
    private static final String UNIT = "unit";
    private static final String DESCRIPTION = "description";
    private static final String ACTION = "action";
    private static final String OUTCOME = "outcome";
    private static final String KIND = "kind";
    private static final String MESSAGE_ID = "messageId";
    private static final String CONFIRMATION_INPUT = "propertyflow_confirmation";
    private static final Set<String> MESSAGE_KINDS = Set.of("information", "question");
    private static final Map<String, Object> MESSAGE = Map.of("type", "string", "minLength", 1, "maxLength", 2000);
    private static final Map<String, Object> RECEIPT = BackendMcpTools.object(Map.of(CASE_ID, STRING, MESSAGE_ID, STRING),
            List.of(CASE_ID, MESSAGE_ID));

    private final Tool tenant = BackendMcpTools.tool(TENANT_TOOL, "Ergänzung zum Fall senden",
            "Bereitet eine Ergänzung zu einem eigenen Fall vor. Der Versand erfolgt nach Bestätigung in PropertyFlow.",
            BackendMcpTools.object(Map.of(CASE_ID, CASE_ID_SCHEMA, TEXT, MESSAGE), List.of(CASE_ID, TEXT)), RECEIPT, false);
    private final Tool staff = BackendMcpTools.tool(STAFF_TOOL, "Fallnachricht veröffentlichen",
            "Bereitet eine Information oder Rückfrage vor. Die Veröffentlichung erfolgt nach Bestätigung in PropertyFlow.",
            BackendMcpTools.object(Map.of(CASE_ID, CASE_ID_SCHEMA, TEXT, MESSAGE, KIND,
                    Map.of("type", "string", "enum", List.of("information", "question"))), List.of(CASE_ID, TEXT, KIND)),
            RECEIPT, false);
    private final Tool comment = BackendMcpTools.tool(COMMENT_TOOL, "Kommentar hinzufügen",
            "Bereitet eine Nachricht zu einem bestehenden berechtigten Fall vor. Der Versand erfolgt nach Bestätigung in PropertyFlow.",
            BackendMcpTools.object(Map.of(CASE_ID, CASE_ID_SCHEMA, TEXT, MESSAGE), List.of(CASE_ID, TEXT)), RECEIPT, false);
    private final Tool intake = BackendMcpTools.tool(INTAKE_TOOL, "Meldung erstellen",
            "Bereitet eine neue Störung oder Schadenmeldung vor. Die Meldung wird nach Bestätigung dem Mieterkonto zugeordnet.",
            BackendMcpTools.object(Map.of(TITLE, boundedText(150), UNIT, boundedText(200),
                    DESCRIPTION, boundedText(CaseDescription.MAX_LENGTH)), List.of(TITLE, UNIT, DESCRIPTION)),
            BackendMcpTools.object(Map.of(CASE_ID, STRING, OUTCOME, STRING), List.of(CASE_ID, OUTCOME)), false);
    private final Tool transition = BackendMcpTools.tool(TRANSITION_TOOL, "Fallstatus ändern",
            "Bereitet eine zulässige Mitarbeiteraktion vor. Die Ausführung erfolgt nach Bestätigung in PropertyFlow über Camunda.",
            BackendMcpTools.object(Map.of(CASE_ID, CASE_ID_SCHEMA, ACTION,
                    Map.of("type", "string", "enum", List.of("fortsetzen", "abschliessen", "wiedereroeffnen"))), List.of(CASE_ID, ACTION)),
            BackendMcpTools.object(Map.of(CASE_ID, STRING, ACTION, STRING, OUTCOME, STRING), List.of(CASE_ID, ACTION, OUTCOME)), false);

    private final McpTenantAuthorization authorization;
    private final McpMessageConfirmations confirmations;

    BackendMcpMessageTools(McpTenantAuthorization authorization, McpMessageConfirmations confirmations) {
        this.authorization = authorization;
        this.confirmations = confirmations;
    }

    List<Tool> definitions(McpAccountContext account) {
        return List.of(tenant, staff, comment, intake, transition).stream()
                .filter(tool -> authorization.allows(account, scope(account, tool.name())))
                .toList();
    }

    McpScope scope(McpAccountContext account, String name) {
        return switch (name) {
            case TENANT_TOOL -> McpScope.TENANT_MESSAGES;
            case COMMENT_TOOL -> account != null && authorization.allows(account, McpScope.STAFF_MESSAGES)
                    ? McpScope.STAFF_MESSAGES : McpScope.TENANT_MESSAGES;
            case STAFF_TOOL -> McpScope.STAFF_MESSAGES;
            case INTAKE_TOOL -> McpScope.TENANT_INTAKE;
            case TRANSITION_TOOL -> McpScope.STAFF_TRANSITIONS;
            default -> null;
        };
    }

    void auditAnonymous(String name) {
        McpScope scope = scope(null, name);
        if (scope != null) {
            confirmations.auditDenied(null, scope, "UNAUTHENTICATED");
        }
    }

    Object call(McpAccountContext account, String name, McpScope scope, Map<String, Object> arguments, JsonNode params, boolean modern) {
        if (!authorization.allows(account, scope)) {
            confirmations.auditDenied(account, scope, "SCOPE_DENIED");
            return BackendMcpTools.error("Das Werkzeug ist für diese Verbindung nicht verfügbar.");
        }
        if (!modern || !params.path("_meta").path("io.modelcontextprotocol/clientCapabilities")
                .path("elicitation").path("url").isObject()) {
            confirmations.auditDenied(account, scope, "URL_MODE_UNAVAILABLE");
            return BackendMcpTools.error("Diese Verbindung unterstützt die Bestätigung in PropertyFlow nicht.");
        }
        UUID operation;
        boolean cancel;
        try {
            JsonNode state = params.path("requestState");
            JsonNode responses = params.path("inputResponses");
            operation = state.isMissingNode() ? null : UUID.fromString(state.asText());
            cancel = cancelled(operation, responses);
            return switch (scope) {
                case TENANT_INTAKE, STAFF_TRANSITIONS -> callCaseAction(account, scope, operation, arguments, cancel);
                default -> callMessage(account, name, scope, operation, arguments, cancel);
            };
        } catch (IllegalArgumentException exception) {
            confirmations.auditDenied(account, scope, "INVALID_INPUT");
            return BackendMcpTools.error("Die Eingaben oder die Bestätigungsreferenz sind ungültig.");
        } catch (CaseActionException exception) {
            return BackendMcpTools.error("Die Aktion ist für diese Fall- oder Kontosituation nicht verfügbar.");
        }
    }

    private Object callMessage(McpAccountContext account, String name, McpScope scope, UUID operation,
            Map<String, Object> arguments, boolean cancel) {
        boolean staffWithKind = STAFF_TOOL.equals(name);
        requireFields(arguments, staffWithKind ? Set.of(CASE_ID, TEXT, KIND) : Set.of(CASE_ID, TEXT));
        String caseId = BackendMcpTools.requiredText(arguments, CASE_ID);
        String text = BackendMcpTools.requiredText(arguments, TEXT);
        boolean answerRequired = staffWithKind && question(arguments);
        if (operation == null) {
            return inputRequired(scope, confirmations.prepare(account, scope, caseId, text, answerRequired));
        }
        View view = confirmations.continuation(account, scope, operation, caseId, text, answerRequired, cancel);
        return switch (view.status()) {
            case WAITING -> inputRequired(scope, operation);
            case SENT -> BackendMcpTools.result(Map.of(CASE_ID, view.caseId(), MESSAGE_ID, view.messageId().toString()));
            case SUBMITTED -> CallToolResult.builder().addTextContent(scope.staff()
                    ? "Die Veröffentlichung läuft noch."
                    : "Deine Nachricht wurde gespeichert. Die Verarbeitung läuft noch. Du musst sie nicht erneut senden.")
                    .build();
            case CANCELLED -> BackendMcpTools.error("Der Vorgang wurde abgebrochen.");
            case REJECTED -> BackendMcpTools.error("Die Nachricht konnte nicht gesendet werden.");
        };
    }

    private Object callCaseAction(McpAccountContext account, McpScope scope, UUID operation, Map<String, Object> arguments, boolean cancel) {
        CaseRequest request;
        if (scope == McpScope.TENANT_INTAKE) {
            requireFields(arguments, Set.of(TITLE, UNIT, DESCRIPTION));
            request = new NewCase(BackendMcpTools.requiredText(arguments, TITLE), BackendMcpTools.requiredText(arguments, UNIT),
                    BackendMcpTools.requiredText(arguments, DESCRIPTION));
        } else {
            requireFields(arguments, Set.of(CASE_ID, ACTION));
            request = new Transition(BackendMcpTools.requiredText(arguments, CASE_ID),
                    Change.valueOf(BackendMcpTools.requiredText(arguments, ACTION).toUpperCase(Locale.ROOT)));
        }
        if (operation == null) {
            return inputRequired(scope, confirmations.prepareCaseAction(account, request));
        }
        View view = confirmations.continueCaseAction(account, operation, request, cancel);
        return switch (view.status()) {
            case WAITING -> inputRequired(scope, operation);
            case SENT -> caseReceipt(request, view, scope == McpScope.TENANT_INTAKE ? "accepted" : "applied");
            case SUBMITTED -> scope == McpScope.TENANT_INTAKE
                    ? CallToolResult.builder().addTextContent("Die Fallanlage wird noch geprüft.").build()
                    : caseReceipt(request, view, "submitted");
            case CANCELLED -> BackendMcpTools.error("Der Vorgang wurde abgebrochen.");
            case REJECTED -> BackendMcpTools.error("Die Aktion konnte nicht ausgeführt werden.");
        };
    }

    private static CallToolResult caseReceipt(CaseRequest request, View view, String outcome) {
        return request instanceof Transition change
                ? BackendMcpTools.result(Map.of(CASE_ID, view.caseId(), ACTION, change.change().name().toLowerCase(Locale.ROOT), OUTCOME, outcome))
                : BackendMcpTools.result(Map.of(CASE_ID, view.caseId(), OUTCOME, outcome));
    }

    private static void requireFields(Map<String, Object> arguments, Set<String> keys) {
        if (!keys.equals(arguments.keySet())) {
            throw new IllegalArgumentException();
        }
    }

    private static Map<String, Object> boundedText(int maxLength) {
        return Map.of("type", "string", "minLength", 1, "maxLength", maxLength);
    }

    private static boolean question(Map<String, Object> arguments) {
        String kind = BackendMcpTools.requiredText(arguments, KIND);
        if (!MESSAGE_KINDS.contains(kind)) {
            throw new IllegalArgumentException();
        }
        return "question".equals(kind);
    }

    private static boolean cancelled(UUID operation, JsonNode responses) {
        if (operation == null && responses.isMissingNode()) {
            return false;
        }
        if (operation == null || !responses.isObject() || responses.size() != 1) {
            throw new IllegalArgumentException();
        }
        return switch (responses.path(CONFIRMATION_INPUT).path("action").asText()) {
            case "accept" -> false;
            case "decline", "cancel" -> true;
            default -> throw new IllegalArgumentException();
        };
    }

    private Map<String, Object> inputRequired(McpScope scope, UUID operation) {
        String path = (scope.staff() ? McpConfirmationLogin.STAFF_PATH : McpConfirmationLogin.TENANT_PATH) + operation;
        return Map.of("resultType", "input_required", "requestState", operation.toString(), "inputRequests",
                Map.of(CONFIRMATION_INPUT, Map.of("method", "elicitation/create", "params", Map.of(
                        "mode", "url", "url", authorization.resourceUri().resolve(path).toASCIIString(),
                        "message", switch (scope) {
                            case TENANT_INTAKE -> "Prüfe und bestätige die neue Meldung in PropertyFlow.";
                            case STAFF_TRANSITIONS -> "Prüfe und bestätige die Fallaktion in PropertyFlow.";
                            default -> "Prüfe und bestätige die Nachricht in PropertyFlow.";
                        }))));
    }
}
