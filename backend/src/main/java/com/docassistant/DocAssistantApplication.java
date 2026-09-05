package com.docassistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * AI-Powered Document Assistant Backend Application.
 *
 * <p>Provides REST APIs for document upload, AI-driven summarization,
 * and conversational Q&amp;A powered by Google Gemini. Integrates with a
 * Python microservice for PDF text extraction and uses Redis for
 * response caching.</p>
 *
 * @author DocAssistant Team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableAsync
public class DocAssistantApplication {

    /**
     * Application entry point.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(DocAssistantApplication.class, args);
    }
}
