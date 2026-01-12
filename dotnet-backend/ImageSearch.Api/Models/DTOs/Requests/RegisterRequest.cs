using System.ComponentModel.DataAnnotations;

namespace ImageSearch.Api.Models.DTOs.Requests;

public record RegisterRequest(
    [Required] string Username,
    [Required] string Password
);
