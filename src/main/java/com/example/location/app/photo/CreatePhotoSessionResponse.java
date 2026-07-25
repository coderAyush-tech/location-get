package com.example.location.app.photo;

public record CreatePhotoSessionResponse(
        String sessionId,
        PhotoSessionStatus status
) {
}
