using ImageSearch.Api.Models.DTOs.Responses;
using ImageSearch.Api.Models.Entities;

namespace ImageSearch.Api.Services;

public interface IFolderService
{
    Task<Folder> CreateOrGetFolderAsync(long userId, string folderName);
    Task<List<FolderResponse>> GetAccessibleFoldersAsync(long userId);
    Task DeleteFoldersAsync(List<long> folderIds, long userId);
    Task ShareFolderAsync(long folderId, long ownerId, string targetUsername, string permission);
    Task<bool> CheckFolderAccessAsync(long userId, long folderId);
}
