package com.example.location.app;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AddressRepository extends MongoRepository<SavedAddress, String> {
    // Agar tumhe custom query chahiye to yahan likh sakte ho
    // Example: List<SavedAddress> findByFullAddress(String fullAddress);
}