package com.imagesearch.controller;

import com.imagesearch.model.dto.request.LoginRequest;
import com.imagesearch.model.dto.request.RegisterRequest;
import com.imagesearch.model.dto.response.LoginResponse;
import com.imagesearch.model.dto.response.RegisterResponse;
import com.imagesearch.service.SessionService;
import com.imagesearch.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for UserController using pure Mockito without Spring context.
 * 
 * This approach:
 * - Avoids Spring Boot context loading issues
 * - Tests controller logic in isolation
 * - Runs much faster than integration tests
 * - Focuses on HTTP request/response behavior
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("User Controller Unit Tests")
public class UserControllerUnitTest {

    @Mock
    private UserService userService;

    @Mock
    private SessionService sessionService;

    @InjectMocks
    private UserController userController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(userController)
                .build();
        objectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("Registration Tests")
    class RegistrationTests {

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should successfully register a new user")
        void testRegisterSuccess() throws Exception {
            RegisterRequest request = new RegisterRequest();
            request.setUsername("testuser");
            request.setPassword("SecurePass123!");

            RegisterResponse response = new RegisterResponse(1L, "testuser");

            when(userService.register(any(RegisterRequest.class)))
                    .thenReturn(response);

            mockMvc.perform(post("/api/users/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.username").value("testuser"))
                    .andExpect(jsonPath("$.id").value(1));
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should reject registration with existing username")
        void testRegisterDuplicateUsername() throws Exception {
            RegisterRequest request = new RegisterRequest();
            request.setUsername("existinguser");
            request.setPassword("SecurePass123!");

            when(userService.register(any(RegisterRequest.class)))
                    .thenThrow(new RuntimeException("Username already exists"));

            mockMvc.perform(post("/api/users/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().is5xxServerError()); // RuntimeException -> 500
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should handle invalid JSON")
        void testRegisterInvalidJson() throws Exception {
            mockMvc.perform(post("/api/users/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ invalid json }"))
                    .andExpect(status().is4xxClientError());
        }
    }

    @Nested
    @DisplayName("Login Tests")
    class LoginTests {

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should successfully login with valid credentials")
        void testLoginSuccess() throws Exception {
            LoginRequest request = new LoginRequest();
            request.setUsername("testuser");
            request.setPassword("SecurePass123!");

            LoginResponse response = new LoginResponse("test-token-123", 1L, "testuser");

            when(userService.login(any(LoginRequest.class)))
                    .thenReturn(response);

            mockMvc.perform(post("/api/users/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value("test-token-123"))
                    .andExpect(jsonPath("$.user_id").value(1))
                    .andExpect(jsonPath("$.username").value("testuser"))
                    .andDo(result -> {
                        System.out.println("Response body: " + result.getResponse().getContentAsString());
                    });
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should reject login with wrong credentials")
        void testLoginWrongCredentials() throws Exception {
            LoginRequest request = new LoginRequest();
            request.setUsername("testuser");
            request.setPassword("WrongPassword!");

            when(userService.login(any(LoginRequest.class)))
                    .thenThrow(new RuntimeException("Invalid credentials"));

            mockMvc.perform(post("/api/users/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().is5xxServerError()); // RuntimeException -> 500
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should handle missing request body")
        void testLoginMissingBody() throws Exception {
            mockMvc.perform(post("/api/users/login")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().is4xxClientError());
        }
    }
}