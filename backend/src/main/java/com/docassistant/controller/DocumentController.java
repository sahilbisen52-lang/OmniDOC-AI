package com.docassistant.controller;

import com.docassistant.dto.DocumentDTO;
import com.docassistant.dto.SummaryRequest;
import com.docassistant.dto.SummaryResponse;
import com.docassistant.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * REST controller for document management operations.
 *
 * <p>Exposes endpoints for uploading PDFs, listing/retrieving documents,
 * deleting documents, and requesting AI-powered summaries.</p>
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Documents", description = "Document upload, retrieval, deletion, and AI summarization")
public class DocumentController {

    private final DocumentService documentService;

    /**
     * Uploads a PDF document for processing.
     *
     * <p>The document is persisted immediately with {@code PROCESSING} status.
     * Text extraction and initial summarization run asynchronously in the
     * background. Poll {@code GET /{id}} to check when status becomes {@code READY}.</p>
     *
     * @param file the PDF file to upload
     * @return the created document's metadata
     * @throws IOException if reading the file fails
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a PDF document",
               description = "Uploads a PDF for asynchronous text extraction and AI analysis.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Document accepted for processing",
                         content = @Content(schema = @Schema(implementation = DocumentDTO.class))),
            @ApiResponse(responseCode = "413", description = "File exceeds maximum upload size"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<DocumentDTO> uploadDocument(
            @Parameter(description = "PDF file to upload", required = true)
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        log.info("POST /api/documents/upload — file='{}', size={}", file.getOriginalFilename(), file.getSize());
        DocumentDTO dto = documentService.uploadDocument(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Lists all documents, newest first.
     *
     * @return list of document metadata
     */
    @GetMapping
    @Operation(summary = "List all documents",
               description = "Returns all uploaded documents sorted by upload date (newest first).")
    @ApiResponse(responseCode = "200", description = "Successful retrieval")
    public ResponseEntity<List<DocumentDTO>> getAllDocuments() {
        log.debug("GET /api/documents");
        return ResponseEntity.ok(documentService.getDocuments());
    }

    /**
     * Retrieves a single document by its UUID.
     *
     * @param id the document UUID
     * @return the document's metadata
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get document by ID",
               description = "Retrieves metadata and status for a specific document.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Document found"),
            @ApiResponse(responseCode = "404", description = "Document not found")
    })
    public ResponseEntity<DocumentDTO> getDocument(
            @Parameter(description = "Document UUID", required = true)
            @PathVariable String id
    ) {
        log.debug("GET /api/documents/{}", id);
        return ResponseEntity.ok(documentService.getDocument(id));
    }

    /**
     * Deletes a document and its cached data.
     *
     * @param id the document UUID
     * @return 204 No Content on success
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a document",
               description = "Permanently deletes a document and evicts associated cache entries.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Document deleted"),
            @ApiResponse(responseCode = "404", description = "Document not found")
    })
    public ResponseEntity<Void> deleteDocument(
            @Parameter(description = "Document UUID", required = true)
            @PathVariable String id
    ) {
        log.info("DELETE /api/documents/{}", id);
        documentService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Generates an AI-powered summary of a document.
     *
     * @param id      the document UUID
     * @param request the summarization request specifying the mode
     * @return the summary, processing time, and cache status
     */
    @PostMapping("/{id}/summarize")
    @Operation(summary = "Summarize a document",
               description = "Generates an AI summary using Google Gemini. Supports modes: brief, detailed, key-points, action-items.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Summary generated",
                         content = @Content(schema = @Schema(implementation = SummaryResponse.class))),
            @ApiResponse(responseCode = "404", description = "Document not found"),
            @ApiResponse(responseCode = "400", description = "Invalid summary mode")
    })
    public ResponseEntity<SummaryResponse> summarizeDocument(
            @Parameter(description = "Document UUID", required = true)
            @PathVariable String id,
            @Valid @RequestBody SummaryRequest request
    ) {
        log.info("POST /api/documents/{}/summarize — mode='{}'", id, request.mode());
        return ResponseEntity.ok(documentService.summarizeDocument(id, request));
    }
}
