package com.docassistant.service;

import com.docassistant.dto.*;
import com.docassistant.exception.DocumentNotFoundException;
import com.docassistant.model.Document;
import com.docassistant.model.DocumentStatus;
import com.docassistant.model.User;
import com.docassistant.repository.DocumentRepository;
import com.docassistant.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Core orchestration service for the Document Assistant application.
 *
 * <p>Coordinates the full document lifecycle — upload, async text extraction
 * (via the Python microservice), AI summarization, chat Q&amp;A, and
 * Redis-backed caching — while delegating specialised work to
 * {@link PythonClientService}, {@link GeminiService}, and {@link CacheService}.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final PythonClientService pythonClientService;
    private final GeminiService geminiService;
    private final CacheService cacheService;
    private final ObjectMapper objectMapper;

    @Value("${cache.ttl-minutes:60}")
    private long cacheTtlMinutes;

    // ------------------------------------------------------------------
    // Document CRUD
    // ------------------------------------------------------------------

    /**
     * Handles document upload: persists the initial entity with {@code PROCESSING}
     * status and kicks off asynchronous text extraction.
     *
     * @param file the uploaded multipart file (must be a PDF)
     * @return a {@link DocumentDTO} with the assigned ID and current status
     * @throws IOException if reading the file bytes fails
     */
    public DocumentDTO uploadDocument(MultipartFile file) throws IOException {
        log.info("Uploading document: '{}' ({} bytes)", file.getOriginalFilename(), file.getSize());

        String userId = getCurrentUserId();

        Document document = Document.builder()
                .filename(file.getOriginalFilename())
                .fileSize(file.getSize())
                .status(DocumentStatus.PROCESSING)
                .uploadedAt(LocalDateTime.now())
                .userId(userId)
                .build();

        document = documentRepository.save(document);
        log.info("Document entity created with ID: {}", document.getId());

        // Trigger async processing pipeline
        processDocumentAsync(document.getId(), file.getBytes(), file.getOriginalFilename());

        return toDTO(document);
    }

    /**
     * Returns all documents sorted by upload date (newest first).
     *
     * @return list of {@link DocumentDTO} instances
     */
    public List<DocumentDTO> getDocuments() {
        String userId = getCurrentUserId();
        if (userId != null) {
            return documentRepository.findAllByUserIdOrderByUploadedAtDesc(userId)
                    .stream()
                    .map(this::toDTO)
                    .collect(Collectors.toList());
        }
        return documentRepository.findAllByOrderByUploadedAtDesc()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves a single document by ID.
     *
     * @param id the document UUID
     * @return the matching {@link DocumentDTO}
     * @throws DocumentNotFoundException if no document exists with the given ID
     */
    public DocumentDTO getDocument(String id) {
        return toDTO(findDocumentOrThrow(id));
    }

    /**
     * Deletes a document and evicts all associated cache entries.
     *
     * @param id the document UUID
     * @throws DocumentNotFoundException if no document exists with the given ID
     */
    public void deleteDocument(String id) {
        Document document = findDocumentOrThrow(id);
        documentRepository.delete(document);
        cacheService.evictDocumentCache(id);
        log.info("Deleted document '{}' ({})", document.getFilename(), id);
    }

    // ------------------------------------------------------------------
    // Summarization
    // ------------------------------------------------------------------

    /**
     * Generates (or retrieves from cache) a summary of the specified document.
     *
     * @param id      the document UUID
     * @param request the summarization request containing the desired mode
     * @return a {@link SummaryResponse} with the summary, timing, and cache status
     * @throws DocumentNotFoundException if the document does not exist
     * @throws IllegalStateException     if the document is not yet in READY status
     */
    public SummaryResponse summarizeDocument(String id, SummaryRequest request) {
        long startTime = System.currentTimeMillis();
        Document document = findDocumentOrThrow(id);

        if (document.getStatus() != DocumentStatus.READY) {
            throw new IllegalStateException(
                    "Document is not ready for summarization. Current status: " + document.getStatus());
        }

        // Check cache
        String cacheKey = cacheService.generateCacheKey(id, "summary:" + request.mode());
        Optional<String> cached = cacheService.getCachedResponse(cacheKey);
        if (cached.isPresent()) {
            long elapsed = System.currentTimeMillis() - startTime;
            return SummaryResponse.builder()
                    .summary(cached.get())
                    .mode(request.mode())
                    .processingTimeMs(elapsed)
                    .cached(true)
                    .build();
        }

        // Generate summary via Gemini
        String summary = geminiService.summarize(document.getExtractedText(), request.mode());

        // Cache the result
        cacheService.cacheResponse(cacheKey, summary, cacheTtlMinutes);

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Generated '{}' summary for document {} in {}ms", request.mode(), id, elapsed);

        return SummaryResponse.builder()
                .summary(summary)
                .mode(request.mode())
                .processingTimeMs(elapsed)
                .cached(false)
                .build();
    }

    // ------------------------------------------------------------------
    // Chat Q&A
    // ------------------------------------------------------------------

    /**
     * Processes a chat question against a document's content.
     *
     * <p>Builds a context window from the document's text chunks (or full
     * text as fallback), checks the cache, and calls Gemini if needed.</p>
     *
     * @param request the chat request containing document ID and user message
     * @return a {@link ChatResponse} with the AI answer, timing, and cache status
     * @throws DocumentNotFoundException if the document does not exist
     * @throws IllegalStateException     if the document is not yet in READY status
     */
    public ChatResponse chat(ChatRequest request) {
        long startTime = System.currentTimeMillis();
        Document document = findDocumentOrThrow(request.documentId());

        if (document.getStatus() != DocumentStatus.READY) {
            throw new IllegalStateException(
                    "Document is not ready for chat. Current status: " + document.getStatus());
        }

        // Check cache
        String cacheKey = cacheService.generateCacheKey(request.documentId(), "chat:" + request.message());
        Optional<String> cached = cacheService.getCachedResponse(cacheKey);
        if (cached.isPresent()) {
            long elapsed = System.currentTimeMillis() - startTime;
            return buildChatResponse(cached.get(), elapsed, true);
        }

        String context = buildContext(document);

        // Call Gemini
        String answer = geminiService.chat(context, request.message());

        // Cache
        cacheService.cacheResponse(cacheKey, answer, cacheTtlMinutes);

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Chat response for document {} generated in {}ms", request.documentId(), elapsed);

        return buildChatResponse(answer, elapsed, false);
    }

    /**
     * Processes a chat question against multiple documents using a keyword RAG approach.
     *
     * @param request the multi-document chat request
     * @return a {@link ChatResponse} with the AI answer, timing, and cache status
     * @throws DocumentNotFoundException if any document does not exist
     * @throws IllegalStateException     if any document is not READY
     */
    public ChatResponse chat(MultiChatRequest request) {
        long startTime = System.currentTimeMillis();

        // Sort IDs to ensure cache key consistency
        List<String> sortedIds = new ArrayList<>(request.documentIds());
        Collections.sort(sortedIds);
        String compositeId = String.join(",", sortedIds);

        // Check cache
        String cacheKey = cacheService.generateCacheKey(compositeId, "multichat:" + request.message());
        Optional<String> cached = cacheService.getCachedResponse(cacheKey);
        if (cached.isPresent()) {
            long elapsed = System.currentTimeMillis() - startTime;
            return buildChatResponse(cached.get(), elapsed, true);
        }

        // Fetch and validate all documents
        List<Document> documents = new ArrayList<>();
        for (String id : request.documentIds()) {
            Document doc = findDocumentOrThrow(id);
            if (doc.getStatus() != DocumentStatus.READY) {
                throw new IllegalStateException(
                        "Document '" + doc.getFilename() + "' is not ready for chat. Current status: " + doc.getStatus());
            }
            documents.add(doc);
        }

        // Tokenize query for relevance scoring
        String cleanQuery = request.message().toLowerCase().replaceAll("[^a-zA-Z0-9\\s]", "");
        Set<String> queryTokens = new HashSet<>(Arrays.asList(cleanQuery.split("\\s+")));
        queryTokens.removeIf(token -> token.length() < 3);

        // Helper record for chunk scoring
        record ScoredChunk(String filename, String text, double score) {}

        List<ScoredChunk> allScoredChunks = new ArrayList<>();

        for (Document doc : documents) {
            List<String> chunks = new ArrayList<>();
            if (doc.getTextChunksJson() != null && !doc.getTextChunksJson().isBlank()) {
                try {
                    chunks = objectMapper.readValue(
                            doc.getTextChunksJson(), new TypeReference<List<String>>() {});
                } catch (JsonProcessingException je) {
                    log.warn("Failed to parse text chunks for document {}, falling back to full text", doc.getId());
                }
            }

            if (chunks.isEmpty() && doc.getExtractedText() != null) {
                String text = doc.getExtractedText();
                int chunkSize = 1000;
                for (int i = 0; i < text.length(); i += chunkSize) {
                    chunks.add(text.substring(i, Math.min(i + chunkSize, text.length())));
                }
            }

            for (String chunkText : chunks) {
                double score = 0.0;
                String chunkLower = chunkText.toLowerCase();
                for (String token : queryTokens) {
                    if (chunkLower.contains(token)) {
                        score += 1.0;
                        int index = 0;
                        while ((index = chunkLower.indexOf(token, index)) != -1) {
                            score += 0.1;
                            index += token.length();
                        }
                    }
                }
                allScoredChunks.add(new ScoredChunk(doc.getFilename(), chunkText, score));
            }
        }

        allScoredChunks.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());

        StringBuilder contextBuilder = new StringBuilder();
        int charCount = 0;
        int budget = 24_000;

        for (ScoredChunk scored : allScoredChunks) {
            if (scored.score() == 0.0 && charCount > 5000) {
                break;
            }
            String formatted = String.format("--- SOURCE FILE: %s ---\n%s\n\n", scored.filename(), scored.text());
            if (charCount + formatted.length() > budget) {
                break;
            }
            contextBuilder.append(formatted);
            charCount += formatted.length();
        }
        String combinedContext = contextBuilder.toString();

        // Call Gemini
        String answer = geminiService.chatMulti(combinedContext, request.message());

        // Cache response
        cacheService.cacheResponse(cacheKey, answer, cacheTtlMinutes);

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Multi-document chat response for {} documents generated in {}ms", request.documentIds().size(), elapsed);

        return buildChatResponse(answer, elapsed, false);
    }

    // ------------------------------------------------------------------
    // Async processing pipeline
    // ------------------------------------------------------------------

    /**
     * Asynchronously extracts text from the uploaded PDF, generates an
     * initial brief summary, and updates the document entity.
     *
     * <p>On success the document status transitions to {@code READY};
     * on failure it transitions to {@code ERROR} with a diagnostic message.</p>
     *
     * @param documentId the persisted document's UUID
     * @param pdfBytes   raw PDF bytes
     * @param filename   original filename
     */
    @Async
    public void processDocumentAsync(String documentId, byte[] pdfBytes, String filename) {
        log.info("Starting async processing pipeline for document {}", documentId);

        try {
            // Step 1: Call Python service for text extraction
            PythonClientService.PythonExtractionResponse extraction =
                    pythonClientService.extractText(pdfBytes, filename).join();

            // Step 2: Update document with extraction results
            Document document = findDocumentOrThrow(documentId);
            document.setExtractedText(extraction.getText());
            document.setPageCount(extraction.getPageCount());

            if (extraction.getChunks() != null) {
                document.setTextChunksJson(objectMapper.writeValueAsString(extraction.getChunks()));
            }

            // Step 3: Auto-generate brief summary
            try {
                String briefSummary = geminiService.summarize(extraction.getText(), "brief");
                document.setSummary(briefSummary);
            } catch (Exception e) {
                log.warn("Auto-summary generation failed for document {}: {}", documentId, e.getMessage());
                // Non-fatal — document is still usable without a summary
            }

            document.setStatus(DocumentStatus.READY);
            documentRepository.save(document);
            log.info("Document {} processed successfully — status set to READY", documentId);



        } catch (Exception e) {
            log.error("Processing pipeline failed for document {}: {}", documentId, e.getMessage(), e);
            documentRepository.findById(documentId).ifPresent(doc -> {
                doc.setStatus(DocumentStatus.ERROR);
                doc.setErrorMessage("Processing failed: " + e.getMessage());
                documentRepository.save(doc);
            });
        }
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    /**
     * Finds a document by ID or throws {@link DocumentNotFoundException}.
     */
    private Document findDocumentOrThrow(String id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));
    }

    /**
     * Builds a context string from document chunks, falling back to full text.
     */
    private String buildContext(Document document) {
        if (document.getTextChunksJson() != null && !document.getTextChunksJson().isBlank()) {
            try {
                List<String> chunks = objectMapper.readValue(
                        document.getTextChunksJson(), new TypeReference<List<String>>() {});
                // Join all chunks (in production, use vector similarity to select relevant chunks)
                return String.join("\n\n", chunks);
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse text chunks for document {}, falling back to full text", document.getId());
            }
        }
        return document.getExtractedText() != null ? document.getExtractedText() : "";
    }

    /**
     * Constructs a {@link ChatResponse} record.
     */
    private ChatResponse buildChatResponse(String content, long processingTimeMs, boolean cached) {
        return ChatResponse.builder()
                .id(UUID.randomUUID().toString())
                .role("assistant")
                .content(content)
                .timestamp(Instant.now().toString())
                .processingTimeMs(processingTimeMs)
                .cached(cached)
                .build();
    }

    /**
     * Maps a {@link Document} entity to a {@link DocumentDTO}.
     */
    private DocumentDTO toDTO(Document document) {
        return DocumentDTO.builder()
                .id(document.getId())
                .filename(document.getFilename())
                .fileSize(document.getFileSize())
                .pageCount(document.getPageCount())
                .uploadedAt(document.getUploadedAt() != null
                        ? document.getUploadedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        : null)
                .status(document.getStatus().name().toLowerCase())
                .errorMessage(document.getErrorMessage())
                .summary(document.getSummary())
                .build();
    }

    /**
     * Extracts the authenticated user's ID from the security context.
     */
    private String getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getCredentials() instanceof String userId) {
            return userId;
        }
        return null;
    }
}
