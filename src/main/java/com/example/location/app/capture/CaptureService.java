package com.example.location.app.capture;

import com.example.location.app.GeoIpService;
import com.example.location.app.LocationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CaptureService {
    private static final Logger log = LoggerFactory.getLogger(CaptureService.class);

    private final CapturedPhotoRepository repository;
    private final CapturedPhotoValidator validator;
    private final GeoIpService geoIpService;

    public CaptureService(
            CapturedPhotoRepository repository,
            CapturedPhotoValidator validator,
            GeoIpService geoIpService
    ) {
        this.repository = repository;
        this.validator = validator;
        this.geoIpService = geoIpService;
    }

    public CaptureResponse save(
            MultipartFile photo,
            Double latitude,
            Double longitude,
            Double accuracy,
            String clientIp
    ) {
        ValidatedCapture validated = validator.validate(photo);
        CaptureLocation location = resolveLocation(latitude, longitude, accuracy, clientIp);

        CapturedPhoto capturedPhoto = new CapturedPhoto(
                validated.bytes(),
                validated.contentType(),
                safeFilename(photo.getOriginalFilename()),
                location.latitude(),
                location.longitude(),
                location.accuracy(),
                location.source(),
                location.address(),
                clientIp
        );

        CapturedPhoto saved = repository.save(capturedPhoto);
        log.info(
                "CAPTURE_SAVE_SUCCESS id={} contentType={} sizeBytes={} locationSource={} clientIp={}",
                saved.getId(),
                saved.getContentType(),
                saved.getSizeBytes(),
                saved.getLocationSource(),
                clientIp
        );
        return CaptureResponse.from(saved);
    }

    private CaptureLocation resolveLocation(
            Double latitude,
            Double longitude,
            Double accuracy,
            String clientIp
    ) {
        if ((latitude == null) != (longitude == null)) {
            throw new CaptureApiException(HttpStatus.BAD_REQUEST,
                    "Latitude and longitude must be provided together.");
        }
        if (latitude != null) {
            if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                    || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
                throw new CaptureApiException(HttpStatus.BAD_REQUEST,
                        "Latitude or longitude is outside the valid range.");
            }
            if (accuracy != null && (!Double.isFinite(accuracy) || accuracy < 0)) {
                throw new CaptureApiException(HttpStatus.BAD_REQUEST,
                        "Accuracy must be a non-negative number.");
            }
            return new CaptureLocation(latitude, longitude, accuracy, "gps", null);
        }
        if (accuracy != null) {
            throw new CaptureApiException(HttpStatus.BAD_REQUEST,
                    "Accuracy cannot be sent without latitude and longitude.");
        }

        LocationResponse fallback = geoIpService.locateOrRawIp(clientIp);
        return new CaptureLocation(
                fallback.latitude(),
                fallback.longitude(),
                null,
                fallback.source(),
                "raw_ip".equals(fallback.source()) ? null : fallback.address()
        );
    }

    private String safeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "camera-photo";
        }
        String normalized = originalFilename.replace('\\', '/');
        String filename = normalized.substring(normalized.lastIndexOf('/') + 1)
                .replace("\r", "")
                .replace("\n", "")
                .trim();
        if (filename.isEmpty()) {
            return "camera-photo";
        }
        return filename.length() <= 200 ? filename : filename.substring(filename.length() - 200);
    }

    private record CaptureLocation(
            Double latitude,
            Double longitude,
            Double accuracy,
            String source,
            String address
    ) {
    }
}
