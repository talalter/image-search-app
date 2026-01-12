using System.ComponentModel.DataAnnotations;

namespace ImageSearch.Api.Models.DTOs.Requests;

public record DeleteFoldersRequest(
    [Required] string Token,
    [Required] List<long> FolderIds
);
