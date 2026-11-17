package com.pertegato.propertyflow.recommendation;

import com.pertegato.propertyflow.recommendation.service.ClosureAssessment;
import java.util.List;

/**
 * Hält fachfremde Integrationstests unabhängig vom Modell; kein Nachweis semantischer Erkennungsqualität.
 */
public final class ClosureAssessmentFixtures {

    private ClosureAssessmentFixtures() {
    }

    public static ClosureAssessment noClosure() {
        return messages -> new ClosureAssessment.Result(ClosureAssessment.Outcome.NONE, null, null, List.of(),
                "Testfall ohne Abschlussvorschlag", null, "test", "fixture-v1", "fixture-validation-v1");
    }
}
