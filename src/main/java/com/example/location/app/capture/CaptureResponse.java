package com.example.location.app.capture;

import java.time.Instant;

public record CaptureResponse(
        String id,
        boolean saved,
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
    static CaptureResponse from(CapturedPhoto capturedPhoto) {
        return new CaptureResponse(
                capturedPhoto.getId(),
                true,
                capturedPhoto.getContentType(),
                capturedPhoto.getSizeBytes(),
                capturedPhoto.getLatitude(),
                capturedPhoto.getLongitude(),
                capturedPhoto.getAccuracy(),
                capturedPhoto.getLocationSource(),
                capturedPhoto.getAddress(),
                capturedPhoto.getClientIp(),
                capturedPhoto.getSavedAt()
        );
    }
}
