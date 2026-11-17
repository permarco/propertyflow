package com.pertegato.propertyflow.recommendation;

import com.pertegato.propertyflow.recommendation.ai.BoundedRecommendationModel;
import com.pertegato.propertyflow.recommendation.ai.ClosureMessageModel;
import com.pertegato.propertyflow.recommendation.service.ClosureMessage;
import com.pertegato.propertyflow.recommendation.service.ClosureMessageService;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "propertyflow.workflow.enabled=false")
@ActiveProfiles("propertyflow-backend")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_RUN_CLOSURE_EVALUATION", matches = "true")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_DATABASE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_EVALUATION_BASE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_EVALUATION_MODEL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_EVALUATION_MODEL_REVISION", matches = ".+")
class ClosureMessageModelEvaluationTest {

    @Autowired
    private ObjectProvider<ChatModel> models;
    private final JsonMapper json = JsonMapper.builder().build();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        ClosureModelEvaluationTest.properties(properties);
    }

    @Test
    void realModelMustGenerateIndividualMessagesAndRejectUnsubstantiatedDrafts() throws Exception {
        ChatModel model = models.getIfAvailable();
        assertNotNull(model);
        ClosureMessageModel communication = new ClosureMessageModel(ChatClient.create(model));
        String modelName = System.getenv("PROPERTYFLOW_EVALUATION_MODEL");
        ClosureMessageEvaluationCorpus.Report report = new ClosureMessageEvaluationCorpus().evaluate((example, context) -> {
            List<ClosureMessageEvaluationCorpus.ModelCall> calls = new CopyOnWriteArrayList<>();
            try (BoundedRecommendationModel bounded = new BoundedRecommendationModel(input -> {
                JsonNode request = json.readTree(input);
                String output = communication.recommend(input);
                calls.add(new ClosureMessageEvaluationCorpus.ModelCall(request.path("operation").asString(),
                        request.path("text").asString(null), request.path("digest").asString(null), output));
                return output;
            }, Duration.ofSeconds(60))) {
                ClosureMessageService service = new ClosureMessageService(input -> {
                    JsonNode request = json.readTree(input);
                    if ("CHECK_DRAFT".equals(example.mode()) && "GENERATE".equals(request.path("operation").asString())) {
                        return json.writeValueAsString(Map.of("text", example.draft(), "ground", context.ground().name()));
                    }
                    return bounded.recommend(input);
                }, modelName, () -> context.rule() == null ? List.of() : List.of(context.rule()));
                ClosureMessage.Result result = service.generate(context.messages(), context.assessment());
                return new ClosureMessageEvaluationCorpus.Evaluated(result, null, List.copyOf(calls));
            } catch (RuntimeException exception) {
                return new ClosureMessageEvaluationCorpus.Evaluated(null, exception.getClass().getSimpleName(), List.copyOf(calls));
            }
        }, modelName, System.getenv("PROPERTYFLOW_EVALUATION_MODEL_REVISION"));
        assertTrue(report.passed(), () -> "Fehlgeschlagene Kommunikationsfälle: " + report.cases().stream()
                .filter(result -> !result.passed()).map(ClosureMessageEvaluationCorpus.CaseResult::id).toList());
    }
}
