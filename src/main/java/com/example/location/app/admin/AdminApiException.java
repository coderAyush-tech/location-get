package com.example.location.app.admin;

import org.springframework.http.HttpStatus;

public class AdminApiException extends RuntimeException {
    private final HttpStatus status;
    private final String title;
    private final Long retryAfterSeconds;

    public AdminApiException(HttpStatus status, String title, String message) {
        this(status, title, message, null);
    }

    public AdminApiException(HttpStatus status, String title, String message, Long retryAfterSeconds) {
        super(message);
        this.status = status;
        this.title = title;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
