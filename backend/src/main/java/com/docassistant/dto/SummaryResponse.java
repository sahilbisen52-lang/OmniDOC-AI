package com.docassistant.dto;

import lombok.Builder;

/**
 * Outbound response payload for a document summarization request.
 *
 * @param summary          the AI-generated summary text
 * @param mode             the summarization mode that was used
 * @param processingTimeMs time taken to generate the summary in milliseconds
 * @param cached           {@code true} if the summary was served from the Redis cache
 */
@Builder
public record SummaryResponse(
        String summary,
        String mode,
        long processingTimeMs,
        boolean cached
) {
}
