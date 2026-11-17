package com.pertegato.propertyflow.triage.config;

import com.pertegato.propertyflow.triage.ai.BoundedAnalysisModel;
import com.pertegato.propertyflow.triage.ai.SpringAiAnalysisModel;
import com.pertegato.propertyflow.triage.service.AnalyzeTool;
import com.pertegato.propertyflow.triage.service.AnalyzeTool.AnalysisModel;
import java.time.Duration;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("propertyflow-backend & !frontend-demo")
public class TriageConfiguration {

    @Bean(destroyMethod = "close")
    BoundedAnalysisModel analysisModel(ObjectProvider<ChatModel> models,
            @Value("${propertyflow.triage.timeout:20s}") Duration timeout) {
        ChatModel configured = models.getIfAvailable();
        AnalysisModel delegate = configured == null ? text -> {
            throw new IllegalStateException("MODEL_NOT_CONFIGURED");
        } : new SpringAiAnalysisModel(configured);
        return new BoundedAnalysisModel(delegate, timeout);
    }

    @Bean
    AnalyzeTool analyzeTool(AnalysisModel model) {
        return new AnalyzeTool(model);
    }
}
