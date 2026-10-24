package com.pertegato.propertyflow.triage;

import com.pertegato.propertyflow.triage.ai.SpringAiAnalysisModel;
import com.pertegato.propertyflow.triage.service.AnalyzeTool.ErrorCode;
import com.pertegato.propertyflow.triage.service.AnalyzeTool.Failure;
import com.pertegato.propertyflow.triage.service.AnalyzeTool.Request;
import com.pertegato.propertyflow.triage.service.AnalyzeTool.Response;
import com.pertegato.propertyflow.triage.service.AnalyzeTool.Success;
import com.pertegato.propertyflow.triage.service.AnalyzeTool;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Explicit opt-in: sends only the synthetic fixture below to local Ollama.
 */
@EnabledIfSystemProperty(named = "ollama.live", matches = "true")
class OllamaAnalyzeToolTest {

    @Test
    void successfulAndInvalidCall() {
        OpenAiChatModel model = OpenAiChatModel.builder()
                .options(OpenAiChatOptions.builder()
                        .baseUrl("http://localhost:11434/v1")
                        .apiKey("ollama")
                        .model(System.getProperty("ollama.model", "qwen3.5:4b"))
                        // A four-value classification does not need a long reasoning trace.
                        .reasoningEffort("none")
                        .maxTokens(32)
                        .temperature(0.0)
                        .timeout(Duration.ofSeconds(120))
                        .maxRetries(0)
                        .build())
                .build();
        SpringAiAnalysisModel adapter = new SpringAiAnalysisModel(model);
        AtomicInteger calls = new AtomicInteger();
        AnalyzeTool tool = new AnalyzeTool(text -> {
            calls.incrementAndGet();
            return adapter.analyze(text);
        });

        long started = System.nanoTime();
        Response success = tool.analyze(new Request("Im Keller platzt ein Wasserrohr. Wasser strömt ungehindert aus."));
        System.out.printf("Ollama analysis duration: %.2f s%n", (System.nanoTime() - started) / 1_000_000_000.0);
        System.out.println("Ollama success call: " + success);
        assertInstanceOf(Success.class, success);

        Response invalid = tool.analyze(new Request("   "));
        System.out.println("Ollama invalid call: " + invalid);
        assertEquals(ErrorCode.INVALID_INPUT, ((Failure) invalid).code());
        assertEquals(1, calls.get(), "Invalid input must not call Ollama");
    }
}
