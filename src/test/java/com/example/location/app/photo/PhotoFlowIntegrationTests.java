package com.example.location.app.photo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhotoFlowIntegrationTests {
    @Mock
    private PhotoSessionRepository repository;
    @Mock
    private PhotoSessionStateRepository stateRepository;
    @Mock
    private ImageStorageService storageService;
    @Mock
    private GeminiImageService geminiImageService;
    @Mock
    private PhotoEnhancementDispatcher dispatcher;

    private final Map<String, PhotoSession> sessions = new ConcurrentHashMap<>();
    private PhotoSessionService sessionService;
    private PhotoEnhancementWorker worker;

    @BeforeEach
    void setUp() {
        PhotoFeatureProperties properties = new PhotoFeatureProperties();
        sessionService = new PhotoSessionService(
                repository,
                stateRepository,
                new PhotoImageValidator(properties),
                storageService,
                dispatcher,
                properties
        );
        worker = new PhotoEnhancementWorker(
                repository,
                stateRepository,
                storageService,
                geminiImageService
        );

        when(repository.save(any(PhotoSession.class))).thenAnswer(invocation -> {
            PhotoSession session = invocation.getArgument(0);
            sessions.put(session.getId(), session);
            return session;
        });
        when(repository.findById(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(sessions.get(invocation.getArgument(0))));
        when(stateRepository.markProcessing(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            PhotoSession session = sessions.get(invocation.getArgument(0));
            session.markProcessing(invocation.getArgument(1), invocation.getArgument(2));
            return session;
        });
        when(stateRepository.markCompleted(anyString(), any(StoredImage.class))).thenAnswer(invocation -> {
            PhotoSession session = sessions.get(invocation.getArgument(0));
            session.markCompleted(invocation.getArgument(1));
            return true;
        });
    }

    @Test
    void completesCreateUploadEnhanceAndPollFlowWithSeparateAssets() {
        StoredImage original = new StoredImage(
                "https://res.cloudinary.com/demo/original.png",
                "original-storage-id",
                "image/png"
        );
        StoredImage enhanced = new StoredImage(
                "https://res.cloudinary.com/demo/enhanced.jpg",
                "enhanced-storage-id",
                "image/jpeg"
        );
        when(storageService.uploadOriginal(anyString(), any(byte[].class), anyString())).thenReturn(original);
        when(storageService.download(original.url()))
                .thenReturn(new ImageData(PhotoImageValidatorTests.ONE_PIXEL_PNG, "image/png"));
        when(geminiImageService.enhance(any(ImageData.class)))
                .thenReturn(new EnhancedImage(new byte[]{7, 8, 9}, "image/jpeg"));
        when(storageService.uploadEnhanced(anyString(), any(byte[].class), anyString())).thenReturn(enhanced);

        CreatePhotoSessionResponse created = sessionService.create();
        PhotoUploadResponse uploaded = sessionService.uploadOriginal(
                created.sessionId(),
                PhotoImageValidatorTests.file(
                        "photo.png",
                        "image/png",
                        PhotoImageValidatorTests.ONE_PIXEL_PNG
                ),
                28.6139,
                77.2090,
                10.0
        );
        PhotoEnhanceResponse processing = sessionService.requestEnhancement(created.sessionId());

        assertEquals(PhotoSessionStatus.CREATED, created.status());
        assertEquals(PhotoSessionStatus.PHOTO_UPLOADED, uploaded.status());
        assertEquals(PhotoSessionStatus.PROCESSING, processing.status());
        verify(dispatcher).dispatch(created.sessionId());

        worker.enhance(created.sessionId());
        PhotoSessionResponse completed = sessionService.get(created.sessionId());

        assertEquals(PhotoSessionStatus.COMPLETED, completed.status());
        assertEquals(original.url(), completed.originalImageUrl());
        assertEquals(enhanced.url(), completed.enhancedImageUrl());
        assertNotNull(completed.createdAt());
        assertNotEquals(original.storageId(), enhanced.storageId());
    }
}
