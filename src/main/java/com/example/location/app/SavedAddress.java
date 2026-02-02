package com.example.location.app;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "addresses")
public class SavedAddress {

    @Id
    private String id;
    private String fullAddress;

    public SavedAddress(String fullAddress) {
        this.fullAddress = fullAddress;
    }

    public String getId() {
        return id;
    }

    public String getFullAddress() {
        return fullAddress;
    }

    public void setFullAddress(String fullAddress) {
        this.fullAddress = fullAddress;
    }
}