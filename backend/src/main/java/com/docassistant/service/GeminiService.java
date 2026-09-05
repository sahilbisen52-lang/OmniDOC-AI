package com.docassistant.service;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Map;

/**
 * Service integrating with Google Gemini AI for document summarization
 * and conversational Q&amp;A.
 *
 * <p>Uses the {@code google-genai} SDK to communicate with the Gemini
 * {@code gemini-2.0-flash} model, which offers low latency suitable for
 * interactive applications. Includes basic retry logic for transient
 * API failures.</p>
 */
@Service
@Slf4j
public class GeminiService {

    @Value("${gemini.api-key}")
    private String apiKey;

    @Value("${gemini.model:gemini-flash-latest}")
    private String modelName;

    private Client client;

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    /** Prompt templates keyed by summarization mode. */
    private static final Map<String, String> SUMMARY_PROMPTS = Map.of(
            "brief",
            "Provide a concise 2-3 sentence summary of the following document. " +
                    "Focus on the main topic and key conclusion.\n\nDocument:\n%s",
            "detailed",
            "Provide a comprehensive detailed summary of the following document. " +
                    "Cover all major sections, arguments, and conclusions in well-structured paragraphs.\n\nDocument:\n%s",
            "key-points",
            "Extract the key points from the following document as a bulleted list. " +
                    "Each point should be a single, clear sentence.\n\nDocument:\n%s",
            "action-items",
            "Extract actionable items and next steps from the following document as a bulleted list. " +
                    "Each item should be specific and actionable. If no explicit action items exist, " +
                    "infer reasonable next steps based on the content.\n\nDocument:\n%s"
    );

    /**
     * Initialises the Gemini client after dependency injection.
     */
    @PostConstruct
    void init() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("GEMINI_API_KEY is not set — AI features will be unavailable");
            return;
        }
        this.client = Client.builder().apiKey(apiKey).build();
        log.info("Gemini client initialised with model '{}'", modelName);
    }

    /**
     * Generates a summary of the given text using the specified mode.
     *
     * @param text the document text to summarize
     * @param mode one of {@code brief}, {@code detailed}, {@code key-points},
     *             or {@code action-items}
     * @return the AI-generated summary
     * @throws RuntimeException if the Gemini API call fails after retries
     */
    public String summarize(String text, String mode) {
        String promptTemplate = SUMMARY_PROMPTS.getOrDefault(mode, SUMMARY_PROMPTS.get("brief"));
        String truncatedText = truncateText(text, 30_000);
        String prompt = String.format(promptTemplate, truncatedText);

        log.info("Requesting '{}' summary from Gemini ({} chars of context)", mode, truncatedText.length());
        return callGeminiWithRetry(prompt);
    }

    /**
     * Sends a chat message to Gemini with document context for Q&amp;A.
     *
     * @param documentContext the relevant text context from the document
     * @param userMessage     the user's question
     * @return the AI-generated answer
     * @throws RuntimeException if the Gemini API call fails after retries
     */
    public String chat(String documentContext, String userMessage) {
        String truncatedContext = truncateText(documentContext, 25_000);
        String prompt = String.format(
                "You are a helpful document assistant. Answer the user's question based on " +
                        "the following document context. If the answer cannot be found in the context, " +
                        "say so clearly. Be accurate and cite specific parts of the document when possible.\n\n" +
                        "Document Context:\n%s\n\nUser Question: %s",
                truncatedContext, userMessage
        );

        log.info("Sending chat query to Gemini: '{}' ({} chars of context)",
                userMessage.substring(0, Math.min(80, userMessage.length())), truncatedContext.length());
        return callGeminiWithRetry(prompt);
    }

    /**
     * Sends a chat message to Gemini with combined multi-document context.
     *
     * @param combinedContext the aggregated context from multiple documents
     * @param userMessage     the user's question
     * @return the AI-generated answer comparing the documents
     */
    public String chatMulti(String combinedContext, String userMessage) {
        String truncatedContext = truncateText(combinedContext, 25_000);
        String prompt = String.format(
                "You are a helpful document assistant. Answer the user's question based on " +
                        "the following multi-document contexts. Each document's content is prefixed with its filename.\n" +
                        "If the answer cannot be found in the contexts, say so clearly. " +
                        "Be accurate, compare the documents where relevant, and clearly cite the source filenames in your answer.\n\n" +
                        "Document Contexts:\n%s\n\nUser Question: %s",
                truncatedContext, userMessage
        );

        log.info("Sending multi-document chat query to Gemini: '{}' ({} chars of context)",
                userMessage.substring(0, Math.min(80, userMessage.length())), truncatedContext.length());
        return callGeminiWithRetry(prompt);
    }

    /**
     * Calls the Gemini API with simple linear-backoff retry logic.
     *
     * @param prompt the prompt to send
     * @return the generated text response
     */
    private String callGeminiWithRetry(String prompt) {
        if (client == null) {
            throw new RuntimeException(
                    "Gemini client is not initialised. Ensure GEMINI_API_KEY is set.");
        }

        RuntimeException lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                GenerateContentResponse response =
                        client.models.generateContent(modelName, prompt, null);
                String text = response.text();

                if (text != null && !text.isBlank()) {
                    log.debug("Gemini response received ({} chars, attempt {})", text.length(), attempt);
                    return text.trim();
                }

                log.warn("Gemini returned empty response on attempt {}", attempt);
                lastException = new RuntimeException("Gemini returned an empty response");

            } catch (Exception e) {
                log.warn("Gemini API call failed on attempt {}/{}: {}", attempt, MAX_RETRIES, e.getMessage());
                lastException = new RuntimeException("Gemini API error: " + e.getMessage(), e);

                if (attempt < MAX_RETRIES) {
                    try {
                        long sleepTime = RETRY_DELAY_MS * attempt;
                        if (e.getMessage() != null && (e.getMessage().contains("429") || e.getMessage().toLowerCase().contains("quota"))) {
                            sleepTime = 5000L * attempt; // Sleep 5s on attempt 1, 10s on attempt 2
                            log.info("Rate limit (429) detected. Applying adaptive backoff. Sleeping for {}ms...", sleepTime);
                        }
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during retry backoff", ie);
                    }
                }
            }
        }

        throw lastException;
    }

    /**
     * Truncates text to a maximum character length to stay within model
     * context window limits.
     *
     * @param text      the text to truncate
     * @param maxLength maximum character length
     * @return truncated text, or the original if already within limits
     */
    private String truncateText(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        log.debug("Truncating text from {} to {} chars", text.length(), maxLength);
        return text.substring(0, maxLength) + "\n\n[Text truncated — original length: " + text.length() + " chars]";
    }
}
