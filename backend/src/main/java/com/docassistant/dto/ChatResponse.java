package com.docassistant.dto;

import lombok.Builder;

/**
 * Outbound response payload for a chat interaction.
 *
 * @param id               unique response identifier
 * @param role             message role ({@code "assistant"})
 * @param content          the AI-generated answer
 * @param timestamp        ISO-8601 timestamp of the response
 * @param processingTimeMs time taken to generate the response in milliseconds
 * @param cached           {@code true} if the response was served from the Redis cache
 */
@Builder
public record ChatResponse(
        String id,
        String role,
        String content,
        String timestamp,
        long processingTimeMs,
        boolean cached
) {
}
