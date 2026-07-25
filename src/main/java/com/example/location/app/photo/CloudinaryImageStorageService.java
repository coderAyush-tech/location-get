package com.example.location.app.photo;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

@Service
public class CloudinaryImageStorageService implements ImageStorageService {
    private final PhotoFeatureProperties.Cloudinary properties;
    private final RestClient restClient;

    public CloudinaryImageStorageService(PhotoFeatureProperties photoProperties) {
        this.properties = photoProperties.getCloudinary();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeoutMillis());
        requestFactory.setReadTimeout(properties.getReadTimeoutMillis());
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public StoredImage uploadOriginal(String sessionId, byte[] imageBytes, String contentType) {
        return upload(sessionId, "original", imageBytes, contentType);
    }

    @Override
    public StoredImage uploadEnhanced(String sessionId, byte[] imageBytes, String contentType) {
        return upload(sessionId, "enhanced", imageBytes, contentType);
    }

    @Override
    public ImageData download(String imageUrl) {
        ensureConfigured();
        URI uri = URI.create(imageUrl);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !"res.cloudinary.com".equalsIgnoreCase(uri.getHost())) {
            throw new PhotoApiException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "Stored image URL is invalid.");
        }

        try {
            var response = restClient.get().uri(uri).retrieve().toEntity(byte[].class);
            byte[] bytes = response.getBody();
            if (bytes == null || bytes.length == 0) {
                throw new PhotoApiException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                        "Stored original image is unavailable.");
            }
            MediaType mediaType = response.getHeaders().getContentType();
            return new ImageData(bytes, mediaType == null ? "image/jpeg" : mediaType.toString());
        } catch (RestClientException exception) {
            throw new PhotoApiException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "Stored original image could not be downloaded.", exception);
        }
    }

    @Override
    public void delete(String storageId) {
        if (storageId == null || storageId.isBlank() || !isConfigured()) {
            return;
        }
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("public_id", storageId);
            restClient.post()
                    .uri(apiUrl("destroy"))
                    .headers(headers -> headers.setBasicAuth(properties.getApiKey(), properties.getApiSecret()))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ignored) {
            // Cleanup is best-effort and the primary failure is logged by the caller.
        }
    }

    private StoredImage upload(
            String sessionId,
            String kind,
            byte[] imageBytes,
            String contentType
    ) {
        ensureConfigured();
        String storageId = "photogenius/sessions/" + sessionId + "/" + kind + "/" + UUID.randomUUID();

        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new NamedByteArrayResource(imageBytes, extension(contentType)));
            body.add("public_id", storageId);
            body.add("overwrite", "false");

            Map<?, ?> response = restClient.post()
                    .uri(apiUrl("upload"))
                    .headers(headers -> headers.setBasicAuth(properties.getApiKey(), properties.getApiSecret()))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            String secureUrl = stringValue(response, "secure_url");
            String publicId = stringValue(response, "public_id");
            if (secureUrl == null || !secureUrl.startsWith("https://") || publicId == null) {
                throw new PhotoApiException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                        "Image storage returned an invalid response.");
            }
            return new StoredImage(secureUrl, publicId, contentType);
        } catch (PhotoApiException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new PhotoApiException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "Image storage is temporarily unavailable.", exception);
        }
    }

    private String apiUrl(String action) {
        return "https://api.cloudinary.com/v1_1/" + properties.getCloudName() + "/image/" + action;
    }

    private void ensureConfigured() {
        if (!isConfigured()) {
            throw new PhotoApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "Image storage is not configured.");
        }
    }

    private boolean isConfigured() {
        return notBlank(properties.getCloudName())
                && notBlank(properties.getApiKey())
                && notBlank(properties.getApiSecret());
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String stringValue(Map<?, ?> response, String key) {
        if (response == null) {
            return null;
        }
        Object value = response.get(key);
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private String extension(String contentType) {
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        private NamedByteArrayResource(byte[] bytes, String extension) {
            super(bytes);
            this.filename = "photo." + extension;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
