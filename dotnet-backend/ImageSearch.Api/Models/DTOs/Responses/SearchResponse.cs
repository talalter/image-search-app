namespace ImageSearch.Api.Models.DTOs.Responses;

public record ImageSearchResult(
    string Image,
    double Similarity
);

public record SearchResponse(
    List<ImageSearchResult> Results
);
