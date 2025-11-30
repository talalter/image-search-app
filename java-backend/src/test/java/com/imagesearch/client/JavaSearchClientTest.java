package com.imagesearch.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Unit tests for JavaSearchClientImpl.
 *
 * Tests the Elasticsearch index deletion functionality using Mockito
 * to mock WebClient behavior.
 */
@ExtendWith(MockitoExtension.class)
class JavaSearchClientTest {

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private WebClient webClient;

    @SuppressWarnings("rawtypes")
    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @SuppressWarnings("rawtypes")
    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private JavaSearchClientImpl javaSearchClient;

    @SuppressWarnings("null")
    @BeforeEach
    void setUp() {
        // Setup WebClient builder mock chain (basic setup only)
        lenient().when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        lenient().when(webClientBuilder.build()).thenReturn(webClient);
    }

    @Test
    @SuppressWarnings({"unchecked", "null"})
    void deleteIndex_WhenServiceEnabled_CallsDeleteEndpoint() {
        // Arrange - Setup WebClient method call chain for DELETE
        when(webClient.delete()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(eq("/api/delete-index/{userId}/{folderId}"), any(Object.class), any(Object.class)))
                .thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Void.class)).thenReturn(Mono.empty());

        javaSearchClient = new JavaSearchClientImpl(
                webClientBuilder,
                "http://localhost:5001",
                30,
                true  // enabled
        );

        Long userId = 1L;
        Long folderId = 5L;

        // Act
        javaSearchClient.deleteIndex(userId, folderId);

        // Assert
        verify(webClient).delete();
        verify(requestHeadersUriSpec).uri("/api/delete-index/{userId}/{folderId}", userId, folderId);
        verify(requestHeadersSpec).retrieve();
        verify(responseSpec).bodyToMono(Void.class);
    }

    @Test
    void deleteIndex_WhenServiceDisabled_SkipsDeletion() {
        // Arrange
        javaSearchClient = new JavaSearchClientImpl(
                webClientBuilder,
                "http://localhost:5001",
                30,
                false  // disabled
        );

        Long userId = 1L;
        Long folderId = 5L;

        // Act
        javaSearchClient.deleteIndex(userId, folderId);

        // Assert - no WebClient calls should be made
        verify(webClient, never()).delete();
    }

    @Test
    @SuppressWarnings({"unchecked", "null"})
    void deleteIndex_WhenServiceReturns404_ThrowsException() {
        // Arrange - Setup WebClient method call chain for DELETE
        when(webClient.delete()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(eq("/api/delete-index/{userId}/{folderId}"), any(Object.class), any(Object.class)))
                .thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        
        javaSearchClient = new JavaSearchClientImpl(
                webClientBuilder,
                "http://localhost:5001",
                30,
                true
        );

        Long userId = 1L;
        Long folderId = 5L;

        // Simulate 404 error
        when(responseSpec.bodyToMono(Void.class))
                .thenReturn(Mono.error(WebClientResponseException.create(
                        404,
                        "Not Found",
                        new HttpHeaders(),
                        new byte[0],
                        StandardCharsets.UTF_8
                )));

        // Act & Assert - should throw WebClientResponseException
        assertThrows(WebClientResponseException.class, 
                () -> javaSearchClient.deleteIndex(userId, folderId));
    }

    @Test
    @SuppressWarnings({"unchecked", "null"})
    void deleteIndex_WhenServiceReturns500_ThrowsException() {
        // Arrange - Setup WebClient method call chain for DELETE
        when(webClient.delete()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(eq("/api/delete-index/{userId}/{folderId}"), any(Object.class), any(Object.class)))
                .thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        
        javaSearchClient = new JavaSearchClientImpl(
                webClientBuilder,
                "http://localhost:5001",
                30,
                true
        );

        Long userId = 1L;
        Long folderId = 5L;

        // Simulate 500 error
        when(responseSpec.bodyToMono(Void.class))
                .thenReturn(Mono.error(WebClientResponseException.create(
                        500,
                        "Internal Server Error",
                        new HttpHeaders(),
                        new byte[0],
                        StandardCharsets.UTF_8
                )));

        // Act & Assert - should throw WebClientResponseException
        assertThrows(WebClientResponseException.class,
                () -> javaSearchClient.deleteIndex(userId, folderId));
    }

    @Test
    @SuppressWarnings({"unchecked", "null"})
    void deleteIndex_WhenTimeout_ThrowsTimeoutException() {
        // Arrange - Setup WebClient method call chain for DELETE
        when(webClient.delete()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(eq("/api/delete-index/{userId}/{folderId}"), any(Object.class), any(Object.class)))
                .thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        
        javaSearchClient = new JavaSearchClientImpl(
                webClientBuilder,
                "http://localhost:5001",
                1,  // very short timeout
                true
        );

        Long userId = 1L;
        Long folderId = 5L;

        // Simulate timeout
        when(responseSpec.bodyToMono(Void.class))
                .thenReturn(Mono.delay(Duration.ofSeconds(10)).then(Mono.empty()));

        // Act & Assert - should throw timeout exception
        assertThrows(RuntimeException.class,
                () -> javaSearchClient.deleteIndex(userId, folderId));
    }

    @Test
    void constructor_InitializesWithCorrectParameters() {
        // Act
        javaSearchClient = new JavaSearchClientImpl(
                webClientBuilder,
                "http://localhost:5001",
                30,
                true
        );

        // Assert - verify WebClient builder was called with correct URL
        verify(webClientBuilder).baseUrl("http://localhost:5001");
        verify(webClientBuilder).build();
    }
}
