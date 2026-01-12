using ImageSearch.Api.Models.DTOs.Requests;
using ImageSearch.Api.Models.DTOs.Responses;
using ImageSearch.Api.Services;
using Microsoft.AspNetCore.Mvc;

namespace ImageSearch.Api.Controllers;

[ApiController]
[Route("api/folders")]
public class FolderController : ControllerBase
{
    private readonly ISessionService _sessionService;
    private readonly IFolderService _folderService;
    private readonly ILogger<FolderController> _logger;

    public FolderController(
        ISessionService sessionService,
        IFolderService folderService,
        ILogger<FolderController> logger)
    {
        _sessionService = sessionService;
        _folderService = folderService;
        _logger = logger;
    }

    [HttpGet]
    [ProducesResponseType(typeof(List<FolderResponse>), StatusCodes.Status200OK)]
    public async Task<IActionResult> GetFolders([FromQuery] string token)
    {
        var userId = await _sessionService.ValidateTokenAndGetUserIdAsync(token);
        _logger.LogInformation("Get folders request for user: {UserId}", userId);

        var folders = await _folderService.GetAccessibleFoldersAsync(userId);
        return Ok(new { folders });
    }

    [HttpDelete]
    [ProducesResponseType(typeof(MessageResponse), StatusCodes.Status200OK)]
    public async Task<IActionResult> DeleteFolders([FromBody] DeleteFoldersRequest request)
    {
        var userId = await _sessionService.ValidateTokenAndGetUserIdAsync(request.Token);
        _logger.LogInformation("Delete folders request for user: {UserId}", userId);

        await _folderService.DeleteFoldersAsync(request.FolderIds, userId);
        return Ok(new MessageResponse($"Deleted {request.FolderIds.Count} folders"));
    }

    [HttpPost("share")]
    [ProducesResponseType(typeof(MessageResponse), StatusCodes.Status200OK)]
    public async Task<IActionResult> ShareFolder([FromBody] ShareFolderRequest request)
    {
        var userId = await _sessionService.ValidateTokenAndGetUserIdAsync(request.Token);
        _logger.LogInformation("Share folder request: folderId={FolderId}, targetUser={TargetUsername}",
            request.FolderId, request.TargetUsername);

        await _folderService.ShareFolderAsync(request.FolderId, userId, request.TargetUsername, request.Permission);
        return Ok(new MessageResponse($"Folder shared successfully with {request.TargetUsername}"));
    }
}
