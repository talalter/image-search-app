package com.imagesearch.search.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to create a new Lucene index for a folder
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateIndexRequest {

    /**
     * User ID owning the folder
     */
    @JsonProperty("user_id")
    private Long userId;

    /**
     * Folder ID to create index for
     */
    @JsonProperty("folder_id")
    private Long folderId;
}
