package com.docassistant.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class CacheService {

    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    private static final String CACHE_PREFIX = "docassistant:";

    public Optional<String> getCachedResponse(String key) {
        String value = cache.get(CACHE_PREFIX + key);
        if (value != null) {
            log.debug("Cache HIT for key: {}", key);
            return Optional.of(value);
        }
        log.debug("Cache MISS for key: {}", key);
        return Optional.empty();
    }

    public void cacheResponse(String key, String value, long ttlMinutes) {
        cache.put(CACHE_PREFIX + key, value);
        log.debug("Cached response for key: {}", key);
    }

    public String generateCacheKey(String documentId, String prompt) {
        try {
            String input = documentId + ":" + prompt;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    public void evictDocumentCache(String documentId) {
        cache.keySet().removeIf(k -> k.contains(documentId));
        log.info("Evicted cache entries for document {}", documentId);
    }
}
