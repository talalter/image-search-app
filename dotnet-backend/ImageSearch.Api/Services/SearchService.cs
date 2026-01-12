using ImageSearch.Api.Clients;
using ImageSearch.Api.Data;
using ImageSearch.Api.Models.DTOs.Responses;
using ImageSearch.Api.Models.SearchService;
using ImageSearch.Api.Utilities;
using Microsoft.EntityFrameworkCore;

namespace ImageSearch.Api.Services;

public class SearchService : ISearchService
{
    private readonly ApplicationDbContext _context;
    private readonly IPythonSearchClient _searchClient;
    private readonly IFolderService _folderService;
    private readonly IConfiguration _configuration;
    private readonly ILogger<SearchService> _logger;

    public SearchService(
        ApplicationDbContext context,
        IPythonSearchClient searchClient,
        IFolderService folderService,
        IConfiguration configuration,
        ILogger<SearchService> logger)
    {
        _context = context;
        _searchClient = searchClient;
        _folderService = folderService;
        _configuration = configuration;
        _logger = logger;
    }

    public async Task<SearchResponse> SearchImagesAsync(long userId, string query, List<long>? folderIds, int topK)
    {
        // Determine which folders to search
        List<long> searchFolderIds;
        if (folderIds != null && folderIds.Any())
        {
            // Validate access to specified folders
            searchFolderIds = new List<long>();
            foreach (var folderId in folderIds)
            {
                if (await _folderService.CheckFolderAccessAsync(userId, folderId))
                {
                    searchFolderIds.Add(folderId);
                }
            }
        }
        else
        {
            // Search all accessible folders
            var accessibleFolders = await _folderService.GetAccessibleFoldersAsync(userId);
            searchFolderIds = accessibleFolders.Select(f => f.Id).ToList();
        }

        if (!searchFolderIds.Any())
        {
            return new SearchResponse(new List<ImageSearchResult>());
        }

        // Build folder owner map (for FAISS index path resolution)
        var folderOwnerMap = await _context.Folders
            .Where(f => searchFolderIds.Contains(f.Id))
            .Select(f => new { f.Id, f.UserId })
            .ToDictionaryAsync(x => x.Id, x => x.UserId);

        // Call Python search service
        var searchRequest = new SearchServiceRequest(
            userId,
            query,
            searchFolderIds,
            folderOwnerMap,
            topK
        );

        var searchResults = await _searchClient.SearchAsync(searchRequest);

        // Enrich results with image URLs
        var imageIds = searchResults.Results.Select(r => r.ImageId).ToList();
        var images = await _context.Images
            .Where(i => imageIds.Contains(i.Id))
            .ToDictionaryAsync(i => i.Id, i => i.Filepath);

        var baseUrl = _configuration.GetValue<string>("BaseUrl", "http://localhost:7000");
        var enrichedResults = searchResults.Results
            .Where(r => images.ContainsKey(r.ImageId))
            .Select(r => new ImageSearchResult(
                $"{baseUrl}/{images[r.ImageId]}",
                r.Score
            ))
            .ToList();

        _logger.LogInformation("Search returned {Count} results for query: {Query}", enrichedResults.Count, query);

        return new SearchResponse(enrichedResults);
    }
}
