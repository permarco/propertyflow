package com.pertegato.propertyflow.triage.service;

import com.pertegato.propertyflow.triage.service.AnalyzeTool.AnalysisModel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class ConversationAnalyzer implements ConversationAnalysis {

    public static final String PROMPT_VERSION = "conversation-analysis-v1";
    private static final int MAX_CONTEXT_LENGTH = 100_000;
    private static final Set<String> URGENCIES = Set.of("LOW", "NORMAL", "HIGH", "CRITICAL");
    private final AnalysisModel model;
    private final String modelName;
    private final JsonMapper json = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    public ConversationAnalyzer(AnalysisModel model, String modelName) {
        this.model = model;
        this.modelName = modelName;
    }

    @Override
    public Result analyze(Input input) {
        String payload = json.writeValueAsString(input);
        if (payload.length() > MAX_CONTEXT_LENGTH || input.messages().isEmpty()) {
            return failure("INVALID_CONTEXT");
        }
        String response;
        try {
            response = model.analyze(payload);
        } catch (RuntimeException exception) {
            return failure("MODEL_UNAVAILABLE");
        }
        try {
            if (response == null || response.length() > 50_000) {
                return failure("INVALID_MODEL_OUTPUT");
            }
            JsonNode root = json.readTree(response);
            require(root.isObject() && root.size() == 3 && root.path("urgency").isString()
                    && URGENCIES.contains(root.path("urgency").asString()) && root.path("humanRequired").isBoolean()
                    && root.path("questions").isArray());
            List<Answer> answers = new ArrayList<>();
            Set<UUID> evaluated = new HashSet<>();
            for (JsonNode item : root.path("questions")) {
                UUID questionId = UUID.fromString(item.path("questionId").asString());
                Question question = input.questions().stream().filter(candidate -> candidate.id().equals(questionId)).findFirst().orElseThrow();
                require(evaluated.add(questionId) && item.path("answered").isBoolean());
                if (item.path("answered").asBoolean()) {
                    require(item.size() == 4 && item.path("evidence").isString());
                    UUID messageId = UUID.fromString(item.path("messageId").asString());
                    Message message = input.messages().stream().filter(candidate -> candidate.id().equals(messageId)).findFirst().orElseThrow();
                    String evidence = item.path("evidence").asString();
                    require("TENANT".equals(message.author()) && message.sequence() > question.sequence()
                            && !evidence.isBlank() && evidence.length() <= 2_000 && message.text().contains(evidence));
                    answers.add(new Answer(questionId, messageId, evidence));
                } else {
                    require(item.size() == 2);
                }
            }
            require(evaluated.size() == input.questions().size());
            return new Result(root.path("urgency").asString(), root.path("humanRequired").asBoolean(), answers,
                    null, modelName, PROMPT_VERSION);
        } catch (RuntimeException exception) {
            return failure("INVALID_MODEL_OUTPUT");
        }
    }

    private Result failure(String code) {
        return new Result(null, false, List.of(), code, modelName, PROMPT_VERSION);
    }

    private void require(boolean valid) {
        if (!valid) {
            throw new IllegalArgumentException("INVALID_MODEL_OUTPUT");
        }
    }
}
