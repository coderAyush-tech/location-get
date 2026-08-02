package com.example.location.app;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CorsConfigTests {

    @Test
    void productionFrontendCanUploadCameraPhoto() {
        TestCorsRegistry registry = new TestCorsRegistry();
        CorsConfig config = new CorsConfig(new String[]{
                "https://bestue.netlify.app",
                "http://localhost:5173"
        });

        config.addCorsMappings(registry);

        CorsConfiguration cors = registry.configurations().get("/api/**");
        assertTrue(cors.getAllowedOriginPatterns().contains("https://bestue.netlify.app"));
        assertTrue(cors.getAllowedOriginPatterns().contains("http://localhost:5173"));
        assertTrue(cors.getAllowedMethods().contains("POST"));
        assertTrue(cors.getAllowedMethods().contains("GET"));
        assertTrue(cors.getAllowedMethods().contains("OPTIONS"));
        assertTrue(cors.getAllowedHeaders().contains("Authorization"));
        assertTrue(cors.getAllowedHeaders().contains("Content-Type"));
        assertTrue(cors.getAllowedHeaders().contains("Accept"));
    }

    @Test
    void productionAdminAuthorizationPreflightSucceeds() throws Exception {
        TestCorsRegistry registry = new TestCorsRegistry();
        new CorsConfig(new String[]{
                "https://bestue.netlify.app",
                "http://localhost:5173"
        }).addCorsMappings(registry);
        CorsConfiguration cors = registry.configurations().get("/api/**");
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/admin/captures");
        request.addHeader(HttpHeaders.ORIGIN, "https://bestue.netlify.app");
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET");
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = new DefaultCorsProcessor().processRequest(cors, request, response);

        assertTrue(allowed);
        assertTrue(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)
                .contains("https://bestue.netlify.app"));
        assertTrue(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS)
                .toLowerCase().contains("authorization"));
    }

    static class TestCorsRegistry extends CorsRegistry {
        Map<String, CorsConfiguration> configurations() {
            return getCorsConfigurations();
        }
    }
}
