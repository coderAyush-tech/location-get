package com.example.location.app.photo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PhotoImageValidatorTests {
    static final byte[] ONE_PIXEL_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
    );

    private PhotoFeatureProperties properties;
    private PhotoImageValidator validator;

    @BeforeEach
    void setUp() {
        properties = new PhotoFeatureProperties();
        validator = new PhotoImageValidator(properties);
    }

    @Test
    void acceptsValidPng() {
        ValidatedImage image = validator.validate(file("photo.png", "image/png", ONE_PIXEL_PNG));

        assertEquals("image/png", image.contentType());
    }

    @Test
    void rejectsEmptyFile() {
        PhotoApiException exception = assertThrows(PhotoApiException.class,
                () -> validator.validate(file("photo.png", "image/png", new byte[0])));

        assertEquals(400, exception.getStatus().value());
    }

    @Test
    void rejectsUnsupportedOrMismatchedFile() {
        PhotoApiException exception = assertThrows(PhotoApiException.class,
                () -> validator.validate(file("photo.gif", "image/gif", "GIF89a".getBytes())));

        assertEquals(415, exception.getStatus().value());
    }

    @Test
    void rejectsOversizedFile() {
        properties.setMaxImageBytes(4);

        PhotoApiException exception = assertThrows(PhotoApiException.class,
                () -> validator.validate(file("photo.png", "image/png", ONE_PIXEL_PNG)));

        assertEquals(413, exception.getStatus().value());
    }

    static MockMultipartFile file(String name, String contentType, byte[] content) {
        return new MockMultipartFile("photo", name, contentType, content);
    }
}
