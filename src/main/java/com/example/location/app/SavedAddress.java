package com.example.location.app;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "addresses")
public class SavedAddress {
    @Id
    private String id;
    private String fullAddress;
    private Double latitude;
    private Double longitude;
    private String source;
    private String clientIp;
    private Instant savedAt;

    public SavedAddress(String fullAddress, Double latitude, Double longitude, String source, String clientIp) {
        this.fullAddress = fullAddress;
        this.latitude = latitude;
        this.longitude = longitude;
        this.source = source;
        this.clientIp = clientIp;
        this.savedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getFullAddress() { return fullAddress; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public String getSource() { return source; }
    public String getClientIp() { return clientIp; }
    public Instant getSavedAt() { return savedAt; }
}
