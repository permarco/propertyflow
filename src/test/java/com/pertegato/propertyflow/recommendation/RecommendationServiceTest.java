package com.pertegato.propertyflow.recommendation;

import com.pertegato.propertyflow.recommendation.knowledge.ApprovedFileKnowledge;
import com.pertegato.propertyflow.recommendation.service.Recommendation.Result;
import com.pertegato.propertyflow.recommendation.service.Recommendation.Source;
import com.pertegato.propertyflow.recommendation.service.RecommendationService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RecommendationServiceTest {

    @TempDir
    private Path directory;

    private final Source source = new Source("heating", "1", "Heizung", "approved/heating", "Heizung durch Verwaltung prüfen lassen.");

    @Test
    void missingKnowledgeDoesNotInvokeModelOrInventARecommendation() {
        AtomicInteger calls = new AtomicInteger();
        Result result = new RecommendationService(new ApprovedFileKnowledge(""), input -> {
            calls.incrementAndGet();
            return "{}";
        }, "test").recommend("Heizung kalt");
        assertEquals("APPROVED_KNOWLEDGE_MISSING", result.failure());
        assertEquals(0, calls.get());
    }

    @Test
    void retainsExactApprovedSourceVersionAndRejectsInventedCitation() {
        Result valid = service(output("heating", "1", "Heizung durch Verwaltung prüfen lassen.")).recommend("Heizung kalt");
        assertNull(valid.failure());
        assertEquals(List.of(source), valid.sources());
        assertEquals("INVALID_RECOMMENDATION", service(output("foreign", "1", source.text())).recommend("Heizung kalt").failure());
        assertEquals("INVALID_RECOMMENDATION", service(output("heating", "2", source.text())).recommend("Heizung kalt").failure());
        assertEquals("INVALID_RECOMMENDATION", service(output("heating", "1", "Mieter muss bezahlen")).recommend("Heizung kalt").failure());
    }

    @Test
    void retrievalExcludesUnapprovedDocumentsAndRetainsVersion() throws Exception {
        Files.writeString(directory.resolve("approved.json"), """
                {"id":"heating","version":"3","title":"Heizung","location":"approved/heating",
                "text":"Die Heizung prüfen.","approved":true}
                """);
        Files.writeString(directory.resolve("draft.json"), """
                {"id":"draft","version":"1","title":"Heizung","location":"draft",
                "text":"Die Heizung austauschen.","approved":false}
                """);
        List<Source> found = new ApprovedFileKnowledge(directory.toString()).search("Heizung ausgefallen");
        assertEquals(1, found.size());
        assertEquals("3", found.getFirst().version());
        assertEquals(List.of(), new ApprovedFileKnowledge(directory.toString()).search("Wasser tropft"));
    }

    private RecommendationService service(String output) {
        return new RecommendationService(input -> List.of(source), input -> output, "test");
    }

    private String output(String id, String version, String evidence) {
        return """
                {"responsibility":"Verwaltung prüfen lassen","procedure":"Mitarbeiter prüft","humanRequired":true,
                "questions":["RQ-001"],"sources":[{"id":"%s","version":"%s","evidence":"%s"}]}
                """.formatted(id, version, evidence);
    }
}
