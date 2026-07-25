package com.example.location.app.photo;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class GoogleGeminiImageService implements GeminiImageService {
    private static final String INTERACTIONS_URL =
            "https://generativelanguage.googleapis.com/v1beta/interactions";

    private final PhotoFeatureProperties.Gemini properties;
    private final RestClient restClient;

    @Autowired
    public GoogleGeminiImageService(PhotoFeatureProperties photoProperties) {
        this.properties = photoProperties.getGemini();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeoutMillis());
        requestFactory.setReadTimeout(properties.getReadTimeoutMillis());
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    GoogleGeminiImageService(PhotoFeatureProperties photoProperties, RestClient restClient) {
        this.properties = photoProperties.getGemini();
        this.restClient = restClient;
    }

    @Override
    public EnhancedImage enhance(ImageData originalImage) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new PhotoApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "AI enhancement is not configured.");
        }

        Map<String, Object> body = Map.of(
                "model", properties.getModel(),
                "input", List.of(
                        Map.of("type", "text", "text", ImageEnhancementPrompt.TEXT),
                        Map.of(
                                "type", "image",
                                "mime_type", originalImage.contentType(),
                                "data", Base64.getEncoder().encodeToString(originalImage.bytes())
                        )
                ),
                "response_format", Map.of("type", "image", "mime_type", "image/jpeg")
        );

        try {
            Map<?, ?> response = restClient.post()
                    .uri(INTERACTIONS_URL)
                    .header("x-goog-api-key", properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            return parseImage(response);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            String safeMessage = switch (status) {
                case 401, 403 -> "AI enhancement authentication failed.";
                case 429 -> "AI enhancement quota is currently unavailable.";
                default -> "AI enhancement provider returned an error.";
            };
            throw new PhotoApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, safeMessage, exception);
        } catch (RestClientException exception) {
            throw new PhotoApiException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "AI enhancement provider is temporarily unavailable.", exception);
        }
    }

    private EnhancedImage parseImage(Map<?, ?> response) {
        Map<?, ?> image = response == null ? null : asMap(response.get("output_image"));
        if (hasImageData(image)) {
            return decodeImage(image);
        }

        if (response != null) {
            for (Object stepValue : asList(response.get("steps"))) {
                Map<?, ?> step = asMap(stepValue);
                for (Object contentValue : asList(step == null ? null : step.get("content"))) {
                    Map<?, ?> content = asMap(contentValue);
                    if (content != null
                            && "image".equals(stringValue(content.get("type")))
                            && hasImageData(content)) {
                        return decodeImage(content);
                    }
                }
            }
        }
        throw new PhotoApiException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                "AI enhancement returned no image.");
    }

    private boolean hasImageData(Map<?, ?> image) {
        return image != null && stringValue(image.get("data")) != null;
    }

    private EnhancedImage decodeImage(Map<?, ?> image) {
        try {
            byte[] bytes = Base64.getDecoder().decode(stringValue(image.get("data")));
            if (bytes.length == 0) {
                throw new IllegalArgumentException("empty image");
            }
            String contentType = stringValue(image.get("mime_type"));
            if (contentType == null) {
                contentType = "image/jpeg";
            }
            if (!contentType.startsWith("image/")) {
                contentType = "image/jpeg";
            }
            return new EnhancedImage(bytes, contentType);
        } catch (IllegalArgumentException exception) {
            throw new PhotoApiException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "AI enhancement returned malformed image data.", exception);
        }
    }

    private Map<?, ?> asMap(Object value) {
        return value instanceof Map<?, ?> map ? map : null;
    }

    private List<?> asList(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private String stringValue(Object value) {
        return value instanceof String text && !text.isBlank() ? text : null;
    }
}
