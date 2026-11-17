package com.pertegato.propertyflow.recommendation.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Liefert belegte Abschlussvorschläge. Prozessfreigabe und Speicherung bleiben beim Fallmodul.
 */
public interface ClosureAssessment {

    Result assess(List<Message> messages);

    default boolean current(Result result, List<Message> messages) {
        return false;
    }

    enum Outcome {
        NONE,
        REVIEW,
        RESOLVED,
        TENANT_RESPONSIBILITY
    }

    record Message(UUID id, String author, String text, long sequence) {
    }

    record Rule(String id, String version, String scope, Map<String, String> prerequisites, Map<String, String> exclusions) {
        public Rule {
            // Gleiche Regelfassungen ergeben auch über Neustarts hinweg dieselbe Reihenfolge im Modellkontext.
            prerequisites = Collections.unmodifiableMap(new TreeMap<>(prerequisites));
            exclusions = Collections.unmodifiableMap(new TreeMap<>(exclusions));
        }
    }

    record Proof(String criterion, UUID messageId, String quote) {
    }

    record Result(Outcome outcome, String ruleId, String ruleVersion, List<Proof> proofs, String explanation,
            String failure, String model, String promptVersion, String validationVersion) {
        public Result {
            proofs = List.copyOf(proofs);
        }

        public boolean proposed() {
            return failure == null && (outcome == Outcome.RESOLVED || outcome == Outcome.TENANT_RESPONSIBILITY);
        }
    }
}
