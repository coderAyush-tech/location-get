package com.example.location.app.admin;

public record AdminCaptureSummary(
        long totalCaptures,
        long capturesToday,
        long gpsCaptures,
        long ipFallbackCaptures,
        long storageBytes
) {
}
