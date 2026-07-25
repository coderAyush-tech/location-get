package com.example.location.app;

import com.mongodb.ConnectionString;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class MongoConnectionLogger {
    private static final Logger log = LoggerFactory.getLogger(MongoConnectionLogger.class);
    private final MongoTemplate mongoTemplate;
    private final Environment environment;

    public MongoConnectionLogger(MongoTemplate mongoTemplate, Environment environment) {
        this.mongoTemplate = mongoTemplate;
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verifyConnection() {
        logConfiguration();
        try {
            mongoTemplate.getDb().runCommand(new Document("ping", 1));
            log.info("MONGO_CONNECTION_SUCCESS database={}", mongoTemplate.getDb().getName());
        } catch (Exception exception) {
            log.error("MONGO_CONNECTION_FAILED type={} message={}", exception.getClass().getSimpleName(), exception.getMessage());
        }
    }

    private void logConfiguration() {
        String mongoEnvironmentUri = environment.getProperty("MONGODB_URI");
        String resolvedUri = environment.getProperty("spring.mongodb.uri");
        String hosts = "unavailable";

        try {
            if (resolvedUri != null && !resolvedUri.isBlank()) {
                hosts = String.join(",", new ConnectionString(resolvedUri).getHosts());
            }
        } catch (IllegalArgumentException exception) {
            hosts = "invalid-uri";
        }

        // Do not log connection strings: they often include database credentials.
        log.info("MONGO_CONFIGURATION environmentUriPresent={} resolvedHosts={}",
                mongoEnvironmentUri != null && !mongoEnvironmentUri.isBlank(), hosts);
    }
}
