package com.example.location.app.capture;

import org.springframework.http.HttpStatus;

public class CaptureApiException extends RuntimeException {
    private final HttpStatus status;

    public CaptureApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public CaptureApiException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
