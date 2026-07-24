package com.example.location.app;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Service
public class LocationService {
    private final AddressRepository addressRepository;
    private final RestTemplate restTemplate;
    private final String token;
    private final boolean storeLocations;

    public LocationService(
            AddressRepository addressRepository,
            @Value("${locationiq.token:}") String token,
            @Value("${app.location.store-enabled:false}") boolean storeLocations) {
        this.addressRepository = addressRepository;
        this.token = token;
        this.storeLocations = storeLocations;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(8_000);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    public LocationResponse reverseGeocode(LocationCordinates location, String clientIp) {
        LocationResponse result;
        if (token.isBlank()) {
            result = fallbackResponse(location, "Address lookup is not configured; coordinates were saved");
            save(result, clientIp);
            return result;
        }
        try {
            URI uri = UriComponentsBuilder.fromUriString("https://us1.locationiq.com/v1/reverse")
                    .queryParam("key", token)
                    .queryParam("lat", location.getLatitude())
                    .queryParam("lon", location.getLongitude())
                    .queryParam("format", "json")
                    .build()
                    .encode()
                    .toUri();
            AddressResponse response = restTemplate.getForObject(uri, AddressResponse.class);
            if (response == null || response.getAddress() == null) {
                result = fallbackResponse(location, "No address found; coordinates were saved");
            } else {
                String address = response.getDisplay_name();
                if (address == null || address.isBlank()) {
                    address = formatAddress(response.getAddress());
                }
                result = new LocationResponse(location.getLatitude(), location.getLongitude(), address, "gps",
                        "Precise browser-provided coordinates");
            }
        } catch (RestClientException exception) {
            result = fallbackResponse(location, "Address lookup is unavailable; coordinates were saved");
        }
        save(result, clientIp);
        return result;
    }

    private LocationResponse fallbackResponse(LocationCordinates location, String note) {
        return new LocationResponse(location.getLatitude(), location.getLongitude(), "Address unavailable", "gps", note);
    }

    private void save(LocationResponse location, String clientIp) {
        if (storeLocations) {
            addressRepository.save(new SavedAddress(location.address(), location.latitude(), location.longitude(),
                    location.source(), clientIp));
        }
    }

    private String formatAddress(AddressResponse.Address address) {
        return String.join(", ", nonBlank(address.getRoad()), nonBlank(address.getCity()),
                nonBlank(address.getState()), nonBlank(address.getCountry()), nonBlank(address.getPostcode()));
    }

    private String nonBlank(String value) {
        return value == null || value.isBlank() ? "Unknown" : value;
    }
}
