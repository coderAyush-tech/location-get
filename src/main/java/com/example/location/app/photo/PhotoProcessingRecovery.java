package com.example.location.app.photo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class PhotoProcessingRecovery {
    private static final Logger log = LoggerFactory.getLogger(PhotoProcessingRecovery.class);
    private final PhotoSessionStateRepository stateRepository;

    public PhotoProcessingRecovery(PhotoSessionStateRepository stateRepository) {
        this.stateRepository = stateRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedSessions() {
        try {
            long recovered = stateRepository.markAbandonedProcessingFailed();
            if (recovered > 0) {
                log.warn("PHOTO_PROCESSING_RECOVERED interruptedSessions={}", recovered);
            }
        } catch (RuntimeException exception) {
            log.error("PHOTO_PROCESSING_RECOVERY_FAILED type={}",
                    exception.getClass().getSimpleName());
        }
    }
}
