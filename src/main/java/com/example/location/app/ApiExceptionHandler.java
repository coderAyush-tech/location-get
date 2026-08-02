package com.example.location.app;

import com.example.location.app.admin.AdminApiException;
import com.example.location.app.capture.CaptureApiException;
import com.example.location.app.health.KeepAliveException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
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
    @ExceptionHandler(AdminApiException.class)
    ResponseEntity<ProblemDetail> adminApiFailure(AdminApiException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(exception.getStatus(), exception.getMessage());
        detail.setTitle(exception.getTitle());
        detail.setProperty("message", exception.getMessage());
        ResponseEntity.BodyBuilder response = ResponseEntity.status(exception.getStatus())
                .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON);
        if (exception.getRetryAfterSeconds() != null) {
            response.header(HttpHeaders.RETRY_AFTER, exception.getRetryAfterSeconds().toString());
        }
        return response.body(detail);
    }

    @ExceptionHandler(KeepAliveException.class)
    ProblemDetail keepAliveFailure(KeepAliveException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(exception.getStatus(), exception.getMessage());
        detail.setTitle("Keep-alive check failed");
        return detail;
    }

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
                "Data could not be saved because the database is unavailable.");
        detail.setTitle("Database unavailable");
        return detail;
    }

    @ExceptionHandler(CaptureApiException.class)
    ProblemDetail captureApiFailure(CaptureApiException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(exception.getStatus(), exception.getMessage());
        detail.setTitle("Photo capture request failed");
        detail.setProperty("message", exception.getMessage());
        return detail;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail uploadTooLarge(MaxUploadSizeExceededException exception) {
        return captureProblem(HttpStatus.PAYLOAD_TOO_LARGE, "Photo exceeds the configured upload limit.");
    }

    @ExceptionHandler({
            MissingServletRequestPartException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    ProblemDetail invalidCaptureRequest(Exception exception) {
        return captureProblem(HttpStatus.BAD_REQUEST, "Photo capture request contains missing or invalid fields.");
    }

    private ProblemDetail captureProblem(HttpStatus status, String message) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setTitle("Photo capture request failed");
        detail.setProperty("message", message);
        return detail;
    }
}
