package com.example.location.app.photo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CloudinaryImageStorageService implements ImageStorageService {
    private static final Logger log = LoggerFactory.getLogger(CloudinaryImageStorageService.class);

    private final PhotoFeatureProperties.Cloudinary properties;
    private final RestClient restClient;
    private final Clock clock;

    @Autowired
    public CloudinaryImageStorageService(PhotoFeatureProperties photoProperties) {
        this.properties = photoProperties.getCloudinary();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeoutMillis());
        requestFactory.setReadTimeout(properties.getReadTimeoutMillis());
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.clock = Clock.systemUTC();
    }

    CloudinaryImageStorageService(
            PhotoFeatureProperties photoProperties,
            RestClient restClient,
            Clock clock
    ) {
        this.properties = photoProperties.getCloudinary();
        this.restClient = restClient;
        this.clock = clock;
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
        } catch (RestClientResponseException exception) {
            logProviderFailure("download", exception);
            throw new PhotoApiException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "Stored original image could not be downloaded.", exception);
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
            Map<String, Object> signedParameters = signedParameters(Map.of("public_id", storageId));
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("public_id", storageId);
            addAuthentication(body, signedParameters);
            restClient.post()
                    .uri(apiUrl("destroy"))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            logProviderFailure("destroy", exception);
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
            Map<String, Object> signedParameters = signedParameters(Map.of(
                    "overwrite", "false",
                    "public_id", storageId
            ));
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new NamedByteArrayResource(imageBytes, extension(contentType)));
            body.add("public_id", storageId);
            body.add("overwrite", "false");
            addAuthentication(body, signedParameters);

            Map<?, ?> response = restClient.post()
                    .uri(apiUrl("upload"))
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
        } catch (RestClientResponseException exception) {
            logProviderFailure("upload", exception);
            throw providerFailure(exception);
        } catch (PhotoApiException exception) {
            throw exception;
        } catch (RestClientException exception) {
            log.error("CLOUDINARY_REQUEST_FAILED action=upload type={} cloudName={}",
                    exception.getClass().getSimpleName(), credential(properties.getCloudName()));
            throw new PhotoApiException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "Image storage is temporarily unavailable.", exception);
        }
    }

    private Map<String, Object> signedParameters(Map<String, Object> requestParameters) {
        Map<String, Object> parameters = new LinkedHashMap<>(requestParameters);
        parameters.put("timestamp", Instant.now(clock).getEpochSecond());
        return parameters;
    }

    private void addAuthentication(
            MultiValueMap<String, Object> body,
            Map<String, Object> signedParameters
    ) {
        body.add("timestamp", String.valueOf(signedParameters.get("timestamp")));
        body.add("api_key", credential(properties.getApiKey()));
        body.add("signature", signParameters(
                signedParameters,
                credential(properties.getApiSecret())
        ));
    }

    static String signParameters(Map<String, ?> parameters, String apiSecret) {
        String canonicalParameters = new TreeMap<>(parameters).entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] signature = digest.digest(
                    (canonicalParameters + apiSecret).getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(signature);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 is required for Cloudinary request signing.", exception);
        }
    }

    private PhotoApiException providerFailure(RestClientResponseException exception) {
        String safeMessage = switch (exception.getStatusCode().value()) {
            case 400 -> "Image storage rejected the upload.";
            case 401, 403 -> "Image storage credentials were rejected.";
            case 404 -> "Image storage cloud name was not found.";
            default -> "Image storage is temporarily unavailable.";
        };
        return new PhotoApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, safeMessage, exception);
    }

    private void logProviderFailure(String action, RestClientResponseException exception) {
        String providerError = null;
        if (exception.getResponseHeaders() != null) {
            providerError = exception.getResponseHeaders().getFirst("X-Cld-Error");
        }
        log.error("CLOUDINARY_REQUEST_FAILED action={} status={} cloudName={} providerError={}",
                action,
                exception.getStatusCode().value(),
                credential(properties.getCloudName()),
                sanitize(providerError));
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "unavailable";
        }
        String sanitized = value.replace('\r', ' ').replace('\n', ' ').trim();
        return sanitized.length() <= 200 ? sanitized : sanitized.substring(0, 200);
    }

    private String apiUrl(String action) {
        return "https://api.cloudinary.com/v1_1/"
                + credential(properties.getCloudName())
                + "/image/"
                + action;
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

    private String credential(String value) {
        return value == null ? "" : value.trim();
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
