package com.imagesearch.controller;

import com.imagesearch.model.dto.request.LoginRequest;
import com.imagesearch.model.dto.request.RegisterRequest;
import com.imagesearch.model.dto.response.LoginResponse;
import com.imagesearch.model.dto.response.RegisterResponse;
import com.imagesearch.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/**
 * Tests for UserController - Authentication endpoints.
 *
 * Tests cover:
 * - User registration
 * - User login
 * - Input validation
 * - Error handling
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("User Authentication Tests")
public class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @Nested
    @DisplayName("Registration Tests")
    class RegistrationTests {

        @Test
        @DisplayName("Should successfully register a new user")
        void testRegisterSuccess() throws Exception {
            RegisterRequest request = new RegisterRequest();
            request.setUsername("testuser");
            request.setPassword("SecurePass123!");

            RegisterResponse response = new RegisterResponse(1L, "testuser");

            when(userService.register(any(RegisterRequest.class)))
                    .thenReturn(response);

            mockMvc.perform(post("/api/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("testuser"));
        }

        @Test
        @DisplayName("Should reject registration with existing username")
        void testRegisterDuplicateUsername() throws Exception {
            RegisterRequest request = new RegisterRequest();
            request.setUsername("existinguser");
            request.setPassword("SecurePass123!");

            when(userService.register(any(RegisterRequest.class)))
                    .thenThrow(new RuntimeException("Username already exists"));

            mockMvc.perform(post("/api/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().is4xxClientError());
        }

        @Test
        @DisplayName("Should reject registration with weak password")
        void testRegisterWeakPassword() throws Exception {
            RegisterRequest request = new RegisterRequest();
            request.setUsername("testuser");
            request.setPassword("123");  // Too short

            mockMvc.perform(post("/api/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().is4xxClientError());
        }

        @Test
        @DisplayName("Should reject registration with empty username")
        void testRegisterEmptyUsername() throws Exception {
            RegisterRequest request = new RegisterRequest();
            request.setUsername("");
            request.setPassword("SecurePass123!");

            mockMvc.perform(post("/api/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().is4xxClientError());
        }

        @Test
        @DisplayName("Should reject registration with missing fields")
        void testRegisterMissingFields() throws Exception {
            mockMvc.perform(post("/api/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().is4xxClientError());
        }
    }

    @Nested
    @DisplayName("Login Tests")
    class LoginTests {

        @Test
        @DisplayName("Should successfully login with valid credentials")
        void testLoginSuccess() throws Exception {
            LoginRequest request = new LoginRequest();
            request.setUsername("testuser");
            request.setPassword("SecurePass123!");

            LoginResponse response = new LoginResponse("test-token-123", 1L, "testuser");

            when(userService.login(any(LoginRequest.class)))
                    .thenReturn(response);

            mockMvc.perform(post("/api/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").exists())
                    .andExpect(jsonPath("$.token").isNotEmpty());
        }

        @Test
        @DisplayName("Should reject login with wrong password")
        void testLoginWrongPassword() throws Exception {
            LoginRequest request = new LoginRequest();
            request.setUsername("testuser");
            request.setPassword("WrongPassword!");

            when(userService.login(any(LoginRequest.class)))
                    .thenThrow(new RuntimeException("Invalid credentials"));

            mockMvc.perform(post("/api/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should reject login with non-existent user")
        void testLoginNonexistentUser() throws Exception {
            LoginRequest request = new LoginRequest();
            request.setUsername("nonexistent");
            request.setPassword("Password123!");

            when(userService.login(any(LoginRequest.class)))
                    .thenThrow(new RuntimeException("User not found"));

            mockMvc.perform(post("/api/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should reject login with missing credentials")
        void testLoginMissingFields() throws Exception {
            mockMvc.perform(post("/api/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().is4xxClientError());
        }
    }

    @Nested
    @DisplayName("Security Tests")
    class SecurityTests {

        @Test
        @DisplayName("Should handle SQL injection attempts safely")
        void testSqlInjectionPrevention() throws Exception {
            LoginRequest request = new LoginRequest();
            request.setUsername("admin' OR '1'='1");
            request.setPassword("password");

            when(userService.login(any(LoginRequest.class)))
                    .thenThrow(new RuntimeException("Invalid credentials"));

            mockMvc.perform(post("/api/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().string(not(containsString("SELECT"))))
                    .andExpect(content().string(not(containsString("FROM"))));
        }

        @Test
        @DisplayName("Should handle XSS attempts safely")
        void testXssProtection() throws Exception {
            RegisterRequest request = new RegisterRequest();
            request.setUsername("<script>alert('xss')</script>");
            request.setPassword("SecurePass123!");

            mockMvc.perform(post("/api/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().is4xxClientError());
        }
    }
}
