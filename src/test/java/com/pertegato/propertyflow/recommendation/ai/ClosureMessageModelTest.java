package com.pertegato.propertyflow.recommendation.ai;

import com.pertegato.propertyflow.recommendation.service.ClosureMessageService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatOptions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClosureMessageModelTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void schemaBindsValidationToTheExactDraftWithoutChoosingTheVerdict() {
        String draft = "Die Heizung funktioniert wieder.";
        String digest = ClosureMessageService.digest(draft);
        String request = json.writeValueAsString(Map.of("operation", "VALIDATE", "text", draft, "digest", digest));
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model("configured-local-model")
                .temperature(0.0)
                .maxTokens(4096)
                .reasoningEffort("none")
                .combineWith(ClosureMessageModel.optionsFor(request))
                .build();
        JsonNode schema = json.readTree(options.getResponseFormat().getJsonSchema());
        assertEquals(digest, schema.path("properties").path("digest").path("enum").get(0).asString());
        assertEquals("boolean", schema.path("properties").path("approved").path("type").asString());
        assertFalse(schema.path("properties").path("approved").has("enum"));
        assertEquals(3, schema.path("properties").size());
        assertEquals("explanation", schema.path("required").get(0).asString());
        assertTrue(options.getResponseFormat().getJsonSchema().indexOf("\"explanation\"")
                < options.getResponseFormat().getJsonSchema().indexOf("\"approved\""));
        assertFalse(schema.path("additionalProperties").asBoolean());
        assertTrue(options.getResponseFormat().getStrict());
        assertEquals("configured-local-model", options.getModel());
        assertEquals(0.0, options.getTemperature());
        assertEquals(4096, options.getMaxTokens());
        assertEquals("none", options.getReasoningEffort());
        assertThrows(IllegalArgumentException.class, () -> ClosureMessageModel.optionsFor(request.replace(draft, "Andere Nachricht.")));
    }

    @Test
    void generationSchemaDoesNotAllowValidationResponsesOrAnotherGround() {
        String request = json.writeValueAsString(Map.of("operation", "GENERATE", "assessment", Map.of("outcome", "RESOLVED")));
        JsonNode schema = json.readTree(ClosureMessageModel.optionsFor(request).build().getResponseFormat().getJsonSchema());
        assertEquals(2, schema.path("properties").size());
        assertEquals("RESOLVED", schema.path("properties").path("ground").path("enum").get(0).asString());
        assertFalse(schema.path("properties").has("approved"));
        assertThrows(IllegalArgumentException.class, () -> ClosureMessageModel.optionsFor("{\"operation\":\"UNKNOWN\"}"));
    }
}
