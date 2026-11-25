package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.recommendation.service.ClosureAssessment;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Die fachliche Evaluation von Abschlussbewertung und Mitteilung ist noch nicht bestanden.
 * Eine Konfiguration oder ein positiver Modellvorschlag darf diese Sperre nicht aufheben.
 */
@Component
@Profile("propertyflow-backend")
public class AutomaticClosureRelease {

    public boolean approved(ClosureAssessment.Result assessment) {
        return false;
    }
}
