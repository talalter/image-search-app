using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace ImageSearch.Api.Models.Entities;

[Table("folders")]
public class Folder
{
    [Key]
    [DatabaseGenerated(DatabaseGeneratedOption.Identity)]
    [Column("id")]
    public long Id { get; set; }

    [Required]
    [Column("user_id")]
    public long UserId { get; set; }

    [Required]
    [MaxLength(255)]
    [Column("folder_name")]
    public string FolderName { get; set; } = null!;

    [Column("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    // Navigation properties
    [ForeignKey("UserId")]
    public virtual User User { get; set; } = null!;

    public virtual ICollection<Image> Images { get; set; } = new List<Image>();
    public virtual ICollection<FolderShare> FolderShares { get; set; } = new List<FolderShare>();
}
