using ImageSearch.Api.Models.DTOs.Requests;
using ImageSearch.Api.Models.DTOs.Responses;
using ImageSearch.Api.Services;
using Microsoft.AspNetCore.Mvc;

namespace ImageSearch.Api.Controllers;

[ApiController]
[Route("api/users")]
public class UserController : ControllerBase
{
    private readonly IUserService _userService;
    private readonly ISessionService _sessionService;
    private readonly ILogger<UserController> _logger;

    public UserController(
        IUserService userService,
        ISessionService sessionService,
        ILogger<UserController> logger)
    {
        _userService = userService;
        _sessionService = sessionService;
        _logger = logger;
    }

    [HttpPost("register")]
    [ProducesResponseType(typeof(RegisterResponse), StatusCodes.Status201Created)]
    public async Task<IActionResult> Register([FromBody] RegisterRequest request)
    {
        _logger.LogInformation("Registration request for username: {Username}", request.Username);
        var response = await _userService.RegisterAsync(request);
        return StatusCode(StatusCodes.Status201Created, response);
    }

    [HttpPost("login")]
    [ProducesResponseType(typeof(LoginResponse), StatusCodes.Status200OK)]
    public async Task<IActionResult> Login([FromBody] LoginRequest request)
    {
        _logger.LogInformation("Login request for username: {Username}", request.Username);
        var response = await _userService.LoginAsync(request);
        return Ok(response);
    }

    [HttpPost("logout")]
    [ProducesResponseType(typeof(MessageResponse), StatusCodes.Status200OK)]
    public async Task<IActionResult> Logout([FromBody] TokenRequest request)
    {
        _logger.LogInformation("Logout request");
        await _userService.LogoutAsync(request.Token);
        return Ok(new MessageResponse("Logout successful"));
    }

    [HttpDelete("delete")]
    [ProducesResponseType(typeof(MessageResponse), StatusCodes.Status200OK)]
    public async Task<IActionResult> DeleteAccount([FromBody] TokenRequest request)
    {
        var userId = await _sessionService.ValidateTokenAndGetUserIdAsync(request.Token);
        _logger.LogInformation("Delete account request for user: {UserId}", userId);
        await _userService.DeleteAccountAsync(userId);
        return Ok(new MessageResponse("Account deleted successfully"));
    }

    private record TokenRequest(string Token);
}
