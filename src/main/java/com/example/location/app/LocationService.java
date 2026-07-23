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

    public LocationResponse reverseGeocode(LocationCordinates location) {
        if (token.isBlank()) {
            throw new LocationLookupException("Location service is not configured");
        }

        URI uri = UriComponentsBuilder.fromUriString("https://us1.locationiq.com/v1/reverse")
                .queryParam("key", token)
                .queryParam("lat", location.getLatitude())
                .queryParam("lon", location.getLongitude())
                .queryParam("format", "json")
                .build()
                .encode()
                .toUri();

        try {
            AddressResponse response = restTemplate.getForObject(uri, AddressResponse.class);
            if (response == null || response.getAddress() == null) {
                throw new LocationLookupException("No address found for these coordinates");
            }

            String address = response.getDisplay_name();
            if (address == null || address.isBlank()) {
                address = formatAddress(response.getAddress());
            }
            LocationResponse result = new LocationResponse(
                    location.getLatitude(), location.getLongitude(), address, "gps",
                    "Precise browser-provided coordinates");

            if (storeLocations) {
                addressRepository.save(new SavedAddress(address));
            }
            return result;
        } catch (RestClientException exception) {
            throw new LocationLookupException("Location lookup is temporarily unavailable", exception);
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
