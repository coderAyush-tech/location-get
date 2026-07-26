package com.example.location.app.photo;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/photo-enhancements")
public class DirectPhotoEnhancementController {
    private final DirectPhotoEnhancementService service;

    public DirectPhotoEnhancementController(DirectPhotoEnhancementService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<byte[]> enhance(@RequestParam("photo") MultipartFile photo) {
        EnhancedImage enhanced = service.enhance(photo);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(enhanced.contentType()));
        headers.setCacheControl(CacheControl.noStore());
        headers.setContentDisposition(ContentDisposition.inline()
                .filename("photogenius-enhanced." + extension(enhanced.contentType()))
                .build());
        return ResponseEntity.ok()
                .headers(headers)
                .body(enhanced.bytes());
    }

    private String extension(String contentType) {
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }
}
