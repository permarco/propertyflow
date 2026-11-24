package com.pertegato.propertyflow.recommendation.service;

import java.util.List;

/**
 * Verwendet ausschliesslich den öffentlichen Verlauf und die belegte Abschlussgrundlage.
 */
public interface ClosureMessage {

    Draft draft(List<ClosureAssessment.Message> messages, ClosureAssessment.Result assessment);

    Result validate(List<ClosureAssessment.Message> messages, ClosureAssessment.Result assessment, Draft draft);

    default Result generate(List<ClosureAssessment.Message> messages, ClosureAssessment.Result assessment) {
        return validate(messages, assessment, draft(messages, assessment));
    }

    record Draft(String text, String model, String promptVersion) {
        public boolean valid() {
            return text != null && !text.isBlank();
        }
    }

    record Result(String text, boolean valid, String model, String promptVersion, String validationVersion) {
    }
}
