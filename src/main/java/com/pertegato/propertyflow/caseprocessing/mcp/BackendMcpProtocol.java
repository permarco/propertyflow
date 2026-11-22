package com.pertegato.propertyflow.caseprocessing.mcp;

import com.pertegato.propertyflow.access.contract.McpAccountContext;
import com.pertegato.propertyflow.access.contract.McpScope;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

final class BackendMcpProtocol {

    static final String SERVER_NAME = "propertyflow";
    static final String SERVER_VERSION = "0.1.0";
    private static final String MODERN = "2026-07-28";
    private static final String LEGACY = "2025-11-25";
    private static final String VERSION_HEADER = "MCP-Protocol-Version";
    private static final String VERSION_META = "io.modelcontextprotocol/protocolVersion";
    private static final String TOOL_LIST = "tools/list";
    private static final String TOOL_CALL = "tools/call";
    private static final String DISCOVER = "server/discover";
    private static final String PING = "ping";
    private static final String JSON_RPC = "jsonrpc";
    private static final String ID = "id";
    private static final List<String> VERSIONS = List.of(MODERN, LEGACY, "2025-06-18", "2025-03-26");
    private static final Map<String, Object> SERVER_META = Map.of(
            "io.modelcontextprotocol/serverInfo", Map.of("name", SERVER_NAME, "version", SERVER_VERSION));

    private final JsonMapper json = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();
    private final BackendMcpTools tools;
    private final BackendMcpMessageTools messages;

    BackendMcpProtocol(BackendMcpTools tools, BackendMcpMessageTools messages) {
        this.tools = tools;
        this.messages = messages;
    }

    void auditAnonymous(byte[] body) {
        try {
            JsonNode message = json.readTree(body);
            if (message != null && TOOL_CALL.equals(message.path("method").asText())) {
                messages.auditAnonymous(message.path("params").path("name").asText());
            }
        } catch (JacksonException ignored) {
            // Bei ungültigem JSON steht keine verifizierbare Werkzeugkennung zur Verfügung.
        }
    }

    ServerResponse handle(ServerRequest request, byte[] body, McpAccountContext account) {
        JsonNode message;
        try {
            message = json.readTree(body);
        } catch (JacksonException exception) {
            return error(HttpStatus.BAD_REQUEST, null, -32700, "Parse error", null);
        }
        if (message == null || !message.isObject() || !message.path(JSON_RPC).isTextual()
                || !"2.0".equals(message.path(JSON_RPC).asText()) || !message.path("method").isTextual()) {
            return error(HttpStatus.BAD_REQUEST, null, -32600, "Invalid Request", null);
        }
        JsonNode id = message.get(ID);
        if (id != null && !id.isTextual() && !id.isIntegralNumber()) {
            return error(HttpStatus.BAD_REQUEST, null, -32600, "Invalid Request", null);
        }
        String method = message.path("method").asText();
        JsonNode params = message.path("params");
        if (!params.isMissingNode() && !params.isObject()) {
            return error(HttpStatus.BAD_REQUEST, id, -32602, "Invalid params", null);
        }
        String headerVersion = request.headers().firstHeader(VERSION_HEADER);
        JsonNode versionMeta = params.path("_meta").path(VERSION_META);
        boolean modern = DISCOVER.equals(method) || !versionMeta.isMissingNode()
                || headerVersion != null && headerVersion.compareTo(MODERN) >= 0;
        if (modern) {
            if (!matches(request, VERSION_HEADER, versionMeta) || !matches(request, "Mcp-Method", message.path("method"))
                    || TOOL_CALL.equals(method) && !matchesName(request, params.path("name"))) {
                return error(HttpStatus.BAD_REQUEST, id, -32020, "Header mismatch", null);
            }
            if (!MODERN.equals(headerVersion)) {
                return unsupported(id, headerVersion);
            }
        } else {
            if (headerVersion != null && !VERSIONS.contains(headerVersion)) {
                return unsupported(id, headerVersion);
            }
            if ("initialize".equals(method) && id != null) {
                return null;
            }
            if (id == null && method.startsWith("notifications/")) {
                return ServerResponse.accepted().build();
            }
        }
        if (id == null) {
            return error(HttpStatus.BAD_REQUEST, null, -32600, "Invalid Request", null);
        }

        return switch (method) {
            case DISCOVER -> success(id, Map.of("supportedVersions", VERSIONS, "capabilities", Map.of("tools", Map.of())), true);
            case PING -> success(id, Map.of(), modern);
            case TOOL_LIST -> params.has("cursor")
                    ? error(HttpStatus.BAD_REQUEST, id, -32602, "Invalid cursor", null)
                    : success(id, Map.of("tools", modern
                            ? Stream.concat(tools.definitions(account).stream(), messages.definitions(account).stream()).toList()
                            : tools.definitions(account)), modern);
            case TOOL_CALL -> call(account, params, id, modern);
            default -> error(modern ? HttpStatus.NOT_FOUND : HttpStatus.OK, id, -32601, "Method not found", null);
        };
    }

    private ServerResponse call(McpAccountContext account, JsonNode params, JsonNode id, boolean modern) {
        JsonNode name = params.path("name");
        JsonNode arguments = params.path("arguments");
        if (!name.isTextual() || name.asText().isBlank() || !arguments.isMissingNode() && !arguments.isObject()) {
            return error(HttpStatus.BAD_REQUEST, id, -32602, "Invalid params", null);
        }
        Map<String, Object> values = arguments.isMissingNode() ? Map.of() : json.convertValue(arguments, new TypeReference<>() {
        });
        McpScope scope = messages.scope(account, name.asText());
        Object invoked = scope == null ? tools.read(account, name.asText(), values)
                : messages.call(account, name.asText(), scope, values, params, modern);
        if (!(invoked instanceof CallToolResult result)) {
            return success(id, invoked, modern);
        }
        ObjectNode output = json.valueToTree(result);
        if (result.structuredContent() != null && (result.content() == null || result.content().isEmpty())) {
            output.putArray("content").addObject().put("type", "text")
                    .put("text", json.writeValueAsString(result.structuredContent()));
        }
        return success(id, output, modern);
    }

    private ServerResponse unsupported(JsonNode id, String version) {
        return error(HttpStatus.BAD_REQUEST, id, -32022, "Unsupported protocol version",
                Map.of("requested", version, "supported", VERSIONS));
    }

    private ServerResponse success(JsonNode id, Object value, boolean modern) {
        ObjectNode result = json.valueToTree(value);
        if (modern) {
            if (!result.has("resultType")) {
                result.put("resultType", "complete");
            }
            result.set("_meta", json.valueToTree(SERVER_META));
        }
        return response(HttpStatus.OK, id, "result", result);
    }

    private ServerResponse error(HttpStatus status, JsonNode id, int code, String message, Map<String, Object> data) {
        ObjectNode value = json.createObjectNode().put("code", code).put("message", message);
        if (data != null) {
            value.set("data", json.valueToTree(data));
        }
        return response(status, id, "error", value);
    }

    private ServerResponse response(HttpStatus status, JsonNode id, String kind, ObjectNode value) {
        ObjectNode response = json.createObjectNode().put(JSON_RPC, "2.0");
        response.set(ID, id);
        response.set(kind, value);
        return ServerResponse.status(status).contentType(MediaType.APPLICATION_JSON).body(response);
    }

    private static boolean matches(ServerRequest request, String header, JsonNode value) {
        List<String> headers = request.headers().header(header);
        return headers.size() == 1 && value.isTextual() && headers.getFirst().equals(value.asText());
    }

    private static boolean matchesName(ServerRequest request, JsonNode name) {
        List<String> headers = request.headers().header("Mcp-Name");
        if (headers.size() != 1 || !name.isTextual()) {
            return false;
        }
        String header = headers.getFirst();
        if (header.startsWith("=?base64?") && header.endsWith("?=")) {
            try {
                header = new String(Base64.getDecoder().decode(header.substring(9, header.length() - 2)), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }
        return name.asText().equals(header);
    }
}
