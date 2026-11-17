package com.pertegato.propertyflow.recommendation.knowledge;

import com.pertegato.propertyflow.recommendation.service.ClosureAssessment.Rule;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Allgemeine RAG-Freigabe genügt nicht: nur zusätzlich freigegebene Abschlussregeln werden geladen.
 */
public final class ApprovedClosureRules {

    private final Path directory;
    private final Clock clock;
    private final JsonMapper json = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    public ApprovedClosureRules(String directory, Clock clock) {
        this.directory = directory == null || directory.isBlank() ? null : Path.of(directory).toAbsolutePath().normalize();
        this.clock = clock;
    }

    public List<Rule> load() {
        if (directory == null) {
            return List.of();
        }
        List<Rule> rules = new ArrayList<>();
        Set<String> identities = new HashSet<>();
        try (Stream<Path> files = Files.list(directory)) {
            List<Path> paths = files.filter(path -> path.getFileName().toString().endsWith(".json")).sorted().limit(101).toList();
            require(paths.size() <= 100);
            for (Path path : paths) {
                require(!Files.isSymbolicLink(path) && Files.isRegularFile(path) && Files.size(path) <= 100_000);
                JsonNode document = json.readTree(Files.readString(path, StandardCharsets.UTF_8));
                JsonNode closure = document.path("automaticClosure");
                if (!document.path("approved").isBoolean() || !document.path("approved").asBoolean()
                        || !closure.path("approved").isBoolean() || !closure.path("approved").asBoolean()) {
                    continue;
                }
                require(closure.isObject() && closure.size() == 6);
                LocalDate from = LocalDate.parse(text(closure, "validFrom", 10));
                require(closure.has("validUntil"));
                LocalDate until = closure.path("validUntil").isNull() ? null : LocalDate.parse(text(closure, "validUntil", 10));
                require(until == null || !until.isBefore(from));
                LocalDate today = LocalDate.now(clock);
                if (today.isBefore(from) || until != null && today.isAfter(until)) {
                    continue;
                }
                String id = text(document, "id", 100);
                require(identities.add(id));
                rules.add(new Rule(id, text(document, "version", 100), text(closure, "scope", 5_000),
                        criteria(closure.path("prerequisites")), criteria(closure.path("exclusions"))));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("CLOSURE_RULES_UNAVAILABLE");
        }
        return List.copyOf(rules);
    }

    private Map<String, String> criteria(JsonNode node) {
        require(node.isObject() && !node.isEmpty() && node.size() <= 20);
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : node.propertyNames()) {
            require(key.matches("[a-z][a-z0-9_-]{0,49}"));
            result.put(key, text(node, key, 2_000));
        }
        return result;
    }

    private String text(JsonNode node, String field, int limit) {
        JsonNode value = node.path(field);
        require(value.isString() && !value.asString().isBlank() && value.asString().length() <= limit);
        return value.asString();
    }

    private void require(boolean condition) {
        if (!condition) {
            throw new IllegalStateException("INVALID_CLOSURE_RULE");
        }
    }
}
