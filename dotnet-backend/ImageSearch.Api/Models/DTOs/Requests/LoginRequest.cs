using System.ComponentModel.DataAnnotations;

namespace ImageSearch.Api.Models.DTOs.Requests;

public record LoginRequest(
    [Required] string Username,
    [Required] string Password
);
