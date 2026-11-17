package com.pertegato.propertyflow.caseprocessing.contract;

import java.util.List;

public record CaseRecommendation(String responsibility, String procedure, List<String> sources, String failure, boolean current,
        String closureAssessment) {
    public CaseRecommendation {
        sources = List.copyOf(sources);
    }
}
