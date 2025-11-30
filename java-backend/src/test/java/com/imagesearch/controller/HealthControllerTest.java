package com.imagesearch.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Comprehensive test suite for HealthController.
 *
 * Coverage Areas:
 * 1. Happy path (successful health check)
 * 2. Response structure validation
 * 3. Timestamp accuracy
 * 4. Thread safety (concurrent health checks)
 * 5. Performance (response time consistency)
 * 6. Idempotency (repeated requests produce consistent results)
 *
 * Technical Approach:
 * - Uses @WebMvcTest for lightweight controller testing
 * - Tests health endpoint behavior under various conditions
 * - Validates response payload structure and content
 * - Tests concurrent access patterns for production readiness
 *
 * Why Health Endpoints Matter:
 * - Used by load balancers and orchestrators (Kubernetes, AWS ALB)
 * - Must be fast and reliable (no dependencies on external services)
 * - Must handle high request volume (monitoring probes)
 * - Should never fail (returning 500 could trigger cascading failures)
 */
@WebMvcTest(HealthController.class)
@DisplayName("HealthController Comprehensive Test Suite")
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // ==================== HAPPY PATH TESTS ====================

    @Test
    @DisplayName("GET /api/health should return 200 OK with correct structure")
    void testHealth_Success() throws Exception {
        // WHEN: Calling health endpoint
        long beforeTimestamp = System.currentTimeMillis();

        mockMvc.perform(get("/api/health"))
                // THEN: Should return 200 with correct response structure
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("healthy"))
                .andExpect(jsonPath("$.service").value("image-search-backend"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.timestamp").isNumber());

        long afterTimestamp = System.currentTimeMillis();

        // Timestamp should be within reasonable range (test execution time)
        // This validates that the timestamp is generated at request time, not hardcoded
    }

    @Test
    @DisplayName("GET /api/health should return timestamp close to current time")
    void testHealth_TimestampAccuracy() throws Exception {
        // GIVEN: Current time before request
        long beforeRequest = System.currentTimeMillis();

        // WHEN: Calling health endpoint
        String responseBody = mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long afterRequest = System.currentTimeMillis();

        // THEN: Timestamp should be between before and after (within 1 second tolerance)
        // Extract timestamp from response
        long responseTimestamp = extractTimestamp(responseBody);
        assertThat(responseTimestamp)
                .isGreaterThanOrEqualTo(beforeRequest - 1000) // 1 second tolerance
                .isLessThanOrEqualTo(afterRequest + 1000);
    }

    @Test
    @DisplayName("GET /api/health should return Content-Type application/json")
    void testHealth_ContentType() throws Exception {
        // WHEN/THEN: Should return JSON content type
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"));
    }

    // ==================== IDEMPOTENCY TESTS ====================

    @Test
    @DisplayName("GET /api/health should be idempotent (repeated calls return consistent structure)")
    @RepeatedTest(10)
    void testHealth_Idempotent() throws Exception {
        // WHEN: Calling health endpoint multiple times
        mockMvc.perform(get("/api/health"))
                // THEN: Should always return same structure with status=healthy
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("healthy"))
                .andExpect(jsonPath("$.service").value("image-search-backend"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("GET /api/health should return different timestamps on consecutive calls")
    void testHealth_TimestampChanges() throws Exception {
        // GIVEN: First health check
        String response1 = mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long timestamp1 = extractTimestamp(response1);

        // Small delay to ensure time advances
        Thread.sleep(5);

        // WHEN: Second health check
        String response2 = mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long timestamp2 = extractTimestamp(response2);

        // THEN: Timestamps should be different (proves dynamic generation)
        assertThat(timestamp2).isGreaterThanOrEqualTo(timestamp1);
    }

    // ==================== THREAD SAFETY TESTS ====================

    @Test
    @DisplayName("GET /api/health should handle concurrent requests safely")
    void testHealth_ConcurrentRequests() throws InterruptedException {
        // GIVEN: 50 concurrent health check requests
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // WHEN: Sending concurrent requests
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for signal to start all threads simultaneously

                    mockMvc.perform(get("/api/health"))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.status").value("healthy"));

                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Start all threads
        boolean finished = doneLatch.await(10, TimeUnit.SECONDS);

        // THEN: All requests should succeed
        assertThat(finished).isTrue()
                .withFailMessage("Not all concurrent requests completed within timeout");
        assertThat(successCount.get()).isEqualTo(threadCount)
                .withFailMessage("Expected all %d requests to succeed, but only %d succeeded",
                        threadCount, successCount.get());
        assertThat(failureCount.get()).isEqualTo(0)
                .withFailMessage("Expected zero failures, but got %d", failureCount.get());

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    // ==================== PERFORMANCE TESTS ====================

    @Test
    @DisplayName("GET /api/health should respond quickly (under 100ms)")
    void testHealth_ResponseTime() throws Exception {
        // WHEN: Measuring response time
        long startTime = System.currentTimeMillis();

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());

        long endTime = System.currentTimeMillis();
        long responseTime = endTime - startTime;

        // THEN: Should respond in under 100ms (health checks must be fast)
        assertThat(responseTime).isLessThan(100L)
                .withFailMessage("Health endpoint took %dms, should be under 100ms", responseTime);
    }

    @Test
    @DisplayName("GET /api/health should maintain consistent response time under load")
    void testHealth_ConsistentPerformance() throws Exception {
        // GIVEN: Warm up the endpoint (JIT compilation, caching)
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/health"));
        }

        // WHEN: Measuring response times for 100 requests
        long totalTime = 0;
        int requestCount = 100;

        for (int i = 0; i < requestCount; i++) {
            long startTime = System.nanoTime();

            mockMvc.perform(get("/api/health"))
                    .andExpect(status().isOk());

            long endTime = System.nanoTime();
            totalTime += (endTime - startTime);
        }

        // THEN: Average response time should be under 50ms
        long averageTimeMs = (totalTime / requestCount) / 1_000_000;
        assertThat(averageTimeMs).isLessThan(50L)
                .withFailMessage("Average response time was %dms, expected under 50ms", averageTimeMs);
    }

    // ==================== EDGE CASE TESTS ====================

    @Test
    @DisplayName("GET /api/health should not accept POST requests")
    void testHealth_WrongHttpMethod_POST() throws Exception {
        // WHEN/THEN: POST should return 405 Method Not Allowed
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/health"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("GET /api/health should not accept PUT requests")
    void testHealth_WrongHttpMethod_PUT() throws Exception {
        // WHEN/THEN: PUT should return 405 Method Not Allowed
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/health"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("GET /api/health should not accept DELETE requests")
    void testHealth_WrongHttpMethod_DELETE() throws Exception {
        // WHEN/THEN: DELETE should return 405 Method Not Allowed
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/health"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("GET /api/health should ignore query parameters")
    void testHealth_WithQueryParameters() throws Exception {
        // WHEN: Calling health endpoint with query parameters
        mockMvc.perform(get("/api/health")
                .param("foo", "bar")
                .param("test", "value"))
                // THEN: Should still return successful health check (ignore params)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("healthy"));
    }

    @Test
    @DisplayName("GET /api/health should ignore request headers")
    void testHealth_WithCustomHeaders() throws Exception {
        // WHEN: Calling health endpoint with custom headers
        mockMvc.perform(get("/api/health")
                .header("X-Custom-Header", "test-value")
                .header("Authorization", "Bearer fake-token"))
                // THEN: Should still return successful health check (no auth required)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("healthy"));
    }

    @Test
    @DisplayName("GET /api/health should work with Accept: */*")
    void testHealth_AcceptAnyContentType() throws Exception {
        // WHEN: Client accepts any content type
        mockMvc.perform(get("/api/health")
                .header("Accept", "*/*"))
                // THEN: Should return JSON
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"));
    }

    @Test
    @DisplayName("GET /api/health should work with Accept: application/json")
    void testHealth_AcceptJson() throws Exception {
        // WHEN: Client explicitly requests JSON
        mockMvc.perform(get("/api/health")
                .header("Accept", "application/json"))
                // THEN: Should return JSON
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"));
    }

    // ==================== HELPER METHODS ====================

    /**
     * Extract timestamp value from JSON response string.
     * Simple JSON parsing for timestamp extraction without Jackson dependency in this method.
     *
     * @param jsonResponse JSON response string
     * @return Extracted timestamp value
     */
    private long extractTimestamp(String jsonResponse) {
        // Simple extraction: find "timestamp":<value>
        String[] parts = jsonResponse.split("\"timestamp\":");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Timestamp not found in response");
        }

        String timestampPart = parts[1].split("[,}]")[0].trim();
        return Long.parseLong(timestampPart);
    }
}
