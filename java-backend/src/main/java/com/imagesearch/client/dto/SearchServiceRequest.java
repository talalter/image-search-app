package com.imagesearch.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

/**
 * Request DTO for Python search microservice.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchServiceRequest {
    private Long userId;
    private String query;
    private List<Long> folderIds;
    private Map<Long, Long> folderOwnerMap; // folder_id -> owner_user_id
    private Integer topK;
}
