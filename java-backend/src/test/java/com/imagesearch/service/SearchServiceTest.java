package com.imagesearch.service;

import com.imagesearch.client.PythonSearchClient;
import com.imagesearch.client.dto.SearchServiceRequest;
import com.imagesearch.client.dto.SearchServiceResponse;
import com.imagesearch.model.entity.Folder;
import com.imagesearch.model.entity.Image;
import com.imagesearch.model.entity.User;
import com.imagesearch.repository.FolderRepository;
import com.imagesearch.repository.ImageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SearchService.
 *
 * Tests cover:
 * - Search functionality
 * - Python search client integration
 * - Folder permission checks
 * - Result mapping
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Search Service Tests")
public class SearchServiceTest {

    @Mock
    private PythonSearchClient pythonSearchClient;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private ImageRepository imageRepository;

    @Mock
    private FolderService folderService;

    @Mock
    private ImageService imageService;

    @InjectMocks
    private SearchService searchService;

    private User testUser;
    private Folder testFolder;
    private Image testImage;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");

        testFolder = new Folder();
        testFolder.setId(1L);
        testFolder.setFolderName("test_folder");
        testFolder.setUser(testUser);

        testImage = new Image();
        testImage.setId(1L);
        testImage.setFilepath("images/1/1/test.png");
        testImage.setUser(testUser);
        testImage.setFolder(testFolder);
    }

    @Nested
    @DisplayName("Search Functionality Tests")
    class SearchFunctionalityTests {

        @Test
        @DisplayName("Should return search results for valid query")
        void testSearchSuccess() {
            // Arrange
            List<Long> folderIds = Arrays.asList(1L);
            String query = "sunset";

            when(folderService.checkFolderAccess(testUser.getId(), 1L))
                    .thenReturn(testFolder);

            SearchServiceResponse.SearchResult resultItem = new SearchServiceResponse.SearchResult(1L, 0.95);

            SearchServiceResponse searchResponse = new SearchServiceResponse();
            searchResponse.setResults(Arrays.asList(resultItem));

            when(pythonSearchClient.search(any(SearchServiceRequest.class)))
                    .thenReturn(searchResponse);

            when(imageService.getImageById(1L))
                    .thenReturn(testImage);

            // Act
            var results = searchService.searchImages(testUser.getId(), query, folderIds, 5);

            // Assert
            assertThat(results).isNotNull();
            assertThat(results.getResults()).hasSize(1);
            assertThat(results.getResults().get(0).getSimilarity()).isEqualTo(0.95);

            verify(pythonSearchClient, times(1)).search(any(SearchServiceRequest.class));
        }

        @Test
        @DisplayName("Should return empty results when no matches found")
        void testSearchNoResults() {
            List<Long> folderIds = Arrays.asList(1L);
            String query = "nonexistent";

            when(folderService.checkFolderAccess(testUser.getId(), 1L))
                    .thenReturn(testFolder);

            SearchServiceResponse searchResponse = new SearchServiceResponse();
            searchResponse.setResults(Arrays.asList());

            when(pythonSearchClient.search(any(SearchServiceRequest.class)))
                    .thenReturn(searchResponse);

            var results = searchService.searchImages(testUser.getId(), query, folderIds, 5);

            assertThat(results).isNotNull();
            assertThat(results.getResults()).isEmpty();
        }

        @Test
        @DisplayName("Should search across multiple folders")
        void testSearchMultipleFolders() {
            List<Long> folderIds = Arrays.asList(1L, 2L);
            String query = "test";

            Folder folder2 = new Folder();
            folder2.setId(2L);
            folder2.setFolderName("folder2");
            folder2.setUser(testUser);

            when(folderService.checkFolderAccess(testUser.getId(), 1L))
                    .thenReturn(testFolder);
            when(folderService.checkFolderAccess(testUser.getId(), 2L))
                    .thenReturn(folder2);

            SearchServiceResponse searchResponse = new SearchServiceResponse();
            searchResponse.setResults(Arrays.asList());

            when(pythonSearchClient.search(any(SearchServiceRequest.class)))
                    .thenReturn(searchResponse);

            searchService.searchImages(testUser.getId(), query, folderIds, 5);

            verify(pythonSearchClient, times(1)).search(any(SearchServiceRequest.class));
        }
    }

    @Nested
    @DisplayName("Permission Tests")
    class PermissionTests {

        @Test
        @DisplayName("Should only search folders user has access to")
        void testSearchOnlyAccessibleFolders() {
            List<Long> folderIds = Arrays.asList(1L, 2L, 3L);
            String query = "test";

            // User only has access to folder 1
            when(folderService.checkFolderAccess(testUser.getId(), 1L))
                    .thenReturn(testFolder);
            when(folderService.checkFolderAccess(testUser.getId(), 2L))
                    .thenThrow(new RuntimeException("No access"));
            when(folderService.checkFolderAccess(testUser.getId(), 3L))
                    .thenThrow(new RuntimeException("No access"));

            SearchServiceResponse searchResponse = new SearchServiceResponse();
            searchResponse.setResults(Arrays.asList());

            when(pythonSearchClient.search(any(SearchServiceRequest.class)))
                    .thenReturn(searchResponse);

            try {
                searchService.searchImages(testUser.getId(), query, folderIds, 5);
            } catch (RuntimeException e) {
                // Expected - user doesn't have access to folders 2 and 3
            }

            // Should have tried to check access to folder 1
            verify(folderService, times(1)).checkFolderAccess(testUser.getId(), 1L);
        }

        @Test
        @DisplayName("Should handle user with no folder access")
        void testSearchNoFolderAccess() {
            List<Long> folderIds = Arrays.asList(1L);
            String query = "test";

            when(folderService.checkFolderAccess(testUser.getId(), 1L))
                    .thenThrow(new RuntimeException("No access"));

            try {
                searchService.searchImages(testUser.getId(), query, folderIds, 5);
            } catch (RuntimeException e) {
                // Expected - user doesn't have access
                assertThat(e.getMessage()).contains("access");
            }

            verify(pythonSearchClient, never()).search(any(SearchServiceRequest.class));
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle search service unavailable")
        void testSearchServiceUnavailable() {
            List<Long> folderIds = Arrays.asList(1L);
            String query = "test";

            when(folderService.checkFolderAccess(testUser.getId(), 1L))
                    .thenReturn(testFolder);

            when(pythonSearchClient.search(any(SearchServiceRequest.class)))
                    .thenThrow(new RuntimeException("Service unavailable"));

            try {
                searchService.searchImages(testUser.getId(), query, folderIds, 5);
            } catch (RuntimeException e) {
                assertThat(e.getMessage()).contains("unavailable");
            }
        }

        @Test
        @DisplayName("Should handle missing image in database")
        void testSearchWithMissingImage() {
            List<Long> folderIds = Arrays.asList(1L);
            String query = "test";

            when(folderService.checkFolderAccess(testUser.getId(), 1L))
                    .thenReturn(testFolder);

            SearchServiceResponse.SearchResult resultItem = new SearchServiceResponse.SearchResult(999L, 0.95);

            SearchServiceResponse searchResponse = new SearchServiceResponse();
            searchResponse.setResults(Arrays.asList(resultItem));

            when(pythonSearchClient.search(any(SearchServiceRequest.class)))
                    .thenReturn(searchResponse);

            when(imageService.getImageById(999L))
                    .thenReturn(null);

            var results = searchService.searchImages(testUser.getId(), query, folderIds, 5);

            // Should skip missing images
            assertThat(results.getResults()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Result Mapping Tests")
    class ResultMappingTests {

        @Test
        @DisplayName("Should correctly map image URLs")
        void testImageUrlMapping() {
            List<Long> folderIds = Arrays.asList(1L);
            String query = "test";

            when(folderService.checkFolderAccess(testUser.getId(), 1L))
                    .thenReturn(testFolder);

            SearchServiceResponse.SearchResult resultItem = new SearchServiceResponse.SearchResult(1L, 0.95);

            SearchServiceResponse searchResponse = new SearchServiceResponse();
            searchResponse.setResults(Arrays.asList(resultItem));

            when(pythonSearchClient.search(any(SearchServiceRequest.class)))
                    .thenReturn(searchResponse);

            when(imageService.getImageById(1L))
                    .thenReturn(testImage);

            var results = searchService.searchImages(testUser.getId(), query, folderIds, 5);

            assertThat(results.getResults().get(0).getImage())
                    .isNotNull()
                    .contains("images/");
        }

        @Test
        @DisplayName("Should sort results by score descending")
        void testResultSorting() {
            List<Long> folderIds = Arrays.asList(1L);
            String query = "test";

            when(folderService.checkFolderAccess(testUser.getId(), 1L))
                    .thenReturn(testFolder);

            SearchServiceResponse.SearchResult result1 = new SearchServiceResponse.SearchResult(1L, 0.75);
            SearchServiceResponse.SearchResult result2 = new SearchServiceResponse.SearchResult(2L, 0.95);

            SearchServiceResponse searchResponse = new SearchServiceResponse();
            searchResponse.setResults(Arrays.asList(result1, result2));

            when(pythonSearchClient.search(any(SearchServiceRequest.class)))
                    .thenReturn(searchResponse);

            Image image2 = new Image();
            image2.setId(2L);
            image2.setFilepath("images/1/1/test2.png");

            when(imageService.getImageById(1L)).thenReturn(testImage);
            when(imageService.getImageById(2L)).thenReturn(image2);

            var results = searchService.searchImages(testUser.getId(), query, folderIds, 5);

            // Results should be sorted by score (highest first)
            assertThat(results.getResults().get(0).getSimilarity()).isGreaterThan(
                    results.getResults().get(1).getSimilarity()
            );
        }
    }
}
