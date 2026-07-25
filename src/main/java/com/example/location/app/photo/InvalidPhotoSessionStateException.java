package com.example.location.app.photo;

import org.springframework.http.HttpStatus;

public class InvalidPhotoSessionStateException extends PhotoApiException {
    public InvalidPhotoSessionStateException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
