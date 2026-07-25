package com.example.location.app;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

@Component
public class MongoConnectionLogger {
    private static final Logger log = LoggerFactory.getLogger(MongoConnectionLogger.class);
    private final MongoTemplate mongoTemplate;

    public MongoConnectionLogger(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verifyConnection() {
        try {
            mongoTemplate.getDb().runCommand(new Document("ping", 1));
            log.info("MONGO_CONNECTION_SUCCESS database={}", mongoTemplate.getDb().getName());
        } catch (Exception exception) {
            log.error("MONGO_CONNECTION_FAILED type={} message={}", exception.getClass().getSimpleName(), exception.getMessage());
        }
    }
}
