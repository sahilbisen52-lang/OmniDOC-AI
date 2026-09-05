package com.docassistant.dto;

import lombok.Builder;

/**
 * Data Transfer Object exposing document metadata to API consumers.
 *
 * <p>Intentionally excludes large fields like {@code extractedText}
 * and {@code textChunksJson} to keep list responses lightweight.</p>
 *
 * @param id         document UUID
 * @param filename   original filename
 * @param fileSize   file size in bytes
 * @param pageCount  number of pages
 * @param id           document UUID
 * @param filename     original filename
 * @param fileSize     file size in bytes
 * @param pageCount    number of pages
 * @param uploadedAt   ISO-8601 upload timestamp
 * @param status       current processing status
 * @param errorMessage error message if processing failed
 * @param summary      auto-generated brief summary (may be {@code null} while processing)
 */
@Builder
public record DocumentDTO(
        String id,
        String filename,
        Long fileSize,
        Integer pageCount,
        String uploadedAt,
        String status,
        String errorMessage,
        String summary
) {
}
