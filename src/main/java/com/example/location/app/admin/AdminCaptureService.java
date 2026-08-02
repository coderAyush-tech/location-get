package com.example.location.app.admin;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class AdminCaptureService {
    private final AdminCaptureStore store;

    public AdminCaptureService(AdminCaptureStore store) {
        this.store = store;
    }

    public AdminCapturePageResponse list(
            int page,
            int size,
            String sort,
            String query,
            String locationSource
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw badRequest("Page must be non-negative and size must be between 1 and 100.");
        }
        String normalizedSort = sort == null ? "createdAt,desc" : sort.replace(" ", "");
        if (!"createdAt,desc".equalsIgnoreCase(normalizedSort)
                && !"savedAt,desc".equalsIgnoreCase(normalizedSort)) {
            throw badRequest("Only newest-first sorting is supported.");
        }

        AdminCaptureQuery.LocationSourceFilter source;
        try {
            source = AdminCaptureQuery.LocationSourceFilter.valueOf(
                    locationSource == null || locationSource.isBlank()
                            ? "ALL"
                            : locationSource.trim().toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException exception) {
            throw badRequest("locationSource must be ALL, GPS, GEO_IP, or RAW_IP.");
        }

        AdminCaptureQuery request = new AdminCaptureQuery(page, size, normalizedQuery(query), source);
        AdminCaptureStore.CaptureSlice slice = store.find(request);
        List<AdminCaptureItem> content = slice.content().stream().map(this::toItem).toList();
        int totalPages = slice.totalElements() == 0
                ? 0
                : (int) Math.ceil((double) slice.totalElements() / size);
        return new AdminCapturePageResponse(
                content,
                page,
                size,
                slice.totalElements(),
                totalPages,
                store.summary()
        );
    }

    public AdminStoredPhoto photo(String captureId) {
        if (captureId == null || captureId.isBlank() || captureId.length() > 200) {
            throw notFound();
        }
        return store.findPhoto(captureId.trim()).orElseThrow(this::notFound);
    }

    private AdminCaptureItem toItem(AdminCaptureMetadata capture) {
        AddressParts addressParts = AddressParts.from(capture.address(), capture.locationSource());
        return new AdminCaptureItem(
                capture.id(),
                true,
                capture.originalFilename(),
                capture.contentType(),
                capture.sizeBytes(),
                null,
                null,
                capture.savedAt(),
                capture.latitude(),
                capture.longitude(),
                capture.accuracy(),
                normalizeLocationSource(capture.locationSource()),
                capture.clientIp(),
                capture.address(),
                addressParts.city(),
                addressParts.region(),
                addressParts.country(),
                null,
                capture.sizeBytes() > 0
        );
    }

    private String normalizeLocationSource(String source) {
        if (source == null) {
            return null;
        }
        return switch (source.toLowerCase(Locale.ROOT)) {
            case "gps" -> "GPS";
            case "ip", "geo_ip" -> "GEO_IP";
            case "raw_ip" -> "RAW_IP";
            default -> source.toUpperCase(Locale.ROOT);
        };
    }

    private String normalizedQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String normalized = query.trim();
        if (normalized.length() > 200) {
            throw badRequest("Search query is too long.");
        }
        return normalized;
    }

    private AdminApiException badRequest(String message) {
        return new AdminApiException(HttpStatus.BAD_REQUEST, "Invalid admin request", message);
    }

    private AdminApiException notFound() {
        return new AdminApiException(HttpStatus.NOT_FOUND, "Photo not found", "Capture or photo was not found.");
    }

    private record AddressParts(String city, String region, String country) {
        static AddressParts from(String address, String locationSource) {
            if (address == null || address.isBlank()
                    || locationSource == null
                    || !(locationSource.equalsIgnoreCase("ip") || locationSource.equalsIgnoreCase("geo_ip"))) {
                return new AddressParts(null, null, null);
            }
            String[] parts = java.util.Arrays.stream(address.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty() && !"Unknown".equalsIgnoreCase(value))
                    .toArray(String[]::new);
            if (parts.length >= 3) {
                return new AddressParts(parts[0], parts[1], parts[parts.length - 1]);
            }
            if (parts.length == 2) {
                return new AddressParts(parts[0], null, parts[1]);
            }
            return parts.length == 1
                    ? new AddressParts(parts[0], null, null)
                    : new AddressParts(null, null, null);
        }
    }
}
