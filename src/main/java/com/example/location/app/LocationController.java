package com.example.location.app;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "${app.cors.allowed-origins}")
public class LocationController {
    private final LocationService locationService;
    private final GeoIpService geoIpService;

    public LocationController(LocationService locationService, GeoIpService geoIpService) {
        this.locationService = locationService;
        this.geoIpService = geoIpService;
    }

    @PostMapping("/location")
    public ResponseEntity<LocationResponse> receiveLocation(@Valid @RequestBody LocationCordinates location,
                                                            HttpServletRequest request) {
        return ResponseEntity.ok(locationService.reverseGeocode(location, request.getRemoteAddr()));
    }

    /**
     * Call this only after the browser reports that precise location permission was denied.
     * IP geolocation is an estimate (normally city/region), not an exact user location.
     */
    @PostMapping("/location/fallback")
    public ResponseEntity<LocationResponse> locationFallback(HttpServletRequest request) {
        String clientIp = request.getRemoteAddr();
        LocationResponse result = geoIpService.locate(clientIp);
        return ResponseEntity.status(HttpStatus.OK).body(result);
    }
}
