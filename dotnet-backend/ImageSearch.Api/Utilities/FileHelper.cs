namespace ImageSearch.Api.Utilities;

public static class FileHelper
{
    private static readonly string[] AllowedExtensions = { ".png", ".jpg", ".jpeg" };

    public static bool IsValidImageExtension(string filename)
    {
        var extension = Path.GetExtension(filename)?.ToLowerInvariant();
        return extension != null && AllowedExtensions.Contains(extension);
    }

    public static string GetUploadPath(long userId, long folderId)
    {
        // Returns: {project_root}/data/uploads/images/{userId}/{folderId}/
        var currentDir = Directory.GetCurrentDirectory();
        var projectRoot = Directory.GetParent(currentDir)?.FullName ?? currentDir;
        return Path.Combine(projectRoot, "data", "uploads", "images", userId.ToString(), folderId.ToString());
    }

    public static string GetRelativeFilepath(long userId, long folderId, string filename)
    {
        // Returns: images/{userId}/{folderId}/{filename}
        return $"images/{userId}/{folderId}/{filename}";
    }

    public static async Task<string> SaveFileAsync(IFormFile file, string uploadPath, string filename)
    {
        Directory.CreateDirectory(uploadPath);
        var filePath = Path.Combine(uploadPath, filename);

        using var stream = new FileStream(filePath, FileMode.Create);
        await file.CopyToAsync(stream);

        return filePath;
    }

    public static void DeleteDirectory(string path)
    {
        if (Directory.Exists(path))
        {
            Directory.Delete(path, recursive: true);
        }
    }
}
