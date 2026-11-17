package com.pertegato.propertyflow.recommendation.knowledge;

import com.pertegato.propertyflow.recommendation.service.KnowledgeSearch;
import com.pertegato.propertyflow.recommendation.service.Recommendation.Source;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads only operator-configured approved documents; never crawls tenant data or arbitrary URLs.
 */
public final class ApprovedFileKnowledge implements KnowledgeSearch {

    private final Path directory;
    private final JsonMapper json = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    public ApprovedFileKnowledge(String directory) {
        this.directory = directory == null || directory.isBlank() ? null : Path.of(directory).toAbsolutePath().normalize();
    }

    @Override
    public List<Source> search(String publicContext) {
        if (directory == null) {
            return List.of();
        }
        Set<String> terms = terms(publicContext);
        List<Match> matches = new ArrayList<>();
        Set<String> identities = new HashSet<>();
        try (Stream<Path> files = Files.list(directory)) {
            List<Path> paths = files.filter(path -> path.getFileName().toString().endsWith(".json")).sorted().limit(101).toList();
            require(paths.size() <= 100);
            for (Path path : paths) {
                require(!Files.isSymbolicLink(path) && Files.size(path) <= 100_000);
                JsonNode document = json.readTree(Files.readString(path, StandardCharsets.UTF_8));
                if (!document.path("approved").asBoolean(false)) {
                    continue;
                }
                Source source = new Source(text(document, "id"), text(document, "version"), text(document, "title"),
                        text(document, "location"), text(document, "text"));
                require(identities.add(source.id()));
                Set<String> content = terms(source.title() + " " + source.text());
                long score = terms.stream().filter(content::contains).count();
                if (score > 0) {
                    matches.add(new Match(source, score));
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("KNOWLEDGE_UNAVAILABLE");
        }
        return matches.stream().sorted(Comparator.comparingLong(Match::score).reversed().thenComparing(match -> match.source().id()))
                .limit(5).map(Match::source).toList();
    }

    private Set<String> terms(String text) {
        Set<String> result = new HashSet<>();
        for (String term : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (term.length() >= 4) {
                result.add(term);
            }
        }
        return result;
    }

    private String text(JsonNode root, String field) {
        JsonNode value = root.path(field);
        require(value.isString() && !value.asString().isBlank() && value.asString().length() <= 20_000);
        return value.asString();
    }

    private void require(boolean valid) {
        if (!valid) {
            throw new IllegalStateException("INVALID_APPROVED_KNOWLEDGE");
        }
    }

    private record Match(Source source, long score) {
    }
}
