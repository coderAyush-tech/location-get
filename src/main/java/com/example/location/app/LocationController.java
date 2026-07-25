package com.example.location.app;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class LocationController {

    private final LocationService locationService;
    private final GeoIpService geoIpService;

    public LocationController(LocationService locationService, GeoIpService geoIpService) {
        this.locationService = locationService;
        this.geoIpService = geoIpService;
    }

    @PostMapping("/location")
    public ResponseEntity<LocationResponse> receiveLocation(
            @Valid @RequestBody LocationCordinates location,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(
                locationService.reverseGeocode(location, getClientIp(request))
        );
    }

    /**
     * Called only when the browser user denies precise-location permission.
     * Uses the actual visitor IP received through Netlify/Render proxy headers.
     */
    @PostMapping("/location/fallback")
    public ResponseEntity<LocationResponse> locationFallback(HttpServletRequest request) {
        return ResponseEntity.ok(
                geoIpService.locate(getClientIp(request))
        );
    }

    private String getClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");

        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String clientIp = forwardedFor.split(",")[0].trim();

            if (!clientIp.isBlank() && !"unknown".equalsIgnoreCase(clientIp)) {
                return clientIp;
            }
        }

        String realIp = request.getHeader("X-Real-IP");

        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        return request.getRemoteAddr();
    }
}
