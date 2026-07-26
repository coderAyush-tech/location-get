package com.example.location.app.photo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DirectPhotoEnhancementServiceTests {
    @Mock
    private PhotoImageValidator imageValidator;

    @Mock
    private GeminiImageService geminiImageService;

    private DirectPhotoEnhancementService service;

    @BeforeEach
    void setUp() {
        service = new DirectPhotoEnhancementService(imageValidator, geminiImageService);
    }

    @Test
    void sendsValidatedPhotoDirectlyToGemini() {
        byte[] original = new byte[]{1, 2, 3};
        byte[] enhanced = new byte[]{9, 8, 7};
        MockMultipartFile photo = new MockMultipartFile(
                "photo",
                "photo.png",
                "image/png",
                original
        );
        when(imageValidator.validate(photo))
                .thenReturn(new ValidatedImage(original, "image/png"));
        when(geminiImageService.enhance(any()))
                .thenReturn(new EnhancedImage(enhanced, "image/jpeg"));

        EnhancedImage response = service.enhance(photo);

        assertArrayEquals(enhanced, response.bytes());
        assertEquals("image/jpeg", response.contentType());
    }

    @Test
    void preservesSafeGeminiFailure() {
        MockMultipartFile photo = new MockMultipartFile(
                "photo",
                "photo.png",
                "image/png",
                new byte[]{1}
        );
        when(imageValidator.validate(photo))
                .thenReturn(new ValidatedImage(new byte[]{1}, "image/png"));
        when(geminiImageService.enhance(any()))
                .thenThrow(new PhotoApiException(
                        HttpStatus.BAD_GATEWAY,
                        "AI enhancement provider returned an error."
                ));

        PhotoApiException exception = assertThrows(
                PhotoApiException.class,
                () -> service.enhance(photo)
        );

        assertEquals(502, exception.getStatus().value());
        assertEquals("AI enhancement provider returned an error.", exception.getMessage());
    }

    @Test
    void rejectsInvalidGeminiImageResponse() {
        MockMultipartFile photo = new MockMultipartFile(
                "photo",
                "photo.png",
                "image/png",
                new byte[]{1}
        );
        when(imageValidator.validate(photo))
                .thenReturn(new ValidatedImage(new byte[]{1}, "image/png"));
        when(geminiImageService.enhance(any()))
                .thenReturn(new EnhancedImage(new byte[0], "image/jpeg"));

        PhotoApiException exception = assertThrows(
                PhotoApiException.class,
                () -> service.enhance(photo)
        );

        assertEquals(502, exception.getStatus().value());
    }
}
