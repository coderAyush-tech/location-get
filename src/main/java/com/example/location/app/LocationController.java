package com.example.location.app;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api")
@CrossOrigin("*")
public class LocationController {

    @Value("${locationiq.token}")
    private String token;

    @PostMapping("/location")
    public ResponseEntity<String> receiveLocation(@RequestBody LocationCordinates location) {
        double lat = location.getLatitude();
        double lon = location.getLongitude();

        String url = "https://us1.locationiq.com/v1/reverse?key=" + token
                + "&lat=" + lat + "&lon=" + lon + "&format=json";

        RestTemplate restTemplate = new RestTemplate();
        AddressResponse response = restTemplate.getForObject(url, AddressResponse.class);

        AddressResponse.Address addr = response.getAddress();
        String road = addr.getRoad() != null ? addr.getRoad() : "N/A";

        String formatted = "Road: " + road +
                ", City: " + addr.getCity() +
                ", State: " + addr.getState() +
                ", Country: " + addr.getCountry() +
                ", Postcode: " + addr.getPostcode();

        return ResponseEntity.ok(formatted);
    }
}