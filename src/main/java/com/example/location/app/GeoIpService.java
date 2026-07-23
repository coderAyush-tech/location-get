package com.example.location.app;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

@Service
public class GeoIpService {
    private final RestTemplate restTemplate;

    public GeoIpService() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(8_000);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    public LocationResponse locate(String clientIp) {
        if (!isPublicAddress(clientIp)) {
            throw new LocationLookupException("A public client IP address is required for IP geolocation");
        }
        URI uri = UriComponentsBuilder.fromUriString("https://ipwho.is/{ip}")
                .buildAndExpand(clientIp)
                .encode()
                .toUri();
        try {
            GeoIpResponse response = restTemplate.getForObject(uri, GeoIpResponse.class);
            if (response == null || !response.isSuccess() || response.getLatitude() == null || response.getLongitude() == null) {
                throw new LocationLookupException("IP geolocation is unavailable");
            }
            String address = String.join(", ", valueOrUnknown(response.getCity()), valueOrUnknown(response.getRegion()),
                    valueOrUnknown(response.getCountry()));
            return new LocationResponse(response.getLatitude(), response.getLongitude(), address, "ip",
                    "Estimated from public IP; accuracy is normally city or region level, not an exact address");
        } catch (RestClientException exception) {
            throw new LocationLookupException("IP geolocation is temporarily unavailable", exception);
        }
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
