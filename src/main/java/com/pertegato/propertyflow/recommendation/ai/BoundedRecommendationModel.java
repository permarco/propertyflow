package com.pertegato.propertyflow.recommendation.ai;

import com.pertegato.propertyflow.recommendation.service.RecommendationService.Model;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Begrenzt Wartezeit und Parallelität auch bei ignoriertem Abbruch des Modellaufrufs.
 */
public final class BoundedRecommendationModel implements Model, AutoCloseable {

    private final Model delegate;
    private final Duration timeout;
    private final ThreadPoolExecutor executor;

    public BoundedRecommendationModel(Model delegate, Duration timeout) {
        if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofMinutes(1)) > 0) {
            throw new IllegalArgumentException("Model timeout must be positive and at most one minute");
        }
        this.delegate = delegate;
        this.timeout = timeout;
        this.executor = new ThreadPoolExecutor(4, 4, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(4),
                Thread.ofPlatform().daemon().name("propertyflow-recommendation-", 0).factory(), new ThreadPoolExecutor.AbortPolicy());
    }

    @Override
    public String recommend(String text) {
        Future<String> result = executor.submit(() -> delegate.recommend(text));
        try {
            return result.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            result.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("MODEL_INTERRUPTED");
        } catch (ExecutionException | TimeoutException exception) {
            result.cancel(true);
            throw new IllegalStateException("MODEL_UNAVAILABLE");
        } finally {
            executor.purge();
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
