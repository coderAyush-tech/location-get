package com.example.location.app.capture;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "captured_photos")
public class CapturedPhoto {
    @Id
    private String id;
    private byte[] photo;
    private String contentType;
    private String originalFilename;
    private long sizeBytes;
    private Double latitude;
    private Double longitude;
    private Double accuracy;
    private String locationSource;
    private String address;
    private String clientIp;
    private Instant savedAt;

    public CapturedPhoto(
            byte[] photo,
            String contentType,
            String originalFilename,
            Double latitude,
            Double longitude,
            Double accuracy,
            String locationSource,
            String address,
            String clientIp
    ) {
        this.photo = photo.clone();
        this.contentType = contentType;
        this.originalFilename = originalFilename;
        this.sizeBytes = photo.length;
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracy = accuracy;
        this.locationSource = locationSource;
        this.address = address;
        this.clientIp = clientIp;
        this.savedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public byte[] getPhoto() {
        return photo.clone();
    }

    public String getContentType() {
        return contentType;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public Double getAccuracy() {
        return accuracy;
    }

    public String getLocationSource() {
        return locationSource;
    }

    public String getAddress() {
        return address;
    }

    public String getClientIp() {
        return clientIp;
    }

    public Instant getSavedAt() {
        return savedAt;
    }
}
