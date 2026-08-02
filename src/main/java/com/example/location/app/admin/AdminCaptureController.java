package com.example.location.app.admin;

import com.example.location.app.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/admin/captures")
public class AdminCaptureController {
    private final AdminCaptureService captureService;
    private final AdminCaptureDeleteService deleteService;
    private final ClientIpResolver clientIpResolver;

    public AdminCaptureController(
            AdminCaptureService captureService,
            AdminCaptureDeleteService deleteService,
            ClientIpResolver clientIpResolver
    ) {
        this.captureService = captureService;
        this.deleteService = deleteService;
        this.clientIpResolver = clientIpResolver;
    }

    @GetMapping
    ResponseEntity<AdminCapturePageResponse> captures(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "ALL") String locationSource
    ) {
        return ResponseEntity.ok()
                .body(captureService.list(page, size, sort, query, locationSource));
    }

    @GetMapping("/{captureId}/photo")
    ResponseEntity<Resource> photo(@PathVariable String captureId) {
        AdminStoredPhoto photo = captureService.photo(captureId);
        String filename = safeFilename(photo.originalFilename());
        MediaType mediaType = safeMediaType(photo.contentType());
        Resource resource = new InputStreamResource(new ByteArrayInputStream(photo.bytes()));
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(filename, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(photo.bytes().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(resource);
    }

    @DeleteMapping("/{captureId}")
    ResponseEntity<Void> delete(@PathVariable String captureId, HttpServletRequest request) {
        Object username = request.getAttribute(AdminAuthorizationInterceptor.ADMIN_USERNAME_ATTRIBUTE);
        if (!(username instanceof String adminUsername) || adminUsername.isBlank()) {
            throw new AdminApiException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Access denied",
                    "Administrator access is required."
            );
        }
        deleteService.delete(captureId, adminUsername, clientIpResolver.resolve(request));
        return ResponseEntity.noContent().build();
    }

    private MediaType safeMediaType(String contentType) {
        return switch (contentType == null ? "" : contentType.toLowerCase()) {
            case MediaType.IMAGE_JPEG_VALUE -> MediaType.IMAGE_JPEG;
            case MediaType.IMAGE_PNG_VALUE -> MediaType.IMAGE_PNG;
            case "image/webp" -> MediaType.parseMediaType("image/webp");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    private String safeFilename(String value) {
        if (value == null || value.isBlank()) {
            return "capture";
        }
        String normalized = value.replace('\\', '/');
        String filename = normalized.substring(normalized.lastIndexOf('/') + 1)
                .replace("\r", "")
                .replace("\n", "")
                .trim();
        return filename.isEmpty() ? "capture" : filename;
    }
}
