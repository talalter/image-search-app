using System.Text.Json;
using ImageSearch.Api.Exceptions;
using ImageSearch.Api.Models.SearchService;

namespace ImageSearch.Api.Clients;

public class PythonSearchClient : IPythonSearchClient
{
    private readonly HttpClient _httpClient;
    private readonly ILogger<PythonSearchClient> _logger;
    private readonly JsonSerializerOptions _jsonOptions;

    public PythonSearchClient(HttpClient httpClient, ILogger<PythonSearchClient> logger)
    {
        _httpClient = httpClient;
        _logger = logger;
        _jsonOptions = new JsonSerializerOptions
        {
            PropertyNamingPolicy = JsonNamingPolicy.SnakeCaseLower,
            PropertyNameCaseInsensitive = true
        };
    }

    public async Task<SearchServiceResponse> SearchAsync(SearchServiceRequest request)
    {
        _logger.LogInformation("Calling Python search service: query='{Query}', folders={Folders}",
            request.Query, string.Join(",", request.FolderIds));

        try
        {
            var response = await _httpClient.PostAsJsonAsync("/api/search", request, _jsonOptions);

            if (!response.IsSuccessStatusCode)
            {
                var errorContent = await response.Content.ReadAsStringAsync();
                _logger.LogError("Python search service returned error: {StatusCode} - {Content}",
                    response.StatusCode, errorContent);
                throw new SearchServiceUnavailableException(
                    $"Search service returned {response.StatusCode}");
            }

            var result = await response.Content.ReadFromJsonAsync<SearchServiceResponse>(_jsonOptions);
            _logger.LogInformation("Python search service returned {Count} results",
                result?.Results?.Count ?? 0);

            return result ?? new SearchServiceResponse(new List<SearchResult>(), 0);
        }
        catch (HttpRequestException ex)
        {
            _logger.LogError(ex, "Failed to connect to Python search service");
            throw new SearchServiceUnavailableException(
                "Search service is unavailable. Please try again later.");
        }
        catch (TaskCanceledException ex)
        {
            _logger.LogError(ex, "Python search service request timed out");
            throw new SearchServiceUnavailableException(
                "Search service request timed out");
        }
    }

    public async Task EmbedImagesAsync(EmbedImagesRequest request)
    {
        _logger.LogInformation("Calling Python service to embed {Count} images for folder {FolderId}",
            request.Images.Count, request.FolderId);

        try
        {
            var response = await _httpClient.PostAsJsonAsync("/api/embed-images", request, _jsonOptions);

            if (!response.IsSuccessStatusCode)
            {
                var errorContent = await response.Content.ReadAsStringAsync();
                _logger.LogError("Python search service returned error during embedding: {StatusCode} - {Content}",
                    response.StatusCode, errorContent);
                throw new SearchServiceUnavailableException(
                    $"Failed to embed images: {response.StatusCode}");
            }

            _logger.LogInformation("Successfully embedded images for folder {FolderId}", request.FolderId);
        }
        catch (HttpRequestException ex)
        {
            _logger.LogError(ex, "Failed to connect to Python search service for embedding");
            throw new SearchServiceUnavailableException(
                "Search service is unavailable for embedding");
        }
    }

    public async Task CreateIndexAsync(long userId, long folderId)
    {
        _logger.LogInformation("Creating FAISS index for user {UserId}, folder {FolderId}",
            userId, folderId);

        try
        {
            var request = new { user_id = userId, folder_id = folderId };
            var response = await _httpClient.PostAsJsonAsync("/api/create-index", request, _jsonOptions);

            if (!response.IsSuccessStatusCode)
            {
                var errorContent = await response.Content.ReadAsStringAsync();
                _logger.LogWarning("Failed to create index: {StatusCode} - {Content}",
                    response.StatusCode, errorContent);
                // Non-critical - index will be created on first image upload
            }
            else
            {
                _logger.LogInformation("Successfully created index for folder {FolderId}", folderId);
            }
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Failed to create index for folder {FolderId} (non-critical)", folderId);
            // Non-critical error - don't throw
        }
    }

    public async Task DeleteIndexAsync(long userId, long folderId)
    {
        _logger.LogInformation("Deleting FAISS index for user {UserId}, folder {FolderId}",
            userId, folderId);

        try
        {
            var response = await _httpClient.DeleteAsync($"/api/delete-index/{userId}/{folderId}");

            if (!response.IsSuccessStatusCode)
            {
                var errorContent = await response.Content.ReadAsStringAsync();
                _logger.LogWarning("Failed to delete index: {StatusCode} - {Content}",
                    response.StatusCode, errorContent);
                // Non-critical - orphaned indexes can be cleaned up later
            }
            else
            {
                _logger.LogInformation("Successfully deleted index for folder {FolderId}", folderId);
            }
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Failed to delete index for folder {FolderId} (non-critical)", folderId);
            // Non-critical error - don't throw
        }
    }
}
