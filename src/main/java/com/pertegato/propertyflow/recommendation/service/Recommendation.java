package com.pertegato.propertyflow.recommendation.service;

import java.util.List;

public interface Recommendation {

    Result recommend(String publicContext);

    record Source(String id, String version, String title, String location, String text) {
    }

    record Result(String responsibility, String procedure, List<Source> sources, List<String> questions,
            boolean humanRequired, String failure, String model, String promptVersion) {
        public Result {
            sources = List.copyOf(sources);
            questions = List.copyOf(questions);
        }
    }
}
