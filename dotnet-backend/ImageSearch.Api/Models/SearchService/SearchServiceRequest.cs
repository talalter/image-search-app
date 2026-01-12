namespace ImageSearch.Api.Models.SearchService;

public record SearchServiceRequest(
    long UserId,
    string Query,
    List<long> FolderIds,
    Dictionary<long, long> FolderOwnerMap,
    int TopK = 5
);
