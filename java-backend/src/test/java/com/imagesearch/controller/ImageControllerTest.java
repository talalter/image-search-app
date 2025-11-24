package com.imagesearch.controller;

import com.imagesearch.model.dto.response.UploadResponse;
import com.imagesearch.service.ImageService;
import com.imagesearch.service.SessionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for ImageController - Image upload and management.
 *
 * Tests cover:
 * - Image upload
 * - Multiple file upload
 * - File type validation
 * - Authentication checks
 * - Error handling
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Image Management Tests")
public class ImageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ImageService imageService;

    @MockBean
    private SessionService sessionService;

    private static final String TEST_TOKEN = "test-token-123";
    private static final Long TEST_USER_ID = 1L;

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
        @DisplayName("Should reject upload without authentication")
        void testUploadWithoutAuth() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "files",
                    "test.png",
                    MediaType.IMAGE_PNG_VALUE,
                    "test".getBytes()
            );

            mockMvc.perform(multipart("/api/images/upload")
                            .file(file)
                            .param("folderName", "test_folder"))
                    .andExpect(status().is4xxClientError());
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
                    .andExpect(status().is4xxClientError());
        }

        @Test
        @DisplayName("Should reject upload with empty folder name")
        void testUploadEmptyFolderName() throws Exception {
            when(sessionService.validateTokenAndGetUserId(TEST_TOKEN)).thenReturn(TEST_USER_ID);

            MockMultipartFile file = new MockMultipartFile(
                    "files",
                    "test.png",
                    MediaType.IMAGE_PNG_VALUE,
                    "test".getBytes()
            );

            mockMvc.perform(multipart("/api/images/upload")
                            .file(file)
                            .param("token", TEST_TOKEN)
                            .param("folderName", ""))
                    .andExpect(status().is4xxClientError());
        }

        @Test
        @DisplayName("Should reject non-image file types")
        void testUploadInvalidFileType() throws Exception {
            when(sessionService.validateTokenAndGetUserId(TEST_TOKEN)).thenReturn(TEST_USER_ID);

            when(imageService.uploadImages(anyLong(), anyString(), anyList()))
                    .thenThrow(new RuntimeException("Invalid file type"));

            // Create a text file instead of image
            MockMultipartFile file = new MockMultipartFile(
                    "files",
                    "test.txt",
                    MediaType.TEXT_PLAIN_VALUE,
                    "not an image".getBytes()
            );

            mockMvc.perform(multipart("/api/images/upload")
                            .file(file)
                            .param("token", TEST_TOKEN)
                            .param("folderName", "test_folder"))
                    .andExpect(status().is4xxClientError());
        }

        @Test
        @DisplayName("Should reject upload with no files")
        void testUploadNoFiles() throws Exception {
            when(sessionService.validateTokenAndGetUserId(TEST_TOKEN)).thenReturn(TEST_USER_ID);

            mockMvc.perform(multipart("/api/images/upload")
                            .param("token", TEST_TOKEN)
                            .param("folderName", "test_folder"))
                    .andExpect(status().is4xxClientError());
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
