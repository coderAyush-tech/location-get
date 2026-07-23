package com.example.location.app;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public class LocationCordinates {
    @NotNull
    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    private Double latitude;

    @NotNull
    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    private Double longitude;

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
}
