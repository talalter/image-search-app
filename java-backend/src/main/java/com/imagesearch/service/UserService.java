package com.imagesearch.service;

import com.imagesearch.client.SearchClient;
import com.imagesearch.exception.DuplicateResourceException;
import com.imagesearch.exception.ResourceNotFoundException;
import com.imagesearch.exception.UnauthorizedException;
import com.imagesearch.model.dto.request.LoginRequest;
import com.imagesearch.model.dto.request.RegisterRequest;
import com.imagesearch.model.dto.response.LoginResponse;
import com.imagesearch.model.dto.response.RegisterResponse;
import com.imagesearch.model.entity.Folder;
import com.imagesearch.model.entity.User;
import com.imagesearch.repository.FolderRepository;
import com.imagesearch.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Service for user management (authentication, registration).
 *
 * Demonstrates:
 * - Password hashing with BCrypt
 * - Transaction management
 * - Exception handling for business logic
 */
@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final SessionService sessionService;
    private final FolderRepository folderRepository;
    private final SearchClient searchClient;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            SessionService sessionService,
            FolderRepository folderRepository,
            SearchClient searchClient) {
        this.userRepository = userRepository;
        this.sessionService = sessionService;
        this.folderRepository = folderRepository;
        this.searchClient = searchClient;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    /**
     * Register a new user.
     *
     * @param request Registration details
     * @return RegisterResponse with user info
     * @throws DuplicateResourceException if username already exists
     */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        logger.info("Registering new user: {}", request.getUsername());

        // Check if username already exists
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username already exists");
        }

        // Hash password using BCrypt
        String hashedPassword = passwordEncoder.encode(request.getPassword());

        // Create and save user
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(hashedPassword);

        User savedUser = userRepository.save(user);
        logger.info("User registered successfully: id={}", savedUser.getId());

        return new RegisterResponse(savedUser.getId(), savedUser.getUsername());
    }

    /**
     * Authenticate user and create session.
     *
     * @param request Login credentials
     * @return LoginResponse with token
     * @throws UnauthorizedException if credentials are invalid
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        logger.info("Login attempt for user: {}", request.getUsername());

        // Find user by username
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("Invalid credentials");
        }

        // Create session token
        String token = sessionService.createSession(user);

        logger.info("User logged in successfully: id={}", user.getId());
        return new LoginResponse(token, user.getId(), user.getUsername());
    }

    /**
     * Logout user (invalidate session).
     *
     * @param token Session token
     */
    public void logout(String token) {
        sessionService.invalidateSession(token);
        logger.info("User logged out");
    }

    /**
     * Delete user account and all associated data.
     *
     * IMPORTANT: Deletion order matters:
     * 1. Delete search indices BEFORE deleting user from database
     *    (because we need to query folders, which cascade-delete with user)
     * 2. Delete user from database
     * 3. Delete physical files
     *
     * @param userId User ID to delete
     */
    @SuppressWarnings("null")
    @Transactional
    public void deleteAccount(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        logger.info("Deleting user account: id={}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        // 1. Delete search indices (requires folder query before user deletion)
        deleteUserSearchIndices(userId);

        // 2. Invalidate all sessions
        sessionService.invalidateAllUserSessions(user);

        // 3. Delete user (cascades to folders, images, shares via JPA)
        userRepository.delete(user);

        // 4. Delete physical image files from filesystem
        deleteUserImages(userId);

        logger.info("User account and all associated data deleted: id={}", userId);
    }

    /**
     * Delete all physical image files for a user.
     */
    private void deleteUserImages(Long userId) {
        try {
            Path currentDir = Paths.get("").toAbsolutePath();
            Path projectRoot = currentDir.getFileName().toString().equals("java-backend")
                ? currentDir.getParent()
                : currentDir;
            Path userImagesPath = projectRoot.resolve("data").resolve("uploads").resolve("images").resolve(userId.toString());

            if (Files.exists(userImagesPath)) {
                try (Stream<Path> paths = Files.walk(userImagesPath)) {
                    paths.sorted(Comparator.reverseOrder())
                         .map(Path::toFile)
                         .forEach(File::delete);
                }
                logger.info("Deleted user images: {}", userImagesPath);
            }
        } catch (IOException e) {
            logger.error("Failed to delete user images for userId=" + userId, e);
            // Don't throw - best effort cleanup
        }
    }

    /**
     * Delete all search indices for a user.
     *
     * Iterates through all user folders and deletes their search indices.
     * This must be called BEFORE deleting the user from the database,
     * because we need to query folders (which cascade-delete with the user).
     *
     * The actual backend (FAISS or Elasticsearch) is determined by the active SearchClient implementation.
     */
    private void deleteUserSearchIndices(Long userId) {
        try {
            // Get user entity
            @SuppressWarnings("null")
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                logger.warn("Cannot delete search indices: user {} not found", userId);
                return;
            }

            // Get all folders owned by this user
            List<Folder> userFolders = folderRepository.findByUser(user);

            if (userFolders.isEmpty()) {
                logger.info("No folders found for user {}, no search indices to delete", userId);
                return;
            }

            logger.info("Deleting search indices for {} folders of user {}",
                       userFolders.size(), userId);

            // Delete search index for each folder (delegates to active backend)
            for (Folder folder : userFolders) {
                searchClient.deleteIndex(userId, folder.getId());
            }

            logger.info("Completed search index deletion for user {}", userId);

        } catch (Exception e) {
            logger.error("Failed to delete search indices for userId=" + userId, e);
            // Don't throw - best effort cleanup
        }
    }

    /**
     * Get user by ID.
     *
     * @param userId User ID
     * @return User entity
     */
    public User getUserById(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }
}
