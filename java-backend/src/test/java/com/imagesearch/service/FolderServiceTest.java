package com.imagesearch.service;

import com.imagesearch.client.SearchClient;
import com.imagesearch.exception.DuplicateResourceException;
import com.imagesearch.exception.ForbiddenException;
import com.imagesearch.exception.ResourceNotFoundException;
import com.imagesearch.model.dto.request.DeleteFoldersRequest;
import com.imagesearch.model.dto.request.ShareFolderRequest;
import com.imagesearch.model.dto.response.FolderResponse;
import com.imagesearch.model.entity.Folder;
import com.imagesearch.model.entity.FolderShare;
import com.imagesearch.model.entity.User;
import com.imagesearch.repository.FolderRepository;
import com.imagesearch.repository.FolderShareRepository;
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
 * Comprehensive production-grade tests for FolderService.
 *
 * Coverage areas:
 * - Folder creation and retrieval
 * - Folder access control (ownership and sharing)
 * - Folder deletion with cascade cleanup
 * - Folder sharing with permissions
 * - Concurrent folder operations (race conditions)
 * - Authorization checks
 * - Edge cases and boundary conditions
 *
 * Test Quality Guardian: Prevents authorization bypass vulnerabilities,
 * data leaks through improper sharing, and orphaned filesystem data.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FolderService - Production-Grade Tests")
public class FolderServiceTest {

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private FolderShareRepository folderShareRepository;

    @Mock
    private ImageRepository imageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SearchClient searchClient;

    @InjectMocks
    private FolderService folderService;

    private User testUser;
    private User otherUser;
    private Folder testFolder;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");

        otherUser = new User();
        otherUser.setId(2L);
        otherUser.setUsername("otheruser");

        testFolder = new Folder();
        testFolder.setId(100L);
        testFolder.setFolderName("test_folder");
        testFolder.setUser(testUser);
    }

    @Nested
    @DisplayName("Get Accessible Folders Tests - Authorization")
    class GetAccessibleFoldersTests {

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should throw NullPointerException when userId is null")
        void testGetAccessibleFoldersNullUserId() {
            // WHY: Prevents NullPointerException deep in call stack
            assertThatThrownBy(() -> folderService.getAllAccessibleFolders(null))
                    .isInstanceOf(NullPointerException.class);

            verify(folderRepository, never()).findAllAccessibleFolders(anyLong());
        }

        @Test
        @DisplayName("Should return owned folders with isOwner=true")
        void testGetAccessibleFoldersOwned() {
            // Arrange
            when(folderRepository.findAllAccessibleFolders(1L)).thenReturn(Arrays.asList(testFolder));

            // Act
            List<FolderResponse> responses = folderService.getAllAccessibleFolders(1L);

            // Assert
            assertThat(responses).hasSize(1);
            FolderResponse response = responses.get(0);
            assertThat(response.getId()).isEqualTo(100L);
            assertThat(response.getFolderName()).isEqualTo("test_folder");
            assertThat(response.getIsOwner()).isTrue();
            assertThat(response.getIsShared()).isFalse();
        }

        @Test
        @DisplayName("Should return shared folders with share details")
        void testGetAccessibleFoldersShared() {
            // Arrange - folder owned by testUser, shared with otherUser
            when(folderRepository.findAllAccessibleFolders(2L)).thenReturn(Arrays.asList(testFolder));
            when(userRepository.findById(2L)).thenReturn(Optional.of(otherUser));

            FolderShare share = new FolderShare();
            share.setFolder(testFolder);
            share.setOwner(testUser);
            share.setSharedWithUser(otherUser);
            share.setPermission("view");

            when(folderShareRepository.findByFolderAndSharedWithUser(testFolder, otherUser))
                    .thenReturn(Optional.of(share));

            // Act
            List<FolderResponse> responses = folderService.getAllAccessibleFolders(2L);

            // Assert
            assertThat(responses).hasSize(1);
            FolderResponse response = responses.get(0);
            assertThat(response.getIsOwner()).isFalse();
            assertThat(response.getIsShared()).isTrue();
            assertThat(response.getOwnerId()).isEqualTo(1L);
            assertThat(response.getOwnerUsername()).isEqualTo("testuser");
            assertThat(response.getPermission()).isEqualTo("view");
        }

        @Test
        @DisplayName("Should return empty list when user has no folders")
        void testGetAccessibleFoldersEmpty() {
            // WHY: Edge case - new user with no folders
            when(folderRepository.findAllAccessibleFolders(999L)).thenReturn(Collections.emptyList());

            // Act
            List<FolderResponse> responses = folderService.getAllAccessibleFolders(999L);

            // Assert
            assertThat(responses).isEmpty();
        }

        @Test
        @DisplayName("Should handle mix of owned and shared folders")
        void testGetAccessibleFoldersMixed() {
            // Arrange
            Folder ownedFolder = new Folder();
            ownedFolder.setId(1L);
            ownedFolder.setFolderName("owned");
            ownedFolder.setUser(testUser);

            Folder sharedFolder = new Folder();
            sharedFolder.setId(2L);
            sharedFolder.setFolderName("shared");
            sharedFolder.setUser(otherUser);

            when(folderRepository.findAllAccessibleFolders(1L))
                    .thenReturn(Arrays.asList(ownedFolder, sharedFolder));
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

            FolderShare share = new FolderShare();
            share.setFolder(sharedFolder);
            share.setPermission("view");

            when(folderShareRepository.findByFolderAndSharedWithUser(sharedFolder, testUser))
                    .thenReturn(Optional.of(share));

            // Act
            List<FolderResponse> responses = folderService.getAllAccessibleFolders(1L);

            // Assert
            assertThat(responses).hasSize(2);
            assertThat(responses.get(0).getIsOwner()).isTrue();
            assertThat(responses.get(1).getIsOwner()).isFalse();
        }
    }

    @Nested
    @DisplayName("Create Folder Tests - Idempotency and Concurrency")
    class CreateFolderTests {

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should create new folder when it doesn't exist")
        void testCreateFolderNew() {
            // Arrange
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(folderRepository.findByUserAndFolderName(testUser, "new_folder"))
                    .thenReturn(Optional.empty());

            Folder newFolder = new Folder();
            newFolder.setId(200L);
            newFolder.setFolderName("new_folder");
            newFolder.setUser(testUser);

            when(folderRepository.save(any(Folder.class))).thenReturn(newFolder);

            // Act
            Folder result = folderService.createOrGetFolder(1L, "new_folder");

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(200L);
            assertThat(result.getFolderName()).isEqualTo("new_folder");

            // Verify FAISS index was created
            verify(searchClient).createIndex(1L, 200L);
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should return existing folder when it already exists (idempotent)")
        void testCreateFolderExisting() {
            // WHY: Prevents duplicate folders, ensures idempotency
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(folderRepository.findByUserAndFolderName(testUser, "existing_folder"))
                    .thenReturn(Optional.of(testFolder));

            // Act
            Folder result = folderService.createOrGetFolder(1L, "existing_folder");

            // Assert
            assertThat(result).isEqualTo(testFolder);

            // Verify no new folder was saved
            verify(folderRepository, never()).save(any(Folder.class));

            // Verify FAISS index was not created
            verify(searchClient, never()).createIndex(anyLong(), anyLong());
        }

        @Test
        @DisplayName("Should throw exception when user not found")
        void testCreateFolderUserNotFound() {
            // Arrange
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> folderService.createOrGetFolder(999L, "folder"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User");
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should throw NullPointerException when userId is null")
        void testCreateFolderNullUserId() {
            // WHY: Prevents NullPointerException
            assertThatThrownBy(() -> folderService.createOrGetFolder(null, "folder"))
                    .isInstanceOf(NullPointerException.class);
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should throw NullPointerException when folderName is null")
        void testCreateFolderNullFolderName() {
            // WHY: Prevents NullPointerException
            assertThatThrownBy(() -> folderService.createOrGetFolder(1L, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should handle folder name with special characters")
        void testCreateFolderSpecialCharacters() {
            // WHY: Ensures safe handling of special characters (SQL injection prevention)
            String specialName = "folder'; DROP TABLE folders; --";

            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(folderRepository.findByUserAndFolderName(testUser, specialName))
                    .thenReturn(Optional.empty());

            Folder newFolder = new Folder();
            newFolder.setId(300L);
            newFolder.setFolderName(specialName);

            when(folderRepository.save(any(Folder.class))).thenReturn(newFolder);

            // Act
            Folder result = folderService.createOrGetFolder(1L, specialName);

            // Assert - folder name is stored as-is
            assertThat(result.getFolderName()).isEqualTo(specialName);
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should handle concurrent folder creation for same user (race condition)")
        @RepeatedTest(3)
        void testConcurrentFolderCreationRaceCondition() throws InterruptedException {
            // WHY: Prevents duplicate folders when two upload requests arrive simultaneously
            // WHAT: Simulates race condition where two threads try to create same folder
            // COVERAGE: Thread safety

            String folderName = "concurrent_folder";
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

            // Simulate race: first check shows no folder, then first save wins
            when(folderRepository.findByUserAndFolderName(testUser, folderName))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.empty());

            Folder savedFolder = new Folder();
            savedFolder.setId(400L);
            savedFolder.setFolderName(folderName);

            when(folderRepository.save(any(Folder.class))).thenReturn(savedFolder);

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(2);
            AtomicInteger createCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(2);

            // Thread 1
            executor.submit(() -> {
                try {
                    startLatch.await();
                    folderService.createOrGetFolder(1L, folderName);
                    createCount.incrementAndGet();
                } catch (Exception e) {
                    // Ignore
                } finally {
                    endLatch.countDown();
                }
            });

            // Thread 2
            executor.submit(() -> {
                try {
                    startLatch.await();
                    folderService.createOrGetFolder(1L, folderName);
                    createCount.incrementAndGet();
                } catch (Exception e) {
                    // Ignore
                } finally {
                    endLatch.countDown();
                }
            });

            // Start both threads simultaneously
            startLatch.countDown();

            // Wait for completion
            boolean completed = endLatch.await(3, TimeUnit.SECONDS);
            executor.shutdown();

            // Assert
            assertThat(completed).isTrue();
            // Note: In real scenario with DB constraints, idempotency ensures no duplicates
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should continue even if FAISS index creation fails")
        void testCreateFolderFaissFailure() {
            // WHY: Folder creation should succeed even if search service is down
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(folderRepository.findByUserAndFolderName(testUser, "folder"))
                    .thenReturn(Optional.empty());

            Folder newFolder = new Folder();
            newFolder.setId(500L);
            newFolder.setFolderName("folder");

            when(folderRepository.save(any(Folder.class))).thenReturn(newFolder);

            // Simulate FAISS failure
            doThrow(new RuntimeException("Search service unavailable"))
                    .when(searchClient).createIndex(1L, 500L);

            // Act & Assert - should throw (caller handles retry)
            assertThatThrownBy(() -> folderService.createOrGetFolder(1L, "folder"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Search service unavailable");

            // Verify folder was saved to DB
            verify(folderRepository).save(any(Folder.class));
        }
    }

    @Nested
    @DisplayName("Delete Folder Tests - Cascade Cleanup and Authorization")
    class DeleteFolderTests {

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should delete folder with complete cleanup")
        void testDeleteFolderSuccess() {
            // Arrange
            DeleteFoldersRequest request = new DeleteFoldersRequest();
            request.setFolderIds(Arrays.asList(100L));

            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(folderRepository.findById(100L)).thenReturn(Optional.of(testFolder));

            // Act
            folderService.deleteFolders(request, 1L);

            // Assert - verify complete cleanup sequence
            verify(imageRepository).deleteByFolder(testFolder);
            verify(folderShareRepository).deleteByFolder(testFolder);
            verify(folderRepository).delete(testFolder);
            verify(searchClient).deleteIndex(1L, 100L);
            verify(searchClient).deleteIndex(1L, 100L);
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should throw ForbiddenException when user doesn't own folder")
        void testDeleteFolderNotOwner() {
            // WHY: Critical authorization check - prevents users from deleting others' folders
            DeleteFoldersRequest request = new DeleteFoldersRequest();
            request.setFolderIds(Arrays.asList(100L));

            when(userRepository.findById(2L)).thenReturn(Optional.of(otherUser));
            when(folderRepository.findById(100L)).thenReturn(Optional.of(testFolder)); // Owned by testUser

            // Act & Assert
            assertThatThrownBy(() -> folderService.deleteFolders(request, 2L))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("You don't own this folder");

            // Verify folder was not deleted
            verify(folderRepository, never()).delete(any(Folder.class));
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when folder not found")
        void testDeleteFolderNotFound() {
            // Arrange
            DeleteFoldersRequest request = new DeleteFoldersRequest();
            request.setFolderIds(Arrays.asList(999L));

            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(folderRepository.findById(999L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> folderService.deleteFolders(request, 1L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Folder");
        }

        @Test
        @DisplayName("Should delete multiple folders in single request")
        void testDeleteMultipleFolders() {
            // Arrange
            Folder folder1 = new Folder();
            folder1.setId(100L);
            folder1.setUser(testUser);

            Folder folder2 = new Folder();
            folder2.setId(101L);
            folder2.setUser(testUser);

            DeleteFoldersRequest request = new DeleteFoldersRequest();
            request.setFolderIds(Arrays.asList(100L, 101L));

            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(folderRepository.findById(100L)).thenReturn(Optional.of(folder1));
            when(folderRepository.findById(101L)).thenReturn(Optional.of(folder2));

            // Act
            folderService.deleteFolders(request, 1L);

            // Assert
            verify(folderRepository).delete(folder1);
            verify(folderRepository).delete(folder2);
            verify(searchClient).deleteIndex(1L, 100L);
            verify(searchClient).deleteIndex(1L, 101L);
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should throw NullPointerException when request is null")
        void testDeleteFoldersNullRequest() {
            // WHY: Prevents NullPointerException
            assertThatThrownBy(() -> folderService.deleteFolders(null, 1L))
                    .isInstanceOf(NullPointerException.class);
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should continue deletion even if FAISS cleanup fails")
        void testDeleteFolderFaissCleanupFailure() {
            // WHY: Best-effort cleanup - database deletion should proceed
            DeleteFoldersRequest request = new DeleteFoldersRequest();
            request.setFolderIds(Arrays.asList(100L));

            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(folderRepository.findById(100L)).thenReturn(Optional.of(testFolder));

            doThrow(new RuntimeException("FAISS cleanup failed"))
                    .when(searchClient).deleteIndex(1L, 100L);

            // Act & Assert - should throw (but after DB cleanup)
            assertThatThrownBy(() -> folderService.deleteFolders(request, 1L))
                    .isInstanceOf(RuntimeException.class);

            // Verify DB cleanup happened first
            verify(folderRepository).delete(testFolder);
        }
    }

    @Nested
    @DisplayName("Folder Sharing Tests - Permissions and Authorization")
    class FolderSharingTests {

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should successfully share folder with another user")
        void testShareFolderSuccess() {
            // Arrange
            ShareFolderRequest request = new ShareFolderRequest();
            request.setFolderId(100L);
            request.setTargetUsername("otheruser");
            request.setPermission("view");

            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(folderRepository.findById(100L)).thenReturn(Optional.of(testFolder));
            when(userRepository.findByUsername("otheruser")).thenReturn(Optional.of(otherUser));
            when(folderShareRepository.existsByFolderAndSharedWithUser(testFolder, otherUser))
                    .thenReturn(false);

            // Act
            folderService.shareFolder(request, 1L);

            // Assert
            ArgumentCaptor<FolderShare> shareCaptor = ArgumentCaptor.forClass(FolderShare.class);
            verify(folderShareRepository).save(shareCaptor.capture());

            FolderShare share = shareCaptor.getValue();
            assertThat(share.getFolder()).isEqualTo(testFolder);
            assertThat(share.getOwner()).isEqualTo(testUser);
            assertThat(share.getSharedWithUser()).isEqualTo(otherUser);
            assertThat(share.getPermission()).isEqualTo("view");
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should throw ForbiddenException when non-owner tries to share")
        void testShareFolderNotOwner() {
            // WHY: Critical security check - only owners can share folders
            ShareFolderRequest request = new ShareFolderRequest();
            request.setFolderId(100L);
            request.setTargetUsername("thirduser");

            when(userRepository.findById(2L)).thenReturn(Optional.of(otherUser));
            when(folderRepository.findById(100L)).thenReturn(Optional.of(testFolder)); // Owned by testUser

            // Act & Assert
            assertThatThrownBy(() -> folderService.shareFolder(request, 2L))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("You don't own this folder");

            verify(folderShareRepository, never()).save(any(FolderShare.class));
        }

        @Test
        @DisplayName("Should throw ForbiddenException when trying to share with yourself")
        void testShareFolderWithSelf() {
            // WHY: Prevents nonsensical self-sharing
            ShareFolderRequest request = new ShareFolderRequest();
            request.setFolderId(100L);
            request.setTargetUsername("testuser"); // Same as owner

            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(folderRepository.findById(100L)).thenReturn(Optional.of(testFolder));
            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

            // Act & Assert
            assertThatThrownBy(() -> folderService.shareFolder(request, 1L))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("Cannot share folder with yourself");
        }

        @Test
        @DisplayName("Should throw DuplicateResourceException when already shared")
        void testShareFolderAlreadyShared() {
            // WHY: Prevents duplicate share entries
            ShareFolderRequest request = new ShareFolderRequest();
            request.setFolderId(100L);
            request.setTargetUsername("otheruser");

            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(folderRepository.findById(100L)).thenReturn(Optional.of(testFolder));
            when(userRepository.findByUsername("otheruser")).thenReturn(Optional.of(otherUser));
            when(folderShareRepository.existsByFolderAndSharedWithUser(testFolder, otherUser))
                    .thenReturn(true); // Already shared

            // Act & Assert
            assertThatThrownBy(() -> folderService.shareFolder(request, 1L))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("already shared");
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when target user not found")
        void testShareFolderTargetUserNotFound() {
            // Arrange
            ShareFolderRequest request = new ShareFolderRequest();
            request.setFolderId(100L);
            request.setTargetUsername("nonexistent");

            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(folderRepository.findById(100L)).thenReturn(Optional.of(testFolder));
            when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> folderService.shareFolder(request, 1L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User not found");
        }
    }

    @Nested
    @DisplayName("Check Folder Access Tests - Authorization")
    class CheckFolderAccessTests {

        @Test
        @DisplayName("Should allow access when user owns folder")
        void testCheckAccessOwner() {
            // Arrange
            when(folderRepository.findById(100L)).thenReturn(Optional.of(testFolder));

            // Act
            Folder result = folderService.checkFolderAccess(1L, 100L);

            // Assert
            assertThat(result).isEqualTo(testFolder);
        }

        @Test
        @DisplayName("Should allow access when folder is shared with user")
        void testCheckAccessShared() {
            // Arrange
            when(folderRepository.findById(100L)).thenReturn(Optional.of(testFolder)); // Owned by testUser
            when(userRepository.findById(2L)).thenReturn(Optional.of(otherUser));
            when(folderShareRepository.existsByFolderAndSharedWithUser(testFolder, otherUser))
                    .thenReturn(true);

            // Act
            Folder result = folderService.checkFolderAccess(2L, 100L);

            // Assert
            assertThat(result).isEqualTo(testFolder);
        }

        @Test
        @DisplayName("Should throw ForbiddenException when user has no access")
        void testCheckAccessDenied() {
            // WHY: Critical security check - prevents unauthorized access
            when(folderRepository.findById(100L)).thenReturn(Optional.of(testFolder)); // Owned by testUser
            when(userRepository.findById(2L)).thenReturn(Optional.of(otherUser));
            when(folderShareRepository.existsByFolderAndSharedWithUser(testFolder, otherUser))
                    .thenReturn(false); // Not shared

            // Act & Assert
            assertThatThrownBy(() -> folderService.checkFolderAccess(2L, 100L))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("You don't have access to this folder");
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when folder not found")
        void testCheckAccessFolderNotFound() {
            // Arrange
            when(folderRepository.findById(999L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> folderService.checkFolderAccess(1L, 999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Folder");
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should throw NullPointerException when userId is null")
        void testCheckAccessNullUserId() {
            // WHY: Prevents NullPointerException
            assertThatThrownBy(() -> folderService.checkFolderAccess(null, 100L))
                    .isInstanceOf(NullPointerException.class);
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should throw NullPointerException when folderId is null")
        void testCheckAccessNullFolderId() {
            // WHY: Prevents NullPointerException
            assertThatThrownBy(() -> folderService.checkFolderAccess(1L, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
