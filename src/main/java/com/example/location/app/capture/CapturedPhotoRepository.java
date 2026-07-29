package com.example.location.app.capture;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface CapturedPhotoRepository extends MongoRepository<CapturedPhoto, String> {
}
