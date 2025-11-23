package com.imagesearch.controller;

import com.imagesearch.model.dto.request.LoginRequest;
import com.imagesearch.model.dto.request.RegisterRequest;
import com.imagesearch.model.dto.response.LoginResponse;
import com.imagesearch.model.dto.response.MessageResponse;
import com.imagesearch.model.dto.response.RegisterResponse;
import com.imagesearch.service.SessionService;
import com.imagesearch.service.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for user management endpoints.
 *
 * RESTful API design:
 * - POST /api/users/register - Create new user
 * - POST /api/users/login - Authenticate user
 * - POST /api/users/logout - Logout user
 * - DELETE /api/users/account - Delete user account
 *
 * Demonstrates:
 * - RESTful naming conventions
 * - HTTP method semantics (POST for creation, DELETE for removal)
 * - DTO validation with @Valid
 * - Proper HTTP status codes
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;
    private final SessionService sessionService;

    public UserController(UserService userService, SessionService sessionService) {
        this.userService = userService;
        this.sessionService = sessionService;
    }

    /**
     * Register a new user.
     * POST /api/users/register
     */
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        logger.info("Registration request for username: {}", request.getUsername());
        RegisterResponse response = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Login user and create session.
     * POST /api/users/login
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        logger.info("Login request for username: {}", request.getUsername());
        LoginResponse response = userService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Logout user (invalidate session).
     * POST /api/users/logout
     */
    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(@RequestBody TokenRequest request) {
        logger.info("Logout request");
        userService.logout(request.token());
        return ResponseEntity.ok(new MessageResponse("Logout successful"));
    }

    /**
     * Delete user account.
     * DELETE /api/users/account
     */
    @DeleteMapping("/delete")
    public ResponseEntity<MessageResponse> deleteAccount(@RequestBody TokenRequest request) {
        Long userId = sessionService.validateTokenAndGetUserId(request.token());
        logger.info("Delete account request for user: {}", userId);
        userService.deleteAccount(userId);
        return ResponseEntity.ok(new MessageResponse("Account deleted successfully"));
    }

    // Helper record for token-only requests
    private record TokenRequest(String token) {}
}
