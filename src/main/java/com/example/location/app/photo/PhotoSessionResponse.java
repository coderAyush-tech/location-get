package com.example.location.app.photo;

import java.time.Instant;

public record PhotoSessionResponse(
        String sessionId,
        PhotoSessionStatus status,
        String originalImageUrl,
        String enhancedImageUrl,
        Double latitude,
        Double longitude,
        Double accuracy,
        Instant createdAt,
        boolean canEnhanceAgain
) {
    static PhotoSessionResponse from(PhotoSession session) {
        return new PhotoSessionResponse(
                session.getId(),
                session.getStatus(),
                session.getOriginalImageUrl(),
                session.getEnhancedImageUrl(),
                session.getLatitude(),
                session.getLongitude(),
                session.getLocationAccuracy(),
                session.getCreatedAt(),
                false
        );
    }
}
