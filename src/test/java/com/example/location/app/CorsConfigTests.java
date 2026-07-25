package com.example.location.app;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CorsConfigTests {

    @Test
    void productionFrontendCanPostUploadAndPoll() {
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
    }

    static class TestCorsRegistry extends CorsRegistry {
        Map<String, CorsConfiguration> configurations() {
            return getCorsConfigurations();
        }
    }
}
