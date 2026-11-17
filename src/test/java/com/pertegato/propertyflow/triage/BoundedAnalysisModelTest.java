package com.pertegato.propertyflow.triage;

import com.pertegato.propertyflow.triage.ai.BoundedAnalysisModel;
import com.pertegato.propertyflow.triage.service.AnalyzeTool;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedAnalysisModelTest {

    @Test
    void timeoutBecomesTypedFailureAndCancelsProviderWithoutLeakingItsMessage() throws Exception {
        CountDownLatch interrupted = new CountDownLatch(1);
        try (BoundedAnalysisModel model = new BoundedAnalysisModel(text -> {
            try {
                new CountDownLatch(1).await();
                throw new AssertionError("Should have been cancelled");
            } catch (InterruptedException exception) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
                throw new IllegalStateException("secret provider detail");
            }
        }, Duration.ofMillis(100))) {
            AnalyzeTool.Response response = assertTimeout(Duration.ofSeconds(2), () -> new AnalyzeTool(model).analyze(new AnalyzeTool.Request("Kalt")));
            assertEquals(AnalyzeTool.ErrorCode.MODEL_UNAVAILABLE, assertInstanceOf(AnalyzeTool.Failure.class, response).code());
            assertTrue(interrupted.await(1, TimeUnit.SECONDS));
        }
    }
}
