package com.docassistant.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Inbound request payload for multi-document chat interactions.
 *
 * @param documentIds the IDs of the documents to query against
 * @param message     the user's natural-language question
 */
public record MultiChatRequest(
        @NotEmpty(message = "At least one Document ID is required")
        List<String> documentIds,

        @NotBlank(message = "Message is required")
        String message
) {
}
