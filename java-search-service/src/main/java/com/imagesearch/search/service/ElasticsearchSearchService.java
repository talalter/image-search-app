package com.imagesearch.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorProperty;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Elasticsearch-based vector search service
 *
 * Manages per-folder Elasticsearch indexes for storing and searching image embeddings
 * using cosine similarity with dense_vector field type.
 *
 * Features:
 * - Per-user, per-folder index organization (index name: images-{userId}-{folderId})
 * - Dense vector storage with cosine similarity (same as FAISS IndexFlatIP with normalized vectors)
 * - Hybrid search capability (can filter by metadata while doing vector search)
 * - Distributed and production-ready
 *
 * Index Mapping:
 * {
 *   "image_id": long,
 *   "folder_id": long,
 *   "embedding": dense_vector (512 dimensions, cosine similarity)
 * }
 */
@Service
@Slf4j
public class ElasticsearchSearchService {

    private final ElasticsearchClient client;

    @Value("${elasticsearch.embedding-dimension:512}")
    private int embeddingDimension;

    public ElasticsearchSearchService(ElasticsearchClient client) {
        this.client = client;
        log.info("ElasticsearchSearchService initialized");
    }

    /**
     * Create a new Elasticsearch index for a folder
     *
     * Index mapping uses dense_vector with cosine similarity, which is equivalent to
     * FAISS IndexFlatIP with normalized vectors (dot product on normalized = cosine).
     *
     * @param userId User ID
     * @param folderId Folder ID
     * @throws IOException if index creation fails
     */
    public void createIndex(Long userId, Long folderId) throws IOException {
        String indexName = getIndexName(userId, folderId);

        // Check if index already exists
        boolean exists = client.indices().exists(ExistsRequest.of(e -> e.index(indexName))).value();
        if (exists) {
            log.info("Index {} already exists", indexName);
            return;
        }

        // Create index with dense_vector mapping
        // CRITICAL: Using cosine similarity to match FAISS IndexFlatIP behavior
        // Elasticsearch will automatically normalize vectors for cosine similarity
        CreateIndexRequest request = CreateIndexRequest.of(i -> i
            .index(indexName)
            .mappings(TypeMapping.of(m -> m
                .properties("image_id", Property.of(p -> p.long_(l -> l)))
                .properties("folder_id", Property.of(p -> p.long_(l -> l)))
                .properties("embedding", Property.of(p -> p.denseVector(DenseVectorProperty.of(d -> d
                    .dims(embeddingDimension)
                    .similarity("cosine")  // Cosine similarity (same as FAISS normalized dot product)
                    .index(true)  // Enable indexing for faster search (uses HNSW)
                    .indexOptions(io -> io
                        .type("hnsw")  // Hierarchical Navigable Small World algorithm
                        .m(16)  // Controls graph connectivity (higher = better recall, more memory)
                        .efConstruction(100)  // Build-time search depth (higher = better quality)
                    )
                ))))
            ))
            .settings(s -> s
                .numberOfShards("1")  // Single shard per folder index (small scale)
                .numberOfReplicas("0")  // No replicas for local development
                .refreshInterval(Time.of(t -> t.time("1s")))  // Refresh every 1 second for near real-time search
            )
        );

        client.indices().create(request);
        log.info("Created Elasticsearch index {} with cosine similarity (dims={})", indexName, embeddingDimension);
    }

    /**
     * Add image embeddings to the index
     *
     * Elasticsearch automatically normalizes vectors when using cosine similarity,
     * so we don't need to manually normalize (unlike Lucene implementation).
     *
     * @param userId User ID
     * @param folderId Folder ID
     * @param imageIds List of image IDs
     * @param embeddings List of 512-dimensional embedding vectors
     * @throws IOException if adding vectors fails
     */
    @SuppressWarnings("null")
    public void addVectors(Long userId, Long folderId,
                          List<Long> imageIds, List<float[]> embeddings) throws IOException {

        if (imageIds.size() != embeddings.size()) {
            throw new IllegalArgumentException("Image IDs and embeddings must have same length");
        }

        String indexName = getIndexName(userId, folderId);

        // Ensure index exists
        boolean exists = client.indices().exists(ExistsRequest.of(e -> e.index(indexName))).value();
        if (!exists) {
            createIndex(userId, folderId);
        }

        // Bulk index documents
        BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();

        for (int i = 0; i < imageIds.size(); i++) {
            final int index = i;

            // Create document with metadata + embedding
            Map<String, Object> document = new HashMap<>();
            document.put("image_id", imageIds.get(index));
            document.put("folder_id", folderId);

            // Convert float[] to List<Float> for Elasticsearch
            List<Float> embeddingList = new ArrayList<>(embeddings.get(index).length);
            for (float val : embeddings.get(index)) {
                embeddingList.add(val);
            }
            document.put("embedding", embeddingList);

            // Use image_id as document ID for idempotent indexing
            String docId = String.valueOf(imageIds.get(index));

            bulkBuilder.operations(op -> op
                .index(idx -> idx
                    .index(indexName)
                    .id(docId)
                    .document(document)
                )
            );
        }

        BulkResponse bulkResponse = client.bulk(bulkBuilder.build());

        if (bulkResponse.errors()) {
            log.error("Bulk indexing had errors for index {}", indexName);
            bulkResponse.items().forEach(item -> {
                if (item.error() != null) {
                    log.error("Failed to index document {}: {}", item.id(), item.error().reason());
                }
            });
            throw new IOException("Bulk indexing failed with errors");
        }

        log.info("Added {} vectors to Elasticsearch index {} (user={}, folder={})",
                imageIds.size(), indexName, userId, folderId);
    }

    /**
     * Search for similar vectors using cosine similarity
     *
     * Uses Elasticsearch kNN search with cosine similarity, which is equivalent to
     * FAISS IndexFlatIP on normalized vectors (dot product = cosine for unit vectors).
     *
     * Elasticsearch automatically handles vector normalization when similarity="cosine".
     *
     * @param userId User ID
     * @param queryEmbedding Query embedding (512-dimensional)
     * @param folderIds List of folder IDs to search
     * @param topK Number of top results to return
     * @return List of scored images sorted by similarity (descending)
     * @throws IOException if search fails
     */
    public List<ScoredImage> search(Long userId, float[] queryEmbedding,
                                   List<Long> folderIds, int topK) throws IOException {

        List<ScoredImage> allResults = new ArrayList<>();

        // Convert float[] to List<Float> for Elasticsearch
        List<Float> queryVector = new ArrayList<>(queryEmbedding.length);
        for (float val : queryEmbedding) {
            queryVector.add(val);
        }

        for (Long folderId : folderIds) {
            String indexName = getIndexName(userId, folderId);

            // Check if index exists
            boolean exists = client.indices().exists(ExistsRequest.of(e -> e.index(indexName))).value();
            if (!exists) {
                log.warn("No index found for user {} folder {}", userId, folderId);
                continue;
            }

            // Perform kNN search
            // Note: Elasticsearch kNN search uses approximate nearest neighbor (HNSW) for performance
            // This is faster than FAISS brute-force but slightly less accurate (configurable via m, ef_search)
            SearchRequest searchRequest = SearchRequest.of(s -> s
                .index(indexName)
                .knn(k -> k
                    .field("embedding")
                    .queryVector(queryVector)
                    .k(topK)  // Number of nearest neighbors to find
                    .numCandidates(topK * 10)  // HNSW search parameter (higher = more accurate, slower)
                )
                .size(topK)  // Limit results returned
                .source(src -> src.fetch(true))  // Fetch document source
            );

            SearchResponse<ObjectNode> response = client.search(searchRequest, ObjectNode.class);

            // Convert hits to ScoredImage objects
            for (Hit<ObjectNode> hit : response.hits().hits()) {
                if (hit.source() == null) continue;

                @SuppressWarnings("null")
                Long imageId = hit.source().get("image_id").asLong();
                @SuppressWarnings("null")
                Long docFolderId = hit.source().get("folder_id").asLong();

                // Elasticsearch returns cosine similarity score (range: -1 to 1)
                // Higher score = more similar (same as FAISS IndexFlatIP on normalized vectors)
                @SuppressWarnings("null")
                float score = hit.score() != null ? hit.score().floatValue() : 0.0f;

                allResults.add(new ScoredImage(imageId, score, docFolderId));
            }
        }

        // Sort by similarity (descending) and take top-k
        // Note: If searching multiple folders, we need to re-sort combined results
        return allResults.stream()
                .sorted((a, b) -> Float.compare(b.score, a.score))
                .limit(topK)
                .collect(Collectors.toList());
    }

    /**
     * Delete an index for a folder
     *
     * @param userId User ID
     * @param folderId Folder ID
     * @throws IOException if deletion fails
     */
    public void deleteIndex(Long userId, Long folderId) throws IOException {
        String indexName = getIndexName(userId, folderId);

        // Check if index exists
        boolean exists = client.indices().exists(ExistsRequest.of(e -> e.index(indexName))).value();
        if (!exists) {
            log.info("Index {} does not exist, nothing to delete", indexName);
            return;
        }

        // Delete index
        DeleteIndexRequest request = DeleteIndexRequest.of(d -> d.index(indexName));
        client.indices().delete(request);

        log.info("Deleted Elasticsearch index {} for user {} folder {}", indexName, userId, folderId);
    }

    /**
     * Check if an index exists for a folder
     */
    public boolean indexExists(Long userId, Long folderId) throws IOException {
        String indexName = getIndexName(userId, folderId);
        return client.indices().exists(ExistsRequest.of(e -> e.index(indexName))).value();
    }

    /**
     * Get index size (number of documents)
     */
    public long getIndexSize(Long userId, Long folderId) throws IOException {
        String indexName = getIndexName(userId, folderId);

        // Check if index exists first
        boolean exists = client.indices().exists(ExistsRequest.of(e -> e.index(indexName))).value();
        if (!exists) {
            return 0;
        }

        // Count documents
        CountRequest countRequest = CountRequest.of(c -> c.index(indexName));
        CountResponse countResponse = client.count(countRequest);

        return countResponse.count();
    }

    // ========== Helper Methods ==========

    /**
     * Generate index name from user and folder IDs
     *
     * Format: images-{userId}-{folderId}
     * Example: images-1-5
     *
     * Elasticsearch index names must be lowercase and cannot contain uppercase letters,
     * spaces, or special characters (except - and _).
     */
    private String getIndexName(Long userId, Long folderId) {
        return String.format("images-%d-%d", userId, folderId);
    }

    /**
     * Scored image result
     *
     * Identical to LuceneSearchService.ScoredImage for API compatibility
     */
    public static class ScoredImage {
        public final Long imageId;
        public final Float score;
        public final Long folderId;

        public ScoredImage(Long imageId, Float score, Long folderId) {
            this.imageId = imageId;
            this.score = score;
            this.folderId = folderId;
        }
    }
}
