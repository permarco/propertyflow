package com.pertegato.propertyflow.recommendation.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class RecommendationService implements Recommendation {

    public static final String PROMPT_VERSION = "recommendation-sources-catalog-v2";
    private final KnowledgeSearch knowledge;
    private final Model model;
    private final String modelName;
    private final JsonMapper json = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    public RecommendationService(KnowledgeSearch knowledge, Model model, String modelName) {
        this.knowledge = knowledge;
        this.model = model;
        this.modelName = modelName;
    }

    @Override
    public Result recommend(String publicContext) {
        if (publicContext == null || publicContext.isBlank() || publicContext.length() > 100_000) {
            return failure("INVALID_CONTEXT");
        }
        List<Source> sources;
        String output;
        try {
            sources = knowledge.search(publicContext);
            if (sources.isEmpty()) {
                return failure("APPROVED_KNOWLEDGE_MISSING");
            }
            output = model.recommend(json.writeValueAsString(new Input(publicContext, sources)));
        } catch (RuntimeException exception) {
            return failure("RECOMMENDATION_UNAVAILABLE");
        }
        try {
            require(output != null && output.length() <= 30_000);
            JsonNode root = json.readTree(output);
            require(root.isObject() && root.size() == 5 && root.path("humanRequired").isBoolean()
                    && root.path("sources").isArray() && root.path("questions").isArray());
            String responsibility = text(root, "responsibility");
            String procedure = text(root, "procedure");
            List<Source> cited = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (JsonNode citation : root.path("sources")) {
                require(citation.isObject() && citation.size() == 3);
                String id = text(citation, "id");
                String version = text(citation, "version");
                String evidence = text(citation, "evidence");
                Source source = sources.stream().filter(candidate -> candidate.id().equals(id)
                        && candidate.version().equals(version)).findFirst().orElseThrow();
                require(seen.add(id) && source.text().contains(evidence));
                // Retain the exact retrieved source version, not a model-generated link or title.
                cited.add(source);
            }
            require(!cited.isEmpty());
            List<String> questions = new ArrayList<>();
            for (JsonNode question : root.path("questions")) {
                require(question.isString() && QuestionCatalog.QUESTIONS.containsKey(question.asString()) && !questions.contains(question.asString()));
                questions.add(question.asString());
            }
            return new Result(responsibility, procedure, cited, questions, root.path("humanRequired").asBoolean(),
                    null, modelName, PROMPT_VERSION);
        } catch (RuntimeException exception) {
            return failure("INVALID_RECOMMENDATION");
        }
    }

    private Result failure(String code) {
        return new Result(null, null, List.of(), List.of(), true, code, modelName, PROMPT_VERSION);
    }

    private String text(JsonNode root, String field) {
        JsonNode value = root.path(field);
        require(value.isString() && !value.asString().isBlank() && value.asString().length() <= 5_000);
        return value.asString();
    }

    private void require(boolean valid) {
        if (!valid) {
            throw new IllegalArgumentException("INVALID_RECOMMENDATION");
        }
    }

    private record Input(String conversation, List<Source> sources) {
    }
    @FunctionalInterface
    public interface Model {
        String recommend(String input);
    }

}
