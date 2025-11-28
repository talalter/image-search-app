package com.imagesearch.search.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Cache Configuration for CLIP Embeddings.
 *
 * Best Practice: Use Caffeine for high-performance in-memory caching
 * to avoid redundant embedding generation for identical inputs.
 *
 * Caching Strategy:
 * - Text embeddings: Cached indefinitely (queries are repeated often)
 * - Image embeddings: Not cached (images are usually embedded once)
 */
@Configuration
@EnableCaching
@Slf4j
public class CacheConfig {

    /**
     * Configure Caffeine cache manager with custom settings.
     *
     * Cache specifications:
     * - textEmbeddings: Up to 10,000 entries, no expiration
     * - modelInfo: Small cache for model metadata
     */
    @Bean
    @SuppressWarnings("null")
    public CacheManager cacheManager() {
        log.info("Initializing Caffeine cache manager for CLIP embeddings");

        CaffeineCacheManager cacheManager = new CaffeineCacheManager();

        // Default cache configuration
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10_000)
                .recordStats()); // Enable metrics

        // Register cache names
        cacheManager.setCacheNames(java.util.List.of(
                "textEmbeddings",
                "modelInfo"
        ));

        return cacheManager;
    }

    /**
     * Custom Caffeine configuration for text embeddings cache.
     *
     * Best Practice: Size-based eviction with LRU policy
     */
    @Bean
    public Caffeine<Object, Object> textEmbeddingsCaffeineConfig() {
        return Caffeine.newBuilder()
                .maximumSize(10_000)
                .recordStats()
                .expireAfterAccess(24, TimeUnit.HOURS);
    }

    /**
     * Custom Caffeine configuration for model info cache.
     */
    @Bean
    public Caffeine<Object, Object> modelInfoCaffeineConfig() {
        return Caffeine.newBuilder()
                .maximumSize(100)
                .recordStats()
                .expireAfterWrite(1, TimeUnit.HOURS);
    }
}
