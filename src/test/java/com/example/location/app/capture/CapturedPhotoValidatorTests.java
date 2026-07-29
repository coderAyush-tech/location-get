package com.example.location.app.capture;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CapturedPhotoValidatorTests {
    static final byte[] ONE_PIXEL_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
    );

    private final CapturedPhotoValidator validator = new CapturedPhotoValidator(1024, 100);

    @Test
    void acceptsValidCameraPhoto() {
        ValidatedCapture validated = validator.validate(file("camera.png", "image/png", ONE_PIXEL_PNG));

        assertEquals("image/png", validated.contentType());
        assertArrayEquals(ONE_PIXEL_PNG, validated.bytes());
    }

    @Test
    void rejectsUnsupportedAndOversizedFiles() {
        assertThrows(CaptureApiException.class,
                () -> validator.validate(file("camera.txt", "text/plain", "not-an-image".getBytes())));

        CapturedPhotoValidator tinyLimit = new CapturedPhotoValidator(4, 100);
        CaptureApiException exception = assertThrows(CaptureApiException.class,
                () -> tinyLimit.validate(file("camera.png", "image/png", ONE_PIXEL_PNG)));
        assertEquals(413, exception.getStatus().value());
    }

    static MockMultipartFile file(String name, String contentType, byte[] bytes) {
        return new MockMultipartFile("photo", name, contentType, bytes);
    }
}
