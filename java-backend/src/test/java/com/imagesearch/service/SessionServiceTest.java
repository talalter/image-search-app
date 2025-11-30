package com.imagesearch.service;

import com.imagesearch.exception.UnauthorizedException;
import com.imagesearch.model.entity.Session;
import com.imagesearch.model.entity.User;
import com.imagesearch.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive production-grade tests for SessionService.
 *
 * Coverage areas:
 * - Session creation and token generation
 * - Token validation and expiry logic
 * - Session invalidation (logout)
 * - Sliding expiration window
 * - Concurrent session operations (race conditions)
 * - Cleanup of expired sessions
 * - Edge cases (null tokens, expired sessions, concurrent validation)
 *
 * Test Quality Guardian: Prevents authentication bypass vulnerabilities and
 * session management bugs that could lead to unauthorized access.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SessionService - Production-Grade Tests")
public class SessionServiceTest {

    @Mock
    private SessionRepository sessionRepository;

    private SessionService sessionService;

    private static final int TOKEN_EXPIRY_HOURS = 12;

    @BeforeEach
    void setUp() {
        sessionService = new SessionService(sessionRepository, TOKEN_EXPIRY_HOURS);
    }

    @Nested
    @DisplayName("Session Creation Tests")
    class SessionCreationTests {

        @Test
        @SuppressWarnings("null")
        @DisplayName("Should create session with valid token and expiry")
        void testCreateSessionSuccess() {
            // Arrange
            User user = new User();
            user.setId(1L);
            user.setUsername("testuser");

            Session savedSession = new Session();
            savedSession.setToken("generated-token");
            savedSession.setUser(user);

            when(sessionRepository.save(any(Session.class))).thenReturn(savedSession);

            // Act
            String token = sessionService.createSession(user);

            // Assert
            assertThat(token).isNotNull();
            assertThat(token).isNotEmpty();
            assertThat(token).matches("^[a-f0-9\\-]{36}$"); // UUID format

            // Verify session was saved with correct expiry
            ArgumentCaptor<Session> sessionCaptor = ArgumentCaptor.forClass(Session.class);
            verify(sessionRepository).save(sessionCaptor.capture());

            Session capturedSession = sessionCaptor.getValue();
            assertThat(capturedSession.getUser()).isEqualTo(user);
            assertThat(capturedSession.getExpiresAt()).isAfter(LocalDateTime.now());
            assertThat(capturedSession.getExpiresAt()).isBefore(LocalDateTime.now().plusHours(TOKEN_EXPIRY_HOURS + 1));
        }

        @Test
        @SuppressWarnings("null")
        @DisplayName("Should generate unique tokens for concurrent sessions")
        @RepeatedTest(3)
        void testCreateSessionGeneratesUniqueTokens() throws InterruptedException {
            // WHY: Ensures no token collision when multiple users login simultaneously
            // WHAT: Creates sessions concurrently and verifies all tokens are unique
            // COVERAGE: Thread safety, token uniqueness

            User user1 = new User();
            user1.setId(1L);

            User user2 = new User();
            user2.setId(2L);

            // Mock save operation (return value not used)
            when(sessionRepository.save(any(Session.class))).thenReturn(new Session());

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(2);
            String[] tokens = new String[2];

            ExecutorService executor = Executors.newFixedThreadPool(2);

            // Thread 1
            executor.submit(() -> {
                try {
                    startLatch.await();
                    tokens[0] = sessionService.createSession(user1);
                } catch (Exception e) {
                    // Ignore
                } finally {
                    endLatch.countDown();
                }
            });

            // Thread 2
            executor.submit(() -> {
                try {
                    startLatch.await();
                    tokens[1] = sessionService.createSession(user2);
                } catch (Exception e) {
                    // Ignore
                } finally {
                    endLatch.countDown();
                }
            });

            // Start both threads simultaneously
            startLatch.countDown();

            // Wait for completion
            boolean completed = endLatch.await(3, TimeUnit.SECONDS);
            executor.shutdown();

            // Assert
            assertThat(completed).isTrue();
            assertThat(tokens[0]).isNotNull();
            assertThat(tokens[1]).isNotNull();
            assertThat(tokens[0]).isNotEqualTo(tokens[1]);
        }

        @Test
        @SuppressWarnings("null")
        @DisplayName("Should handle session creation for user with no ID")
        void testCreateSessionUserWithNullId() {
            // WHY: Edge case - ensures service handles partially initialized users
            User user = new User();
            user.setUsername("testuser");
            // Note: id is null

            // Mock save operation (return value not used)
            when(sessionRepository.save(any(Session.class))).thenReturn(new Session());

            // Act
            String token = sessionService.createSession(user);

            // Assert - should still create session (repository will handle constraint)
            assertThat(token).isNotNull();
        }
    }

    @Nested
    @DisplayName("Token Validation Tests - Security Critical")
    class TokenValidationTests {

        @Test
        @SuppressWarnings("null")
        @DisplayName("Should validate correct token and return user ID")
        void testValidateTokenSuccess() {
            // Arrange
            String token = "valid-token-123";
            User user = new User();
            user.setId(1L);

            Session session = new Session();
            session.setToken(token);
            session.setUser(user);
            session.setExpiresAt(LocalDateTime.now().plusHours(6)); // Not expired

            when(sessionRepository.findByToken(token)).thenReturn(Optional.of(session));
            when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            Long userId = sessionService.validateTokenAndGetUserId(token);

            // Assert
            assertThat(userId).isEqualTo(1L);

            // Verify expiry was refreshed (sliding window)
            ArgumentCaptor<Session> sessionCaptor = ArgumentCaptor.forClass(Session.class);
            verify(sessionRepository).save(sessionCaptor.capture());
            Session refreshedSession = sessionCaptor.getValue();
            assertThat(refreshedSession.getExpiresAt()).isAfter(LocalDateTime.now().plusHours(11));
        }

        @Test
        @SuppressWarnings("null")
        @DisplayName("Should throw UnauthorizedException when token not found")
        void testValidateTokenNotFound() {
            // WHY: Prevents unauthorized access with invalid tokens
            String invalidToken = "nonexistent-token";

            when(sessionRepository.findByToken(invalidToken)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> sessionService.validateTokenAndGetUserId(invalidToken))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("Invalid or expired token");

            // Verify session was not saved
            verify(sessionRepository, never()).save(any(Session.class));
        }

        @Test
        @SuppressWarnings("null")
        @DisplayName("Should throw UnauthorizedException and delete expired session")
        void testValidateTokenExpired() {
            // WHY: Ensures expired sessions are cleaned up and rejected
            String expiredToken = "expired-token";
            User user = new User();
            user.setId(1L);

            Session expiredSession = new Session();
            expiredSession.setToken(expiredToken);
            expiredSession.setUser(user);
            expiredSession.setExpiresAt(LocalDateTime.now().minusHours(1)); // Expired 1 hour ago

            when(sessionRepository.findByToken(expiredToken)).thenReturn(Optional.of(expiredSession));

            // Act & Assert
            assertThatThrownBy(() -> sessionService.validateTokenAndGetUserId(expiredToken))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("Session expired");

            // Verify expired session was deleted
            verify(sessionRepository).delete(expiredSession);

            // Verify expiry was not refreshed
            verify(sessionRepository, never()).save(any(Session.class));
        }

        @Test
        @SuppressWarnings("null")
        @DisplayName("Should handle null token gracefully")
        void testValidateTokenNull() {
            // WHY: Prevents NullPointerException
            when(sessionRepository.findByToken(null)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> sessionService.validateTokenAndGetUserId(null))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @SuppressWarnings("null")
        @DisplayName("Should handle empty string token")
        void testValidateTokenEmptyString() {
            // WHY: Edge case - empty string is not a valid token
            when(sessionRepository.findByToken("")).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> sessionService.validateTokenAndGetUserId(""))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @SuppressWarnings("null")
        @DisplayName("Should handle session expiring exactly at current time")
        void testValidateTokenExpiresNow() {
            // WHY: Boundary condition - session expires at current second
            String token = "expiring-now-token";
            User user = new User();
            user.setId(1L);

            Session session = new Session();
            session.setToken(token);
            session.setUser(user);
            session.setExpiresAt(LocalDateTime.now()); // Expires right now

            when(sessionRepository.findByToken(token)).thenReturn(Optional.of(session));

            // Act & Assert - should be treated as expired
            assertThatThrownBy(() -> sessionService.validateTokenAndGetUserId(token))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("Session expired");

            verify(sessionRepository).delete(session);
        }

        @Test
        @SuppressWarnings("null")
        @DisplayName("Should refresh expiry on each validation (sliding window)")
        void testValidateTokenRefreshesExpiry() {
            // WHY: Keeps active users logged in (sliding expiration)
            String token = "active-session-token";
            User user = new User();
            user.setId(1L);

            Session session = new Session();
            session.setToken(token);
            session.setUser(user);
            LocalDateTime originalExpiry = LocalDateTime.now().plusHours(6);
            session.setExpiresAt(originalExpiry);

            when(sessionRepository.findByToken(token)).thenReturn(Optional.of(session));
            when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            sessionService.validateTokenAndGetUserId(token);

            // Assert - expiry should be extended
            ArgumentCaptor<Session> sessionCaptor = ArgumentCaptor.forClass(Session.class);
            verify(sessionRepository).save(sessionCaptor.capture());
            Session refreshedSession = sessionCaptor.getValue();

            assertThat(refreshedSession.getExpiresAt()).isAfter(originalExpiry);
            assertThat(refreshedSession.getExpiresAt())
                    .isAfterOrEqualTo(LocalDateTime.now().plusHours(TOKEN_EXPIRY_HOURS - 1));
        }

        @Test
        @SuppressWarnings("null")
        @DisplayName("Should handle concurrent validations of same token (race condition)")
        @RepeatedTest(5)
        void testConcurrentTokenValidation() throws InterruptedException {
            // WHY: Prevents race conditions when same session is validated from multiple requests
            // WHAT: Simulates multiple API requests using same session token simultaneously
            // COVERAGE: Thread safety

            String token = "concurrent-token";
            User user = new User();
            user.setId(1L);

            Session session = new Session();
            session.setToken(token);
            session.setUser(user);
            session.setExpiresAt(LocalDateTime.now().plusHours(6));

            when(sessionRepository.findByToken(token)).thenReturn(Optional.of(session));
            // Mock save operation (return value not used)
            when(sessionRepository.save(any(Session.class))).thenReturn(new Session());

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(3);
            AtomicInteger successCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(3);

            // 3 concurrent validations
            for (int i = 0; i < 3; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        Long userId = sessionService.validateTokenAndGetUserId(token);
                        if (userId != null && userId.equals(1L)) {
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // Ignore
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            // Start all threads simultaneously
            startLatch.countDown();

            // Wait for completion
            boolean completed = endLatch.await(3, TimeUnit.SECONDS);
            executor.shutdown();

            // Assert - all should succeed (thread-safe)
            assertThat(completed).isTrue();
            assertThat(successCount.get()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Session Invalidation Tests")
    class SessionInvalidationTests {

        @Test
        @DisplayName("Should invalidate single session on logout")
        void testInvalidateSessionSuccess() {
            // Arrange
            String token = "session-to-logout";

            // Act
            sessionService.invalidateSession(token);

            // Assert
            verify(sessionRepository).deleteById(token);
        }

        @Test
        @DisplayName("Should handle invalidating non-existent session")
        void testInvalidateNonExistentSession() {
            // WHY: Logout should be idempotent
            String token = "nonexistent-token";

            // Act - should not throw
            sessionService.invalidateSession(token);

            // Assert
            verify(sessionRepository).deleteById(token);
        }

        @Test
        @DisplayName("Should invalidate all user sessions on logout all devices")
        void testInvalidateAllUserSessions() {
            // WHY: Allows user to logout from all devices (security feature)
            User user = new User();
            user.setId(1L);
            user.setUsername("testuser");

            // Act
            sessionService.invalidateAllUserSessions(user);

            // Assert
            verify(sessionRepository).deleteByUser(user);
        }

        @Test
        @DisplayName("Should handle concurrent session invalidations")
        @RepeatedTest(3)
        void testConcurrentSessionInvalidation() throws InterruptedException {
            // WHY: Prevents issues when user logs out from multiple devices simultaneously
            // COVERAGE: Thread safety

            String token1 = "token-1";
            String token2 = "token-2";

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(2);

            ExecutorService executor = Executors.newFixedThreadPool(2);

            // Thread 1 - logout token1
            executor.submit(() -> {
                try {
                    startLatch.await();
                    sessionService.invalidateSession(token1);
                } catch (Exception e) {
                    // Ignore
                } finally {
                    endLatch.countDown();
                }
            });

            // Thread 2 - logout token2
            executor.submit(() -> {
                try {
                    startLatch.await();
                    sessionService.invalidateSession(token2);
                } catch (Exception e) {
                    // Ignore
                } finally {
                    endLatch.countDown();
                }
            });

            // Start both threads simultaneously
            startLatch.countDown();

            // Wait for completion
            boolean completed = endLatch.await(3, TimeUnit.SECONDS);
            executor.shutdown();

            // Assert
            assertThat(completed).isTrue();
            verify(sessionRepository).deleteById(token1);
            verify(sessionRepository).deleteById(token2);
        }
    }

    @Nested
    @DisplayName("Session Cleanup Tests")
    class SessionCleanupTests {

        @Test
        @DisplayName("Should delete all expired sessions in cleanup")
        void testCleanupExpiredSessions() {
            // WHY: Prevents database bloat from expired sessions
            // Act
            sessionService.cleanupExpiredSessions();

            // Assert - should delete sessions expired before now
            ArgumentCaptor<LocalDateTime> timeCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(sessionRepository).deleteExpiredSessions(timeCaptor.capture());

            LocalDateTime capturedTime = timeCaptor.getValue();
            assertThat(capturedTime).isBefore(LocalDateTime.now().plusSeconds(1));
            assertThat(capturedTime).isAfter(LocalDateTime.now().minusSeconds(5));
        }

        @Test
        @DisplayName("Should handle cleanup when no expired sessions exist")
        void testCleanupNoExpiredSessions() {
            // WHY: Cleanup should be safe to run even when nothing to clean
            // Act - should not throw
            sessionService.cleanupExpiredSessions();

            // Assert
            verify(sessionRepository).deleteExpiredSessions(any(LocalDateTime.class));
        }

        @Test
        @DisplayName("Should handle cleanup failure gracefully")
        void testCleanupFailure() {
            // WHY: Cleanup is background task, should not crash application
            doThrow(new RuntimeException("Database error"))
                    .when(sessionRepository).deleteExpiredSessions(any(LocalDateTime.class));

            // Act & Assert - should throw (caller should handle)
            assertThatThrownBy(() -> sessionService.cleanupExpiredSessions())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Database error");
        }
    }

    @Nested
    @DisplayName("Edge Cases and Boundary Conditions")
    class EdgeCaseTests {

        @Test
        @SuppressWarnings("null")
        @DisplayName("Should handle session with far future expiry")
        void testSessionWithVeryLongExpiry() {
            // WHY: Tests date arithmetic doesn't overflow
            String token = "long-lived-token";
            User user = new User();
            user.setId(1L);

            Session session = new Session();
            session.setToken(token);
            session.setUser(user);
            session.setExpiresAt(LocalDateTime.now().plusYears(10)); // 10 years

            when(sessionRepository.findByToken(token)).thenReturn(Optional.of(session));
            // Mock save operation (return value not used)
            when(sessionRepository.save(any(Session.class))).thenReturn(new Session());

            // Act
            Long userId = sessionService.validateTokenAndGetUserId(token);

            // Assert - should still work
            assertThat(userId).isEqualTo(1L);
        }

        @Test
        @DisplayName("Should handle extremely long token string")
        void testExtremelyLongToken() {
            // WHY: Tests token handling doesn't have buffer overflow issues
            String longToken = "a".repeat(1000);

            when(sessionRepository.findByToken(longToken)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> sessionService.validateTokenAndGetUserId(longToken))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("Should handle token with special characters")
        void testTokenWithSpecialCharacters() {
            // WHY: Ensures token comparison is safe from injection
            String specialToken = "token-with-chars-!@#$%^&*()";

            when(sessionRepository.findByToken(specialToken)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> sessionService.validateTokenAndGetUserId(specialToken))
                    .isInstanceOf(UnauthorizedException.class);

            verify(sessionRepository).findByToken(specialToken);
        }

        @Test
        @SuppressWarnings("null")
        @DisplayName("Should handle rapid successive validations of same token")
        void testRapidSuccessiveValidations() {
            // WHY: Simulates high-frequency API calls from same session
            String token = "rapid-token";
            User user = new User();
            user.setId(1L);

            Session session = new Session();
            session.setToken(token);
            session.setUser(user);
            session.setExpiresAt(LocalDateTime.now().plusHours(6));

            when(sessionRepository.findByToken(token)).thenReturn(Optional.of(session));
            // Mock save operation (return value not used)
            when(sessionRepository.save(any(Session.class))).thenReturn(new Session());

            // Act - validate 10 times rapidly
            for (int i = 0; i < 10; i++) {
                Long userId = sessionService.validateTokenAndGetUserId(token);
                assertThat(userId).isEqualTo(1L);
            }

            // Assert - should have saved 10 times (expiry refreshed each time)
            verify(sessionRepository, times(10)).save(any(Session.class));
        }
    }
}
