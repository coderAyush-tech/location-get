package com.example.location.app.photo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

class CloudinaryImageStorageServiceTests {
    private static final Instant FIXED_TIME = Instant.ofEpochSecond(1_717_000_000L);

    private MockRestServiceServer server;
    private CloudinaryImageStorageService service;

    @BeforeEach
    void setUp() {
        PhotoFeatureProperties properties = new PhotoFeatureProperties();
        properties.getCloudinary().setCloudName(" test-cloud ");
        properties.getCloudinary().setApiKey(" test-api-key ");
        properties.getCloudinary().setApiSecret(" test-api-secret ");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new CloudinaryImageStorageService(
                properties,
                builder.build(),
                Clock.fixed(FIXED_TIME, ZoneOffset.UTC)
        );
    }

    @Test
    void signsUploadAndReturnsSecureCloudinaryUrl() {
        String response = """
                {
                  "secure_url": "https://res.cloudinary.com/test-cloud/image/upload/photo.jpg",
                  "public_id": "photogenius/sessions/session-1/original/generated"
                }
                """;
        server.expect(once(), requestTo(
                        "https://api.cloudinary.com/v1_1/test-cloud/image/upload"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andExpect(content().string(containsString("name=\"api_key\"")))
                .andExpect(content().string(containsString("test-api-key")))
                .andExpect(content().string(containsString("name=\"timestamp\"")))
                .andExpect(content().string(containsString(
                        String.valueOf(FIXED_TIME.getEpochSecond()))))
                .andExpect(content().string(containsString("name=\"signature\"")))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        StoredImage stored = service.uploadOriginal(
                "session-1",
                new byte[]{1, 2, 3},
                "image/jpeg"
        );

        assertEquals(
                "https://res.cloudinary.com/test-cloud/image/upload/photo.jpg",
                stored.url()
        );
        assertEquals(
                "photogenius/sessions/session-1/original/generated",
                stored.storageId()
        );
        server.verify();
    }

    @Test
    void unsignedPresetUploadsWithoutApiCredentials() {
        PhotoFeatureProperties properties = new PhotoFeatureProperties();
        properties.getCloudinary().setCloudName("pixelart");
        properties.getCloudinary().setUploadPreset("photogenius_unsigned");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer unsignedServer = MockRestServiceServer.bindTo(builder).build();
        CloudinaryImageStorageService unsignedService = new CloudinaryImageStorageService(
                properties,
                builder.build(),
                Clock.fixed(FIXED_TIME, ZoneOffset.UTC)
        );
        String response = """
                {
                  "secure_url": "https://res.cloudinary.com/pixelart/image/upload/generated.jpg",
                  "public_id": "photogenius/sessions/session-2/original/generated"
                }
                """;
        unsignedServer.expect(once(), requestTo(
                        "https://api.cloudinary.com/v1_1/pixelart/image/upload"))
                .andExpect(content().string(containsString("name=\"upload_preset\"")))
                .andExpect(content().string(containsString("photogenius_unsigned")))
                .andExpect(content().string(containsString("name=\"folder\"")))
                .andExpect(content().string(containsString(
                        "photogenius/sessions/session-2/original")))
                .andExpect(content().string(not(containsString("name=\"api_key\""))))
                .andExpect(content().string(not(containsString("name=\"signature\""))))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        StoredImage stored = unsignedService.uploadOriginal(
                "session-2",
                new byte[]{1, 2, 3},
                "image/jpeg"
        );

        assertEquals(
                "https://res.cloudinary.com/pixelart/image/upload/generated.jpg",
                stored.url()
        );
        unsignedServer.verify();
    }

    @Test
    void mapsRejectedCredentialsToSafeMessage() {
        server.expect(once(), requestTo(
                        "https://api.cloudinary.com/v1_1/test-cloud/image/upload"))
                .andRespond(withUnauthorizedRequest());

        PhotoApiException exception = assertThrows(PhotoApiException.class,
                () -> service.uploadOriginal("session-1", new byte[]{1}, "image/jpeg"));

        assertEquals(502, exception.getStatus().value());
        assertEquals("Image storage credentials were rejected.", exception.getMessage());
        server.verify();
    }

    @Test
    void createsCloudinaryCompatibleShaOneSignature() {
        String signature = CloudinaryImageStorageService.signParameters(
                Map.of(
                        "timestamp", 1_735_000_000L,
                        "public_id", "sample",
                        "overwrite", "false"
                ),
                "test-secret"
        );

        assertEquals("8b388afbec477b191019968bc5217c4773a8316a", signature);
    }
}
