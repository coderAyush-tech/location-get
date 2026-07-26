package com.example.location.app.photo;

import com.example.location.app.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DirectPhotoEnhancementControllerTests {
    @Mock
    private DirectPhotoEnhancementService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DirectPhotoEnhancementController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void returnsEnhancedImageBytesWithoutStorageUrl() throws Exception {
        byte[] enhanced = new byte[]{9, 8, 7};
        when(service.enhance(any()))
                .thenReturn(new EnhancedImage(enhanced, "image/jpeg"));

        mockMvc.perform(multipart("/api/v1/photo-enhancements")
                        .file(PhotoImageValidatorTests.file(
                                "photo.png",
                                "image/png",
                                PhotoImageValidatorTests.ONE_PIXEL_PNG
                        )))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes(enhanced))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string(
                        "Content-Disposition",
                        "inline; filename=\"photogenius-enhanced.jpg\""
                ));
    }

    @Test
    void returnsSafeJsonWhenGeminiFails() throws Exception {
        when(service.enhance(any()))
                .thenThrow(new PhotoApiException(
                        HttpStatus.BAD_GATEWAY,
                        "AI enhancement provider returned an error."
                ));

        mockMvc.perform(multipart("/api/v1/photo-enhancements")
                        .file(PhotoImageValidatorTests.file(
                                "photo.png",
                                "image/png",
                                PhotoImageValidatorTests.ONE_PIXEL_PNG
                        )))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message")
                        .value("AI enhancement provider returned an error."));
    }
}
