using Microsoft.AspNetCore.Mvc;

namespace ImageSearch.Api.Controllers;

[ApiController]
[Route("")]
public class HealthController : ControllerBase
{
    [HttpGet("health")]
    public IActionResult Health()
    {
        return Ok(new
        {
            status = "Healthy",
            service = "image-search-dotnet-backend",
            timestamp = DateTime.UtcNow
        });
    }
}
