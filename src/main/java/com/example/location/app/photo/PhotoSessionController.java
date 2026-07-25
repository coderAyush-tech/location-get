package com.example.location.app.photo;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/photo-sessions")
public class PhotoSessionController {
    private final PhotoSessionService service;

    public PhotoSessionController(PhotoSessionService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<CreatePhotoSessionResponse> create() {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create());
    }

    @PostMapping(path = "/{sessionId}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<PhotoUploadResponse> uploadPhoto(
            @PathVariable String sessionId,
            @RequestParam("photo") MultipartFile photo,
            @RequestParam(value = "latitude", required = false) Double latitude,
            @RequestParam(value = "longitude", required = false) Double longitude,
            @RequestParam(value = "accuracy", required = false) Double accuracy
    ) {
        return ResponseEntity.ok(service.uploadOriginal(sessionId, photo, latitude, longitude, accuracy));
    }

    @PostMapping("/{sessionId}/enhance")
    ResponseEntity<PhotoEnhanceResponse> enhance(@PathVariable String sessionId) {
        return ResponseEntity.accepted().body(service.requestEnhancement(sessionId));
    }

    @GetMapping("/{sessionId}")
    ResponseEntity<PhotoSessionResponse> get(@PathVariable String sessionId) {
        return ResponseEntity.ok(service.get(sessionId));
    }
}
