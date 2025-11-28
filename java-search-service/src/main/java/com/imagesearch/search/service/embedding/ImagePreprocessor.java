package com.imagesearch.search.service.embedding;

import com.imagesearch.search.exception.EmbeddingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Image Preprocessing for CLIP Model.
 *
 * Preprocesses images to match CLIP's expected input format:
 * - Resize to 224x224
 * - Convert to RGB
 * - Normalize with CLIP's mean and std
 * - Convert to CHW format (channels, height, width)
 *
 * Best Practice: Separate preprocessing logic for maintainability
 * and consistent behavior with the original CLIP model.
 *
 * Reference: https://github.com/openai/CLIP/blob/main/clip/clip.py
 */
@Component
@Slf4j
public class ImagePreprocessor {

    // CLIP normalization constants
    private static final float[] MEAN = {0.48145466f, 0.4578275f, 0.40821073f};
    private static final float[] STD = {0.26862954f, 0.26130258f, 0.27577711f};
    
    // ThreadLocal caches to reduce object allocation in hot paths
    private static final ThreadLocal<BufferedImage> RGB_CACHE = ThreadLocal.withInitial(() -> 
        new BufferedImage(224, 224, BufferedImage.TYPE_INT_RGB));
    
    private static final ThreadLocal<float[]> PIXEL_CACHE = ThreadLocal.withInitial(() -> 
        new float[3 * 224 * 224]);


    /**
     * Preprocess image file for CLIP model inference.
     *
     * @param imagePath Path to image file (relative or absolute)
     * @param targetSize Target image size (default: 224)
     * @return Float array in CHW format [3, 224, 224]
     */
    public float[] preprocessImage(String imagePath, int targetSize) {
        try {
            // Resolve path - handle both absolute and relative paths
            File imageFile = resolveImagePath(imagePath);

            if (!imageFile.exists()) {
                throw new EmbeddingException("Image file not found: " + imagePath + " (resolved to: " + imageFile.getAbsolutePath() + ")");
            }

            BufferedImage image = ImageIO.read(imageFile);
            if (image == null) {
                throw new EmbeddingException("Failed to read image: " + imagePath);
            }

            return preprocessImage(image, targetSize);

        } catch (IOException e) {
            throw new EmbeddingException("Failed to load image: " + imagePath, e);
        }
    }

    /**
     * Resolve image path to absolute path.
     * Handles paths like "images/1/3/photo.jpg" by mapping to "data/uploads/images/1/3/photo.jpg"
     */
    private File resolveImagePath(String imagePath) {
        File file = new File(imagePath);

        // If absolute path, use as-is
        if (file.isAbsolute()) {
            return file;
        }

        // If path starts with "images/", convert to "data/uploads/images/"
        if (imagePath.startsWith("images/")) {
            String dataPath = imagePath.replace("images/", "data/uploads/images/");

            // Try relative to current working directory
            File dataFile = new File(dataPath);
            if (dataFile.exists()) {
                return dataFile;
            }

            // Try relative to project root (one level up from java-search-service)
            File projectRoot = new File("..").getAbsoluteFile();
            File projectFile = new File(projectRoot, dataPath);
            if (projectFile.exists()) {
                return projectFile;
            }

            // Return the data path anyway (will fail with clear error)
            return dataFile;
        }

        // For other relative paths, use as-is
        return file;
    }

    /**
     * Preprocess BufferedImage for CLIP model inference.
     *
     * @param image Input image
     * @param targetSize Target image size (default: 224)
     * @return Float array in CHW format [3, targetSize, targetSize]
     */
    public float[] preprocessImage(BufferedImage image, int targetSize) {
        // Step 1: Convert to RGB (if needed)
        BufferedImage rgbImage = convertToRGB(image);

        // Step 2: Resize to target size (center crop if needed)
        BufferedImage resized = resizeAndCenterCrop(rgbImage, targetSize);

        // Step 3: Normalize and convert to CHW format
        return normalizeAndConvertToCHW(resized);
    }

    /**
     * Convert image to RGB format.
     * Memory optimized: reuses BufferedImage when possible.
     */
    private BufferedImage convertToRGB(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_RGB) {
            return image;
        }

        // Try to reuse cached RGB image if dimensions match
        BufferedImage rgbCache = RGB_CACHE.get();
        BufferedImage rgbImage;
        
        if (rgbCache.getWidth() == image.getWidth() && rgbCache.getHeight() == image.getHeight()) {
            rgbImage = rgbCache;
        } else {
            rgbImage = new BufferedImage(
                    image.getWidth(),
                    image.getHeight(),
                    BufferedImage.TYPE_INT_RGB
            );
        }

        Graphics2D g = rgbImage.createGraphics();
        try {
            g.drawImage(image, 0, 0, null);
        } finally {
            g.dispose(); // Always dispose in finally block
        }

        return rgbImage;
    }

    /**
     * Resize image to target size with center crop.
     *
     * CLIP preprocessing:
     * 1. Resize shortest side to target size
     * 2. Center crop to square
     */
    private BufferedImage resizeAndCenterCrop(BufferedImage image, int targetSize) {
        int width = image.getWidth();
        int height = image.getHeight();

        // Calculate resize dimensions (maintain aspect ratio)
        int newWidth, newHeight;
        if (width < height) {
            newWidth = targetSize;
            newHeight = (int) ((double) height / width * targetSize);
        } else {
            newHeight = targetSize;
            newWidth = (int) ((double) width / height * targetSize);
        }

        // Resize image
        BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, 0, 0, newWidth, newHeight, null);
        g.dispose();

        // Center crop to square
        int cropX = (newWidth - targetSize) / 2;
        int cropY = (newHeight - targetSize) / 2;

        return resized.getSubimage(cropX, cropY, targetSize, targetSize);
    }

    /**
     * Normalize image and convert to CHW format.
     * Memory optimized: reuses float array when possible.
     *
     * Output format: [C, H, W] where C=3 (RGB), H=W=224
     * Normalization: (pixel / 255.0 - mean) / std
     */
    private float[] normalizeAndConvertToCHW(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int totalPixels = 3 * height * width;

        // Reuse cached array if size matches (typical case for 224x224)
        float[] pixels;
        if (totalPixels == 3 * 224 * 224) {
            pixels = PIXEL_CACHE.get();
        } else {
            pixels = new float[totalPixels]; // Fallback for non-standard sizes
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);

                // Extract RGB components
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                // Normalize to [0, 1]
                float rNorm = r / 255.0f;
                float gNorm = g / 255.0f;
                float bNorm = b / 255.0f;

                // Apply CLIP normalization: (x - mean) / std
                float rNormalized = (rNorm - MEAN[0]) / STD[0];
                float gNormalized = (gNorm - MEAN[1]) / STD[1];
                float bNormalized = (bNorm - MEAN[2]) / STD[2];

                // Store in CHW format: [C, H, W]
                int channelSize = height * width;
                int pixelIndex = y * width + x;

                pixels[0 * channelSize + pixelIndex] = rNormalized; // Red channel
                pixels[1 * channelSize + pixelIndex] = gNormalized; // Green channel
                pixels[2 * channelSize + pixelIndex] = bNormalized; // Blue channel
            }
        }

        return pixels;
    }

    /**
     * Convert float array to ONNX input tensor format.
     *
     * @param pixels Preprocessed pixels in CHW format
     * @return FloatBuffer ready for ONNX Runtime
     */
    public FloatBuffer toFloatBuffer(float[] pixels) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(pixels.length * 4);
        buffer.order(ByteOrder.nativeOrder());
        FloatBuffer floatBuffer = buffer.asFloatBuffer();
        floatBuffer.put(pixels);
        floatBuffer.rewind();
        return floatBuffer;
    }

    /**
     * Get expected input shape for ONNX model.
     *
     * @return Array of [batch_size, channels, height, width]
     */
    public long[] getInputShape(int targetSize) {
        return new long[]{1, 3, targetSize, targetSize};
    }
}
