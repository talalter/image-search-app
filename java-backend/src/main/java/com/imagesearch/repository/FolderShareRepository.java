package com.imagesearch.repository;

import com.imagesearch.model.entity.FolderShare;
import com.imagesearch.model.entity.Folder;
import com.imagesearch.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for FolderShare entity.
 *
 * Manages folder sharing permissions between users.
 */
@Repository
public interface FolderShareRepository extends JpaRepository<FolderShare, Long> {

    /**
     * Get all folders shared WITH a user.
     * @param sharedWithUser The user who received the shares
     * @return List of folder shares
     */
    List<FolderShare> findBySharedWithUser(User sharedWithUser);

    /**
     * Get all shares created BY a user (folders they shared with others).
     * @param owner The user who owns the shared folders
     * @return List of folder shares
     */
    List<FolderShare> findByOwner(User owner);

    /**
     * Find specific share relationship.
     * @param folder The folder
     * @param owner The folder owner
     * @param sharedWithUser The user the folder is shared with
     * @return Optional containing the share if found
     */
    Optional<FolderShare> findByFolderAndOwnerAndSharedWithUser(
        Folder folder, User owner, User sharedWithUser
    );

    /**
     * Check if a folder is shared with a specific user.
     * @param folder The folder
     * @param sharedWithUser The user to check
     * @return true if folder is shared with user
     */
    boolean existsByFolderAndSharedWithUser(Folder folder, User sharedWithUser);

    /**
     * Find share by folder and shared-with user (without requiring owner).
     * @param folder The folder
     * @param sharedWithUser The user the folder is shared with
     * @return Optional containing the share if found
     */
    Optional<FolderShare> findByFolderAndSharedWithUser(Folder folder, User sharedWithUser);

    /**
     * Delete all shares for a specific folder (when folder is deleted).
     * @param folder The folder
     */
    void deleteByFolder(Folder folder);
}
