package com.example.location.app.photo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PhotoSessionDomainTests {

    @Test
    void createsOpaqueSessionInCreatedState() {
        PhotoSession session = PhotoSession.create();

        assertNotNull(session.getId());
        assertEquals(36, session.getId().length());
        assertEquals(PhotoSessionStatus.CREATED, session.getStatus());
    }

    @Test
    void preventsEnhancementWithoutOriginalPhoto() {
        PhotoSession session = PhotoSession.create();

        assertThrows(InvalidPhotoSessionStateException.class,
                () -> session.markProcessing("model", "prompt"));
    }

    @Test
    void preventsDuplicateProcessingAndCompletesWithEnhancedUrl() {
        PhotoSession session = uploadedSession();
        session.markProcessing("gemini-3.1-flash-image", ImageEnhancementPrompt.VERSION);

        assertThrows(InvalidPhotoSessionStateException.class,
                () -> session.markProcessing("model", "prompt"));

        session.markCompleted(new StoredImage(
                "https://res.cloudinary.com/demo/enhanced.jpg",
                "enhanced-id",
                "image/jpeg"
        ));

        assertEquals(PhotoSessionStatus.COMPLETED, session.getStatus());
        assertEquals("https://res.cloudinary.com/demo/enhanced.jpg", session.getEnhancedImageUrl());
    }

    @Test
    void marksProcessingFailureWithSafeMetadata() {
        PhotoSession session = uploadedSession();
        session.markProcessing("model", "prompt");

        session.markFailed("UPSTREAM_PROCESSING_FAILED");

        assertEquals(PhotoSessionStatus.FAILED, session.getStatus());
        assertEquals("UPSTREAM_PROCESSING_FAILED", session.getErrorMessage());
    }

    static PhotoSession uploadedSession() {
        PhotoSession session = PhotoSession.create();
        session.attachOriginal(
                new StoredImage(
                        "https://res.cloudinary.com/demo/original.png",
                        "original-id",
                        "image/png"
                ),
                28.6139,
                77.2090,
                15.0
        );
        return session;
    }
}
