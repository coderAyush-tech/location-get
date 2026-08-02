package com.example.location.app.admin;

import java.time.Instant;

record AdminCaptureMetadata(
        String id,
        String originalFilename,
        String contentType,
        long sizeBytes,
        Double latitude,
        Double longitude,
        Double accuracy,
        String locationSource,
        String address,
        String clientIp,
        Instant savedAt
) {
}
