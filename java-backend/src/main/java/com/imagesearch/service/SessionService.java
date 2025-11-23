package com.imagesearch.service;

import com.imagesearch.exception.UnauthorizedException;
import com.imagesearch.model.entity.Session;
import com.imagesearch.model.entity.User;
import com.imagesearch.repository.SessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service for managing user sessions (token-based authentication).
 *
 * Handles:
 * - Creating new sessions (login)
 * - Validating tokens
 * - Invalidating sessions (logout)
 * - Session expiry
 */
@Service
public class SessionService {

    private final SessionRepository sessionRepository;
    private final int tokenExpiryHours;

    public SessionService(
            SessionRepository sessionRepository,
            @Value("${session.token-expiry-hours}") int tokenExpiryHours) {
        this.sessionRepository = sessionRepository;
        this.tokenExpiryHours = tokenExpiryHours;
    }

    /**
     * Create a new session for a user (on login).
     *
     * @param user The authenticated user
     * @return The generated session token
     */
    @Transactional
    public String createSession(User user) {
        String token = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(tokenExpiryHours);

        Session session = new Session();
        session.setToken(token);
        session.setUser(user);
        session.setExpiresAt(expiresAt);

        sessionRepository.save(session);
        return token;
    }

    /**
     * Validate a token and return the associated user ID.
     *
     * @param token The session token
     * @return User ID if token is valid
     * @throws UnauthorizedException if token is invalid or expired
     */
    public Long validateTokenAndGetUserId(String token) {
        Session session = sessionRepository.findByToken(token)
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired token"));

        if (session.isExpired()) {
            sessionRepository.delete(session);
            throw new UnauthorizedException("Session expired");
        }

        // Refresh expiry on each use (sliding expiration)
        session.setExpiresAt(LocalDateTime.now().plusHours(tokenExpiryHours));
        sessionRepository.save(session);

        return session.getUser().getId();
    }

    /**
     * Invalidate a session (logout).
     *
     * @param token The session token to invalidate
     */
    @Transactional
    public void invalidateSession(String token) {
        sessionRepository.deleteById(token);
    }

    /**
     * Invalidate all sessions for a user (logout all devices).
     *
     * @param user The user whose sessions to invalidate
     */
    @Transactional
    public void invalidateAllUserSessions(User user) {
        sessionRepository.deleteByUser(user);
    }

    /**
     * Clean up expired sessions (scheduled task).
     */
    @Transactional
    public void cleanupExpiredSessions() {
        sessionRepository.deleteExpiredSessions(LocalDateTime.now());
    }
}
