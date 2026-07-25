package com.example.location.app.photo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhotoEnhancementWorkerTests {
    @Mock
    private PhotoSessionRepository repository;
    @Mock
    private PhotoSessionStateRepository stateRepository;
    @Mock
    private ImageStorageService storageService;
    @Mock
    private GeminiImageService geminiImageService;

    private PhotoEnhancementWorker worker;
    private PhotoSession processing;

    @BeforeEach
    void setUp() {
        worker = new PhotoEnhancementWorker(repository, stateRepository, storageService, geminiImageService);
        processing = PhotoSessionDomainTests.uploadedSession();
        processing.markProcessing("gemini-3.1-flash-image", ImageEnhancementPrompt.VERSION);
        when(repository.findById(processing.getId())).thenReturn(Optional.of(processing));
    }

    @Test
    void marksCompletedAfterGeminiAndSeparateEnhancedUpload() {
        when(storageService.download(processing.getOriginalImageUrl()))
                .thenReturn(new ImageData(new byte[]{1, 2, 3}, "image/png"));
        when(geminiImageService.enhance(any(ImageData.class)))
                .thenReturn(new EnhancedImage(new byte[]{4, 5, 6}, "image/jpeg"));
        StoredImage enhanced = new StoredImage(
                "https://res.cloudinary.com/demo/enhanced.jpg",
                "enhanced-id",
                "image/jpeg"
        );
        when(storageService.uploadEnhanced(
                org.mockito.ArgumentMatchers.eq(processing.getId()),
                any(byte[].class),
                org.mockito.ArgumentMatchers.eq("image/jpeg")))
                .thenReturn(enhanced);
        when(stateRepository.markCompleted(processing.getId(), enhanced)).thenReturn(true);

        worker.enhance(processing.getId());

        verify(stateRepository).markCompleted(processing.getId(), enhanced);
        verify(stateRepository, never()).markFailed(anyString(), anyString());
    }

    @Test
    void marksFailedWhenGeminiFails() {
        when(storageService.download(processing.getOriginalImageUrl()))
                .thenReturn(new ImageData(new byte[]{1}, "image/png"));
        when(geminiImageService.enhance(any(ImageData.class)))
                .thenThrow(new PhotoApiException(
                        org.springframework.http.HttpStatus.BAD_GATEWAY,
                        "AI enhancement provider returned an error."
                ));

        worker.enhance(processing.getId());

        verify(stateRepository).markFailed(processing.getId(), "UPSTREAM_PROCESSING_FAILED");
        verify(storageService, never()).uploadEnhanced(anyString(), any(), anyString());
    }

    @Test
    void marksFailedWhenEnhancedStorageFails() {
        when(storageService.download(processing.getOriginalImageUrl()))
                .thenReturn(new ImageData(new byte[]{1}, "image/png"));
        when(geminiImageService.enhance(any(ImageData.class)))
                .thenReturn(new EnhancedImage(new byte[]{2}, "image/jpeg"));
        when(storageService.uploadEnhanced(anyString(), any(), anyString()))
                .thenThrow(new PhotoApiException(
                        org.springframework.http.HttpStatus.BAD_GATEWAY,
                        "Image storage is temporarily unavailable."
                ));

        worker.enhance(processing.getId());

        verify(stateRepository).markFailed(processing.getId(), "UPSTREAM_PROCESSING_FAILED");
    }
}
