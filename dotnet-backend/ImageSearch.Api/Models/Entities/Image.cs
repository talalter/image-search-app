using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace ImageSearch.Api.Models.Entities;

[Table("images")]
public class Image
{
    [Key]
    [DatabaseGenerated(DatabaseGeneratedOption.Identity)]
    [Column("id")]
    public long Id { get; set; }

    [Required]
    [Column("user_id")]
    public long UserId { get; set; }

    [Required]
    [Column("folder_id")]
    public long FolderId { get; set; }

    [Required]
    [MaxLength(500)]
    [Column("filepath")]
    public string Filepath { get; set; } = null!;

    [Column("uploaded_at")]
    public DateTime UploadedAt { get; set; } = DateTime.UtcNow;

    // Navigation properties
    [ForeignKey("UserId")]
    public virtual User User { get; set; } = null!;

    [ForeignKey("FolderId")]
    public virtual Folder Folder { get; set; } = null!;
}
