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
                locationService.reverseGeocode(location, ClientIpResolver.resolve(request))
        );
    }

    /**
     * Called only when the browser user denies precise-location permission.
     * Uses the actual visitor IP received through Netlify/Render proxy headers.
     */
    @PostMapping("/location/fallback")
    public ResponseEntity<LocationResponse> locationFallback(HttpServletRequest request) {
        return ResponseEntity.ok(
                geoIpService.locate(ClientIpResolver.resolve(request))
        );
    }
}
