package com.imagesearch.repository;

import com.imagesearch.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

/**
 * Repository interface for User entity.
 *
 * Spring Data JPA automatically implements this interface at runtime,
 * providing CRUD operations and custom query methods.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find user by username (for login).
     * @param username The username to search for
     * @return Optional containing the user if found
     */
    Optional<User> findByUsername(String username);

    /**
     * Check if username already exists (for registration validation).
     * @param username The username to check
     * @return true if username exists
     */
    boolean existsByUsername(String username);
}
