package com.pertegato.propertyflow.caseprocessing.workflow;

import io.camunda.client.spring.properties.CamundaClientProperties;
import java.net.URI;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CamundaEndpointConfigurationTest {

    @ParameterizedTest
    @ValueSource(strings = {"propertyflow-backend", "propertyflow-backend,propertyflow-dev"})
    void bindsBothConfiguredEndpointsInsteadOfSilentlyUsingTheDevelopmentEngine(String profiles) {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(ClientBinding.class)
                .withPropertyValues("spring.profiles.active=" + profiles, "spring.config.import=",
                        "PROPERTYFLOW_CAMUNDA_URL=http://127.0.0.1:18086",
                        "PROPERTYFLOW_CAMUNDA_GRPC_URL=http://127.0.0.1:26506")
                .run(context -> {
                    CamundaClientProperties properties = context.getBean(CamundaClientProperties.class);
                    assertEquals(URI.create("http://127.0.0.1:18086"), properties.getRestAddress());
                    assertEquals(URI.create("http://127.0.0.1:26506"), properties.getGrpcAddress());
                    assertTrue(properties.getPreferRestOverGrpc());
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CamundaClientProperties.class)
    static class ClientBinding {
    }
}
