using ImageSearch.Api.Models.DTOs.Requests;
using ImageSearch.Api.Models.DTOs.Responses;

namespace ImageSearch.Api.Services;

public interface IUserService
{
    Task<RegisterResponse> RegisterAsync(RegisterRequest request);
    Task<LoginResponse> LoginAsync(LoginRequest request);
    Task LogoutAsync(string token);
    Task DeleteAccountAsync(long userId);
}
