package com.example.location.app.photo;

import org.springframework.http.HttpStatus;

public class PhotoSessionNotFoundException extends PhotoApiException {
    public PhotoSessionNotFoundException() {
        super(HttpStatus.NOT_FOUND, "Photo session was not found.");
    }
}
