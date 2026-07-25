package com.example.location.app;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GeoIpResponseMappingTests {
    @Test
    void mapsIpWhoResponse() {
        GeoIpResponse response = GeoIpService.mapResponse(Map.of(
                "success", true, "latitude", 28.61, "longitude", 77.20,
                "city", "New Delhi", "region", "Delhi", "country", "India"));

        assertEquals(28.61, response.latitude());
        assertEquals("India", response.country());
    }

    @Test
    void mapsIpApiResponse() {
        GeoIpResponse response = GeoIpService.mapResponse(Map.of(
                "latitude", "28.61", "longitude", "77.20",
                "city", "New Delhi", "region", "Delhi", "country_name", "India"));

        assertEquals(77.20, response.longitude());
        assertEquals("India", response.country());
    }

    @Test
    void rejectsProviderError() {
        assertNull(GeoIpService.mapResponse(Map.of("error", true, "reason", "rate limited")));
    }
}
