package com.imagesearch.search.health;

import com.imagesearch.search.service.embedding.OnnxClipEmbeddingService;
import com.imagesearch.search.service.model.OnnxModelManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health Indicator for CLIP Model Status.
 *
 * Best Practice: Custom health indicators for Spring Boot Actuator
 * provide visibility into application health and readiness.
 *
 * This indicator reports:
 * - Whether ONNX models are loaded
 * - Model file locations
 * - Embedding dimensions
 * - Memory usage (future enhancement)
 *
 * Accessible via: GET /actuator/health
 */
@Component
@Slf4j
public class ClipModelHealthIndicator implements HealthIndicator {

    private final OnnxModelManager modelManager;
    private final OnnxClipEmbeddingService embeddingService;

    public ClipModelHealthIndicator(
            OnnxModelManager modelManager,
            OnnxClipEmbeddingService embeddingService) {

        this.modelManager = modelManager;
        this.embeddingService = embeddingService;
    }

    @Override
    public Health health() {
        try {
            // Check if models are loaded and ready
            boolean isReady = embeddingService.isReady();

            if (isReady) {
                return Health.up()
                        .withDetail("status", "Models loaded and ready")
                        .withDetail("textModelPath", modelManager.getProperties().getTextModelPath())
                        .withDetail("imageModelPath", modelManager.getProperties().getImageModelPath())
                        .withDetail("embeddingDimension", embeddingService.getEmbeddingDimension())
                        .withDetail("modelsLoaded", modelManager.isModelsLoaded())
                        .build();
            } else {
                return Health.down()
                        .withDetail("status", "Models not loaded")
                        .withDetail("reason", "Model initialization may have failed")
                        .build();
            }

        } catch (Exception e) {
            log.error("Health check failed", e);
            return Health.down()
                    .withDetail("status", "Health check error")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
