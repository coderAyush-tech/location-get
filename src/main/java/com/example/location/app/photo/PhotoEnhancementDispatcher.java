package com.example.location.app.photo;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class PhotoEnhancementDispatcher {
    private final PhotoEnhancementWorker worker;

    public PhotoEnhancementDispatcher(PhotoEnhancementWorker worker) {
        this.worker = worker;
    }

    @Async("photoEnhancementExecutor")
    public void dispatch(String sessionId) {
        worker.enhance(sessionId);
    }
}
