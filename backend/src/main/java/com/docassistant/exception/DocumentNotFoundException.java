package com.docassistant.exception;

/**
 * Thrown when a document lookup by ID yields no result.
 *
 * <p>Mapped to HTTP 404 by {@link GlobalExceptionHandler}.</p>
 */
public class DocumentNotFoundException extends RuntimeException {

    /**
     * Constructs the exception with the missing document's ID.
     *
     * @param documentId the ID that was not found
     */
    public DocumentNotFoundException(String documentId) {
        super("Document not found with ID: " + documentId);
    }
}
