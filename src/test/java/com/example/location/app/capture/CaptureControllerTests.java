package com.example.location.app.capture;

import com.example.location.app.ApiExceptionHandler;
import com.example.location.app.ClientIpResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static com.example.location.app.capture.CapturedPhotoValidatorTests.ONE_PIXEL_PNG;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CaptureControllerTests {
    @Mock
    private CaptureService captureService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ClientIpResolver clientIpResolver = new ClientIpResolver(new String[]{
                "127.0.0.0/8", "10.0.0.0/8"
        });
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CaptureController(captureService, clientIpResolver))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void uploadsCameraPhotoAndLocation() throws Exception {
        MockMultipartFile photo = new MockMultipartFile(
                "photo", "camera.png", "image/png", ONE_PIXEL_PNG
        );
        when(captureService.save(
                eq(photo), eq(28.6139), eq(77.209), eq(12.0), eq("106.222.248.114")
        )).thenReturn(new CaptureResponse(
                "capture-1",
                true,
                "image/png",
                ONE_PIXEL_PNG.length,
                28.6139,
                77.209,
                12.0,
                "gps",
                null,
                "106.222.248.114",
                Instant.parse("2026-07-29T10:00:00Z")
        ));

        mockMvc.perform(multipart("/api/v1/captures")
                        .file(photo)
                        .param("latitude", "28.6139")
                        .param("longitude", "77.209")
                        .param("accuracy", "12")
                        .header("X-Forwarded-For", "106.222.248.114, 10.0.0.1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("capture-1"))
                .andExpect(jsonPath("$.saved").value(true))
                .andExpect(jsonPath("$.locationSource").value("gps"))
                .andExpect(jsonPath("$.clientIp").value("106.222.248.114"));
    }

    @Test
    void rejectsMissingPhoto() throws Exception {
        mockMvc.perform(multipart("/api/v1/captures")
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Photo capture request failed"));
    }
}
