package com.example.location.app.health;

import org.springframework.http.HttpStatus;

public class KeepAliveException extends RuntimeException {
    private final HttpStatus status;

    public KeepAliveException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
