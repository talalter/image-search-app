using ImageSearch.Api.Models.DTOs.Responses;

namespace ImageSearch.Api.Services;

public interface IImageService
{
    Task<UploadResponse> UploadImagesAsync(long userId, string folderName, IFormFileCollection files);
}
