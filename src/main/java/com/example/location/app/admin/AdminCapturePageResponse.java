package com.example.location.app.admin;

import java.util.List;

public record AdminCapturePageResponse(
        List<AdminCaptureItem> content,
        int number,
        int size,
        long totalElements,
        int totalPages,
        AdminCaptureSummary summary
) {
}
