package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.recommendation.service.ClosureAssessment;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class AutomaticClosureBasisTest {

    @Test
    void reorderedDistinctProofsReuseTheSamePublicationBasis() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        List<ClosureAssessment.Proof> proofs = List.of(
                new ClosureAssessment.Proof("PROBLEM_RESOLVED", first, "Fenster behoben."),
                new ClosureAssessment.Proof("PROBLEM_RESOLVED", second, "Klingel behoben."),
                new ClosureAssessment.Proof("PROBLEM_RESOLVED", second, "Beide Anliegen erledigt."),
                new ClosureAssessment.Proof("NO_FURTHER_HELP", second, "Keine weitere Hilfe erforderlich."),
                new ClosureAssessment.Proof("WHOLE_CASE_COVERED", second, "Beide Anliegen erledigt."),
                new ClosureAssessment.Proof("NO_CONTRADICTION", second, "Beide Anliegen erledigt."));
        List<ClosureAssessment.Proof> reversed = new ArrayList<>(proofs);
        Collections.reverse(reversed);
        assertEquals(AutomaticClosureService.basisHash(ground(proofs)), AutomaticClosureService.basisHash(ground(reversed)));
        reversed.set(0, new ClosureAssessment.Proof("NO_CONTRADICTION", second, "Andere Bestätigung."));
        assertNotEquals(AutomaticClosureService.basisHash(ground(proofs)), AutomaticClosureService.basisHash(ground(reversed)));
    }

    private ClosureAssessment.Result ground(List<ClosureAssessment.Proof> proofs) {
        return new ClosureAssessment.Result(ClosureAssessment.Outcome.RESOLVED, null, null, proofs,
                "Synthetischer Nachweis der Veröffentlichungsidentität.", null, "fixture", "fixture", "fixture");
    }
}
