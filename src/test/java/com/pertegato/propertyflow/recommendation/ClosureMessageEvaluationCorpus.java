package com.pertegato.propertyflow.recommendation;

import com.pertegato.propertyflow.recommendation.ai.ClosureMessageModel;
import com.pertegato.propertyflow.recommendation.service.ClosureAssessment;
import com.pertegato.propertyflow.recommendation.service.ClosureMessage;
import com.pertegato.propertyflow.recommendation.service.ClosureMessageService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Prüft echte Generierung und kontrollierte Entwürfe getrennt; technische Ablehnung ist kein Inhaltsnachweis.
 */
final class ClosureMessageEvaluationCorpus {

    private static final Path PATH = Path.of("src/test/resources/evaluation/closure-message-v5.json");
    private final JsonMapper json = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    Corpus read() throws IOException {
        return json.readValue(Files.readString(PATH), Corpus.class);
    }

    Report evaluate(BiFunction<Example, Context, Evaluated> evaluator, String model, String revision)
            throws IOException, NoSuchAlgorithmException {
        Corpus corpus = read();
        List<CaseResult> results = new ArrayList<>();
        save(report(corpus, results, model, revision, false));
        for (Example example : corpus.cases()) {
            Context context = corpus.contexts().stream().filter(candidate -> candidate.id().equals(example.contextId())).findFirst().orElseThrow();
            Evaluated evaluated = evaluator.apply(example, context);
            results.add(judge(example, evaluated));
            save(report(corpus, results, model, revision, false));
        }
        Report report = report(corpus, results, model, revision,
                results.size() == corpus.cases().size() && results.stream().allMatch(CaseResult::passed));
        save(report);
        return report;
    }

    CaseResult judge(Example example, Evaluated evaluated) {
        boolean semanticDecision = evaluated.calls().stream().filter(call -> "VALIDATE".equals(call.operation())).anyMatch(call -> {
            try {
                JsonNode answer = json.readTree(call.output());
                return answer.isObject() && answer.size() == 3 && answer.path("explanation").isString()
                        && !answer.path("explanation").asString().isBlank() && answer.path("explanation").asString().length() <= 2_000
                        && answer.path("approved").isBoolean()
                        && answer.path("approved").asBoolean() == example.expectedValid()
                        && call.digest().equals(answer.path("digest").asString());
            } catch (RuntimeException exception) {
                return false;
            }
        });
        boolean valid = evaluated.actual() != null && evaluated.actual().valid();
        String text = evaluated.actual() == null || evaluated.actual().text() == null ? "" : evaluated.actual().text().toLowerCase(Locale.ROOT);
        boolean contentChecks = !example.expectedValid() || example.requiredConcepts().stream()
                .allMatch(alternatives -> alternatives.stream().anyMatch(value -> text.contains(value.toLowerCase(Locale.ROOT))))
                && example.forbiddenFragments().stream().noneMatch(value -> text.contains(value.toLowerCase(Locale.ROOT)));
        boolean formatOnly = "format".equals(example.category()) && !example.expectedValid()
                && evaluated.calls().stream().noneMatch(call -> "VALIDATE".equals(call.operation()));
        boolean passed = evaluated.failure() == null && evaluated.actual() != null && valid == example.expectedValid()
                && (semanticDecision || formatOnly) && contentChecks;
        return new CaseResult(example.id(), example.mode(), example.category(), example.expectedValid(), evaluated.actual(),
                evaluated.failure(), evaluated.calls(), semanticDecision, contentChecks, passed);
    }

    private Report report(Corpus corpus, List<CaseResult> results, String model, String revision, boolean passed)
            throws IOException, NoSuchAlgorithmException {
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(PATH)));
        return new Report(corpus.version(), digest, model, revision, ClosureMessageService.PROMPT_VERSION,
                ClosureMessageService.VALIDATION_VERSION, Map.of("temperature", "0", "maxTokens", "4096", "timeoutSeconds", "60",
                        "responseFormat", "json_schema", "schemaVersion", ClosureMessageModel.SCHEMA_VERSION, "reasoningEffort",
                        System.getenv().getOrDefault("PROPERTYFLOW_EVALUATION_REASONING_EFFORT", "none")),
                Instant.now().toString(), corpus.cases().size(), List.copyOf(results), passed);
    }

    private void save(Report report) throws IOException {
        Files.writeString(Path.of("target/closure-message-evaluation.json"), json.writerWithDefaultPrettyPrinter().writeValueAsString(report));
    }

    record Corpus(String version, boolean syntheticRulesOnly, List<Context> contexts, List<Example> cases) {
    }

    record Context(String id, List<ClosureAssessment.Message> messages, ClosureAssessment.Outcome ground,
            ClosureAssessment.Rule rule, int evidenceMessage) {

        ClosureAssessment.Result assessment() {
            List<String> criteria = new ArrayList<>();
            if (ground == ClosureAssessment.Outcome.RESOLVED) {
                criteria.addAll(List.of("PROBLEM_RESOLVED", "NO_FURTHER_HELP", "WHOLE_CASE_COVERED", "NO_CONTRADICTION"));
            } else {
                criteria.addAll(List.of("SCOPE", "WHOLE_CASE_COVERED", "NO_CONTRADICTION"));
                rule.prerequisites().keySet().forEach(key -> criteria.add("PREREQUISITE:" + key));
                rule.exclusions().keySet().forEach(key -> criteria.add("EXCLUSION_ABSENT:" + key));
            }
            ClosureAssessment.Message evidence = messages.get(evidenceMessage);
            List<ClosureAssessment.Proof> proofs = criteria.stream()
                    .map(criterion -> new ClosureAssessment.Proof(criterion, evidence.id(), evidence.text())).toList();
            // Die negativen Kontextfälle enthalten absichtlich eine fachlich überholte oder unzureichende Bewertung.
            return new ClosureAssessment.Result(ground, rule == null ? null : rule.id(), rule == null ? null : rule.version(), proofs,
                    "Synthetische Eingangsgrundlage; kein Ergebnis einer realen Grundbewertung.", null, "fixture", "fixture", "fixture");
        }
    }

    record Example(String id, String contextId, String mode, String category, String draft, boolean expectedValid,
            List<List<String>> requiredConcepts, List<String> forbiddenFragments) {
    }

    record ModelCall(String operation, String text, String digest, String output) {
    }

    record Evaluated(ClosureMessage.Result actual, String failure, List<ModelCall> calls) {
    }

    record CaseResult(String id, String mode, String category, boolean expectedValid, ClosureMessage.Result actual, String failure,
            List<ModelCall> calls, boolean semanticDecision, boolean contentChecks, boolean passed) {
    }

    record Report(String corpusVersion, String corpusSha256, String model, String modelRevision, String promptVersion,
            String validationVersion, Map<String, String> modelOptions, String evaluatedAt, int expectedCases,
            List<CaseResult> cases, boolean passed) {
    }
}
