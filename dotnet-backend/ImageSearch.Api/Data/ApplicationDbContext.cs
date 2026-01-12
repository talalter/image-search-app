using ImageSearch.Api.Models.Entities;
using Microsoft.EntityFrameworkCore;

namespace ImageSearch.Api.Data;

public class ApplicationDbContext : DbContext
{
    public ApplicationDbContext(DbContextOptions<ApplicationDbContext> options)
        : base(options) { }

    public DbSet<User> Users { get; set; } = null!;
    public DbSet<Session> Sessions { get; set; } = null!;
    public DbSet<Folder> Folders { get; set; } = null!;
    public DbSet<Image> Images { get; set; } = null!;
    public DbSet<FolderShare> FolderShares { get; set; } = null!;

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        base.OnModelCreating(modelBuilder);

        // User entity configuration
        modelBuilder.Entity<User>(entity =>
        {
            entity.HasIndex(e => e.Username).IsUnique();
            entity.Property(e => e.CreatedAt).HasDefaultValueSql("CURRENT_TIMESTAMP");
        });

        // Session entity configuration
        modelBuilder.Entity<Session>(entity =>
        {
            entity.HasOne(s => s.User)
                  .WithMany(u => u.Sessions)
                  .HasForeignKey(s => s.UserId)
                  .OnDelete(DeleteBehavior.Cascade);

            entity.Property(e => e.CreatedAt).HasDefaultValueSql("CURRENT_TIMESTAMP");
            entity.Property(e => e.LastSeen).HasDefaultValueSql("CURRENT_TIMESTAMP");
        });

        // Folder entity configuration
        modelBuilder.Entity<Folder>(entity =>
        {
            entity.HasIndex(e => new { e.UserId, e.FolderName }).IsUnique();

            entity.HasOne(f => f.User)
                  .WithMany(u => u.Folders)
                  .HasForeignKey(f => f.UserId)
                  .OnDelete(DeleteBehavior.Cascade);

            entity.Property(e => e.CreatedAt).HasDefaultValueSql("CURRENT_TIMESTAMP");
        });

        // Image entity configuration
        modelBuilder.Entity<Image>(entity =>
        {
            entity.HasOne(i => i.User)
                  .WithMany(u => u.Images)
                  .HasForeignKey(i => i.UserId)
                  .OnDelete(DeleteBehavior.Cascade);

            entity.HasOne(i => i.Folder)
                  .WithMany(f => f.Images)
                  .HasForeignKey(i => i.FolderId)
                  .OnDelete(DeleteBehavior.Cascade);

            entity.Property(e => e.UploadedAt).HasDefaultValueSql("CURRENT_TIMESTAMP");
        });

        // FolderShare entity configuration
        modelBuilder.Entity<FolderShare>(entity =>
        {
            entity.HasIndex(e => new { e.FolderId, e.SharedWithUserId }).IsUnique();

            entity.HasOne(fs => fs.Folder)
                  .WithMany(f => f.FolderShares)
                  .HasForeignKey(fs => fs.FolderId)
                  .OnDelete(DeleteBehavior.Cascade);

            entity.HasOne(fs => fs.Owner)
                  .WithMany()
                  .HasForeignKey(fs => fs.OwnerId)
                  .OnDelete(DeleteBehavior.Restrict);

            entity.HasOne(fs => fs.SharedWithUser)
                  .WithMany()
                  .HasForeignKey(fs => fs.SharedWithUserId)
                  .OnDelete(DeleteBehavior.Cascade);

            entity.Property(e => e.Permission).HasDefaultValue("view");
            entity.Property(e => e.CreatedAt).HasDefaultValueSql("CURRENT_TIMESTAMP");
        });
    }
}
