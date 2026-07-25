package com.example.location.app.photo;

import com.example.location.app.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PhotoSessionControllerTests {
    @Mock
    private PhotoSessionService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PhotoSessionController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void createUsesExactRouteAndReturns201() throws Exception {
        when(service.create()).thenReturn(new CreatePhotoSessionResponse("session-1", PhotoSessionStatus.CREATED));

        mockMvc.perform(post("/api/v1/photo-sessions"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").value("session-1"))
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    void uploadUsesExactMultipartFieldNames() throws Exception {
        when(service.uploadOriginal(eq("session-1"), any(), eq(28.6), eq(77.2), eq(12.0)))
                .thenReturn(new PhotoUploadResponse(
                        "session-1",
                        PhotoSessionStatus.PHOTO_UPLOADED,
                        "https://res.cloudinary.com/demo/original.png",
                        28.6,
                        77.2,
                        12.0
                ));

        mockMvc.perform(multipart("/api/v1/photo-sessions/session-1/photo")
                        .file(PhotoImageValidatorTests.file(
                                "photo.png",
                                "image/png",
                                PhotoImageValidatorTests.ONE_PIXEL_PNG
                        ))
                        .param("latitude", "28.6")
                        .param("longitude", "77.2")
                        .param("accuracy", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("session-1"))
                .andExpect(jsonPath("$.status").value("PHOTO_UPLOADED"))
                .andExpect(jsonPath("$.originalImageUrl").isNotEmpty());

        verify(service).uploadOriginal(eq("session-1"), any(), eq(28.6), eq(77.2), eq(12.0));
    }

    @Test
    void enhanceReturns202ProcessingImmediately() throws Exception {
        when(service.requestEnhancement("session-1"))
                .thenReturn(new PhotoEnhanceResponse("session-1", PhotoSessionStatus.PROCESSING));

        mockMvc.perform(post("/api/v1/photo-sessions/session-1/enhance"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.sessionId").value("session-1"))
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    void getReturnsPollingContractAndNeverEnablesEnhanceAgain() throws Exception {
        when(service.get("session-1")).thenReturn(new PhotoSessionResponse(
                "session-1",
                PhotoSessionStatus.COMPLETED,
                "https://res.cloudinary.com/demo/original.png",
                "https://res.cloudinary.com/demo/enhanced.jpg",
                28.6,
                77.2,
                12.0,
                Instant.parse("2026-07-25T00:00:00Z"),
                false
        ));

        mockMvc.perform(get("/api/v1/photo-sessions/session-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.originalImageUrl").isNotEmpty())
                .andExpect(jsonPath("$.enhancedImageUrl").isNotEmpty())
                .andExpect(jsonPath("$.canEnhanceAgain").value(false));
    }

    @Test
    void errorsExposeSafeMessageField() throws Exception {
        when(service.get("missing")).thenThrow(new PhotoSessionNotFoundException());

        mockMvc.perform(get("/api/v1/photo-sessions/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Photo session was not found."));
    }
}
