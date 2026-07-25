package com.example.location.app;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

@Service
public class GeoIpService {
    private final RestTemplate restTemplate;
    private final AddressRepository addressRepository;
    private final boolean storeLocations;

    public GeoIpService(AddressRepository addressRepository,
                        @Value("${app.location.store-enabled:true}") boolean storeLocations) {
        this.addressRepository = addressRepository;
        this.storeLocations = storeLocations;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(8_000);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    public LocationResponse locate(String clientIp) {
        if (!isPublicAddress(clientIp)) {
            throw new LocationLookupException("A public client IP address is required for IP geolocation");
        }

        GeoIpResponse geoIp = lookup(clientIp);
        String address = String.join(", ", valueOrUnknown(geoIp.city()), valueOrUnknown(geoIp.region()),
                valueOrUnknown(geoIp.country()));
        LocationResponse result = new LocationResponse(geoIp.latitude(), geoIp.longitude(), address, "ip",
                "Estimated from public IP; accuracy is normally city or region level, not an exact address");
        if (storeLocations) {
            addressRepository.save(new SavedAddress(result.address(), result.latitude(), result.longitude(),
                    result.source(), clientIp));
        }
        return result;
    }

    private GeoIpResponse lookup(String clientIp) {
        List<URI> providers = List.of(
                UriComponentsBuilder.fromUriString("https://ipwho.is/{ip}").buildAndExpand(clientIp).encode().toUri(),
                UriComponentsBuilder.fromUriString("https://ipapi.co/{ip}/json/").buildAndExpand(clientIp).encode().toUri()
        );

        for (URI provider : providers) {
            try {
                Map<?, ?> response = restTemplate.getForObject(provider, Map.class);
                GeoIpResponse result = mapResponse(response);
                if (result != null) {
                    return result;
                }
            } catch (RestClientException ignored) {
                // Try the independent provider before reporting a temporary failure.
            }
        }
        throw new LocationLookupException("IP geolocation is temporarily unavailable");
    }

    static GeoIpResponse mapResponse(Map<?, ?> response) {
        if (response == null || Boolean.FALSE.equals(response.get("success")) || response.containsKey("error")) {
            return null;
        }
        Double latitude = number(response.get("latitude"));
        Double longitude = number(response.get("longitude"));
        if (latitude == null || longitude == null) {
            return null;
        }
        String country = string(response.get("country_name"));
        if (country == null) {
            country = string(response.get("country"));
        }
        return new GeoIpResponse(latitude, longitude, string(response.get("city")), string(response.get("region")), country);
    }

    private static Double number(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String string) {
            try {
                return Double.parseDouble(string);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String string(Object value) {
        return value instanceof String string && !string.isBlank() ? string : null;
    }

    private boolean isPublicAddress(String clientIp) {
        try {
            InetAddress address = InetAddress.getByName(clientIp);
            return !(address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress() || address.isMulticastAddress());
        } catch (UnknownHostException exception) {
            return false;
        }
    }

    private String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "Unknown" : value;
    }
}
