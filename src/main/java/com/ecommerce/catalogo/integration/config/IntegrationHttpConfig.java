package com.ecommerce.catalogo.integration.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class IntegrationHttpConfig {

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    public RestClient.Builder integrationRestClientBuilder(IntegrationProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getResilience().getConnectTimeoutMs());
        factory.setReadTimeout(properties.getResilience().getReadTimeoutMs());
        return RestClient.builder().requestFactory(factory);
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService connectorSearchExecutor() {
        return Executors.newFixedThreadPool(9);
    }
}

