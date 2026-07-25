package com.example.location.app.photo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static com.example.location.app.photo.PhotoImageValidatorTests.ONE_PIXEL_PNG;
import static com.example.location.app.photo.PhotoImageValidatorTests.file;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhotoSessionServiceTests {
    @Mock
    private PhotoSessionRepository repository;
    @Mock
    private PhotoSessionStateRepository stateRepository;
    @Mock
    private ImageStorageService storageService;
    @Mock
    private PhotoEnhancementDispatcher dispatcher;

    private PhotoSessionService service;

    @BeforeEach
    void setUp() {
        PhotoFeatureProperties properties = new PhotoFeatureProperties();
        PhotoImageValidator validator = new PhotoImageValidator(properties);
        service = new PhotoSessionService(
                repository,
                stateRepository,
                validator,
                storageService,
                dispatcher,
                properties
        );
    }

    @Test
    void createsSessionWithStableResponseField() {
        when(repository.save(any(PhotoSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreatePhotoSessionResponse response = service.create();

        assertEquals(PhotoSessionStatus.CREATED, response.status());
        assertEquals(36, response.sessionId().length());
    }

    @Test
    void uploadsValidPhotoAndStoresOptionalLocation() {
        PhotoSession session = PhotoSession.create();
        when(repository.findById(session.getId())).thenReturn(Optional.of(session));
        when(storageService.uploadOriginal(anyString(), any(byte[].class), anyString()))
                .thenReturn(new StoredImage(
                        "https://res.cloudinary.com/demo/original.png",
                        "original-id",
                        "image/png"
                ));
        when(repository.save(any(PhotoSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PhotoUploadResponse response = service.uploadOriginal(
                session.getId(),
                file("photo.png", "image/png", ONE_PIXEL_PNG),
                28.6139,
                77.2090,
                12.5
        );

        assertEquals(PhotoSessionStatus.PHOTO_UPLOADED, response.status());
        assertEquals(28.6139, response.latitude());
        assertEquals(77.2090, response.longitude());
        assertEquals(12.5, response.accuracy());
    }

    @Test
    void rejectsInvalidLatitudeBeforeStorageUpload() {
        PhotoSession session = PhotoSession.create();
        when(repository.findById(session.getId())).thenReturn(Optional.of(session));

        PhotoApiException exception = assertThrows(PhotoApiException.class,
                () -> service.uploadOriginal(
                        session.getId(),
                        file("photo.png", "image/png", ONE_PIXEL_PNG),
                        91.0,
                        77.0,
                        10.0
                ));

        assertEquals(400, exception.getStatus().value());
        verify(storageService, never()).uploadOriginal(anyString(), any(), anyString());
    }

    @Test
    void rejectsInvalidLongitude() {
        PhotoSession session = PhotoSession.create();
        when(repository.findById(session.getId())).thenReturn(Optional.of(session));

        PhotoApiException exception = assertThrows(PhotoApiException.class,
                () -> service.uploadOriginal(
                        session.getId(),
                        file("photo.png", "image/png", ONE_PIXEL_PNG),
                        28.0,
                        181.0,
                        10.0
                ));

        assertEquals(400, exception.getStatus().value());
    }

    @Test
    void rejectsNegativeAccuracy() {
        PhotoSession session = PhotoSession.create();
        when(repository.findById(session.getId())).thenReturn(Optional.of(session));

        PhotoApiException exception = assertThrows(PhotoApiException.class,
                () -> service.uploadOriginal(
                        session.getId(),
                        file("photo.png", "image/png", ONE_PIXEL_PNG),
                        28.0,
                        77.0,
                        -1.0
                ));

        assertEquals(400, exception.getStatus().value());
    }

    @Test
    void queuesValidEnhancementAndReturnsProcessing() {
        PhotoSession processing = PhotoSessionDomainTests.uploadedSession();
        processing.markProcessing("gemini-3.1-flash-image", ImageEnhancementPrompt.VERSION);
        when(stateRepository.markProcessing(anyString(), anyString(), anyString())).thenReturn(processing);

        PhotoEnhanceResponse response = service.requestEnhancement(processing.getId());

        assertEquals(PhotoSessionStatus.PROCESSING, response.status());
        verify(dispatcher).dispatch(processing.getId());
    }

    @Test
    void rejectsEnhancementWithoutOriginalPhoto() {
        when(stateRepository.markProcessing(anyString(), anyString(), anyString()))
                .thenThrow(new InvalidPhotoSessionStateException(
                        "An original photo must be uploaded before enhancement."));

        assertThrows(InvalidPhotoSessionStateException.class,
                () -> service.requestEnhancement("missing-photo"));
        verify(dispatcher, never()).dispatch(anyString());
    }

    @Test
    void rejectsDuplicateProcessingEnhancement() {
        when(stateRepository.markProcessing(anyString(), anyString(), anyString()))
                .thenThrow(new InvalidPhotoSessionStateException(
                        "This photo session is already being processed."));

        PhotoApiException exception = assertThrows(PhotoApiException.class,
                () -> service.requestEnhancement("processing-session"));

        assertEquals(409, exception.getStatus().value());
    }

    @Test
    void getsSanitizedSessionWithOriginalAndEnhancedUrls() {
        PhotoSession session = PhotoSessionDomainTests.uploadedSession();
        session.markProcessing("model", "prompt");
        session.markCompleted(new StoredImage(
                "https://res.cloudinary.com/demo/enhanced.jpg",
                "enhanced-id",
                "image/jpeg"
        ));
        when(repository.findById(session.getId())).thenReturn(Optional.of(session));

        PhotoSessionResponse response = service.get(session.getId());

        assertEquals(session.getId(), response.sessionId());
        assertEquals(PhotoSessionStatus.COMPLETED, response.status());
        assertEquals("https://res.cloudinary.com/demo/original.png", response.originalImageUrl());
        assertEquals("https://res.cloudinary.com/demo/enhanced.jpg", response.enhancedImageUrl());
        assertEquals(false, response.canEnhanceAgain());
    }
}
