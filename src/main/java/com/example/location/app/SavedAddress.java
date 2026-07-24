package com.example.location.app;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "addresses")
public class SavedAddress {
    @Id
    private String id;
    private String fullAddress;
    private double latitude;
    private double longitude;
    private String source;
    private String clientIp;
    private Instant savedAt;

    public SavedAddress(String fullAddress, double latitude, double longitude, String source, String clientIp) {
        this.fullAddress = fullAddress;
        this.latitude = latitude;
        this.longitude = longitude;
        this.source = source;
        this.clientIp = clientIp;
        this.savedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getFullAddress() { return fullAddress; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getSource() { return source; }
    public String getClientIp() { return clientIp; }
    public Instant getSavedAt() { return savedAt; }
}
