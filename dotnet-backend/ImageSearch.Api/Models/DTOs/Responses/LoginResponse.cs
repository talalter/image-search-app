namespace ImageSearch.Api.Models.DTOs.Responses;

public record LoginResponse(
    string Token,
    long UserId,
    string Username,
    string Message = "Login successful"
);
