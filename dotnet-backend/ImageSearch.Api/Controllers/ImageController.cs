using ImageSearch.Api.Models.DTOs.Responses;
using ImageSearch.Api.Services;
using Microsoft.AspNetCore.Mvc;

namespace ImageSearch.Api.Controllers;

[ApiController]
[Route("api/images")]
public class ImageController : ControllerBase
{
    private readonly ISessionService _sessionService;
    private readonly IImageService _imageService;
    private readonly ISearchService _searchService;
    private readonly ILogger<ImageController> _logger;

    public ImageController(
        ISessionService sessionService,
        IImageService imageService,
        ISearchService searchService,
        ILogger<ImageController> logger)
    {
        _sessionService = sessionService;
        _imageService = imageService;
        _searchService = searchService;
        _logger = logger;
    }

    [HttpPost("upload")]
    [RequestSizeLimit(100_000_000)] // 100 MB
    [ProducesResponseType(typeof(UploadResponse), StatusCodes.Status200OK)]
    public async Task<IActionResult> UploadImages(
        [FromForm] string token,
        [FromForm] string folderName,
        [FromForm] IFormFileCollection files)
    {
        var userId = await _sessionService.ValidateTokenAndGetUserIdAsync(token);
        _logger.LogInformation("Upload images request: user={UserId}, folder={FolderName}, count={Count}",
            userId, folderName, files.Count);

        var response = await _imageService.UploadImagesAsync(userId, folderName, files);
        return Ok(response);
    }

    [HttpGet("search")]
    [ProducesResponseType(typeof(SearchResponse), StatusCodes.Status200OK)]
    public async Task<IActionResult> SearchImages(
        [FromQuery] string token,
        [FromQuery] string query,
        [FromQuery] string? folder_ids = null,
        [FromQuery] int top_k = 5)
    {
        var userId = await _sessionService.ValidateTokenAndGetUserIdAsync(token);

        List<long>? folderIds = null;
        if (!string.IsNullOrEmpty(folder_ids))
        {
            folderIds = folder_ids.Split(',').Select(long.Parse).ToList();
        }

        _logger.LogInformation("Search request: user={UserId}, query={Query}, folders={FolderIds}",
            userId, query, folder_ids ?? "all");

        var response = await _searchService.SearchImagesAsync(userId, query, folderIds, top_k);
        return Ok(response);
    }
}
