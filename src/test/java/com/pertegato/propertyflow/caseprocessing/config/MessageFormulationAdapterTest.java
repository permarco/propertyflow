package com.pertegato.propertyflow.caseprocessing.config;

import com.pertegato.propertyflow.caseprocessing.service.MessageFormulationModel;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageFormulationAdapterTest {

    @Test
    void configuredSpringAiAdapterStreamsThroughLocalProtocolStubWithoutCallingARealModel() throws Exception {
        HttpServer provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        CountDownLatch finish = new CountDownLatch(1);
        CountDownLatch firstChunk = new CountDownLatch(1);
        AtomicReference<String> request = new AtomicReference<>();
        provider.createContext("/v1/chat/completions", exchange -> {
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try {
                exchange.getResponseBody().write(chunk("Guten Tag, ").getBytes(StandardCharsets.UTF_8));
                exchange.getResponseBody().flush();
                finish.await(5, TimeUnit.SECONDS);
                exchange.getResponseBody().write((chunk("der Techniker kommt Dienstag.") + "data: [DONE]\n\n")
                        .getBytes(StandardCharsets.UTF_8));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        provider.start();
        try {
            ChatModel chat = OpenAiChatModel.builder()
                    .options(OpenAiChatOptions.builder()
                            .baseUrl("http://127.0.0.1:" + provider.getAddress().getPort() + "/v1")
                            .apiKey("synthetic-test-key")
                            .model("protocol-stub")
                            .timeout(Duration.ofSeconds(10))
                            .maxRetries(0)
                            .build())
                    .build();
            DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
            beans.registerSingleton("model", chat);
            MessageFormulationModel adapter = new MessageFormulationConfiguration().messageFormulationModel(beans.getBeanProvider(ChatModel.class));
            CompletableFuture<List<String>> output = adapter.formulate("Techniker kommt Dienstag.")
                    .doOnNext(text -> firstChunk.countDown()).collectList().toFuture();
            assertThat(firstChunk.await(4, TimeUnit.SECONDS)).isTrue();
            assertThat(output.isDone()).isFalse();
            finish.countDown();
            assertThat(String.join("", output.get(5, TimeUnit.SECONDS))).isEqualTo("Guten Tag, der Techniker kommt Dienstag.");
            JsonNode payload = JsonMapper.builder().build().readTree(request.get());
            assertThat(payload.path("stream").asBoolean()).isTrue();
            assertThat(payload.path("messages").size()).isEqualTo(2);
            assertThat(payload.path("messages").get(1).path("content").asString()).isEqualTo("Techniker kommt Dienstag.");
            assertThat(payload.has("tools")).isFalse();
        } finally {
            finish.countDown();
            provider.stop(0);
        }
    }

    @Test
    void missingProviderFailsWithoutFakeSuggestion() {
        MessageFormulationModel adapter = new MessageFormulationConfiguration()
                .messageFormulationModel(new DefaultListableBeanFactory().getBeanProvider(ChatModel.class));
        assertThatThrownBy(() -> adapter.formulate("Text").blockLast()).hasMessage("MODEL_NOT_CONFIGURED");
    }

    private String chunk(String text) {
        return "data: " + JsonMapper.builder().build().writeValueAsString(java.util.Map.of(
                "id", "test", "object", "chat.completion.chunk", "created", 1, "model", "protocol-stub",
                "choices", List.of(java.util.Map.of("index", 0, "delta", java.util.Map.of("content", text))))) + "\n\n";
    }
}
