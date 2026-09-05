package com.docassistant.model;

/**
 * Represents the processing lifecycle status of an uploaded document.
 *
 * <ul>
 *   <li>{@link #PROCESSING} — Text extraction and initial summarization are in progress.</li>
 *   <li>{@link #READY} — Document is fully processed and available for Q&amp;A.</li>
 *   <li>{@link #ERROR} — An error occurred during processing.</li>
 * </ul>
 */
public enum DocumentStatus {
    /** Document is being processed (text extraction, chunking, initial summarization). */
    PROCESSING,

    /** Document processing completed successfully; ready for queries. */
    READY,

    /** An error occurred during document processing. */
    ERROR
}
