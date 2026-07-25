package com.example.location.app.photo;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PhotoSessionRepository extends MongoRepository<PhotoSession, String> {
}
