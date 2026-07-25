package com.example.location.app.photo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GoogleGeminiImageServiceTests {
    private MockRestServiceServer server;
    private GoogleGeminiImageService service;

    @BeforeEach
    void setUp() {
        PhotoFeatureProperties properties = new PhotoFeatureProperties();
        properties.getGemini().setApiKey("test-only-key");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new GoogleGeminiImageService(properties, builder.build());
    }

    @Test
    void sendsOriginalOnlyToBackendGeminiAndParsesOutputImage() {
        byte[] enhancedBytes = new byte[]{9, 8, 7};
        String response = """
                {
                  "output_image": {
                    "data": "%s",
                    "mime_type": "image/jpeg"
                  }
                }
                """.formatted(Base64.getEncoder().encodeToString(enhancedBytes));
        server.expect(once(), requestTo("https://generativelanguage.googleapis.com/v1beta/interactions"))
                .andExpect(header("x-goog-api-key", "test-only-key"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        EnhancedImage enhanced = service.enhance(new ImageData(new byte[]{1, 2, 3}, "image/png"));

        assertArrayEquals(enhancedBytes, enhanced.bytes());
        assertEquals("image/jpeg", enhanced.contentType());
        server.verify();
    }

    @Test
    void rejectsSuccessfulResponseWithoutImage() {
        server.expect(once(), requestTo("https://generativelanguage.googleapis.com/v1beta/interactions"))
                .andRespond(withSuccess("{\"output_text\":\"no image\"}", MediaType.APPLICATION_JSON));

        PhotoApiException exception = assertThrows(PhotoApiException.class,
                () -> service.enhance(new ImageData(new byte[]{1}, "image/png")));

        assertEquals(502, exception.getStatus().value());
    }
}
