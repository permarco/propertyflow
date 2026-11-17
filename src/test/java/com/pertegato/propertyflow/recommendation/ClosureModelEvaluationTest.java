package com.pertegato.propertyflow.recommendation;

import com.pertegato.propertyflow.recommendation.ai.BoundedRecommendationModel;
import com.pertegato.propertyflow.recommendation.service.ClosureAssessment;
import com.pertegato.propertyflow.recommendation.service.ClosureAssessmentService;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bewusst freizugebender echter Modelllauf. Der Standardtestlauf ruft keinen Modellserver auf.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "propertyflow.workflow.enabled=false")
@ActiveProfiles("propertyflow-backend")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_RUN_CLOSURE_EVALUATION", matches = "true")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_DATABASE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_EVALUATION_BASE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_EVALUATION_MODEL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_EVALUATION_MODEL_REVISION", matches = ".+")
class ClosureModelEvaluationTest {

    @Autowired
    private ObjectProvider<ChatModel> models;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("PROPERTYFLOW_TEST_DATABASE_URL"));
        properties.add("spring.datasource.username", () -> "propertyflow_test");
        properties.add("spring.datasource.password", () -> "propertyflow_test");
        properties.add("propertyflow.links.encryption-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
        properties.add("spring.ai.model.chat", () -> "openai");
        properties.add("spring.ai.openai.base-url", () -> System.getenv("PROPERTYFLOW_EVALUATION_BASE_URL"));
        properties.add("spring.ai.openai.chat.options.model", () -> System.getenv("PROPERTYFLOW_EVALUATION_MODEL"));
        properties.add("spring.ai.openai.api-key", () -> System.getenv().getOrDefault("PROPERTYFLOW_EVALUATION_API_KEY", "local-evaluation"));
        properties.add("spring.ai.openai.chat.options.temperature", () -> "0");
        properties.add("spring.ai.openai.chat.options.max-tokens", () -> "4096");
        properties.add("spring.ai.openai.chat.options.reasoning-effort",
                () -> System.getenv().getOrDefault("PROPERTYFLOW_EVALUATION_REASONING_EFFORT", "none"));
        properties.add("propertyflow.triage.timeout", () -> "60s");
        properties.add("propertyflow.recommendation.knowledge-directory", () -> "");
    }

    @Test
    void realModelMustPassAllPositiveNegativeAndAmbiguousCases() throws Exception {
        evaluate(new ClosureEvaluationCorpus());
    }

    @Test
    void realModelMustPassAdditionalWholeCaseExamples() throws Exception {
        evaluate(new ClosureEvaluationCorpus(Path.of("src/test/resources/evaluation/closure-ground-additional-v1.json"),
                Path.of("target/closure-additional-evaluation.json")));
    }

    @Test
    void realModelMustRespectEverySyntheticRulePrerequisiteAndException() throws Exception {
        evaluate(new ClosureEvaluationCorpus(Path.of("src/test/resources/evaluation/closure-rule-v2.json"),
                Path.of("target/closure-rule-evaluation.json")));
    }

    private void evaluate(ClosureEvaluationCorpus corpus) throws Exception {
        ChatModel chatModel = models.getIfAvailable();
        assertNotNull(chatModel, "Der explizite Evaluationslauf benötigt einen konfigurierten ChatModel-Adapter.");
        ChatClient client = ChatClient.create(chatModel);
        String modelName = System.getenv("PROPERTYFLOW_EVALUATION_MODEL");
        ClosureEvaluationCorpus.Report report = corpus.evaluate(example -> {
            AtomicReference<String> output = new AtomicReference<>();
            try (BoundedRecommendationModel bounded = new BoundedRecommendationModel(input -> {
                String content = client.prompt()
                        .system(ClosureAssessmentService.systemPromptFor(input))
                        .user(input)
                        .call()
                        .content();
                output.set(content);
                return content;
            }, Duration.ofSeconds(60))) {
                ClosureAssessment assessment = new ClosureAssessmentService(bounded::recommend, modelName, example::rules);
                return new ClosureEvaluationCorpus.Evaluated(assessment.assess(example.messages()), output.get());
            }
        }, modelName, System.getenv("PROPERTYFLOW_EVALUATION_MODEL_REVISION"));
        assertTrue(report.passed(), () -> "Fehlgeschlagene Modellfälle: " + report.cases().stream()
                .filter(result -> !result.passed()).map(ClosureEvaluationCorpus.CaseResult::id).toList());
    }
}
