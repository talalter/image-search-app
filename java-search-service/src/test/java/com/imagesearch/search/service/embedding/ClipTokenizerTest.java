package com.imagesearch.search.service.embedding;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CLIP Tokenizer.
 *
 * Best Practice: Comprehensive unit tests for core business logic
 */
class ClipTokenizerTest {

    private ClipTokenizer tokenizer;

    @BeforeEach
    void setUp() {
        tokenizer = new ClipTokenizer();
    }

    @Test
    void testEncodeSimpleText() {
        // Given
        String text = "a cat";
        int maxLength = 77;

        // When
        long[] tokens = tokenizer.encode(text, maxLength);

        // Then
        assertNotNull(tokens);
        assertEquals(maxLength, tokens.length, "Token array should be padded to maxLength");

        // First token should be START_TOKEN (49406)
        assertEquals(49406, tokens[0], "First token should be START token");

        // Should contain padding (0) at the end
        assertEquals(0, tokens[maxLength - 1], "Last tokens should be padding");
    }

    @Test
    void testEncodeEmptyString() {
        // Given
        String text = "";
        int maxLength = 77;

        // When
        long[] tokens = tokenizer.encode(text, maxLength);

        // Then
        assertNotNull(tokens);
        assertEquals(maxLength, tokens.length);

        // Should only have START and END tokens
        assertEquals(49406, tokens[0], "Should have START token");
        assertEquals(49407, tokens[1], "Should have END token");
        assertEquals(0, tokens[2], "Rest should be padding");
    }

    @Test
    void testEncodeLongText() {
        // Given
        String longText = "the quick brown fox jumps over the lazy dog ".repeat(10);
        int maxLength = 77;

        // When
        long[] tokens = tokenizer.encode(longText, maxLength);

        // Then
        assertNotNull(tokens);
        assertEquals(maxLength, tokens.length, "Should be truncated to maxLength");

        // Should have START token at beginning
        assertEquals(49406, tokens[0], "Should have START token");

        // Should have END token at position maxLength-1 (after truncation)
        assertEquals(49407, tokens[maxLength - 1], "Should have END token at last position");
    }

    @Test
    void testVocabularySize() {
        // When
        int vocabSize = tokenizer.getVocabSize();

        // Then
        assertTrue(vocabSize > 0, "Vocabulary should not be empty");
        assertTrue(vocabSize > 100, "Should have reasonable vocabulary size");
    }

    @Test
    void testTokenizationConsistency() {
        // Given
        String text = "sunset over mountains";

        // When
        long[] tokens1 = tokenizer.encode(text, 77);
        long[] tokens2 = tokenizer.encode(text, 77);

        // Then
        assertArrayEquals(tokens1, tokens2, "Same text should produce same tokens");
    }

    @Test
    void testCaseInsensitivity() {
        // Given
        String lowerCase = "hello world";
        String upperCase = "HELLO WORLD";

        // When
        long[] tokens1 = tokenizer.encode(lowerCase, 77);
        long[] tokens2 = tokenizer.encode(upperCase, 77);

        // Then
        assertArrayEquals(tokens1, tokens2, "Tokenization should be case-insensitive");
    }
}
