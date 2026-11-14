package com.example.location.app.capture;

import com.example.location.app.GeoIpService;
import com.example.location.app.LocationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import java.io.IOException;
import java.util.Set;

@Service
public class CaptureService {
    private static final Logger log = LoggerFactory.getLogger(CaptureService.class);

    private final CapturedPhotoRepository repository;
    private final CapturedPhotoValidator validator;
    private final GeoIpService geoIpService;
    private final long maxAudioBytes;

    public CaptureService(CapturedPhotoRepository repository, CapturedPhotoValidator validator, GeoIpService geoIpService) {
        this(repository, validator, geoIpService, 2_097_152L);
    }

    @Autowired
    public CaptureService(
            CapturedPhotoRepository repository,
            CapturedPhotoValidator validator,
            GeoIpService geoIpService,
            @Value("${app.capture.max-audio-bytes:2097152}") long maxAudioBytes
    ) {
        this.repository = repository;
        this.validator = validator;
        this.geoIpService = geoIpService;
        this.maxAudioBytes = maxAudioBytes;
    }

    public CaptureResponse save(
            MultipartFile photo,
            Double latitude,
            Double longitude,
            Double accuracy,
            String clientIp
    ) {
        return save(photo, null, latitude, longitude, accuracy, clientIp);
    }

    public CaptureResponse save(
            MultipartFile photo,
            MultipartFile audio,
            Double latitude,
            Double longitude,
            Double accuracy,
            String clientIp
    ) {
        ValidatedCapture validated = validator.validate(photo);
        ValidatedAudio validatedAudio = validateAudio(audio);
        CaptureLocation location = resolveLocation(latitude, longitude, accuracy, clientIp);

        CapturedPhoto capturedPhoto = new CapturedPhoto(
                validated.bytes(),
                validated.contentType(),
                safeFilename(photo.getOriginalFilename()),
                validatedAudio == null ? null : validatedAudio.bytes(),
                validatedAudio == null ? null : validatedAudio.contentType(),
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

    private ValidatedAudio validateAudio(MultipartFile audio) {
        if (audio == null || audio.isEmpty()) return null;
        if (audio.getSize() > maxAudioBytes) {
            throw new CaptureApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Audio exceeds the configured upload limit.");
        }
        String type = audio.getContentType() == null ? "" : audio.getContentType().split(";", 2)[0].trim().toLowerCase();
        if (!Set.of("audio/webm", "audio/ogg", "audio/mp4", "audio/mpeg").contains(type)) {
            throw new CaptureApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported audio format.");
        }
        try {
            byte[] bytes = audio.getBytes();
            return bytes.length == 0 ? null : new ValidatedAudio(bytes, type);
        } catch (IOException exception) {
            throw new CaptureApiException(HttpStatus.BAD_REQUEST, "Audio could not be read.", exception);
        }
    }

    private record ValidatedAudio(byte[] bytes, String contentType) { }

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
