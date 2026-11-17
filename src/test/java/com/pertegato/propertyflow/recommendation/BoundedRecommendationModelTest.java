package com.pertegato.propertyflow.recommendation;

import com.pertegato.propertyflow.recommendation.ai.BoundedRecommendationModel;
import com.pertegato.propertyflow.recommendation.service.Recommendation;
import com.pertegato.propertyflow.recommendation.service.RecommendationService;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedRecommendationModelTest {

    @Test
    void providerTimeoutCancelsCallAndReturnsAuditableFailureWithoutProviderDetails() throws Exception {
        CountDownLatch interrupted = new CountDownLatch(1);
        try (BoundedRecommendationModel model = new BoundedRecommendationModel(input -> {
            try {
                new CountDownLatch(1).await();
                throw new AssertionError("Abbruch erwartet");
            } catch (InterruptedException exception) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Vertrauliche Anbieterdiagnose");
            }
        }, Duration.ofMillis(100))) {
            Recommendation.Source source = new Recommendation.Source("TEST", "1", "Testquelle", "Test", "Testinhalt");
            RecommendationService service = new RecommendationService(context -> List.of(source), model, "test");
            Recommendation.Result result = assertTimeout(Duration.ofSeconds(2), () -> service.recommend("Heizung kalt"));
            assertEquals("RECOMMENDATION_UNAVAILABLE", result.failure());
            assertTrue(result.humanRequired());
            assertTrue(interrupted.await(1, TimeUnit.SECONDS));
        }
    }
}
