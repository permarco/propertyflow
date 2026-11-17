package com.pertegato.propertyflow.recommendation.config;

import com.pertegato.propertyflow.recommendation.ai.BoundedRecommendationModel;
import com.pertegato.propertyflow.recommendation.ai.ClosureMessageModel;
import com.pertegato.propertyflow.recommendation.knowledge.ApprovedClosureRules;
import com.pertegato.propertyflow.recommendation.service.ClosureAssessment;
import com.pertegato.propertyflow.recommendation.service.ClosureAssessmentService;
import com.pertegato.propertyflow.recommendation.service.ClosureMessage;
import com.pertegato.propertyflow.recommendation.service.ClosureMessageService;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("propertyflow-backend & !frontend-demo")
public class ClosureAssessmentConfiguration implements AutoCloseable {

    private BoundedRecommendationModel bounded;
    private BoundedRecommendationModel communication;

    @Bean
    ClosureAssessment closureAssessment(ObjectProvider<ChatModel> models,
            @Value("${propertyflow.recommendation.knowledge-directory:}") String directory,
            @Value("${propertyflow.triage.timeout:20s}") Duration timeout,
            @Value("${spring.ai.openai.chat.options.model:unconfigured}") String modelName) {
        ChatModel model = models.getIfAvailable();
        ChatClient client = model == null ? null : ChatClient.create(model);
        bounded = new BoundedRecommendationModel(input -> {
            if (client == null) {
                throw new IllegalStateException("MODEL_NOT_CONFIGURED");
            }
            return client.prompt()
                    .system(ClosureAssessmentService.systemPromptFor(input))
                    .user(input)
                    .call()
                    .content();
        }, timeout);
        ApprovedClosureRules rules = new ApprovedClosureRules(directory, Clock.systemUTC());
        return new ClosureAssessmentService(bounded::recommend, modelName, rules::load);
    }

    @Bean
    ClosureMessage closureMessage(ObjectProvider<ChatModel> models,
            @Value("${propertyflow.recommendation.knowledge-directory:}") String directory,
            @Value("${propertyflow.triage.timeout:20s}") Duration timeout,
            @Value("${spring.ai.openai.chat.options.model:unconfigured}") String modelName) {
        ChatModel model = models.getIfAvailable();
        ChatClient client = model == null ? null : ChatClient.create(model);
        communication = new BoundedRecommendationModel(new ClosureMessageModel(client), timeout);
        ApprovedClosureRules rules = new ApprovedClosureRules(directory, Clock.systemUTC());
        return new ClosureMessageService(communication::recommend, modelName, rules::load);
    }

    @Override
    @PreDestroy
    public void close() {
        if (communication != null) {
            communication.close();
        }
        if (bounded != null) {
            bounded.close();
        }
    }
}
