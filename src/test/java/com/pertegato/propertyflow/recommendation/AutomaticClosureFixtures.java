package com.pertegato.propertyflow.recommendation;

import com.pertegato.propertyflow.caseprocessing.service.AutomaticClosureRelease;
import com.pertegato.propertyflow.recommendation.service.ClosureAssessment;
import com.pertegato.propertyflow.recommendation.service.ClosureAssessmentService;
import com.pertegato.propertyflow.recommendation.service.ClosureMessage;
import com.pertegato.propertyflow.recommendation.service.ClosureMessageService;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Nur synthetische Infrastrukturtests; diese Freigabe ist kein Nachweis der Modellqualität.
 */
public final class AutomaticClosureFixtures {

    private AutomaticClosureFixtures() {
    }

    public static ClosureAssessment assessment() {
        JsonMapper json = JsonMapper.builder().build();
        return new ClosureAssessmentService(context -> {
            JsonNode messages = json.readTree(context).path("messages");
            JsonNode last = messages.get(0);
            for (JsonNode message : messages) {
                if ("TENANT".equals(message.path("author").asString())) {
                    last = message;
                }
            }
            String id = last.path("id").asString();
            String quote = last.path("text").asString();
            return json.writeValueAsString(Map.of("outcome", "RESOLVED", "ruleId", json.nullNode(), "ruleVersion", json.nullNode(),
                    "proofs", List.of("PROBLEM_RESOLVED", "NO_FURTHER_HELP", "WHOLE_CASE_COVERED", "NO_CONTRADICTION").stream()
                            .map(criterion -> Map.of("criterion", criterion, "messageId", id, "quote", quote)).toList(),
                    "explanation", "Synthetische Gesamtbehebung", "promptVersion", ClosureAssessmentService.PROMPT_VERSION));
        }, "synthetic-fixture", List::of);
    }

    public static ClosureMessage.Result message() {
        return new ClosureMessage.Result("Sie haben die vollständige Behebung der Heizungsstörung bestätigt. Wir schliessen Ihren Fall ab.",
                true, "synthetic-fixture", ClosureMessageService.PROMPT_VERSION, ClosureMessageService.VALIDATION_VERSION);
    }

    public static final class Release extends AutomaticClosureRelease {

        public boolean enabled = true;

        @Override
        public boolean approved(ClosureAssessment.Result result) {
            return enabled && result != null && result.proposed();
        }
    }
}
