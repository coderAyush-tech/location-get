package com.example.location.app.admin;

import com.example.location.app.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminCaptureApiTests {
    private static final byte[] PHOTO_BYTES = new byte[]{1, 2, 3, 4, 5};

    private FakeAdminCaptureStore store;
    private AdminTokenService tokenService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        store = new FakeAdminCaptureStore();
        AdminCaptureService captureService = new AdminCaptureService(store);
        AdminProperties properties = AdminAuthControllerTests.properties(5);
        tokenService = new AdminTokenService(properties, new ObjectMapper());
        AdminAuthorizationInterceptor interceptor = new AdminAuthorizationInterceptor(tokenService);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminCaptureController(captureService))
                .setControllerAdvice(new ApiExceptionHandler())
                .addInterceptors(interceptor)
                .addFilters(new AdminSecurityHeadersFilter())
                .build();
    }

    @Test
    void captureListRequiresBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/admin/captures"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void invalidAndExpiredTokensReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/captures")
                        .header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized());

        String expired = tokenService.issue("admin", "ADMIN", Duration.ofSeconds(-1)).value();
        mockMvc.perform(get("/api/v1/admin/captures")
                        .header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signedNonAdminTokenReturnsForbidden() throws Exception {
        String token = tokenService.issue("viewer", "USER", Duration.ofMinutes(15)).value();
        mockMvc.perform(get("/api/v1/admin/captures")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Access denied"));
    }

    @Test
    void authorizedListUsesPaginationSearchFilterAndNewestFirstContract() throws Exception {
        mockMvc.perform(get("/api/v1/admin/captures")
                        .param("page", "2")
                        .param("size", "1")
                        .param("sort", "createdAt,desc")
                        .param("query", "Delhi")
                        .param("locationSource", "GEO_IP")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(jsonPath("$.number").value(2))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].id").value("newest-capture"))
                .andExpect(jsonPath("$.content[0].fileName").value("camera.jpg"))
                .andExpect(jsonPath("$.content[0].locationSource").value("GEO_IP"))
                .andExpect(jsonPath("$.content[0].city").value("Delhi"))
                .andExpect(jsonPath("$.content[0].country").value("India"))
                .andExpect(jsonPath("$.content[0].width").doesNotExist())
                .andExpect(jsonPath("$.summary.storageBytes").value(300));

        assertEquals(2, store.lastQuery.page());
        assertEquals(1, store.lastQuery.size());
        assertEquals("Delhi", store.lastQuery.query());
        assertEquals(AdminCaptureQuery.LocationSourceFilter.GEO_IP, store.lastQuery.locationSource());
    }

    @Test
    void authorizedPhotoReturnsStoredBytesAndMimeType() throws Exception {
        mockMvc.perform(get("/api/v1/admin/captures/newest-capture/photo")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes(PHOTO_BYTES))
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("inline")));
    }

    @Test
    void missingPhotoReturnsNotFoundProblem() throws Exception {
        mockMvc.perform(get("/api/v1/admin/captures/missing/photo")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Photo not found"));
    }

    private String bearerAdmin() {
        return "Bearer " + tokenService.issueAdminToken("admin").value();
    }

    private static class FakeAdminCaptureStore implements AdminCaptureStore {
        AdminCaptureQuery lastQuery;

        @Override
        public CaptureSlice find(AdminCaptureQuery query) {
            lastQuery = query;
            AdminCaptureMetadata newest = new AdminCaptureMetadata(
                    "newest-capture",
                    "camera.jpg",
                    "image/jpeg",
                    100,
                    28.6139,
                    77.209,
                    null,
                    "ip",
                    "Delhi, Delhi, India",
                    "203.0.113.5",
                    Instant.parse("2026-08-02T12:30:00Z")
            );
            return new CaptureSlice(List.of(newest), 3);
        }

        @Override
        public AdminCaptureSummary summary() {
            return new AdminCaptureSummary(3, 1, 1, 2, 300);
        }

        @Override
        public Optional<AdminStoredPhoto> findPhoto(String captureId) {
            if (!"newest-capture".equals(captureId)) {
                return Optional.empty();
            }
            return Optional.of(new AdminStoredPhoto(PHOTO_BYTES, "image/jpeg", "camera.jpg"));
        }
    }
}
