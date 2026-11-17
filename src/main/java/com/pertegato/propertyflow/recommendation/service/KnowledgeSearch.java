package com.pertegato.propertyflow.recommendation.service;

import com.pertegato.propertyflow.recommendation.service.Recommendation.Source;
import java.util.List;

public interface KnowledgeSearch {

    List<Source> search(String publicContext);
}
