package com.pertegato.propertyflow.recommendation.service;

import java.util.List;

/**
 * Verwendet ausschliesslich den öffentlichen Verlauf und die belegte Abschlussgrundlage.
 */
public interface ClosureMessage {

    Result generate(List<ClosureAssessment.Message> messages, ClosureAssessment.Result assessment);

    record Result(String text, boolean valid, String model, String promptVersion, String validationVersion) {
    }
}
