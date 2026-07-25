package com.example.location.app.photo;

public record PhotoUploadResponse(
        String sessionId,
        PhotoSessionStatus status,
        String originalImageUrl,
        Double latitude,
        Double longitude,
        Double accuracy
) {
}
