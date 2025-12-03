package com.imagesearch.repository;

import com.imagesearch.model.entity.Image;
import com.imagesearch.model.entity.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Set;

/**
 * Repository interface for Image entity.
 *
 * Handles image metadata storage and retrieval.
 */
@Repository
public interface ImageRepository extends JpaRepository<Image, Long> {

    /**
     * Get all images in a folder.
     * @param folder The folder containing the images
     * @return List of images
     */
    List<Image> findByFolder(Folder folder);

    /**
     * Delete all images in a folder (cascade when folder deleted).
     * @param folder The folder whose images to delete
     */
    void deleteByFolder(Folder folder);

    /**
     * Count images in a folder.
     * @param folder The folder
     * @return Number of images
     */
    long countByFolder(Folder folder);

    /**
     * Batch lookup images by IDs for search result enrichment.
     * This performs a single database query instead of N queries.
     *
     * @param imageIds Set of image IDs to retrieve
     * @return List of images (may be fewer than requested if some IDs don't exist)
     */
    @Query("SELECT i FROM Image i WHERE i.id IN :imageIds")
    List<Image> findAllByIdIn(@Param("imageIds") Set<Long> imageIds);
}
