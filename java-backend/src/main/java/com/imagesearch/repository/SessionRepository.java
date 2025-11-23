package com.imagesearch.repository;

import com.imagesearch.model.entity.Session;
import com.imagesearch.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Repository interface for Session entity.
 *
 * Manages user authentication tokens and session lifecycle.
 */
@Repository
public interface SessionRepository extends JpaRepository<Session, String> {

    /**
     * Find session by token (for authentication).
     * @param token The session token
     * @return Optional containing the session if found
     */
    Optional<Session> findByToken(String token);

    /**
     * Delete all sessions for a specific user (logout all devices).
     * @param user The user whose sessions to delete
     */
    void deleteByUser(User user);

    /**
     * Delete expired sessions (cleanup task).
     * @param now Current timestamp
     */
    @Modifying
    @Query("DELETE FROM Session s WHERE s.expiresAt <= :now")
    void deleteExpiredSessions(LocalDateTime now);
}
