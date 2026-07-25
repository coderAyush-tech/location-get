package com.example.location.app.photo;

public record StoredImage(
        String url,
        String storageId,
        String contentType
) {
}
