using System.Threading.Channels;
using ImageSearch.Api.Data;
using ImageSearch.Api.Exceptions;
using ImageSearch.Api.Models.DTOs.Responses;
using ImageSearch.Api.Models.Entities;
using ImageSearch.Api.Models.SearchService;
using ImageSearch.Api.Utilities;

namespace ImageSearch.Api.Services;

public class ImageService : IImageService
{
    private readonly ApplicationDbContext _context;
    private readonly IFolderService _folderService;
    private readonly Channel<EmbeddingTask> _embeddingQueue;
    private readonly ILogger<ImageService> _logger;

    public ImageService(
        ApplicationDbContext context,
        IFolderService folderService,
        Channel<EmbeddingTask> embeddingQueue,
        ILogger<ImageService> logger)
    {
        _context = context;
        _folderService = folderService;
        _embeddingQueue = embeddingQueue;
        _logger = logger;
    }

    public async Task<UploadResponse> UploadImagesAsync(long userId, string folderName, IFormFileCollection files)
    {
        if (files == null || files.Count == 0)
        {
            throw new BadRequestException("No files provided");
        }

        // Validate file extensions
        foreach (var file in files)
        {
            if (!FileHelper.IsValidImageExtension(file.FileName))
            {
                throw new BadRequestException($"Invalid file type: {file.FileName}. Only .png, .jpg, .jpeg are allowed.");
            }
        }

        // Create or get folder
        var folder = await _folderService.CreateOrGetFolderAsync(userId, folderName);
        var uploadPath = FileHelper.GetUploadPath(userId, folder.Id);

        // Save files and create database records
        var imagesToEmbed = new List<ImageInfo>();
        int successCount = 0;

        foreach (var file in files)
        {
            try
            {
                var filename = Path.GetFileName(file.FileName);
                await FileHelper.SaveFileAsync(file, uploadPath, filename);

                var relativePath = FileHelper.GetRelativeFilepath(userId, folder.Id, filename);

                var image = new Image
                {
                    UserId = userId,
                    FolderId = folder.Id,
                    Filepath = relativePath,
                    UploadedAt = DateTime.UtcNow
                };

                _context.Images.Add(image);
                await _context.SaveChangesAsync();

                imagesToEmbed.Add(new ImageInfo(image.Id, relativePath));
                successCount++;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Failed to upload file: {FileName}", file.FileName);
            }
        }

        // Queue embeddings for background processing
        if (imagesToEmbed.Any())
        {
            await _embeddingQueue.Writer.WriteAsync(new EmbeddingTask(userId, folder.Id, imagesToEmbed));
        }

        _logger.LogInformation("Uploaded {Count} images to folder {FolderId}", successCount, folder.Id);

        return new UploadResponse(
            $"Successfully uploaded {successCount} images. Processing embeddings in background...",
            folder.Id,
            successCount
        );
    }
}

public record EmbeddingTask(long UserId, long FolderId, List<ImageInfo> Images);
