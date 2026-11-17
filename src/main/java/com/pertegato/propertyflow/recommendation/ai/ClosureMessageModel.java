package com.pertegato.propertyflow.recommendation.ai;

import com.pertegato.propertyflow.recommendation.service.ClosureMessageService;
import com.pertegato.propertyflow.recommendation.service.RecommendationService.Model;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel.ResponseFormat;
import org.springframework.ai.openai.OpenAiChatOptions;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Bindet Rollen und technische Ausgabegrenzen an die serverseitige Operation; Inhalte bleiben gesondert zu prüfen.
 */
public final class ClosureMessageModel implements Model {

    public static final String SCHEMA_VERSION = "closure-message-schema-v2";
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();
    private final ChatClient client;

    public ClosureMessageModel(ChatClient client) {
        this.client = client;
    }

    @Override
    public String recommend(String input) {
        if (client == null) {
            throw new IllegalStateException("MODEL_NOT_CONFIGURED");
        }
        return client.prompt()
                .system(ClosureMessageService.systemPromptFor(input))
                .user(input)
                .options(optionsFor(input))
                .call()
                .content();
    }

    static OpenAiChatOptions.Builder optionsFor(String input) {
        JsonNode request = JSON.readTree(input);
        Map<String, Object> properties;
        List<String> required;
        switch (request.path("operation").asString()) {
            case "GENERATE" -> {
                String ground = request.path("assessment").path("outcome").asString();
                require(Set.of("RESOLVED", "TENANT_RESPONSIBILITY").contains(ground));
                properties = new TreeMap<>(Map.of("text", Map.of("type", "string"),
                        "ground", Map.of("type", "string", "enum", List.of(ground))));
                required = List.of("text", "ground");
            }
            case "VALIDATE" -> {
                String digest = request.path("digest").asString();
                require(request.path("text").isString() && digest.matches("[0-9a-f]{64}")
                        && digest.equals(ClosureMessageService.digest(request.path("text").asString())));
                // Der Decoder darf nur den zu diesem Entwurf gehörenden Digest ausgeben; der Dienst prüft ihn trotzdem erneut.
                // Die Quellenprüfung steht vor dem Urteil; kein vorab gewählter Boolean darf die Begründung vorwegnehmen.
                properties = new LinkedHashMap<>();
                properties.put("explanation", Map.of("type", "string"));
                properties.put("approved", Map.of("type", "boolean"));
                properties.put("digest", Map.of("type", "string", "enum", List.of(digest)));
                required = List.of("explanation", "approved", "digest");
            }
            default -> throw new IllegalArgumentException("INVALID_CLOSURE_MESSAGE_OPERATION");
        }
        String schema = JSON.writeValueAsString(new TreeMap<>(Map.of("type", "object", "properties", properties,
                "required", required, "additionalProperties", false)));
        // Der unverbaute Customizer erhält die konfigurierten Modelloptionen; build() würde Provider-Defaults einsetzen.
        return OpenAiChatOptions.builder()
                .responseFormat(ResponseFormat.builder().jsonSchema(schema).strict(true).build());
    }

    private static void require(boolean condition) {
        if (!condition) {
            throw new IllegalArgumentException("INVALID_CLOSURE_MESSAGE_CONTEXT");
        }
    }
}
