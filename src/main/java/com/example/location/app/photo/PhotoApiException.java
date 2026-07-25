package com.example.location.app.photo;

import org.springframework.http.HttpStatus;

public class PhotoApiException extends RuntimeException {
    private final HttpStatus status;

    public PhotoApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public PhotoApiException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
