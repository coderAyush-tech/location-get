package com.example.location.app;

public record LocationResponse(
        double latitude,
        double longitude,
        String address,
        String source,
        String accuracyNote,
        String clientIp) {
}
