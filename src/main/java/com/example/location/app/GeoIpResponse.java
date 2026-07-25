package com.example.location.app;

public record GeoIpResponse(
        double latitude,
        double longitude,
        String city,
        String region,
        String country) {
}
