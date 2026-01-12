using System.ComponentModel.DataAnnotations;

namespace ImageSearch.Api.Models.DTOs.Requests;

public record ShareFolderRequest(
    [Required] string Token,
    [Required] long FolderId,
    [Required] string TargetUsername,
    [Required] string Permission
);
