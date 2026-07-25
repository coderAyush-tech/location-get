package com.example.location.app.photo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PhotoSessionService {
    private static final Logger log = LoggerFactory.getLogger(PhotoSessionService.class);

    private final PhotoSessionRepository repository;
    private final PhotoSessionStateRepository stateRepository;
    private final PhotoImageValidator imageValidator;
    private final ImageStorageService storageService;
    private final PhotoEnhancementDispatcher enhancementDispatcher;
    private final PhotoFeatureProperties properties;

    public PhotoSessionService(
            PhotoSessionRepository repository,
            PhotoSessionStateRepository stateRepository,
            PhotoImageValidator imageValidator,
            ImageStorageService storageService,
            PhotoEnhancementDispatcher enhancementDispatcher,
            PhotoFeatureProperties properties
    ) {
        this.repository = repository;
        this.stateRepository = stateRepository;
        this.imageValidator = imageValidator;
        this.storageService = storageService;
        this.enhancementDispatcher = enhancementDispatcher;
        this.properties = properties;
    }

    public CreatePhotoSessionResponse create() {
        try {
            PhotoSession session = repository.save(PhotoSession.create());
            log.info("PHOTO_SESSION_CREATED sessionId={}", session.getId());
            return new CreatePhotoSessionResponse(session.getId(), session.getStatus());
        } catch (DataAccessException exception) {
            log.error("PHOTO_SESSION_CREATE_FAILED type={}", exception.getClass().getSimpleName());
            throw databaseUnavailable(exception);
        }
    }

    public PhotoUploadResponse uploadOriginal(
            String sessionId,
            MultipartFile photo,
            Double latitude,
            Double longitude,
            Double accuracy
    ) {
        PhotoSession session = findRequired(sessionId);
        session.ensurePhotoCanBeUploaded();
        validateLocation(latitude, longitude, accuracy);
        ValidatedImage validatedImage = imageValidator.validate(photo);

        StoredImage storedImage;
        try {
            storedImage = storageService.uploadOriginal(
                    sessionId,
                    validatedImage.bytes(),
                    validatedImage.contentType()
            );
        } catch (RuntimeException exception) {
            log.error("PHOTO_ORIGINAL_UPLOAD_FAILED sessionId={} type={}",
                    sessionId, exception.getClass().getSimpleName());
            throw exception;
        }
        try {
            session.attachOriginal(storedImage, latitude, longitude, accuracy);
            PhotoSession saved = repository.save(session);
            log.info("PHOTO_ORIGINAL_UPLOAD_SUCCESS sessionId={} storageId={}",
                    sessionId, storedImage.storageId());
            return new PhotoUploadResponse(
                    saved.getId(),
                    saved.getStatus(),
                    saved.getOriginalImageUrl(),
                    saved.getLatitude(),
                    saved.getLongitude(),
                    saved.getLocationAccuracy()
            );
        } catch (OptimisticLockingFailureException exception) {
            storageService.delete(storedImage.storageId());
            log.warn("PHOTO_ORIGINAL_UPLOAD_FAILED sessionId={} reason=concurrent_upload", sessionId);
            throw new InvalidPhotoSessionStateException(
                    "Original photo was already uploaded by another request.");
        } catch (DataAccessException exception) {
            storageService.delete(storedImage.storageId());
            log.error("PHOTO_ORIGINAL_UPLOAD_FAILED sessionId={} reason=database", sessionId);
            throw databaseUnavailable(exception);
        } catch (RuntimeException exception) {
            storageService.delete(storedImage.storageId());
            log.error("PHOTO_ORIGINAL_UPLOAD_FAILED sessionId={} type={}",
                    sessionId, exception.getClass().getSimpleName());
            throw exception;
        }
    }

    public PhotoEnhanceResponse requestEnhancement(String sessionId) {
        PhotoSession processing = stateRepository.markProcessing(
                sessionId,
                properties.getGemini().getModel(),
                ImageEnhancementPrompt.VERSION
        );
        try {
            enhancementDispatcher.dispatch(sessionId);
        } catch (TaskRejectedException exception) {
            stateRepository.markFailed(sessionId, "PROCESSING_CAPACITY_EXCEEDED");
            throw new PhotoApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Photo processing capacity is temporarily full.", exception);
        }
        log.info("PHOTO_ENHANCEMENT_QUEUED sessionId={}", sessionId);
        return new PhotoEnhanceResponse(processing.getId(), processing.getStatus());
    }

    public PhotoSessionResponse get(String sessionId) {
        return PhotoSessionResponse.from(findRequired(sessionId));
    }

    private PhotoSession findRequired(String sessionId) {
        try {
            return repository.findById(sessionId).orElseThrow(PhotoSessionNotFoundException::new);
        } catch (PhotoApiException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw databaseUnavailable(exception);
        }
    }

    private void validateLocation(Double latitude, Double longitude, Double accuracy) {
        if ((latitude == null) != (longitude == null)) {
            throw new PhotoApiException(HttpStatus.BAD_REQUEST,
                    "Latitude and longitude must be provided together.");
        }
        if (accuracy != null && latitude == null) {
            throw new PhotoApiException(HttpStatus.BAD_REQUEST,
                    "Accuracy requires latitude and longitude.");
        }
        if (latitude != null && (!Double.isFinite(latitude) || latitude < -90 || latitude > 90)) {
            throw new PhotoApiException(HttpStatus.BAD_REQUEST,
                    "Latitude must be between -90 and 90.");
        }
        if (longitude != null && (!Double.isFinite(longitude) || longitude < -180 || longitude > 180)) {
            throw new PhotoApiException(HttpStatus.BAD_REQUEST,
                    "Longitude must be between -180 and 180.");
        }
        if (accuracy != null && (!Double.isFinite(accuracy)
                || accuracy < 0
                || accuracy > properties.getMaxLocationAccuracy())) {
            throw new PhotoApiException(HttpStatus.BAD_REQUEST,
                    "Accuracy must be non-negative and within the configured limit.");
        }
    }

    private PhotoApiException databaseUnavailable(DataAccessException exception) {
        return new PhotoApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "Photo session storage is temporarily unavailable.", exception);
    }
}
