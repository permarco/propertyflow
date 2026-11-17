package com.pertegato.propertyflow.recommendation;

import com.pertegato.propertyflow.recommendation.service.ClosureMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClosureMessageEvaluationCorpusTest {

    private final ClosureMessageEvaluationCorpus corpus = new ClosureMessageEvaluationCorpus();

    @Test
    void corpusSeparatesGenerationSemanticChecksAndFormalGuards() throws Exception {
        ClosureMessageEvaluationCorpus.Corpus data = corpus.read();
        assertTrue(data.syntheticRulesOnly());
        assertEquals(34, data.cases().size());
        assertEquals(34, data.cases().stream().map(ClosureMessageEvaluationCorpus.Example::id).distinct().count());
        assertEquals(5, data.cases().stream().filter(example -> "GENERATE".equals(example.mode())).count());
        assertEquals(3, data.cases().stream().filter(example -> "format".equals(example.category())).count());
        for (ClosureMessageEvaluationCorpus.Example example : data.cases()) {
            assertTrue(data.contexts().stream().anyMatch(context -> context.id().equals(example.contextId())));
            if ("GENERATE".equals(example.mode())) {
                assertTrue(example.expectedValid());
                assertFalse(example.requiredConcepts().isEmpty());
            }
        }
    }

    @Test
    void invalidOrUnboundRejectionsAreNotSuccessfulSemanticEvaluations() throws Exception {
        ClosureMessageEvaluationCorpus.Example example = corpus.read().cases().stream()
                .filter(candidate -> "invented-technician".equals(candidate.id())).findFirst().orElseThrow();
        ClosureMessage.Result invalid = new ClosureMessage.Result(null, false, "fixture", "fixture", "fixture");
        for (String output : List.of("not-json", "{\"approved\":false,\"digest\":\"bound\"}",
                "{\"explanation\":\"Synthetische Prüfbegründung.\",\"approved\":false,\"digest\":\"other\"}",
                "{\"explanation\":\"Synthetische Prüfbegründung.\",\"approved\":true,\"digest\":\"bound\"}")) {
            assertFalse(corpus.judge(example, new ClosureMessageEvaluationCorpus.Evaluated(invalid, null,
                    List.of(new ClosureMessageEvaluationCorpus.ModelCall("VALIDATE", example.draft(), "bound", output)))).passed());
        }
        assertFalse(corpus.judge(example, new ClosureMessageEvaluationCorpus.Evaluated(invalid, null, List.of())).passed());
        assertFalse(corpus.judge(example, new ClosureMessageEvaluationCorpus.Evaluated(null, "timeout", List.of())).passed());
        assertTrue(corpus.judge(example, new ClosureMessageEvaluationCorpus.Evaluated(invalid, null,
                List.of(new ClosureMessageEvaluationCorpus.ModelCall("VALIDATE", example.draft(), "bound",
                        "{\"explanation\":\"Synthetische Prüfbegründung.\",\"approved\":false,\"digest\":\"bound\"}")))).passed());
    }

    @Test
    void modelApprovalDoesNotHideTheObservedUnresolvedDoorText() throws Exception {
        ClosureMessageEvaluationCorpus.Example example = corpus.read().cases().stream()
                .filter(candidate -> "generate-door".equals(candidate.id())).findFirst().orElseThrow();
        ClosureMessage.Result incorrect = new ClosureMessage.Result("Die Wohnungstür klemmt.", true, "fixture", "fixture", "fixture");
        ClosureMessageEvaluationCorpus.CaseResult judged = corpus.judge(example,
                new ClosureMessageEvaluationCorpus.Evaluated(incorrect, null,
                        List.of(new ClosureMessageEvaluationCorpus.ModelCall("VALIDATE", incorrect.text(), "bound",
                                "{\"explanation\":\"Synthetische Prüfbegründung.\",\"approved\":true,\"digest\":\"bound\"}"))));
        assertTrue(judged.semanticDecision());
        assertFalse(judged.contentChecks());
        assertFalse(judged.passed());
    }
}
