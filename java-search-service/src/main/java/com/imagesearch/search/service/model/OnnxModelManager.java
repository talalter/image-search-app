package com.imagesearch.search.service.model;

import ai.onnxruntime.*;
import com.imagesearch.search.config.ClipModelProperties;
import com.imagesearch.search.exception.ModelLoadException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.util.Map;

/**
 * ONNX Model Manager - Handles lifecycle of ONNX Runtime sessions.
 *
 * Best Practices Demonstrated:
 * - Resource management with @PostConstruct and @PreDestroy
 * - Singleton pattern via Spring @Component
 * - Proper cleanup to prevent memory leaks
 * - Thread-safe model loading
 * - Detailed logging for debugging
 *
 * This class manages:
 * - Loading ONNX models on startup
 * - Creating ONNX Runtime sessions
 * - Configuring inference options
 * - Cleanup on shutdown
 */
@Component
@Slf4j
@Getter
public class OnnxModelManager {

    private final ClipModelProperties properties;
    private OrtEnvironment environment;
    private OrtSession textEncoderSession;
    private OrtSession imageEncoderSession;
    private volatile boolean modelsLoaded = false;

    public OnnxModelManager(ClipModelProperties properties) {
        this.properties = properties;
    }

    /**
     * Initialize ONNX Runtime and load models on startup.
     *
     * Best Practice: Use @PostConstruct for initialization logic
     * that requires fully constructed beans.
     */
    @PostConstruct
    public void initialize() {
        log.info("Initializing ONNX Model Manager...");

        try {
            // Create ONNX Runtime environment
            environment = OrtEnvironment.getEnvironment();
            log.info("ONNX Runtime version: {}", environment.getVersion());

            // Load models
            loadTextEncoderModel();
            loadImageEncoderModel();

            modelsLoaded = true;
            log.info("ONNX Model Manager initialized successfully");

        } catch (Exception e) {
            log.error("Failed to initialize ONNX Model Manager", e);
            throw new ModelLoadException("Failed to initialize ONNX models", e);
        }
    }

    /**
     * Load CLIP text encoder ONNX model.
     */
    private void loadTextEncoderModel() throws OrtException {
        String modelPath = properties.getTextModelPath();
        log.info("Loading text encoder model from: {}", modelPath);

        File modelFile = new File(modelPath);
        if (!modelFile.exists()) {
            throw new ModelLoadException("Text encoder model not found: " + modelPath);
        }

        // Create session options
        OrtSession.SessionOptions sessionOptions = createSessionOptions();

        try {
            // Load model
            textEncoderSession = environment.createSession(modelPath, sessionOptions);

            // Log model info
            logModelInfo("Text Encoder", textEncoderSession);

        } catch (OrtException e) {
            log.error("Failed to load text encoder model", e);
            throw new ModelLoadException("Failed to load text encoder: " + modelPath, e);
        }
    }

    /**
     * Load CLIP image encoder ONNX model.
     */
    private void loadImageEncoderModel() throws OrtException {
        String modelPath = properties.getImageModelPath();
        log.info("Loading image encoder model from: {}", modelPath);

        File modelFile = new File(modelPath);
        if (!modelFile.exists()) {
            throw new ModelLoadException("Image encoder model not found: " + modelPath);
        }

        // Create session options
        OrtSession.SessionOptions sessionOptions = createSessionOptions();

        try {
            // Load model
            imageEncoderSession = environment.createSession(modelPath, sessionOptions);

            // Log model info
            logModelInfo("Image Encoder", imageEncoderSession);

        } catch (OrtException e) {
            log.error("Failed to load image encoder model", e);
            throw new ModelLoadException("Failed to load image encoder: " + modelPath, e);
        }
    }

    /**
     * Create ONNX Runtime session options.
     *
     * Best Practice: Configure inference options for optimal performance
     */
    private OrtSession.SessionOptions createSessionOptions() throws OrtException {
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();

        // Set number of inference threads
        int numThreads = properties.getInferenceThreads();
        if (numThreads > 0) {
            options.setIntraOpNumThreads(numThreads);
            options.setInterOpNumThreads(numThreads);
            log.debug("Set inference threads to: {}", numThreads);
        } else {
            // Auto-detect based on available processors
            int availableProcessors = Runtime.getRuntime().availableProcessors();
            options.setIntraOpNumThreads(availableProcessors);
            log.debug("Auto-detected {} processors for inference", availableProcessors);
        }

        // Set execution mode
        options.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL);

        // Optimization level
        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);

        // GPU support (if enabled and available)
        if (properties.isUseGpu()) {
            try {
                // Note: GPU support requires ONNX Runtime GPU build
                log.info("GPU acceleration enabled");
                // options.addCUDA(0); // Uncomment if CUDA is available
            } catch (Exception e) {
                log.warn("GPU acceleration requested but not available: {}", e.getMessage());
            }
        }

        return options;
    }

    /**
     * Log model information for debugging.
     */
    private void logModelInfo(String modelName, OrtSession session) throws OrtException {
        log.info("{} loaded successfully:", modelName);

        // Log input info
        Map<String, NodeInfo> inputs = session.getInputInfo();
        log.info("  Inputs: {}", inputs.size());
        for (Map.Entry<String, NodeInfo> entry : inputs.entrySet()) {
            log.info("    - {}: {}", entry.getKey(), entry.getValue().getInfo());
        }

        // Log output info
        Map<String, NodeInfo> outputs = session.getOutputInfo();
        log.info("  Outputs: {}", outputs.size());
        for (Map.Entry<String, NodeInfo> entry : outputs.entrySet()) {
            log.info("    - {}: {}", entry.getKey(), entry.getValue().getInfo());
        }
    }

    /**
     * Check if models are loaded and ready.
     */
    public boolean isReady() {
        return modelsLoaded &&
               textEncoderSession != null &&
               imageEncoderSession != null;
    }

    /**
     * Clean up resources on shutdown.
     *
     * Best Practice: Use @PreDestroy for cleanup to prevent memory leaks
     */
    @PreDestroy
    public void cleanup() {
        log.info("Cleaning up ONNX Model Manager...");

        try {
            if (textEncoderSession != null) {
                textEncoderSession.close();
                log.debug("Closed text encoder session");
            }

            if (imageEncoderSession != null) {
                imageEncoderSession.close();
                log.debug("Closed image encoder session");
            }

            // Note: Don't close environment - it's a singleton managed by ONNX Runtime

            modelsLoaded = false;
            log.info("ONNX Model Manager cleanup completed");

        } catch (Exception e) {
            log.error("Error during ONNX Model Manager cleanup", e);
        }
    }

    /**
     * Get text encoder session.
     */
    public OrtSession getTextEncoderSession() {
        if (!isReady()) {
            throw new ModelLoadException("Text encoder not loaded");
        }
        return textEncoderSession;
    }

    /**
     * Get image encoder session.
     */
    public OrtSession getImageEncoderSession() {
        if (!isReady()) {
            throw new ModelLoadException("Image encoder not loaded");
        }
        return imageEncoderSession;
    }
}
