using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace ImageSearch.Api.Models.Entities;

[Table("folder_shares")]
public class FolderShare
{
    [Key]
    [DatabaseGenerated(DatabaseGeneratedOption.Identity)]
    [Column("id")]
    public long Id { get; set; }

    [Required]
    [Column("folder_id")]
    public long FolderId { get; set; }

    [Required]
    [Column("owner_id")]
    public long OwnerId { get; set; }

    [Required]
    [Column("shared_with_user_id")]
    public long SharedWithUserId { get; set; }

    [Required]
    [MaxLength(50)]
    [Column("permission")]
    public string Permission { get; set; } = "view";

    [Column("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    // Navigation properties
    [ForeignKey("FolderId")]
    public virtual Folder Folder { get; set; } = null!;

    [ForeignKey("OwnerId")]
    public virtual User Owner { get; set; } = null!;

    [ForeignKey("SharedWithUserId")]
    public virtual User SharedWithUser { get; set; } = null!;
}
