package com.imagesearch.model.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * Request DTO for image search.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchRequest {

    private String token;
    private String query;
    private List<Long> folderIds;
    private Integer topK = 5;
}
