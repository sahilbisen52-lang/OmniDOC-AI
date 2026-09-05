package com.docassistant.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Client service for communicating with the Python text-extraction microservice.
 *
 * <p>Sends uploaded PDF bytes to the Python service's {@code /preprocess}
 * endpoint via multipart POST and returns the extracted text, text chunks,
 * page count, and metadata.</p>
 *
 * <p>All calls are executed asynchronously via {@link CompletableFuture}
 * so that the upload endpoint can return immediately while processing
 * continues in the background.</p>
 */
@Service
@Slf4j
public class PythonClientService {

    private final RestTemplate restTemplate;
    private final String pythonServiceUrl;

    /**
     * Constructs the client service.
     *
     * @param restTemplate     the shared {@link RestTemplate} bean
     * @param pythonServiceUrl base URL of the Python microservice
     */
    public PythonClientService(
            RestTemplate restTemplate,
            @Value("${python.service-url}") String pythonServiceUrl
    ) {
        this.restTemplate = restTemplate;
        this.pythonServiceUrl = pythonServiceUrl;
    }

    /**
     * Sends PDF bytes to the Python microservice for text extraction.
     *
     * <p>The call is wrapped in a {@link CompletableFuture} so that the
     * caller can proceed without blocking. On failure, the future completes
     * exceptionally with the underlying cause.</p>
     *
     * @param pdfBytes  raw bytes of the uploaded PDF
     * @param filename  original filename (sent as a multipart form field)
     * @return a future that resolves to the extraction response
     */
    public CompletableFuture<PythonExtractionResponse> extractText(byte[] pdfBytes, String filename) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Sending PDF '{}' ({} bytes) to Python service for extraction",
                        filename, pdfBytes.length);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.MULTIPART_FORM_DATA);

                // Build the multipart body
                MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                ByteArrayResource fileResource = new ByteArrayResource(pdfBytes) {
                    @Override
                    public String getFilename() {
                        return filename;
                    }
                };
                body.add("file", new HttpEntity<>(fileResource, createFileHeaders(filename)));

                HttpEntity<MultiValueMap<String, Object>> requestEntity =
                        new HttpEntity<>(body, headers);

                String url = pythonServiceUrl + "/preprocess";
                ResponseEntity<PythonExtractionResponse> response = restTemplate.exchange(
                        url,
                        HttpMethod.POST,
                        requestEntity,
                        PythonExtractionResponse.class
                );

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    log.info("Python service extraction completed for '{}': {} pages, {} chunks",
                            filename, response.getBody().getPageCount(),
                            response.getBody().getChunks() != null
                                    ? response.getBody().getChunks().size() : 0);
                    return response.getBody();
                }

                throw new RuntimeException("Python service returned status " + response.getStatusCode());

            } catch (RestClientException e) {
                log.error("Failed to call Python service for '{}': {}", filename, e.getMessage());
                throw new RuntimeException("Python extraction service unavailable: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Creates multipart content-disposition headers for the file part.
     */
    private HttpHeaders createFileHeaders(String filename) {
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.APPLICATION_PDF);
        fileHeaders.setContentDispositionFormData("file", filename);
        return fileHeaders;
    }

    // ----------------------------------------------------------------
    // Inner response class
    // ----------------------------------------------------------------

    /**
     * Deserialization target for the Python service's JSON response.
     */
    @Data
    public static class PythonExtractionResponse {

        /** The full extracted plain-text content. */
        private String text;

        /** Semantically chunked text segments for contextual retrieval. */
        private List<String> chunks;

        /** Number of pages in the PDF. */
        @JsonProperty("page_count")
        private int pageCount;

        /** Arbitrary metadata returned by the Python service. */
        private Map<String, Object> metadata;
    }
}
