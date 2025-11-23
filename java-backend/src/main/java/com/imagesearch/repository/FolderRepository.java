package com.imagesearch.repository;

import com.imagesearch.model.entity.Folder;
import com.imagesearch.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Folder entity.
 *
 * Handles folder CRUD operations and ownership queries.
 */
@Repository
public interface FolderRepository extends JpaRepository<Folder, Long> {

    /**
     * Get all folders owned by a user.
     * @param user The user who owns the folders
     * @return List of folders
     */
    List<Folder> findByUser(User user);

    /**
     * Find folder by user and folder name.
     * @param user The folder owner
     * @param folderName The folder name
     * @return Optional containing the folder if found
     */
    Optional<Folder> findByUserAndFolderName(User user, String folderName);

    /**
     * Check if a folder exists with given id and user (for authorization).
     * @param id Folder ID
     * @param user Folder owner
     * @return true if folder exists and belongs to user
     */
    boolean existsByIdAndUser(Long id, User user);

    /**
     * Get all folders accessible by a user (owned + shared with them).
     * Uses LEFT JOIN to include both owned folders and shared folders.
     *
     * @param userId The user ID
     * @return List of folders (owned and shared)
     */
    @Query("SELECT DISTINCT f FROM Folder f " +
           "LEFT JOIN FolderShare fs ON fs.folder = f " +
           "WHERE f.user.id = :userId OR fs.sharedWithUser.id = :userId")
    List<Folder> findAllAccessibleFolders(@Param("userId") Long userId);
}
