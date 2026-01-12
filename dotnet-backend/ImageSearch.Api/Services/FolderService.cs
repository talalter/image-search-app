using ImageSearch.Api.Clients;
using ImageSearch.Api.Data;
using ImageSearch.Api.Exceptions;
using ImageSearch.Api.Models.DTOs.Responses;
using ImageSearch.Api.Models.Entities;
using ImageSearch.Api.Utilities;
using Microsoft.EntityFrameworkCore;

namespace ImageSearch.Api.Services;

public class FolderService : IFolderService
{
    private readonly ApplicationDbContext _context;
    private readonly IPythonSearchClient _searchClient;
    private readonly ILogger<FolderService> _logger;

    public FolderService(
        ApplicationDbContext context,
        IPythonSearchClient searchClient,
        ILogger<FolderService> logger)
    {
        _context = context;
        _searchClient = searchClient;
        _logger = logger;
    }

    public async Task<Folder> CreateOrGetFolderAsync(long userId, string folderName)
    {
        var folder = await _context.Folders
            .Where(f => f.UserId == userId && f.FolderName == folderName)
            .FirstOrDefaultAsync();

        if (folder == null)
        {
            folder = new Folder
            {
                UserId = userId,
                FolderName = folderName,
                CreatedAt = DateTime.UtcNow
            };

            _context.Folders.Add(folder);
            await _context.SaveChangesAsync();

            // Create FAISS index
            try
            {
                await _searchClient.CreateIndexAsync(userId, folder.Id);
            }
            catch (Exception ex)
            {
                _logger.LogWarning(ex, "Failed to create index for folder {FolderId}", folder.Id);
            }

            _logger.LogInformation("Created folder: {FolderName} (ID: {FolderId}) for user {UserId}",
                folderName, folder.Id, userId);
        }

        return folder;
    }

    public async Task<List<FolderResponse>> GetAccessibleFoldersAsync(long userId)
    {
        // Get owned folders
        var ownedFolders = await _context.Folders
            .Where(f => f.UserId == userId)
            .Select(f => new FolderResponse(f.Id, f.FolderName, true, false, null, null))
            .ToListAsync();

        // Get shared folders
        var sharedFolders = await _context.FolderShares
            .Include(fs => fs.Folder)
            .Include(fs => fs.Owner)
            .Where(fs => fs.SharedWithUserId == userId)
            .Select(fs => new FolderResponse(
                fs.Folder.Id,
                fs.Folder.FolderName,
                false,
                true,
                fs.OwnerId,
                fs.Owner.Username))
            .ToListAsync();

        var allFolders = new List<FolderResponse>();
        allFolders.AddRange(ownedFolders);
        allFolders.AddRange(sharedFolders);

        return allFolders;
    }

    public async Task DeleteFoldersAsync(List<long> folderIds, long userId)
    {
        foreach (var folderId in folderIds)
        {
            var folder = await _context.Folders
                .Include(f => f.Images)
                .Where(f => f.Id == folderId && f.UserId == userId)
                .FirstOrDefaultAsync();

            if (folder != null)
            {
                // Delete FAISS index
                try
                {
                    await _searchClient.DeleteIndexAsync(userId, folderId);
                }
                catch (Exception ex)
                {
                    _logger.LogWarning(ex, "Failed to delete index for folder {FolderId}", folderId);
                }

                // Delete from database (cascade deletes images and shares)
                _context.Folders.Remove(folder);
                await _context.SaveChangesAsync();

                // Delete physical files
                try
                {
                    var uploadPath = FileHelper.GetUploadPath(userId, folderId);
                    FileHelper.DeleteDirectory(uploadPath);
                }
                catch (Exception ex)
                {
                    _logger.LogWarning(ex, "Failed to delete files for folder {FolderId}", folderId);
                }

                _logger.LogInformation("Deleted folder {FolderId} for user {UserId}", folderId, userId);
            }
        }
    }

    public async Task ShareFolderAsync(long folderId, long ownerId, string targetUsername, string permission)
    {
        var folder = await _context.Folders
            .Where(f => f.Id == folderId && f.UserId == ownerId)
            .FirstOrDefaultAsync();

        if (folder == null)
        {
            throw new ResourceNotFoundException($"Folder with ID {folderId} not found or not owned by user");
        }

        var targetUser = await _context.Users
            .Where(u => u.Username == targetUsername)
            .FirstOrDefaultAsync();

        if (targetUser == null)
        {
            throw new ResourceNotFoundException($"User '{targetUsername}' not found");
        }

        if (targetUser.Id == ownerId)
        {
            throw new BadRequestException("Cannot share folder with yourself");
        }

        // Check if already shared
        var existingShare = await _context.FolderShares
            .Where(fs => fs.FolderId == folderId && fs.SharedWithUserId == targetUser.Id)
            .FirstOrDefaultAsync();

        if (existingShare != null)
        {
            throw new DuplicateResourceException($"Folder is already shared with user '{targetUsername}'");
        }

        var share = new FolderShare
        {
            FolderId = folderId,
            OwnerId = ownerId,
            SharedWithUserId = targetUser.Id,
            Permission = permission,
            CreatedAt = DateTime.UtcNow
        };

        _context.FolderShares.Add(share);
        await _context.SaveChangesAsync();

        _logger.LogInformation("Shared folder {FolderId} with user {TargetUsername}", folderId, targetUsername);
    }

    public async Task<bool> CheckFolderAccessAsync(long userId, long folderId)
    {
        // Check if user owns the folder
        var isOwner = await _context.Folders
            .AnyAsync(f => f.Id == folderId && f.UserId == userId);

        if (isOwner) return true;

        // Check if folder is shared with user
        var hasAccess = await _context.FolderShares
            .AnyAsync(fs => fs.FolderId == folderId && fs.SharedWithUserId == userId);

        return hasAccess;
    }
}
