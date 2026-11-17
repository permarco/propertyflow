package com.pertegato.propertyflow.caseprocessing.workflow;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@Profile("propertyflow-backend & !frontend-demo")
@ConditionalOnProperty(name = "propertyflow.workflow.enabled", havingValue = "true")
@EnableScheduling
public class WorkflowConfiguration {
}
