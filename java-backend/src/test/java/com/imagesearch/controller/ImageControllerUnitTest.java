package com.imagesearch.controller;

import com.imagesearch.model.dto.response.UploadResponse;
import com.imagesearch.service.ImageService;
import com.imagesearch.service.SearchService;
import com.imagesearch.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for ImageController using pure Mockito without Spring context.
 * 
 * This approach:
 * - Avoids Spring Boot context loading issues
 * - Tests controller logic in isolation
 * - Runs much faster than integration tests
 * - Focuses on HTTP request/response behavior
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Image Controller Unit Tests")
public class ImageControllerUnitTest {

    @Mock
    private ImageService imageService;

    @Mock
    private SearchService searchService;

    @Mock
    private SessionService sessionService;

    @InjectMocks
    private ImageController imageController;

    private MockMvc mockMvc;

    private static final String TEST_TOKEN = "test-token-123";
    private static final Long TEST_USER_ID = 1L;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(imageController)
                .build();
    }

    @Nested
    @DisplayName("Image Upload Tests")
    class ImageUploadTests {

        @Test
        @DisplayName("Should successfully upload a single image")
        void testUploadSingleImage() throws Exception {
            // Mock authentication
            when(sessionService.validateTokenAndGetUserId(TEST_TOKEN)).thenReturn(TEST_USER_ID);

            // Mock upload service
            UploadResponse response = new UploadResponse(
                    "Upload successful",
                    1L,
                    1
            );
            when(imageService.uploadImages(anyLong(), anyString(), anyList()))
                    .thenReturn(response);

            // Create test image file
            MockMultipartFile file = new MockMultipartFile(
                    "files",
                    "test.png",
                    MediaType.IMAGE_PNG_VALUE,
                    "test image content".getBytes()
            );

            mockMvc.perform(multipart("/api/images/upload")
                            .file(file)
                            .param("token", TEST_TOKEN)
                            .param("folderName", "test_folder"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.folder_id").value(1))
                    .andExpect(jsonPath("$.uploaded_count").value(1));
        }

        @Test
        @DisplayName("Should successfully upload multiple images")
        void testUploadMultipleImages() throws Exception {
            when(sessionService.validateTokenAndGetUserId(TEST_TOKEN)).thenReturn(TEST_USER_ID);

            UploadResponse response = new UploadResponse(
                    "Upload successful",
                    1L,
                    3
            );
            when(imageService.uploadImages(anyLong(), anyString(), anyList()))
                    .thenReturn(response);

            // Create multiple test files
            MockMultipartFile file1 = new MockMultipartFile(
                    "files", "test1.png", MediaType.IMAGE_PNG_VALUE, "image1".getBytes()
            );
            MockMultipartFile file2 = new MockMultipartFile(
                    "files", "test2.png", MediaType.IMAGE_PNG_VALUE, "image2".getBytes()
            );
            MockMultipartFile file3 = new MockMultipartFile(
                    "files", "test3.jpg", MediaType.IMAGE_JPEG_VALUE, "image3".getBytes()
            );

            mockMvc.perform(multipart("/api/images/upload")
                            .file(file1)
                            .file(file2)
                            .file(file3)
                            .param("token", TEST_TOKEN)
                            .param("folderName", "batch_upload"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.uploaded_count").value(3));
        }

        @Test
        @DisplayName("Should reject upload with invalid token")
        void testUploadInvalidToken() throws Exception {
            when(sessionService.validateTokenAndGetUserId("invalid-token")).thenReturn(null);

            MockMultipartFile file = new MockMultipartFile(
                    "files",
                    "test.png",
                    MediaType.IMAGE_PNG_VALUE,
                    "test".getBytes()
            );

            mockMvc.perform(multipart("/api/images/upload")
                            .file(file)
                            .param("token", "invalid-token")
                            .param("folderName", "test_folder"))
                    .andExpect(status().is5xxServerError()); // NullPointerException -> 500
        }

        @Test
        @DisplayName("Should handle service exception")
        void testUploadServiceException() throws Exception {
            when(sessionService.validateTokenAndGetUserId(TEST_TOKEN)).thenReturn(TEST_USER_ID);

            when(imageService.uploadImages(anyLong(), anyString(), anyList()))
                    .thenThrow(new RuntimeException("Upload failed"));

            MockMultipartFile file = new MockMultipartFile(
                    "files",
                    "test.png",
                    MediaType.IMAGE_PNG_VALUE,
                    "test".getBytes()
            );

            mockMvc.perform(multipart("/api/images/upload")
                            .file(file)
                            .param("token", TEST_TOKEN)
                            .param("folderName", "test_folder"))
                    .andExpect(status().is5xxServerError()); // RuntimeException -> 500
        }
    }

    @Nested
    @DisplayName("File Validation Tests")
    class FileValidationTests {

        @Test
        @DisplayName("Should accept PNG files")
        void testAcceptPngFiles() throws Exception {
            when(sessionService.validateTokenAndGetUserId(TEST_TOKEN)).thenReturn(TEST_USER_ID);

            UploadResponse response = new UploadResponse("Success", 1L, 1);
            when(imageService.uploadImages(anyLong(), anyString(), anyList()))
                    .thenReturn(response);

            MockMultipartFile file = new MockMultipartFile(
                    "files",
                    "test.png",
                    MediaType.IMAGE_PNG_VALUE,
                    "PNG content".getBytes()
            );

            mockMvc.perform(multipart("/api/images/upload")
                            .file(file)
                            .param("token", TEST_TOKEN)
                            .param("folderName", "test"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should accept JPEG files")
        void testAcceptJpegFiles() throws Exception {
            when(sessionService.validateTokenAndGetUserId(TEST_TOKEN)).thenReturn(TEST_USER_ID);

            UploadResponse response = new UploadResponse("Success", 1L, 1);
            when(imageService.uploadImages(anyLong(), anyString(), anyList()))
                    .thenReturn(response);

            MockMultipartFile file = new MockMultipartFile(
                    "files",
                    "test.jpg",
                    MediaType.IMAGE_JPEG_VALUE,
                    "JPEG content".getBytes()
            );

            mockMvc.perform(multipart("/api/images/upload")
                            .file(file)
                            .param("token", TEST_TOKEN)
                            .param("folderName", "test"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should handle large file uploads")
        void testLargeFileUpload() throws Exception {
            when(sessionService.validateTokenAndGetUserId(TEST_TOKEN)).thenReturn(TEST_USER_ID);

            UploadResponse response = new UploadResponse("Success", 1L, 1);
            when(imageService.uploadImages(anyLong(), anyString(), anyList()))
                    .thenReturn(response);

            // Create a larger file (1MB)
            byte[] largeContent = new byte[1024 * 1024];
            MockMultipartFile file = new MockMultipartFile(
                    "files",
                    "large.png",
                    MediaType.IMAGE_PNG_VALUE,
                    largeContent
            );

            mockMvc.perform(multipart("/api/images/upload")
                            .file(file)
                            .param("token", TEST_TOKEN)
                            .param("folderName", "large_files"))
                    .andExpect(status().isOk());
        }
    }
}