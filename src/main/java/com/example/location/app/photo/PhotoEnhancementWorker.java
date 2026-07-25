package com.example.location.app.photo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PhotoEnhancementWorker {
    private static final Logger log = LoggerFactory.getLogger(PhotoEnhancementWorker.class);

    private final PhotoSessionRepository repository;
    private final PhotoSessionStateRepository stateRepository;
    private final ImageStorageService storageService;
    private final GeminiImageService geminiImageService;

    public PhotoEnhancementWorker(
            PhotoSessionRepository repository,
            PhotoSessionStateRepository stateRepository,
            ImageStorageService storageService,
            GeminiImageService geminiImageService
    ) {
        this.repository = repository;
        this.stateRepository = stateRepository;
        this.storageService = storageService;
        this.geminiImageService = geminiImageService;
    }

    public void enhance(String sessionId) {
        StoredImage enhancedStoredImage = null;
        try {
            PhotoSession session = repository.findById(sessionId)
                    .orElseThrow(PhotoSessionNotFoundException::new);
            if (session.getStatus() != PhotoSessionStatus.PROCESSING) {
                log.warn("PHOTO_ENHANCEMENT_SKIPPED sessionId={} reason=invalid_state status={}",
                        sessionId, session.getStatus());
                return;
            }

            log.info("PHOTO_ENHANCEMENT_STARTED sessionId={} model={} promptVersion={}",
                    sessionId, session.getGeminiModel(), session.getPromptVersion());
            ImageData original = storageService.download(session.getOriginalImageUrl());
            EnhancedImage enhanced = geminiImageService.enhance(original);
            enhancedStoredImage = storageService.uploadEnhanced(
                    sessionId,
                    enhanced.bytes(),
                    enhanced.contentType()
            );

            if (!stateRepository.markCompleted(sessionId, enhancedStoredImage)) {
                storageService.delete(enhancedStoredImage.storageId());
                log.warn("PHOTO_ENHANCEMENT_ORPHAN_CLEANED sessionId={} reason=state_changed", sessionId);
                return;
            }
            log.info("PHOTO_ENHANCEMENT_COMPLETED sessionId={} enhancedStorageId={}",
                    sessionId, enhancedStoredImage.storageId());
        } catch (RuntimeException exception) {
            if (enhancedStoredImage != null) {
                storageService.delete(enhancedStoredImage.storageId());
            }
            try {
                stateRepository.markFailed(sessionId, safeFailureCode(exception));
            } catch (RuntimeException stateException) {
                log.error("PHOTO_ENHANCEMENT_FAILURE_STATE_UPDATE_FAILED sessionId={} type={}",
                        sessionId, stateException.getClass().getSimpleName());
            }
            log.error("PHOTO_ENHANCEMENT_FAILED sessionId={} type={}",
                    sessionId, exception.getClass().getSimpleName());
        }
    }

    private String safeFailureCode(RuntimeException exception) {
        if (exception instanceof PhotoApiException photoException) {
            if (photoException.getStatus().value() == 503) {
                return "CONFIGURATION_OR_DEPENDENCY_UNAVAILABLE";
            }
            if (photoException.getStatus().value() == 502) {
                return "UPSTREAM_PROCESSING_FAILED";
            }
        }
        return "PROCESSING_FAILED";
    }
}
