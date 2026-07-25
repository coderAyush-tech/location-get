package com.example.location.app;

import com.example.location.app.photo.PhotoApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidRequest(MethodArgumentNotValidException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Latitude must be between -90 and 90, and longitude between -180 and 180.");
        detail.setTitle("Invalid location coordinates");
        return detail;
    }

    @ExceptionHandler(LocationLookupException.class)
    ProblemDetail lookupFailed(LocationLookupException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, exception.getMessage());
        detail.setTitle("Location lookup failed");
        return detail;
    }

    @ExceptionHandler(DataAccessException.class)
    ProblemDetail databaseUnavailable(DataAccessException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Location could not be saved because the database is unavailable.");
        detail.setTitle("Database unavailable");
        return detail;
    }

    @ExceptionHandler(PhotoApiException.class)
    ProblemDetail photoApiFailure(PhotoApiException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(exception.getStatus(), exception.getMessage());
        detail.setTitle("Photo session request failed");
        detail.setProperty("message", exception.getMessage());
        return detail;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail uploadTooLarge(MaxUploadSizeExceededException exception) {
        return photoProblem(HttpStatus.PAYLOAD_TOO_LARGE, "Photo exceeds the configured upload limit.");
    }

    @ExceptionHandler({
            MissingServletRequestPartException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    ProblemDetail invalidPhotoRequest(Exception exception) {
        return photoProblem(HttpStatus.BAD_REQUEST, "Photo session request contains missing or invalid fields.");
    }

    private ProblemDetail photoProblem(HttpStatus status, String message) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setTitle("Photo session request failed");
        detail.setProperty("message", message);
        return detail;
    }
}
