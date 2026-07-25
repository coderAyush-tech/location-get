package com.example.location.app.photo;

public record ValidatedImage(
        byte[] bytes,
        String contentType
) {
}
