package com.example.location.app.photo;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

@Component
public class PhotoImageValidator {
    private static final Set<String> SUPPORTED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private final PhotoFeatureProperties properties;

    public PhotoImageValidator(PhotoFeatureProperties properties) {
        this.properties = properties;
    }

    public ValidatedImage validate(MultipartFile photo) {
        if (photo == null || photo.isEmpty()) {
            throw new PhotoApiException(HttpStatus.BAD_REQUEST, "Photo file must not be empty.");
        }
        if (photo.getSize() > properties.getMaxImageBytes()) {
            throw new PhotoApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Photo exceeds the configured upload limit.");
        }

        try {
            byte[] bytes = photo.getBytes();
            String detectedType = detectContentType(bytes);
            String declaredType = normalizeContentType(photo.getContentType());

            if (detectedType == null || !SUPPORTED_TYPES.contains(detectedType)) {
                throw new PhotoApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "Only JPEG, PNG, and WebP photos are supported.");
            }
            if (declaredType != null && !declaredType.equals(detectedType)) {
                throw new PhotoApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "Photo content does not match its declared media type.");
            }

            if ("image/webp".equals(detectedType)) {
                validateWebpStructure(bytes);
            }
            validateDimensions(bytes, detectedType);
            return new ValidatedImage(bytes, detectedType);
        } catch (PhotoApiException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new PhotoApiException(HttpStatus.BAD_REQUEST, "Photo could not be read.", exception);
        }
    }

    private void validateWebpStructure(byte[] bytes) {
        if (bytes.length < 20) {
            throw new PhotoApiException(HttpStatus.BAD_REQUEST, "Photo is malformed.");
        }
        long declaredRiffSize = littleEndianUnsignedInt(bytes, 4);
        if (declaredRiffSize + 8 > bytes.length) {
            throw new PhotoApiException(HttpStatus.BAD_REQUEST, "Photo is malformed.");
        }
        String chunk = new String(bytes, 12, 4, java.nio.charset.StandardCharsets.US_ASCII);
        if (!Set.of("VP8 ", "VP8L", "VP8X").contains(chunk)) {
            throw new PhotoApiException(HttpStatus.BAD_REQUEST, "Photo is malformed.");
        }
    }

    private long littleEndianUnsignedInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xffL)
                | ((bytes[offset + 1] & 0xffL) << 8)
                | ((bytes[offset + 2] & 0xffL) << 16)
                | ((bytes[offset + 3] & 0xffL) << 24);
    }

    private void validateDimensions(byte[] bytes, String contentType) throws IOException {
        if ("image/webp".equals(contentType)) {
            return;
        }

        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw new PhotoApiException(HttpStatus.BAD_REQUEST, "Photo is malformed.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new PhotoApiException(HttpStatus.BAD_REQUEST, "Photo is malformed.");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0
                        || width > properties.getMaxImageDimension()
                        || height > properties.getMaxImageDimension()) {
                    throw new PhotoApiException(HttpStatus.BAD_REQUEST,
                            "Photo dimensions are invalid or exceed the configured limit.");
                }
            } finally {
                reader.dispose();
            }
        }
    }

    private String detectContentType(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        if (bytes.length >= 8
                && (bytes[0] & 0xff) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4e
                && bytes[3] == 0x47
                && bytes[4] == 0x0d
                && bytes[5] == 0x0a
                && bytes[6] == 0x1a
                && bytes[7] == 0x0a) {
            return "image/png";
        }
        if (bytes.length >= 12
                && bytes[0] == 'R'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == 'F'
                && bytes[8] == 'W'
                && bytes[9] == 'E'
                && bytes[10] == 'B'
                && bytes[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        int separator = contentType.indexOf(';');
        return (separator >= 0 ? contentType.substring(0, separator) : contentType)
                .trim()
                .toLowerCase();
    }
}
