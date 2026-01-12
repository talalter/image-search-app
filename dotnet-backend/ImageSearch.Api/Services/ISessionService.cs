using ImageSearch.Api.Models.Entities;

namespace ImageSearch.Api.Services;

public interface ISessionService
{
    Task<string> CreateSessionAsync(User user);
    Task<long> ValidateTokenAndGetUserIdAsync(string token);
    Task InvalidateSessionAsync(string token);
    Task CleanupExpiredSessionsAsync();
}
