package com.example.location.app.photo;

public record PhotoEnhanceResponse(
        String sessionId,
        PhotoSessionStatus status
) {
}
