using System.Security.Cryptography;

namespace ImageSearch.Api.Utilities;

public static class TokenGenerator
{
    public static string GenerateSecureToken()
    {
        // Generate 32-byte random token (matches Java/Python implementation)
        var bytes = new byte[32];
        using var rng = RandomNumberGenerator.Create();
        rng.GetBytes(bytes);
        return Convert.ToBase64String(bytes);
    }
}
