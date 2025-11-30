package com.imagesearch.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class to log active search backend on startup.
 *
 * This provides clear visibility into which search implementation is active,
 * which is important for debugging and operations.
 */
@Configuration
public class SearchBackendConfig {

    private static final Logger logger = LoggerFactory.getLogger(SearchBackendConfig.class);

    @Value("${search.backend.type:python}")
    private String searchBackendType;

    @PostConstruct
    public void logActiveBackend() {
        logger.info("============================================================");
        logger.info("ACTIVE SEARCH BACKEND: {}", searchBackendType.toUpperCase());

        if ("python".equalsIgnoreCase(searchBackendType)) {
            logger.info("Using Python Search Service (FAISS) on port 5000");
            logger.info("Embeddings stored in: data/indexes/");
            logger.info("Data storage: File-based FAISS indexes");
        } else if ("java".equalsIgnoreCase(searchBackendType)) {
            logger.info("Using Java Search Service (Elasticsearch) on port 5001");
            logger.info("Embeddings stored in: Elasticsearch cluster");
            logger.info("Data storage: Distributed Elasticsearch indexes");
        } else {
            logger.warn("Unknown search backend type: {}. Defaulting to Python/FAISS.", searchBackendType);
        }

        logger.info("============================================================");
    }
}
