package com.docassistant.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * General application configuration.
 *
 * <p>Provides shared infrastructure beans such as {@link RestTemplate}
 * (for calling the Python extraction microservice) and a pre-configured
 * {@link ObjectMapper} for consistent JSON handling across the app.</p>
 */
@Configuration
public class AppConfig {

    /**
     * Creates a {@link RestTemplate} with sensible timeouts for
     * communicating with the Python microservice.
     *
     * @param builder the auto-configured {@link RestTemplateBuilder}
     * @return configured {@link RestTemplate}
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Creates a globally-shared {@link ObjectMapper} with Java 8+
     * date/time support enabled and timestamp serialization disabled.
     *
     * @return configured {@link ObjectMapper}
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }
}
