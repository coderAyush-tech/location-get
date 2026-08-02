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
    private final ClientIpResolver clientIpResolver;

    public LocationController(
            LocationService locationService,
            GeoIpService geoIpService,
            ClientIpResolver clientIpResolver
    ) {
        this.locationService = locationService;
        this.geoIpService = geoIpService;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/location")
    public ResponseEntity<LocationResponse> receiveLocation(
            @Valid @RequestBody LocationCordinates location,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(
                locationService.reverseGeocode(location, clientIpResolver.resolve(request))
        );
    }

    /**
     * Called only when the browser user denies precise-location permission.
     * Uses the actual visitor IP received through Netlify/Render proxy headers.
     */
    @PostMapping("/location/fallback")
    public ResponseEntity<LocationResponse> locationFallback(HttpServletRequest request) {
        return ResponseEntity.ok(
                geoIpService.locate(clientIpResolver.resolve(request))
        );
    }
}
