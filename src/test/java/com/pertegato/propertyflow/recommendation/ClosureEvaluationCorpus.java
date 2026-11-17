package com.pertegato.propertyflow.recommendation;

import com.pertegato.propertyflow.recommendation.service.ClosureAssessment;
import com.pertegato.propertyflow.recommendation.service.ClosureAssessment.Message;
import com.pertegato.propertyflow.recommendation.service.ClosureAssessment.Outcome;
import com.pertegato.propertyflow.recommendation.service.ClosureAssessment.Result;
import com.pertegato.propertyflow.recommendation.service.ClosureAssessmentService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import tools.jackson.databind.json.JsonMapper;

/**
 * Versionierter, ausschliesslich synthetischer Bestand; Formatprüfungen zählen nicht als Modellnachweis.
 */
final class ClosureEvaluationCorpus {

    private final Path path;
    private final Path reportPath;
    private final JsonMapper json = JsonMapper.builder().build();

    ClosureEvaluationCorpus() {
        this(Path.of("src/test/resources/evaluation/closure-ground-v1.json"), Path.of("target/closure-model-evaluation.json"));
    }

    ClosureEvaluationCorpus(Path path, Path reportPath) {
        this.path = path;
        this.reportPath = reportPath;
    }

    Corpus read() throws IOException {
        return json.readValue(Files.readString(path), Corpus.class);
    }

    Report evaluate(Function<Example, Evaluated> evaluator, String model, String revision) throws IOException, NoSuchAlgorithmException {
        Corpus corpus = read();
        List<CaseResult> results = new ArrayList<>();
        save(report(corpus, results, model, revision, false));
        for (Example example : corpus.cases()) {
            Evaluated evaluated = evaluator.apply(example);
            Result actual = evaluated.actual();
            boolean passed = actual.failure() == null && example.expected().contains(actual.outcome());
            results.add(new CaseResult(example.id(), example.category(), example.expected(), actual, evaluated.modelOutput(), passed));
            // Ein Zwischenstand bleibt auch bei Abbruch ausdrücklich unvollständig und nicht bestanden.
            save(report(corpus, results, model, revision, false));
        }
        Report report = report(corpus, results, model, revision,
                results.size() == corpus.cases().size() && results.stream().allMatch(CaseResult::passed));
        save(report);
        return report;
    }

    private Report report(Corpus corpus, List<CaseResult> results, String model, String revision, boolean passed)
            throws IOException, NoSuchAlgorithmException {
        List<Outcome> grounds = corpus.cases().stream().flatMap(example -> example.expected().stream())
                .filter(outcome -> outcome == Outcome.RESOLVED || outcome == Outcome.TENANT_RESPONSIBILITY).distinct().sorted().toList();
        return new Report(corpus.version(), digest(), model, revision, ClosureAssessmentService.PROMPT_VERSION,
                ClosureAssessmentService.VALIDATION_VERSION, options(), Instant.now().toString(), grounds,
                corpus.cases().size(), List.copyOf(results), passed);
    }

    private Map<String, String> options() {
        return Map.of("temperature", "0", "maxTokens", "4096", "timeoutSeconds", "60", "reasoningEffort",
                System.getenv().getOrDefault("PROPERTYFLOW_EVALUATION_REASONING_EFFORT", "none"));
    }

    private String digest() throws IOException, NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private void save(Report report) throws IOException {
        Files.createDirectories(reportPath.getParent());
        Files.writeString(reportPath, json.writerWithDefaultPrettyPrinter().writeValueAsString(report));
    }

    record Corpus(String version, List<Example> cases) {
    }

    record Example(String id, String category, List<Message> messages, List<Outcome> expected, List<ClosureAssessment.Rule> rules) {
        Example {
            rules = rules == null ? List.of() : List.copyOf(rules);
        }
    }

    record Evaluated(Result actual, String modelOutput) {
    }

    record CaseResult(String id, String category, List<Outcome> expected, Result actual, String modelOutput, boolean passed) {
    }

    record Report(String corpusVersion, String corpusSha256, String model, String modelRevision, String promptVersion,
            String validationVersion, Map<String, String> modelOptions, String evaluatedAt, List<Outcome> evaluatedGrounds, int expectedCases,
            List<CaseResult> cases, boolean passed) {
    }
}
