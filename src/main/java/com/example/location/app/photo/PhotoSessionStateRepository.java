package com.example.location.app.photo;

import org.springframework.dao.DataAccessException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public class PhotoSessionStateRepository {
    private final MongoTemplate mongoTemplate;
    private final PhotoSessionRepository repository;

    public PhotoSessionStateRepository(MongoTemplate mongoTemplate, PhotoSessionRepository repository) {
        this.mongoTemplate = mongoTemplate;
        this.repository = repository;
    }

    public PhotoSession markProcessing(String sessionId, String model, String promptVersion) {
        try {
            Query query = Query.query(Criteria.where("_id").is(sessionId)
                    .and("status").is(PhotoSessionStatus.PHOTO_UPLOADED)
                    .and("originalImageUrl").ne(null));
            Update update = new Update()
                    .set("status", PhotoSessionStatus.PROCESSING)
                    .set("geminiModel", model)
                    .set("promptVersion", promptVersion)
                    .set("updatedAt", Instant.now())
                    .unset("errorMessage");

            PhotoSession processing = mongoTemplate.findAndModify(
                    query,
                    update,
                    FindAndModifyOptions.options().returnNew(true),
                    PhotoSession.class
            );
            if (processing != null) {
                return processing;
            }

            PhotoSession current = repository.findById(sessionId)
                    .orElseThrow(PhotoSessionNotFoundException::new);
            current.ensureEnhancementCanStart();
            throw new InvalidPhotoSessionStateException("Photo session could not enter processing state.");
        } catch (PhotoApiException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw databaseUnavailable(exception);
        }
    }

    public boolean markCompleted(String sessionId, StoredImage enhancedImage) {
        try {
            Query query = Query.query(Criteria.where("_id").is(sessionId)
                    .and("status").is(PhotoSessionStatus.PROCESSING));
            Update update = new Update()
                    .set("status", PhotoSessionStatus.COMPLETED)
                    .set("enhancedImageUrl", enhancedImage.url())
                    .set("enhancedImageStorageId", enhancedImage.storageId())
                    .set("updatedAt", Instant.now())
                    .unset("errorMessage");
            return mongoTemplate.updateFirst(query, update, PhotoSession.class).getModifiedCount() == 1;
        } catch (DataAccessException exception) {
            throw databaseUnavailable(exception);
        }
    }

    public void markFailed(String sessionId, String safeFailureCode) {
        try {
            Query query = Query.query(Criteria.where("_id").is(sessionId)
                    .and("status").is(PhotoSessionStatus.PROCESSING));
            Update update = new Update()
                    .set("status", PhotoSessionStatus.FAILED)
                    .set("errorMessage", safeFailureCode)
                    .set("updatedAt", Instant.now());
            mongoTemplate.updateFirst(query, update, PhotoSession.class);
        } catch (DataAccessException exception) {
            throw databaseUnavailable(exception);
        }
    }

    public long markAbandonedProcessingFailed() {
        try {
            Query query = Query.query(Criteria.where("status").is(PhotoSessionStatus.PROCESSING));
            Update update = new Update()
                    .set("status", PhotoSessionStatus.FAILED)
                    .set("errorMessage", "PROCESS_INTERRUPTED_BY_RESTART")
                    .set("updatedAt", Instant.now());
            return mongoTemplate.updateMulti(query, update, PhotoSession.class).getModifiedCount();
        } catch (DataAccessException exception) {
            throw databaseUnavailable(exception);
        }
    }

    private PhotoApiException databaseUnavailable(DataAccessException exception) {
        return new PhotoApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "Photo session storage is temporarily unavailable.", exception);
    }
}
