using ImageSearch.Api.Data;
using ImageSearch.Api.Exceptions;
using ImageSearch.Api.Models.Entities;
using ImageSearch.Api.Utilities;
using Microsoft.EntityFrameworkCore;

namespace ImageSearch.Api.Services;

public class SessionService : ISessionService
{
    private readonly ApplicationDbContext _context;
    private readonly IConfiguration _configuration;
    private readonly ILogger<SessionService> _logger;

    public SessionService(
        ApplicationDbContext context,
        IConfiguration configuration,
        ILogger<SessionService> logger)
    {
        _context = context;
        _configuration = configuration;
        _logger = logger;
    }

    public async Task<string> CreateSessionAsync(User user)
    {
        var token = TokenGenerator.GenerateSecureToken();
        var expiryHours = _configuration.GetValue<int>("Session:TokenExpiryHours", 12);

        var session = new Session
        {
            Token = token,
            UserId = user.Id,
            CreatedAt = DateTime.UtcNow,
            ExpiresAt = DateTime.UtcNow.AddHours(expiryHours),
            LastSeen = DateTime.UtcNow
        };

        _context.Sessions.Add(session);
        await _context.SaveChangesAsync();

        _logger.LogInformation("Created session for user {UserId}", user.Id);
        return token;
    }

    public async Task<long> ValidateTokenAndGetUserIdAsync(string token)
    {
        var session = await _context.Sessions
            .Where(s => s.Token == token)
            .FirstOrDefaultAsync();

        if (session == null || session.IsExpired())
        {
            _logger.LogWarning("Invalid or expired token: {Token}", token);
            throw new UnauthorizedException("Invalid or expired session token");
        }

        // Extend session expiry (sliding window)
        var expiryHours = _configuration.GetValue<int>("Session:TokenExpiryHours", 12);
        session.ExpiresAt = DateTime.UtcNow.AddHours(expiryHours);
        session.LastSeen = DateTime.UtcNow;
        await _context.SaveChangesAsync();

        return session.UserId;
    }

    public async Task InvalidateSessionAsync(string token)
    {
        var session = await _context.Sessions
            .Where(s => s.Token == token)
            .FirstOrDefaultAsync();

        if (session != null)
        {
            _context.Sessions.Remove(session);
            await _context.SaveChangesAsync();
            _logger.LogInformation("Invalidated session for user {UserId}", session.UserId);
        }
    }

    public async Task CleanupExpiredSessionsAsync()
    {
        var expiredSessions = await _context.Sessions
            .Where(s => s.ExpiresAt <= DateTime.UtcNow)
            .ToListAsync();

        if (expiredSessions.Any())
        {
            _context.Sessions.RemoveRange(expiredSessions);
            await _context.SaveChangesAsync();
            _logger.LogInformation("Cleaned up {Count} expired sessions", expiredSessions.Count);
        }
    }
}
