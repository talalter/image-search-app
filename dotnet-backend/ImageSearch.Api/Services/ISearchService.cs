using ImageSearch.Api.Models.DTOs.Responses;

namespace ImageSearch.Api.Services;

public interface ISearchService
{
    Task<SearchResponse> SearchImagesAsync(long userId, string query, List<long>? folderIds, int topK);
}
