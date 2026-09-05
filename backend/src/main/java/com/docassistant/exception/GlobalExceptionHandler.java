package com.docassistant.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralised exception handler that translates exceptions into
 * consistent JSON error responses.
 *
 * <p>Response format:</p>
 * <pre>{@code
 * {
 *   "timestamp": "2024-01-01T00:00:00Z",
 *   "status": 404,
 *   "error": "Not Found",
 *   "message": "Document not found with ID: abc-123"
 * }
 * }</pre>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handles {@link DocumentNotFoundException} → 404.
     *
     * @param ex the exception
     * @return a 404 error response
     */
    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleDocumentNotFound(DocumentNotFoundException ex) {
        log.warn("Document not found: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * Handles file upload size violations → 413.
     *
     * @param ex the exception
     * @return a 413 error response
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("Upload size exceeded: {}", ex.getMessage());
        return buildErrorResponse(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "File size exceeds the maximum allowed upload size of 50MB"
        );
    }

    /**
     * Handles Bean Validation failures → 400.
     *
     * @param ex the exception
     * @return a 400 error response with per-field details
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex) {
        List<String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.toList());

        log.warn("Validation failed: {}", fieldErrors);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Bad Request");
        body.put("message", "Validation failed");
        body.put("details", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Handles {@link IllegalStateException} → 409 Conflict.
     *
     * <p>Used when operations are attempted on documents that are not
     * in the required state (e.g., summarizing a document still processing).</p>
     *
     * @param ex the exception
     * @return a 409 error response
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        log.warn("Illegal state: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * Catch-all handler for unexpected exceptions → 500.
     *
     * @param ex the exception
     * @return a 500 error response
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);

        // Detect Gemini API rate limits / quota exceeded
        String message = ex.getMessage();
        Throwable cause = ex.getCause();
        boolean isRateLimit = (message != null && (message.contains("429") || message.toLowerCase().contains("rate limit") || message.toLowerCase().contains("quota exceeded")));
        if (!isRateLimit && cause != null) {
            String causeMsg = cause.getMessage();
            isRateLimit = (causeMsg != null && (causeMsg.contains("429") || causeMsg.toLowerCase().contains("rate limit") || causeMsg.toLowerCase().contains("quota exceeded")));
        }

        if (isRateLimit) {
            return buildErrorResponse(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "The AI service is receiving too many requests. Please wait 30-60 seconds and try again."
            );
        }

        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later."
        );
    }

    // ------------------------------------------------------------------
    // Helper
    // ------------------------------------------------------------------

    /**
     * Builds a consistent error response body.
     */
    private ResponseEntity<Map<String, Object>> buildErrorResponse(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
