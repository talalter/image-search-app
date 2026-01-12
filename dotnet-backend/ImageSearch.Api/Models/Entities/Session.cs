using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace ImageSearch.Api.Models.Entities;

[Table("sessions")]
public class Session
{
    [Key]
    [MaxLength(255)]
    [Column("token")]
    public string Token { get; set; } = null!;

    [Required]
    [Column("user_id")]
    public long UserId { get; set; }

    [Column("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    [Required]
    [Column("expires_at")]
    public DateTime ExpiresAt { get; set; }

    [Column("last_seen")]
    public DateTime LastSeen { get; set; } = DateTime.UtcNow;

    // Navigation property
    [ForeignKey("UserId")]
    public virtual User User { get; set; } = null!;

    public bool IsExpired() => DateTime.UtcNow > ExpiresAt;
}
