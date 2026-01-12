namespace ImageSearch.Api.Models.DTOs.Responses;

public record FolderResponse(
    long Id,
    string FolderName,
    bool IsOwner = true,
    bool IsShared = false,
    long? OwnerId = null,
    string? OwnerUsername = null
);
