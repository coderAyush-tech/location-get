package com.example.location.app;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api")
@CrossOrigin("*")
public class LocationController {

    private final AddressRepository addressRepository;

    @Value("${locationiq.token}")
    private String token;

    public LocationController(AddressRepository addressRepository) {
        this.addressRepository = addressRepository;
    }

    @PostMapping("/location")
    public ResponseEntity<String> receiveLocation(@RequestBody LocationCordinates location) {
        double lat = location.getLatitude();
        double lon = location.getLongitude();

        String url = "https://us1.locationiq.com/v1/reverse?key=" + token
                + "&lat=" + lat + "&lon=" + lon + "&format=json";

        RestTemplate restTemplate = new RestTemplate();
        AddressResponse response = restTemplate.getForObject(url, AddressResponse.class);

        if (response == null || response.getAddress() == null) {
            System.out.println("No address found for lat=" + lat + ", lon=" + lon);
            return ResponseEntity.badRequest().body("Invalid response from LocationIQ");
        }

        AddressResponse.Address addr = response.getAddress();
        String road = addr.getRoad() != null ? addr.getRoad() : "N/A";

        String formatted = "Coordinates: (" + lat + ", " + lon + ") | " +
                "Road: " + road +
                ", City: " + addr.getCity() +
                ", State: " + addr.getState() +
                ", Country: " + addr.getCountry() +
                ", Postcode: " + addr.getPostcode();

        // ✅ Print in logs with coordinates
        System.out.println("Full Address + Coords (Not Saved): " + formatted);

        // ❌ Commented out DB save
         addressRepository.save(new SavedAddress(formatted));

        return ResponseEntity.ok(formatted);
    }

    // Optional test endpoint (commented out)
     @GetMapping("/testdb")
     public ResponseEntity<String> testDb() {
         SavedAddress test = new SavedAddress("Test Address");
         addressRepository.save(test);
         return ResponseEntity.ok("Saved test address with id: " + test.getId());
     }
}