namespace ImageSearch.Api.Models.SearchService;

public record SearchResult(
    long ImageId,
    double Score,
    long FolderId
);

public record SearchServiceResponse(
    List<SearchResult> Results,
    int Total
);
