namespace ImageSearch.Api.Models.DTOs.Responses;

public record UploadResponse(
    string Message,
    long FolderId,
    int UploadedCount
);
