package com.example.location.app.health;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

@Service
public class KeepAliveService {
    private static final Logger log = LoggerFactory.getLogger(KeepAliveService.class);

    private final MongoTemplate mongoTemplate;
    private final String expectedToken;

    public KeepAliveService(
            MongoTemplate mongoTemplate,
            @Value("${app.keep-alive.token:}") String expectedToken
    ) {
        this.mongoTemplate = mongoTemplate;
        this.expectedToken = expectedToken;
    }

    public KeepAliveResponse ping(String suppliedToken) {
        verifyToken(suppliedToken);

        try {
            Document result = mongoTemplate.executeCommand(new Document("ping", 1));
            Number ok = result.get("ok", Number.class);
            if (ok == null || ok.doubleValue() != 1.0) {
                throw new KeepAliveException(HttpStatus.SERVICE_UNAVAILABLE,
                        "MongoDB did not acknowledge the keep-alive ping.");
            }
        } catch (KeepAliveException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            log.warn("KEEP_ALIVE_MONGO_FAILED type={}", exception.getClass().getSimpleName());
            throw new KeepAliveException(HttpStatus.SERVICE_UNAVAILABLE,
                    "MongoDB is unavailable.");
        } catch (RuntimeException exception) {
            log.warn("KEEP_ALIVE_MONGO_FAILED type={}", exception.getClass().getSimpleName());
            throw new KeepAliveException(HttpStatus.SERVICE_UNAVAILABLE,
                    "MongoDB ping failed.");
        }

        log.info("KEEP_ALIVE_SUCCESS mongodb=up");
        return new KeepAliveResponse("up", "up", Instant.now());
    }

    private void verifyToken(String suppliedToken) {
        if (expectedToken == null || expectedToken.isBlank()) {
            throw new KeepAliveException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Keep-alive token is not configured.");
        }
        if (suppliedToken == null || suppliedToken.isBlank()
                || !MessageDigest.isEqual(
                expectedToken.getBytes(StandardCharsets.UTF_8),
                suppliedToken.getBytes(StandardCharsets.UTF_8))) {
            throw new KeepAliveException(HttpStatus.UNAUTHORIZED,
                    "Invalid keep-alive token.");
        }
    }
}
