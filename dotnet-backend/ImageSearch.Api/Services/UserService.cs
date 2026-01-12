using ImageSearch.Api.Clients;
using ImageSearch.Api.Data;
using ImageSearch.Api.Exceptions;
using ImageSearch.Api.Models.DTOs.Requests;
using ImageSearch.Api.Models.DTOs.Responses;
using ImageSearch.Api.Models.Entities;
using ImageSearch.Api.Utilities;
using Microsoft.EntityFrameworkCore;

namespace ImageSearch.Api.Services;

public class UserService : IUserService
{
    private readonly ApplicationDbContext _context;
    private readonly ISessionService _sessionService;
    private readonly IPythonSearchClient _searchClient;
    private readonly ILogger<UserService> _logger;

    public UserService(
        ApplicationDbContext context,
        ISessionService sessionService,
        IPythonSearchClient searchClient,
        ILogger<UserService> logger)
    {
        _context = context;
        _sessionService = sessionService;
        _searchClient = searchClient;
        _logger = logger;
    }

    public async Task<RegisterResponse> RegisterAsync(RegisterRequest request)
    {
        // Check if username already exists
        var existingUser = await _context.Users
            .Where(u => u.Username == request.Username)
            .FirstOrDefaultAsync();

        if (existingUser != null)
        {
            throw new DuplicateResourceException($"Username '{request.Username}' is already taken");
        }

        // Create new user with hashed password
        var user = new User
        {
            Username = request.Username,
            Password = PasswordHasher.HashPassword(request.Password),
            CreatedAt = DateTime.UtcNow
        };

        _context.Users.Add(user);
        await _context.SaveChangesAsync();

        _logger.LogInformation("Registered new user: {Username} (ID: {UserId})",
            user.Username, user.Id);

        return new RegisterResponse(user.Id, user.Username);
    }

    public async Task<LoginResponse> LoginAsync(LoginRequest request)
    {
        var user = await _context.Users
            .Where(u => u.Username == request.Username)
            .FirstOrDefaultAsync();

        if (user == null || !PasswordHasher.VerifyPassword(request.Password, user.Password))
        {
            _logger.LogWarning("Failed login attempt for username: {Username}", request.Username);
            throw new UnauthorizedException("Invalid username or password");
        }

        // Create session token
        var token = await _sessionService.CreateSessionAsync(user);

        _logger.LogInformation("User logged in: {Username} (ID: {UserId})",
            user.Username, user.Id);

        return new LoginResponse(token, user.Id, user.Username);
    }

    public async Task LogoutAsync(string token)
    {
        await _sessionService.InvalidateSessionAsync(token);
        _logger.LogInformation("User logged out with token: {Token}", token.Substring(0, 8) + "...");
    }

    public async Task DeleteAccountAsync(long userId)
    {
        var user = await _context.Users
            .Include(u => u.Folders)
            .Include(u => u.Sessions)
            .Include(u => u.Images)
            .Where(u => u.Id == userId)
            .FirstOrDefaultAsync();

        if (user == null)
        {
            throw new ResourceNotFoundException($"User with ID {userId} not found");
        }

        _logger.LogInformation("Deleting account for user: {Username} (ID: {UserId})",
            user.Username, userId);

        // Delete FAISS indexes for all user folders
        foreach (var folder in user.Folders)
        {
            try
            {
                await _searchClient.DeleteIndexAsync(userId, folder.Id);
            }
            catch (Exception ex)
            {
                _logger.LogWarning(ex, "Failed to delete index for folder {FolderId}", folder.Id);
                // Continue with deletion even if index deletion fails
            }
        }

        // Delete user (cascade deletes sessions, folders, images, shares)
        _context.Users.Remove(user);
        await _context.SaveChangesAsync();

        // Delete physical files
        try
        {
            var userUploadPath = FileHelper.GetUploadPath(userId, 0);
            var userDir = Directory.GetParent(userUploadPath)?.FullName;
            if (userDir != null && Directory.Exists(userDir))
            {
                FileHelper.DeleteDirectory(userDir);
                _logger.LogInformation("Deleted physical files for user {UserId}", userId);
            }
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Failed to delete physical files for user {UserId}", userId);
        }

        _logger.LogInformation("Account deleted for user {UserId}", userId);
    }
}
