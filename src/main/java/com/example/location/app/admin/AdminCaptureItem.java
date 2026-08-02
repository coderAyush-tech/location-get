package com.example.location.app.admin;

import java.time.Instant;

public record AdminCaptureItem(
        String id,
        boolean saved,
        String fileName,
        String contentType,
        long fileSizeBytes,
        Integer width,
        Integer height,
        Instant createdAt,
        Double latitude,
        Double longitude,
        Double accuracy,
        String locationSource,
        String ipAddress,
        String address,
        String city,
        String region,
        String country,
        String userAgent,
        boolean photoAvailable
) {
}
