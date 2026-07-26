package com.example.location.app.photo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;
import java.util.UUID;

@Service
public class DirectPhotoEnhancementService {
    private static final Logger log = LoggerFactory.getLogger(DirectPhotoEnhancementService.class);
    private static final Set<String> SUPPORTED_OUTPUT_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");

    private final PhotoImageValidator imageValidator;
    private final GeminiImageService geminiImageService;

    public DirectPhotoEnhancementService(
            PhotoImageValidator imageValidator,
            GeminiImageService geminiImageService
    ) {
        this.imageValidator = imageValidator;
        this.geminiImageService = geminiImageService;
    }

    public EnhancedImage enhance(MultipartFile photo) {
        String requestId = UUID.randomUUID().toString();
        ValidatedImage original = imageValidator.validate(photo);
        log.info("DIRECT_PHOTO_ENHANCEMENT_STARTED requestId={} contentType={} bytes={}",
                requestId, original.contentType(), original.bytes().length);
        try {
            EnhancedImage enhanced = geminiImageService.enhance(
                    new ImageData(original.bytes(), original.contentType())
            );
            validateOutput(enhanced);
            log.info("DIRECT_PHOTO_ENHANCEMENT_COMPLETED requestId={} contentType={} bytes={}",
                    requestId, enhanced.contentType(), enhanced.bytes().length);
            return enhanced;
        } catch (PhotoApiException exception) {
            log.error("DIRECT_PHOTO_ENHANCEMENT_FAILED requestId={} type={} status={}",
                    requestId,
                    exception.getClass().getSimpleName(),
                    exception.getStatus().value());
            throw exception;
        } catch (RuntimeException exception) {
            log.error("DIRECT_PHOTO_ENHANCEMENT_FAILED requestId={} type={}",
                    requestId, exception.getClass().getSimpleName());
            throw new PhotoApiException(
                    HttpStatus.BAD_GATEWAY,
                    "AI enhancement is temporarily unavailable.",
                    exception
            );
        }
    }

    private void validateOutput(EnhancedImage enhanced) {
        if (enhanced == null
                || enhanced.bytes() == null
                || enhanced.bytes().length == 0
                || !SUPPORTED_OUTPUT_TYPES.contains(enhanced.contentType())) {
            throw new PhotoApiException(
                    HttpStatus.BAD_GATEWAY,
                    "AI enhancement returned an invalid image."
            );
        }
    }
}
