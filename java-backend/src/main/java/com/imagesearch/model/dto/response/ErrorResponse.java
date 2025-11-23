package com.imagesearch.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Error response DTO for consistent error handling.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    private String detail;
    private int status;
    private LocalDateTime timestamp;
    private String path;

    public ErrorResponse(String detail, int status) {
        this.detail = detail;
        this.status = status;
        this.timestamp = LocalDateTime.now();
    }
}
