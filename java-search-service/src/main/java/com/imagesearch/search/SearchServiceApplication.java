package com.imagesearch.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Java Search Service Application
 *
 * ONNX CLIP-based semantic image search using Lucene vector storage
 *
 * Features:
 * - CLIP text/image embeddings using ONNX Runtime
 * - Vector similarity search using Apache Lucene
 * - RESTful API for embedding generation and search
 *
 * Architecture:
 * - ClipEmbeddingService: ONNX CLIP model inference
 * - LuceneSearchService: Vector index management and cosine similarity search
 * - SearchController: REST API endpoints
 */
@SpringBootApplication
public class SearchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchServiceApplication.class, args);
    }
}
