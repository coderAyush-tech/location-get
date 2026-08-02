package com.example.location.app;

public record LocationResponse(
        Double latitude,
        Double longitude,
        String address,
        String source,
        String accuracyNote,
        String clientIp) {
}
