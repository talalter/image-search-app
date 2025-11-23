package com.imagesearch.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * FolderShare entity for folder sharing permissions.
 *
 * Represents sharing relationships between users and folders.
 * Supports different permission levels (view, edit, etc.).
 */
@Entity
@Table(name = "folder_shares", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"folder_id", "shared_with_user_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FolderShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id", nullable = false)
    private Folder folder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_with_user_id", nullable = false)
    private User sharedWithUser;

    @Column(nullable = false)
    private String permission = "view";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
