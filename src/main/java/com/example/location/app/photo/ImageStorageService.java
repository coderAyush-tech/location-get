package com.example.location.app.photo;

public interface ImageStorageService {
    StoredImage uploadOriginal(String sessionId, byte[] imageBytes, String contentType);

    StoredImage uploadEnhanced(String sessionId, byte[] imageBytes, String contentType);

    ImageData download(String imageUrl);

    void delete(String storageId);
}
