package com.imagesearch.search.service.embedding;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Image Preprocessor.
 *
 * Best Practice: Test core business logic without external dependencies
 */
class ImagePreprocessorTest {

    private ImagePreprocessor preprocessor;

    @BeforeEach
    void setUp() {
        preprocessor = new ImagePreprocessor();
    }

    @Test
    void testPreprocessSquareImage() {
        // Given
        BufferedImage image = new BufferedImage(224, 224, BufferedImage.TYPE_INT_RGB);

        // Fill with test pattern
        for (int y = 0; y < 224; y++) {
            for (int x = 0; x < 224; x++) {
                int rgb = (128 << 16) | (128 << 8) | 128; // Gray
                image.setRGB(x, y, rgb);
            }
        }

        // When
        float[] pixels = preprocessor.preprocessImage(image, 224);

        // Then
        assertNotNull(pixels);
        assertEquals(3 * 224 * 224, pixels.length, "Should have 3 channels * 224 * 224 pixels");

        // Verify normalization (values should be roughly around 0 after normalization)
        for (float pixel : pixels) {
            // Normalized values should be within reasonable range
            assertTrue(pixel >= -3.0f && pixel <= 3.0f, "Normalized pixel should be within range");
        }
    }

    @Test
    void testPreprocessRectangularImage() {
        // Given - Wide image
        BufferedImage image = new BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB);

        // When
        float[] pixels = preprocessor.preprocessImage(image, 224);

        // Then
        assertNotNull(pixels);
        assertEquals(3 * 224 * 224, pixels.length, "Should be resized to 224x224");
    }

    @Test
    void testGetInputShape() {
        // When
        long[] shape = preprocessor.getInputShape(224);

        // Then
        assertNotNull(shape);
        assertEquals(4, shape.length, "Should be 4D tensor");
        assertEquals(1, shape[0], "Batch size should be 1");
        assertEquals(3, shape[1], "Should have 3 channels (RGB)");
        assertEquals(224, shape[2], "Height should be 224");
        assertEquals(224, shape[3], "Width should be 224");
    }

    @Test
    void testPreprocessDifferentSizes() {
        // Test that preprocessor can handle different target sizes
        BufferedImage image = new BufferedImage(512, 512, BufferedImage.TYPE_INT_RGB);

        // 224x224 (CLIP default)
        float[] pixels224 = preprocessor.preprocessImage(image, 224);
        assertEquals(3 * 224 * 224, pixels224.length);

        // 384x384 (larger model)
        float[] pixels384 = preprocessor.preprocessImage(image, 384);
        assertEquals(3 * 384 * 384, pixels384.length);
    }
}
