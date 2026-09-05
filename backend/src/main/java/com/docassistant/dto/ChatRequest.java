package com.docassistant.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Inbound request payload for chat interactions.
 *
 * @param documentId the ID of the document to query against
 * @param message    the user's natural-language question
 */
public record ChatRequest(
        @NotBlank(message = "Document ID is required")
        String documentId,

        @NotBlank(message = "Message is required")
        String message
) {
}
