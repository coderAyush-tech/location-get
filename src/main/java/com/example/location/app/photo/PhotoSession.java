package com.example.location.app.photo;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Document(collection = "photo_sessions")
public class PhotoSession {
    @Id
    private String id;

    @Version
    private Long version;

    private String originalImageUrl;
    private String originalImageStorageId;
    private String originalImageContentType;
    private String enhancedImageUrl;
    private String enhancedImageStorageId;
    private Double latitude;
    private Double longitude;
    private Double locationAccuracy;
    private PhotoSessionStatus status;
    private String geminiModel;
    private String promptVersion;
    private Instant createdAt;
    private Instant updatedAt;
    private String errorMessage;

    protected PhotoSession() {
    }

    private PhotoSession(String id, Instant now) {
        this.id = id;
        this.status = PhotoSessionStatus.CREATED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static PhotoSession create() {
        return new PhotoSession(UUID.randomUUID().toString(), Instant.now());
    }

    public void ensurePhotoCanBeUploaded() {
        if (status != PhotoSessionStatus.CREATED) {
            throw new InvalidPhotoSessionStateException(
                    "Original photo can only be uploaded to a newly created session.");
        }
    }

    public void attachOriginal(
            StoredImage storedImage,
            Double latitude,
            Double longitude,
            Double accuracy
    ) {
        ensurePhotoCanBeUploaded();
        this.originalImageUrl = storedImage.url();
        this.originalImageStorageId = storedImage.storageId();
        this.originalImageContentType = storedImage.contentType();
        this.latitude = latitude;
        this.longitude = longitude;
        this.locationAccuracy = accuracy;
        this.status = PhotoSessionStatus.PHOTO_UPLOADED;
        this.errorMessage = null;
        this.updatedAt = Instant.now();
    }

    public void ensureEnhancementCanStart() {
        if (status == PhotoSessionStatus.PROCESSING) {
            throw new InvalidPhotoSessionStateException("This photo session is already being processed.");
        }
        if (status != PhotoSessionStatus.PHOTO_UPLOADED || originalImageUrl == null) {
            throw new InvalidPhotoSessionStateException(
                    "An original photo must be uploaded before enhancement.");
        }
    }

    public void markProcessing(String model, String promptVersion) {
        ensureEnhancementCanStart();
        this.status = PhotoSessionStatus.PROCESSING;
        this.geminiModel = model;
        this.promptVersion = promptVersion;
        this.errorMessage = null;
        this.updatedAt = Instant.now();
    }

    public void markCompleted(StoredImage enhancedImage) {
        if (status != PhotoSessionStatus.PROCESSING) {
            throw new InvalidPhotoSessionStateException("Only a processing session can be completed.");
        }
        this.enhancedImageUrl = Objects.requireNonNull(enhancedImage.url());
        this.enhancedImageStorageId = Objects.requireNonNull(enhancedImage.storageId());
        this.status = PhotoSessionStatus.COMPLETED;
        this.errorMessage = null;
        this.updatedAt = Instant.now();
    }

    public void markFailed(String safeFailureCode) {
        if (status != PhotoSessionStatus.PROCESSING) {
            throw new InvalidPhotoSessionStateException("Only a processing session can fail.");
        }
        this.status = PhotoSessionStatus.FAILED;
        this.errorMessage = safeFailureCode;
        this.updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public Long getVersion() {
        return version;
    }

    public String getOriginalImageUrl() {
        return originalImageUrl;
    }

    public String getOriginalImageStorageId() {
        return originalImageStorageId;
    }

    public String getOriginalImageContentType() {
        return originalImageContentType;
    }

    public String getEnhancedImageUrl() {
        return enhancedImageUrl;
    }

    public String getEnhancedImageStorageId() {
        return enhancedImageStorageId;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public Double getLocationAccuracy() {
        return locationAccuracy;
    }

    public PhotoSessionStatus getStatus() {
        return status;
    }

    public String getGeminiModel() {
        return geminiModel;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
