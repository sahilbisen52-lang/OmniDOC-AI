package com.docassistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Inbound request payload for document summarization.
 *
 * @param mode summarization mode — one of {@code brief}, {@code detailed},
 *             {@code key-points}, or {@code action-items}
 */
public record SummaryRequest(
        @NotBlank(message = "Summary mode is required")
        @Pattern(regexp = "brief|detailed|key-points|action-items",
                 message = "Mode must be one of: brief, detailed, key-points, action-items")
        String mode
) {
}
