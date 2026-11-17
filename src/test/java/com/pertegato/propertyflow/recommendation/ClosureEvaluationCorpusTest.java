package com.pertegato.propertyflow.recommendation;

import com.pertegato.propertyflow.recommendation.service.ClosureAssessment;
import com.pertegato.propertyflow.recommendation.service.ClosureAssessment.Outcome;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClosureEvaluationCorpusTest {

    @Test
    void corpusCoversPositiveNegativeAndAmbiguousWholeCaseScenarios() throws Exception {
        ClosureEvaluationCorpus.Corpus corpus = new ClosureEvaluationCorpus().read();
        assertEquals("closure-ground-corpus-v1", corpus.version());
        assertEquals(24, corpus.cases().size());
        assertEquals(24, corpus.cases().stream().map(ClosureEvaluationCorpus.Example::id).distinct().count());
        assertEquals(Set.of("positive", "negative", "ambiguous"),
                corpus.cases().stream().map(ClosureEvaluationCorpus.Example::category).collect(Collectors.toSet()));
        for (ClosureEvaluationCorpus.Example example : corpus.cases()) {
            assertFalse(example.messages().isEmpty());
            assertFalse(example.expected().isEmpty());
            assertEquals("positive".equals(example.category()), example.expected().contains(Outcome.RESOLVED));
        }
        assertTrue(corpus.cases().stream().anyMatch(example -> "later-contradiction".equals(example.id())));
        assertTrue(corpus.cases().stream().anyMatch(example -> "prompt-injection".equals(example.id())));
        assertTrue(corpus.cases().stream().anyMatch(example -> "staff-only-resolution".equals(example.id())));
    }

    @Test
    void additionalAndRuleCorporaCoverBothDirectionsWithoutProductionRules() throws Exception {
        ClosureEvaluationCorpus.Corpus additional = new ClosureEvaluationCorpus(
                Path.of("src/test/resources/evaluation/closure-ground-additional-v1.json"), Path.of("target/unused.json")).read();
        assertEquals(14, additional.cases().size());
        assertEquals(7, additional.cases().stream().filter(example -> example.expected().contains(Outcome.RESOLVED)).count());
        ClosureEvaluationCorpus.Corpus rules = new ClosureEvaluationCorpus(
                Path.of("src/test/resources/evaluation/closure-rule-v2.json"), Path.of("target/unused.json")).read();
        assertEquals(13, rules.cases().size());
        assertEquals(2, rules.cases().stream().filter(example -> example.expected().contains(Outcome.TENANT_RESPONSIBILITY)).count());
        assertTrue(rules.cases().stream().flatMap(example -> example.rules().stream())
                .allMatch(rule -> rule.id().startsWith("EVAL-") && rule.version().equals("fixture-v1")));
    }

    @Test
    void invalidNegativeResultsAndInterruptedReportsNeverPass(@TempDir Path directory) throws Exception {
        JsonMapper json = JsonMapper.builder().build();
        ClosureEvaluationCorpus.Example negative = new ClosureEvaluationCorpus().read().cases().stream()
                .filter(example -> example.expected().contains(Outcome.REVIEW)).findFirst().orElseThrow();
        Path input = directory.resolve("corpus.json");
        Path report = directory.resolve("report.json");
        Files.writeString(input, json.writeValueAsString(new ClosureEvaluationCorpus.Corpus("fixture", List.of(negative))));
        ClosureEvaluationCorpus evaluator = new ClosureEvaluationCorpus(input, report);
        ClosureAssessment.Result invalid = new ClosureAssessment.Result(Outcome.REVIEW, null, null, List.of(), "",
                "INVALID_CLOSURE_ASSESSMENT", "fixture", "fixture", "fixture");
        assertFalse(evaluator.evaluate(example -> new ClosureEvaluationCorpus.Evaluated(invalid, "invalid"), "fixture", "fixture").passed());
        assertThrows(IllegalStateException.class, () -> evaluator.evaluate(example -> {
            throw new IllegalStateException("Unterbrochener Testlauf");
        }, "fixture", "fixture"));
        ClosureEvaluationCorpus.Report interrupted = json.readValue(Files.readString(report), ClosureEvaluationCorpus.Report.class);
        assertFalse(interrupted.passed());
        assertEquals(1, interrupted.expectedCases());
        assertTrue(interrupted.cases().isEmpty());
    }
}
