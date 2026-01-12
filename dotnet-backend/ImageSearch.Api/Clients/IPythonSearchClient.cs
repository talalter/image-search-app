using ImageSearch.Api.Models.SearchService;

namespace ImageSearch.Api.Clients;

public interface IPythonSearchClient
{
    Task<SearchServiceResponse> SearchAsync(SearchServiceRequest request);
    Task EmbedImagesAsync(EmbedImagesRequest request);
    Task CreateIndexAsync(long userId, long folderId);
    Task DeleteIndexAsync(long userId, long folderId);
}
