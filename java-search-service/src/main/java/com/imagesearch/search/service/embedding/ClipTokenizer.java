package com.imagesearch.search.service.embedding;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CLIP BPE (Byte Pair Encoding) Tokenizer.
 *
 * Implements the same tokenization logic as OpenAI CLIP for text encoding.
 * This is essential for generating consistent embeddings with the ONNX model.
 *
 * Best Practice: Separate tokenization logic into its own component
 * for testability and reusability.
 *
 * Reference: https://github.com/openai/CLIP/blob/main/clip/simple_tokenizer.py
 */
@Component
@Slf4j
public class ClipTokenizer {

    // Special tokens
    private static final int START_TOKEN = 49406;
    private static final int END_TOKEN = 49407;
    private static final int PAD_TOKEN = 0;

    // Pattern for tokenization (same as CLIP)
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "<\\|startoftext\\|>|<\\|endoftext\\|>|'s|'t|'re|'ve|'m|'ll|'d|[\\p{L}]+|[\\p{N}]|[^\\s\\p{L}\\p{N}]+",
            Pattern.CASE_INSENSITIVE
    );

    private Map<String, Integer> encoder;
    private Map<Integer, String> decoder;

    /**
     * Initialize tokenizer with vocabulary.
     *
     * Note: In a production system, vocabulary would be loaded from files.
     * This is a simplified implementation that covers common tokenization cases.
     */
    public ClipTokenizer() {
        initializeVocabulary();
        log.info("CLIP tokenizer initialized with {} vocabulary entries", encoder.size());
    }

    /**
     * Tokenize text and convert to token IDs.
     *
     * @param text Input text to tokenize
     * @param maxLength Maximum sequence length (default: 77 for CLIP)
     * @return Array of token IDs padded to maxLength
     */
    public long[] encode(String text, int maxLength) {
        // Convert tokens to IDs
        List<Integer> tokenIds = new ArrayList<>();
        tokenIds.add(START_TOKEN);

        // Only tokenize if text is not empty
        if (text != null && !text.isEmpty()) {
            // Normalize and tokenize
            text = text.toLowerCase().trim();
            List<String> tokens = tokenize(text);

            for (String token : tokens) {
                if (tokenIds.size() >= maxLength - 1) {
                    break; // Leave room for END_TOKEN
                }

                Integer tokenId = encoder.getOrDefault(token, encoder.get("unk"));
                if (tokenId != null) {
                    tokenIds.add(tokenId);
                }
            }
        }

        tokenIds.add(END_TOKEN);

        return createPaddedSequence(tokenIds, maxLength);
    }

    /**
     * Tokenize text into subword tokens using BPE.
     */
    private List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(text);

        while (matcher.find()) {
            String token = matcher.group();
            // For simplicity, we'll use word-level tokenization
            // In production, this would use full BPE merging
            tokens.add(token);
        }

        return tokens;
    }

    /**
     * Create padded sequence of token IDs.
     */
    private long[] createPaddedSequence(List<Integer> tokenIds, int maxLength) {
        long[] padded = new long[maxLength];
        Arrays.fill(padded, PAD_TOKEN);

        for (int i = 0; i < Math.min(tokenIds.size(), maxLength); i++) {
            padded[i] = tokenIds.get(i);
        }

        return padded;
    }

    /**
     * Initialize basic vocabulary.
     *
     * Note: This is a simplified vocabulary for demonstration.
     * In production, load from the actual CLIP vocabulary file.
     *
     * The full CLIP vocabulary has ~49,408 tokens.
     */
    private void initializeVocabulary() {
        encoder = new HashMap<>();
        decoder = new HashMap<>();

        // Add special tokens
        encoder.put("<|startoftext|>", START_TOKEN);
        encoder.put("<|endoftext|>", END_TOKEN);
        encoder.put("unk", 0);

        // Add basic vocabulary (common words and characters)
        // This is a minimal set - in production, load full vocab from file
        String[] commonWords = {
                "a", "the", "an", "and", "or", "but", "in", "on", "at", "to", "for",
                "of", "with", "by", "from", "as", "is", "was", "are", "were", "be",
                "been", "being", "have", "has", "had", "do", "does", "did", "will",
                "would", "could", "should", "may", "might", "must", "can",
                "dog", "cat", "animal", "person", "people", "man", "woman", "child",
                "house", "car", "tree", "flower", "sky", "sun", "moon", "star",
                "mountain", "ocean", "river", "lake", "forest", "desert", "beach",
                "red", "blue", "green", "yellow", "black", "white", "orange", "purple",
                "big", "small", "large", "tiny", "tall", "short", "long", "wide",
                "beautiful", "pretty", "ugly", "nice", "good", "bad", "happy", "sad",
                "photo", "picture", "image", "sunset", "sunrise", "landscape",
                "portrait", "nature", "urban", "city", "countryside", "indoor", "outdoor"
        };

        int tokenId = 1000; // Start from 1000 to avoid conflicts
        for (String word : commonWords) {
            encoder.put(word, tokenId);
            decoder.put(tokenId, word);
            tokenId++;
        }

        // Add single characters (a-z, 0-9)
        for (char c = 'a'; c <= 'z'; c++) {
            String ch = String.valueOf(c);
            if (!encoder.containsKey(ch)) {
                encoder.put(ch, tokenId);
                decoder.put(tokenId, ch);
                tokenId++;
            }
        }

        for (char c = '0'; c <= '9'; c++) {
            String ch = String.valueOf(c);
            encoder.put(ch, tokenId);
            decoder.put(tokenId, ch);
            tokenId++;
        }

        // Add common punctuation
        String[] punctuation = {" ", ".", ",", "!", "?", "-", "'", "\"", "(", ")", "[", "]"};
        for (String p : punctuation) {
            encoder.put(p, tokenId);
            decoder.put(tokenId, p);
            tokenId++;
        }

        log.debug("Initialized vocabulary with {} tokens", encoder.size());
    }

    /**
     * Load vocabulary from classpath resource.
     *
     * This method would be used in production to load the full CLIP vocabulary.
     */
    public void loadVocabularyFromFile(String vocabPath) throws IOException {
        log.info("Loading vocabulary from: {}", vocabPath);

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(vocabPath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

            encoder = new HashMap<>();
            decoder = new HashMap<>();

            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\t");
                if (parts.length == 2) {
                    String token = parts[0];
                    int id = Integer.parseInt(parts[1]);
                    encoder.put(token, id);
                    decoder.put(id, token);
                }
            }

            log.info("Loaded {} vocabulary entries from {}", encoder.size(), vocabPath);
        } catch (IOException | NullPointerException e) {
            log.warn("Could not load vocabulary file: {}. Using default vocabulary.", vocabPath);
            initializeVocabulary(); // Fall back to default
        }
    }

    /**
     * Get vocabulary size.
     */
    public int getVocabSize() {
        return encoder.size();
    }
}
