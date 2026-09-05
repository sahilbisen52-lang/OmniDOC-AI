package com.docassistant.controller;

import com.docassistant.dto.ChatRequest;
import com.docassistant.dto.MultiChatRequest;
import com.docassistant.dto.ChatResponse;
import com.docassistant.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for conversational document Q&amp;A.
 *
 * <p>Accepts a user question and a document ID, builds a context window
 * from the document's extracted text, and returns an AI-generated answer
 * via Google Gemini.</p>
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Chat", description = "Conversational Q&A against uploaded documents")
public class ChatController {

    private final DocumentService documentService;

    /**
     * Processes a chat message against a document.
     *
     * <p>The response includes the AI answer, processing time in
     * milliseconds, and whether the answer was served from cache.</p>
     *
     * @param request the chat request containing document ID and user message
     * @return the AI-generated answer
     */
    @PostMapping
    @Operation(summary = "Chat with a document",
               description = "Send a natural-language question and receive an AI-generated answer " +
                       "based on the document's content. Responses are cached for identical queries.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chat response generated",
                         content = @Content(schema = @Schema(implementation = ChatResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request (missing fields)"),
            @ApiResponse(responseCode = "404", description = "Document not found")
    })
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        log.info("POST /api/chat — documentId='{}', message='{}'",
                request.documentId(),
                request.message().substring(0, Math.min(80, request.message().length())));
        return ResponseEntity.ok(documentService.chat(request));
    }

    /**
     * Processes a chat message against multiple documents.
     *
     * @param request the multi-document chat request
     * @return the AI-generated answer comparing the documents
     */
    @PostMapping("/multi")
    @Operation(summary = "Chat with multiple documents",
               description = "Send a natural-language question and receive an AI-generated answer " +
                       "based on the content of multiple selected documents.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chat response generated",
                         content = @Content(schema = @Schema(implementation = ChatResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request (missing fields)"),
            @ApiResponse(responseCode = "404", description = "One or more documents not found")
    })
    public ResponseEntity<ChatResponse> chatMulti(@Valid @RequestBody MultiChatRequest request) {
        log.info("POST /api/chat/multi — documentIds={}, message='{}'",
                request.documentIds(),
                request.message().substring(0, Math.min(80, request.message().length())));
        return ResponseEntity.ok(documentService.chat(request));
    }
}
