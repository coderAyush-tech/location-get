package com.example.location.app.admin;

public record AdminCaptureQuery(
        int page,
        int size,
        String query,
        LocationSourceFilter locationSource
) {
    public enum LocationSourceFilter {
        ALL,
        GPS,
        GEO_IP,
        RAW_IP
    }
}
