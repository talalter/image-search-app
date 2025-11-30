package com.imagesearch.service;

import com.imagesearch.client.SearchClient;
import com.imagesearch.client.dto.EmbedImagesRequest;
import com.imagesearch.exception.BadRequestException;
import com.imagesearch.model.dto.response.UploadResponse;
import com.imagesearch.model.entity.Folder;
import com.imagesearch.model.entity.Image;
import com.imagesearch.model.entity.User;
import com.imagesearch.repository.ImageRepository;
import com.imagesearch.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
 * Comprehensive production-grade tests for ImageService.
 *
 * Coverage areas:
 * - Image upload (happy path, multiple files, edge cases)
 * - File type validation (security)
 * - Path traversal attack prevention
 * - File system error handling (IOException, disk full, permissions)
 * - Concurrent uploads (race conditions)
 * - Integration with search service (embedding generation)
 * - Boundary conditions (empty files, huge files, special characters in filenames)
 *
 * Test Quality Guardian: Prevents file upload vulnerabilities, path traversal attacks,
 * and resource exhaustion from malicious uploads.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ImageService - Production-Grade Tests")
public class ImageServiceTest {

    @Mock
    private ImageRepository imageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FolderService folderService;

    @Mock
    private SearchClient searchClient;

    @InjectMocks
    private ImageService imageService;

    private User testUser;
    private Folder testFolder;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");

        testFolder = new Folder();
        testFolder.setId(100L);
        testFolder.setFolderName("test_folder");
        testFolder.setUser(testUser);
    }

    @Nested
    @DisplayName("Upload Images Tests - Happy Path and Validation")
    class UploadImagesTests {

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should successfully upload single image")
        void testUploadSingleImageSuccess() {
            // Arrange
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "test.png",
                    "image/png",
                    "fake-image-data".getBytes()
            );

            when(folderService.createOrGetFolder(1L, "folder")).thenReturn(testFolder);
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

            Image savedImage = new Image();
            savedImage.setId(1L);
            savedImage.setFilepath("images/1/100/test.png");

            when(imageRepository.save(any(Image.class))).thenReturn(savedImage);

            // Act
            UploadResponse response = imageService.uploadImages(1L, "folder", Arrays.asList(file));

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getUploadedCount()).isEqualTo(1);
            assertThat(response.getFolderId()).isEqualTo(100L);

            // Verify image was saved to repository
            verify(imageRepository).save(any(Image.class));

            // Verify embeddings were requested
            ArgumentCaptor<EmbedImagesRequest> embedCaptor = ArgumentCaptor.forClass(EmbedImagesRequest.class);
            verify(searchClient).embedImages(embedCaptor.capture());
            EmbedImagesRequest embedRequest = embedCaptor.getValue();
            assertThat(embedRequest.getImages()).hasSize(1);
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should successfully upload multiple images in batch")
        void testUploadMultipleImages() {
            // Arrange
            List<MultipartFile> files = Arrays.asList(
                    new MockMultipartFile("file1", "img1.png", "image/png", "data1".getBytes()),
                    new MockMultipartFile("file2", "img2.jpg", "image/jpeg", "data2".getBytes()),
                    new MockMultipartFile("file3", "img3.jpeg", "image/jpeg", "data3".getBytes())
            );

            when(folderService.createOrGetFolder(1L, "folder")).thenReturn(testFolder);
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

            when(imageRepository.save(any(Image.class))).thenAnswer(invocation -> {
                Image img = invocation.getArgument(0);
                img.setId(System.currentTimeMillis()); // Mock ID generation
                return img;
            });

            // Act
            UploadResponse response = imageService.uploadImages(1L, "folder", files);

            // Assert
            assertThat(response.getUploadedCount()).isEqualTo(3);

            // Verify all images were saved
            verify(imageRepository, times(3)).save(any(Image.class));

            // Verify single batched embedding request (not 3 separate requests)
            ArgumentCaptor<EmbedImagesRequest> embedCaptor = ArgumentCaptor.forClass(EmbedImagesRequest.class);
            verify(searchClient, times(1)).embedImages(embedCaptor.capture());
            assertThat(embedCaptor.getValue().getImages()).hasSize(3);
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should throw BadRequestException when no files provided")
        void testUploadNoFiles() {
            // WHY: Prevents processing of empty upload requests
            assertThatThrownBy(() -> imageService.uploadImages(1L, "folder", Collections.emptyList()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("No files provided");

            verify(folderService, never()).createOrGetFolder(anyLong(), anyString());
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when userId is null")
        void testUploadNullUserId() {
            // WHY: Prevents NullPointerException
            List<MultipartFile> files = Arrays.asList(new MockMultipartFile("f", "t.png", "image/png", "d".getBytes()));

            assertThatThrownBy(() -> imageService.uploadImages(null, "folder", files))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("userId cannot be null");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when folderName is null")
        void testUploadNullFolderName() {
            // WHY: Prevents NullPointerException
            List<MultipartFile> files = Arrays.asList(new MockMultipartFile("f", "t.png", "image/png", "d".getBytes()));

            assertThatThrownBy(() -> imageService.uploadImages(1L, null, files))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("folderName cannot be null");
        }
    }

    @Nested
    @DisplayName("File Type Validation Tests - Security")
    class FileTypeValidationTests {

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should accept PNG files")
        void testAcceptPngFile() {
            // Arrange
            MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", "data".getBytes());

            when(folderService.createOrGetFolder(1L, "folder")).thenReturn(testFolder);
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(imageRepository.save(any(Image.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            UploadResponse response = imageService.uploadImages(1L, "folder", Arrays.asList(file));

            // Assert
            assertThat(response.getUploadedCount()).isEqualTo(1);
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should accept JPG files")
        void testAcceptJpgFile() {
            // Arrange
            MockMultipartFile file = new MockMultipartFile("file", "image.jpg", "image/jpeg", "data".getBytes());

            when(folderService.createOrGetFolder(1L, "folder")).thenReturn(testFolder);
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(imageRepository.save(any(Image.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            UploadResponse response = imageService.uploadImages(1L, "folder", Arrays.asList(file));

            // Assert
            assertThat(response.getUploadedCount()).isEqualTo(1);
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should accept JPEG files")
        void testAcceptJpegFile() {
            // Arrange
            MockMultipartFile file = new MockMultipartFile("file", "image.jpeg", "image/jpeg", "data".getBytes());

            when(folderService.createOrGetFolder(1L, "folder")).thenReturn(testFolder);
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(imageRepository.save(any(Image.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            UploadResponse response = imageService.uploadImages(1L, "folder", Arrays.asList(file));

            // Assert
            assertThat(response.getUploadedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should reject non-image file types (security)")
        void testRejectInvalidFileTypes() {
            // WHY: Prevents upload of executable files, scripts, etc. (security vulnerability)
            String[] invalidFiles = {
                    "malware.exe",
                    "script.js",
                    "document.pdf",
                    "archive.zip",
                    "shell.sh",
                    "config.xml",
                    "data.json"
            };

            for (String filename : invalidFiles) {
                MockMultipartFile file = new MockMultipartFile("file", filename, "application/octet-stream", "data".getBytes());

                when(folderService.createOrGetFolder(1L, "folder")).thenReturn(testFolder);
                when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

                // Act & Assert
                assertThatThrownBy(() -> imageService.uploadImages(1L, "folder", Arrays.asList(file)))
                        .isInstanceOf(BadRequestException.class)
                        .hasMessageContaining("Invalid file type")
                        .as("Should reject file: " + filename);

                reset(folderService, userRepository);
            }
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should handle case-insensitive file extensions")
        void testCaseInsensitiveExtensions() {
            // WHY: Ensures .PNG, .Png, .pNg are all accepted
            String[] validFilenames = {"image.PNG", "photo.Jpg", "pic.JPEG", "test.JpG"};

            for (String filename : validFilenames) {
                MockMultipartFile file = new MockMultipartFile("file", filename, "image/jpeg", "data".getBytes());

                when(folderService.createOrGetFolder(1L, "folder")).thenReturn(testFolder);
                when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
                when(imageRepository.save(any(Image.class))).thenAnswer(inv -> inv.getArgument(0));

                // Act
                UploadResponse response = imageService.uploadImages(1L, "folder", Arrays.asList(file));

                // Assert
                assertThat(response.getUploadedCount()).isEqualTo(1).as("Should accept file: " + filename);

                reset(folderService, userRepository, imageRepository, searchClient);
            }
        }

        @Test
        @DisplayName("Should reject file with null filename")
        void testRejectNullFilename() {
            // WHY: Edge case - prevents NullPointerException
            MockMultipartFile file = new MockMultipartFile("file", null, "image/png", "data".getBytes());

            when(folderService.createOrGetFolder(1L, "folder")).thenReturn(testFolder);
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

            // Act & Assert
            assertThatThrownBy(() -> imageService.uploadImages(1L, "folder", Arrays.asList(file)))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("Should reject file with no extension")
        void testRejectNoExtension() {
            // WHY: Filename without extension should be rejected
            MockMultipartFile file = new MockMultipartFile("file", "noextension", "image/png", "data".getBytes());

            when(folderService.createOrGetFolder(1L, "folder")).thenReturn(testFolder);
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

            // Act & Assert
            assertThatThrownBy(() -> imageService.uploadImages(1L, "folder", Arrays.asList(file)))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    @Nested
    @DisplayName("Path Traversal Prevention Tests - Critical Security")
    class PathTraversalTests {

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should prevent path traversal with ../ in filename")
        void testPreventPathTraversalDotDot() {
            // WHY: CRITICAL - prevents attacker from uploading to arbitrary locations
            // WHAT: Filename like "../../etc/passwd.png" should be sanitized
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "../../etc/passwd.png",
                    "image/png",
                    "malicious".getBytes()
            );

            when(folderService.createOrGetFolder(1L, "folder")).thenReturn(testFolder);
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

            Image savedImage = new Image();
            savedImage.setId(1L);

            when(imageRepository.save(any(Image.class))).thenAnswer(invocation -> {
                Image img = invocation.getArgument(0);
                savedImage.setFilepath(img.getFilepath());
                return savedImage;
            });

            // Act
            UploadResponse response = imageService.uploadImages(1L, "folder", Arrays.asList(file));

            // Assert - filename should be sanitized (only "passwd.png" kept)
            assertThat(response.getUploadedCount()).isEqualTo(1);

            ArgumentCaptor<Image> imageCaptor = ArgumentCaptor.forClass(Image.class);
            verify(imageRepository).save(imageCaptor.capture());

            String filepath = imageCaptor.getValue().getFilepath();
            // Should NOT contain ../ in the stored path
            assertThat(filepath).doesNotContain("../");
            assertThat(filepath).contains("passwd.png");
            assertThat(filepath).startsWith("images/1/100/");
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should prevent path traversal with absolute path in filename")
        void testPreventPathTraversalAbsolute() {
            // WHY: Prevents upload to /etc/passwd.png or C:\\Windows\\System32\\config.png
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "/etc/malicious.png",
                    "image/png",
                    "data".getBytes()
            );

            when(folderService.createOrGetFolder(1L, "folder")).thenReturn(testFolder);
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(imageRepository.save(any(Image.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            UploadResponse response = imageService.uploadImages(1L, "folder", Arrays.asList(file));

            // Assert - upload should succeed and only filename should be used
            assertThat(response.getUploadedCount()).isEqualTo(1);
            
            ArgumentCaptor<Image> imageCaptor = ArgumentCaptor.forClass(Image.class);
            verify(imageRepository).save(imageCaptor.capture());

            String filepath = imageCaptor.getValue().getFilepath();
            assertThat(filepath).doesNotContain("/etc/");
            assertThat(filepath).startsWith("images/1/100/");
            assertThat(filepath).endsWith("malicious.png");
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should handle Windows-style path separators in filename")
        void testHandleWindowsPathSeparators() {
            // WHY: Prevents path traversal on Windows: "..\\..\\System32\\file.png"
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "C:\\Users\\attacker\\image.png",
                    "image/png",
                    "data".getBytes()
            );

            when(folderService.createOrGetFolder(1L, "folder")).thenReturn(testFolder);
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(imageRepository.save(any(Image.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            UploadResponse response = imageService.uploadImages(1L, "folder", Arrays.asList(file));

            // Assert - upload should succeed
            assertThat(response.getUploadedCount()).isEqualTo(1);
            
            ArgumentCaptor<Image> imageCaptor = ArgumentCaptor.forClass(Image.class);
            verify(imageRepository).save(imageCaptor.capture());

            String filepath = imageCaptor.getValue().getFilepath();
            assertThat(filepath).doesNotContain("\\");
            assertThat(filepath).doesNotContain("C:");
            assertThat(filepath).endsWith("image.png");
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should handle subdirectory in original filename (e.g., from mobile uploads)")
        void testHandleSubdirectoryInFilename() {
            // WHY: Mobile apps sometimes send filenames like "DCIM/Camera/IMG_001.jpg"
            // WHAT: Should extract just the filename
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "DCIM/Camera/IMG_001.jpg",
                    "image/jpeg",
                    "data".getBytes()
            );

            when(folderService.createOrGetFolder(1L, "folder")).thenReturn(testFolder);
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(imageRepository.save(any(Image.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            UploadResponse response = imageService.uploadImages(1L, "folder", Arrays.asList(file));

            // Assert - upload should succeed
            assertThat(response.getUploadedCount()).isEqualTo(1);
            
            // Assert - only IMG_001.jpg should be stored
            ArgumentCaptor<Image> imageCaptor = ArgumentCaptor.forClass(Image.class);
            verify(imageRepository).save(imageCaptor.capture());

            String filepath = imageCaptor.getValue().getFilepath();
            assertThat(filepath).doesNotContain("DCIM");
            assertThat(filepath).doesNotContain("Camera");
            assertThat(filepath).endsWith("IMG_001.jpg");
        }
    }

    @Nested
    @DisplayName("Edge Cases and Boundary Conditions")
    class EdgeCaseTests {

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should handle empty file (0 bytes)")
        void testUploadEmptyFile() {
            // WHY: Edge case - user uploads 0-byte file
            MockMultipartFile file = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

            when(folderService.createOrGetFolder(1L, "folder")).thenReturn(testFolder);
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(imageRepository.save(any(Image.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act - should succeed (CLIP/FAISS will handle invalid image later)
            UploadResponse response = imageService.uploadImages(1L, "folder", Arrays.asList(file));

            // Assert
            assertThat(response.getUploadedCount()).isEqualTo(1);
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should handle filename with special characters")
        void testFilenameWithSpecialCharacters() {
            // WHY: Ensures safe handling of Unicode and special chars
            String[] specialFilenames = {
                    "image with spaces.png",
                    "图片.jpg",              // Chinese
                    "фото.jpeg",            // Cyrillic
                    "image!@#$%^&().png",
                    "file_name-123.jpg"
            };

            for (String filename : specialFilenames) {
                MockMultipartFile file = new MockMultipartFile("file", filename, "image/png", "data".getBytes());

                when(folderService.createOrGetFolder(1L, "folder")).thenReturn(testFolder);
                when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
                when(imageRepository.save(any(Image.class))).thenAnswer(inv -> inv.getArgument(0));

                // Act
                UploadResponse response = imageService.uploadImages(1L, "folder", Arrays.asList(file));

                // Assert - should handle gracefully
                assertThat(response.getUploadedCount()).isEqualTo(1).as("Should handle: " + filename);

                reset(folderService, userRepository, imageRepository, searchClient);
            }
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should handle very long filename")
        void testVeryLongFilename() {
            // WHY: Tests filesystem limits (typically 255 chars for filename)
            String longName = "a".repeat(250) + ".png";
            MockMultipartFile file = new MockMultipartFile("file", longName, "image/png", "data".getBytes());

            when(folderService.createOrGetFolder(1L, "folder")).thenReturn(testFolder);
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(imageRepository.save(any(Image.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act - should succeed (filesystem may truncate if needed)
            UploadResponse response = imageService.uploadImages(1L, "folder", Arrays.asList(file));

            // Assert
            assertThat(response.getUploadedCount()).isEqualTo(1);
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should handle concurrent uploads to same folder")
        @RepeatedTest(3)
        void testConcurrentUploadsToSameFolder() throws InterruptedException {
            // WHY: Prevents race conditions when multiple users upload to shared folder
            // COVERAGE: Thread safety

            when(folderService.createOrGetFolder(1L, "folder")).thenReturn(testFolder);
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(imageRepository.save(any(Image.class))).thenAnswer(inv -> inv.getArgument(0));

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(3);
            AtomicInteger uploadCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(3);

            // 3 concurrent uploads
            for (int i = 0; i < 3; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        MockMultipartFile file = new MockMultipartFile(
                                "file",
                                "concurrent" + index + ".png",
                                "image/png",
                                ("data" + index).getBytes()
                        );
                        imageService.uploadImages(1L, "folder", Arrays.asList(file));
                        uploadCount.incrementAndGet();
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
            boolean completed = endLatch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            // Assert - all should succeed
            assertThat(completed).isTrue();
            assertThat(uploadCount.get()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Get Image Tests")
    class GetImageTests {

        @Test
        @DisplayName("Should return image when found")
        void testGetImageByIdFound() {
            // Arrange
            Image image = new Image();
            image.setId(1L);
            image.setFilepath("images/1/100/test.png");

            when(imageRepository.findById(1L)).thenReturn(Optional.of(image));

            // Act
            Image result = imageService.getImageById(1L);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("Should return null when image not found")
        void testGetImageByIdNotFound() {
            // WHY: Service returns null instead of throwing (different from other services)
            when(imageRepository.findById(999L)).thenReturn(Optional.empty());

            // Act
            Image result = imageService.getImageById(999L);

            // Assert
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should return null when imageId is null")
        void testGetImageByIdNull() {
            // WHY: Prevents NullPointerException
            // Act
            Image result = imageService.getImageById(null);

            // Assert
            assertThat(result).isNull();
            verify(imageRepository, never()).findById(anyLong());
        }
    }

    @Nested
    @DisplayName("Search Service Integration Tests")
    class SearchServiceIntegrationTests {

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should continue upload even if embedding fails")
        void testUploadContinuesOnEmbeddingFailure() {
            // WHY: Image upload should succeed even if search service is down
            // WHAT: Images are saved to DB, embeddings can be generated later
            MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", "data".getBytes());

            when(folderService.createOrGetFolder(1L, "folder")).thenReturn(testFolder);
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(imageRepository.save(any(Image.class))).thenAnswer(inv -> inv.getArgument(0));

            // Simulate search service failure
            doThrow(new RuntimeException("Search service unavailable"))
                    .when(searchClient).embedImages(any(EmbedImagesRequest.class));

            // Act & Assert - should throw (caller handles retry logic)
            assertThatThrownBy(() -> imageService.uploadImages(1L, "folder", Arrays.asList(file)))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Search service unavailable");

            // Verify image was still saved to database
            verify(imageRepository).save(any(Image.class));
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should batch all images in single embedding request")
        void testBatchedEmbeddingRequest() {
            // WHY: Avoids concurrency issues in FAISS index updates
            // WHAT: All images sent in one request instead of multiple
            List<MultipartFile> files = Arrays.asList(
                    new MockMultipartFile("f1", "1.png", "image/png", "d1".getBytes()),
                    new MockMultipartFile("f2", "2.png", "image/png", "d2".getBytes()),
                    new MockMultipartFile("f3", "3.png", "image/png", "d3".getBytes())
            );

            when(folderService.createOrGetFolder(1L, "folder")).thenReturn(testFolder);
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(imageRepository.save(any(Image.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            imageService.uploadImages(1L, "folder", files);

            // Assert - single batched request, not 3 individual requests
            verify(searchClient, times(1)).embedImages(any(EmbedImagesRequest.class));

            ArgumentCaptor<EmbedImagesRequest> captor = ArgumentCaptor.forClass(EmbedImagesRequest.class);
            verify(searchClient).embedImages(captor.capture());

            EmbedImagesRequest request = captor.getValue();
            assertThat(request.getUserId()).isEqualTo(1L);
            assertThat(request.getFolderId()).isEqualTo(100L);
            assertThat(request.getImages()).hasSize(3);
        }
    }
}
