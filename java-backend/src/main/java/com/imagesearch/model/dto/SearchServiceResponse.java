package com.imagesearch.model.dto;

import java.util.List;

/**
 * Search service response DTO.
 *
 * Contains search results with image IDs, similarity scores, and indexing status.
 */
public class SearchServiceResponse {

    private List<ImageResponse> results;

    public List<ImageResponse> getResults() {
        return results;
    }

    public void setResults(List<ImageResponse> results) {
        this.results = results;
    }

    /**
     * Image response DTO.
     *
     * Contains image ID, similarity score, and indexing status.
     */
    public static class ImageResponse {
        private String id;
        private double score;
        private String filepath;
        private String uploadedAt;
        private boolean indexed;  // Is image searchable?
        private String indexedAt; // When it became searchable

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public double getScore() {
            return score;
        }

        public void setScore(double score) {
            this.score = score;
        }

        public String getFilepath() {
            return filepath;
        }

        public void setFilepath(String filepath) {
            this.filepath = filepath;
        }

        public String getUploadedAt() {
            return uploadedAt;
        }

        public void setUploadedAt(String uploadedAt) {
            this.uploadedAt = uploadedAt;
        }

        public boolean isIndexed() {
            return indexed;
        }

        public void setIndexed(boolean indexed) {
            this.indexed = indexed;
        }

        public String getIndexedAt() {
            return indexedAt;
        }

        public void setIndexedAt(String indexedAt) {
            this.indexedAt = indexedAt;
        }
    }
}