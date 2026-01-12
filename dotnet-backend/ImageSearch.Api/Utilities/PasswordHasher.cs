namespace ImageSearch.Api.Utilities;

public static class PasswordHasher
{
    public static string HashPassword(string password)
    {
        // BCrypt.Net generates compatible hashes with Java's BCrypt
        // Uses work factor of 12 (compatible with Java and Python BCrypt)
        return BCrypt.Net.BCrypt.HashPassword(password, workFactor: 12);
    }

    public static bool VerifyPassword(string password, string hash)
    {
        try
        {
            return BCrypt.Net.BCrypt.Verify(password, hash);
        }
        catch
        {
            return false;
        }
    }
}
