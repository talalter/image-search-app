namespace ImageSearch.Api.Models.SearchService;

public record ImageInfo(
    long ImageId,
    string Filepath
);

public record EmbedImagesRequest(
    long UserId,
    long FolderId,
    List<ImageInfo> Images
);
