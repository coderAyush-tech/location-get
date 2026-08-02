package com.example.location.app;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class LocationControllerRegressionTests {
    @Mock
    private LocationService locationService;
    @Mock
    private GeoIpService geoIpService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ClientIpResolver clientIpResolver = new ClientIpResolver(new String[]{"127.0.0.0/8"});
        mockMvc = MockMvcBuilders
                .standaloneSetup(new LocationController(locationService, geoIpService, clientIpResolver))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void existingGpsRouteAndResponseRemainUnchanged() throws Exception {
        when(locationService.reverseGeocode(
                org.mockito.ArgumentMatchers.any(LocationCordinates.class),
                org.mockito.ArgumentMatchers.eq("203.0.113.5")
        )).thenReturn(new LocationResponse(
                28.6139,
                77.209,
                "New Delhi",
                "gps",
                "Precise browser-provided coordinates",
                "203.0.113.5"
        ));

        mockMvc.perform(post("/api/location")
                        .header("X-Forwarded-For", "203.0.113.5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"latitude":28.6139,"longitude":77.209}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude").value(28.6139))
                .andExpect(jsonPath("$.source").value("gps"))
                .andExpect(jsonPath("$.clientIp").value("203.0.113.5"));
    }

    @Test
    void existingFallbackRouteAndResponseRemainUnchanged() throws Exception {
        when(geoIpService.locate("203.0.113.5")).thenReturn(new LocationResponse(
                28.6,
                77.2,
                "New Delhi, Delhi, India",
                "ip",
                "Estimated from public IP",
                "203.0.113.5"
        ));

        mockMvc.perform(post("/api/location/fallback")
                        .header("X-Forwarded-For", "203.0.113.5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("ip"))
                .andExpect(jsonPath("$.clientIp").value("203.0.113.5"));

        verify(geoIpService).locate("203.0.113.5");
    }
}
