package com.example.location.app.admin;

import com.mongodb.client.result.DeleteResult;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Repository
class MongoAdminCaptureStore implements AdminCaptureStore {
    private static final String COLLECTION = "captured_photos";

    private final MongoTemplate mongoTemplate;
    private final Clock clock;
    private final TransactionTemplate deleteTransaction;
    private final GridFsVideoStore videos;

    @Autowired
    MongoAdminCaptureStore(
            MongoTemplate mongoTemplate,
            MongoDatabaseFactory databaseFactory,
            GridFsVideoStore videos
    ) {
        this(mongoTemplate, Clock.systemUTC(),
                new TransactionTemplate(new MongoTransactionManager(databaseFactory)), videos);
    }

    MongoAdminCaptureStore(MongoTemplate mongoTemplate) {
        this(mongoTemplate, Clock.systemUTC(), null, null);
    }

    MongoAdminCaptureStore(MongoTemplate mongoTemplate, Clock clock) {
        this(mongoTemplate, clock, null, null);
    }

    MongoAdminCaptureStore(
            MongoTemplate mongoTemplate,
            Clock clock,
            TransactionTemplate deleteTransaction,
            GridFsVideoStore videos
    ) {
        this.mongoTemplate = mongoTemplate;
        this.clock = clock;
        this.deleteTransaction = deleteTransaction;
        this.videos = videos;
    }

    @Override
    public CaptureSlice find(AdminCaptureQuery request) {
        Criteria filters = filters(request);
        Query dataQuery = filters == null ? new Query() : Query.query(filters);
        dataQuery.fields().exclude("photo");
        dataQuery.fields().exclude("audio");
        dataQuery.fields().exclude("video");
        dataQuery.with(Sort.by(
                Sort.Order.desc("savedAt"),
                Sort.Order.desc("_id")
        ));
        dataQuery.skip((long) request.page() * request.size()).limit(request.size());

        List<MongoCaptureMetadata> documents = mongoTemplate.find(
                dataQuery,
                MongoCaptureMetadata.class,
                COLLECTION
        );
        Query countQuery = filters == null ? new Query() : Query.query(filters);
        long total = mongoTemplate.count(countQuery, COLLECTION);
        return new CaptureSlice(documents.stream().map(MongoCaptureMetadata::toMetadata).toList(), total);
    }

    @Override
    public AdminCaptureSummary summary() {
        long total = mongoTemplate.count(new Query(), COLLECTION);
        Instant startOfToday = LocalDate.now(clock).atStartOfDay().toInstant(ZoneOffset.UTC);
        long today = mongoTemplate.count(
                Query.query(Criteria.where("savedAt").gte(startOfToday)),
                COLLECTION
        );
        long gps = mongoTemplate.count(
                Query.query(Criteria.where("locationSource").in("gps", "GPS")),
                COLLECTION
        );
        long ipFallback = mongoTemplate.count(
                Query.query(Criteria.where("locationSource").in("ip", "geo_ip", "GEO_IP", "raw_ip", "RAW_IP")),
                COLLECTION
        );

        Document grouped = mongoTemplate.getCollection(COLLECTION)
                .aggregate(List.of(new Document("$group", new Document("_id", null)
                        .append("storageBytes", new Document("$sum", new Document("$add", List.of(
                                new Document("$ifNull", List.of("$sizeBytes", 0)),
                                new Document("$ifNull", List.of("$audioSizeBytes", 0)),
                                new Document("$ifNull", List.of("$videoSizeBytes", 0))
                        )))))))
                .first();
        long storageBytes = grouped == null ? 0 : numberAsLong(grouped.get("storageBytes"));
        return new AdminCaptureSummary(total, today, gps, ipFallback, storageBytes);
    }

    @Override
    public Optional<AdminStoredPhoto> findPhoto(String captureId) {
        Query query = Query.query(Criteria.where("_id").is(idValue(captureId)));
        query.fields().include("photo", "contentType", "originalFilename");
        MongoPhotoContent document = mongoTemplate.findOne(query, MongoPhotoContent.class, COLLECTION);
        if (document == null || document.photo == null || document.photo.length == 0) {
            return Optional.empty();
        }
        return Optional.of(new AdminStoredPhoto(document.photo, document.contentType, document.originalFilename));
    }

    @Override
    public Optional<AdminStoredAudio> findAudio(String captureId) {
        Query query = Query.query(Criteria.where("_id").is(idValue(captureId)));
        query.fields().include("audio", "audioContentType");
        MongoAudioContent document = mongoTemplate.findOne(query, MongoAudioContent.class, COLLECTION);
        if (document == null || document.audio == null || document.audio.length == 0) return Optional.empty();
        return Optional.of(new AdminStoredAudio(document.audio, document.audioContentType));
    }

    @Override
    public Optional<AdminStoredVideo> findVideo(String captureId) {
        Query query = Query.query(Criteria.where("_id").is(idValue(captureId)));
        query.fields().include("videoGridFsId");
        MongoVideoContent document = mongoTemplate.findOne(query, MongoVideoContent.class, COLLECTION);
        if (document == null || document.videoGridFsId == null || videos == null) return Optional.empty();
        return videos.openDownload(document.videoGridFsId)
                .map(video -> new AdminStoredVideo(video.stream(), video.sizeBytes(), video.contentType()));
    }

    @Override
    public DeleteOutcome deleteExact(String captureId) {
        if (deleteTransaction == null) {
            return removeExact(captureId);
        }
        DeleteOutcome outcome = deleteTransaction.execute(status -> removeExact(captureId));
        if (outcome == null) {
            throw new AdminCaptureDeleteConflictException("MongoDB transaction returned no deletion outcome.");
        }
        return outcome;
    }

    private DeleteOutcome removeExact(String captureId) {
        Query videoQuery = Query.query(Criteria.where("_id").is(idValue(captureId)));
        videoQuery.fields().include("videoGridFsId");
        MongoVideoContent videoReference = mongoTemplate.findOne(videoQuery, MongoVideoContent.class, COLLECTION);
        Query query = Query.query(Criteria.where("_id").is(idValue(captureId)));
        DeleteResult result = mongoTemplate.remove(query, COLLECTION);
        if (!result.wasAcknowledged() || result.getDeletedCount() > 1) {
            throw new AdminCaptureDeleteConflictException("MongoDB did not confirm one safe capture deletion.");
        }
        if (result.getDeletedCount() == 1) {
            if (videoReference != null && videoReference.videoGridFsId != null && videos != null) {
                videos.delete(videoReference.videoGridFsId);
            }
            return DeleteOutcome.DELETED;
        }
        return DeleteOutcome.NOT_FOUND;
    }

    private Criteria filters(AdminCaptureQuery request) {
        List<Criteria> filters = new ArrayList<>();
        if (request.query() != null && !request.query().isBlank()) {
            String text = request.query().trim();
            Pattern pattern = Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE);
            List<Criteria> searchFields = new ArrayList<>();
            searchFields.add(Criteria.where("originalFilename").regex(pattern));
            searchFields.add(Criteria.where("clientIp").regex(pattern));
            // Existing documents store the Geo-IP city, region and country in this combined address field.
            searchFields.add(Criteria.where("address").regex(pattern));
            searchFields.add(Criteria.where("_id").is(text));
            if (ObjectId.isValid(text)) {
                searchFields.add(Criteria.where("_id").is(new ObjectId(text)));
            }
            filters.add(new Criteria().orOperator(searchFields));
        }

        Criteria source = switch (request.locationSource()) {
            case ALL -> null;
            case GPS -> Criteria.where("locationSource").in("gps", "GPS");
            case GEO_IP -> Criteria.where("locationSource").in("ip", "geo_ip", "GEO_IP");
            case RAW_IP -> Criteria.where("locationSource").in("raw_ip", "RAW_IP");
        };
        if (source != null) {
            filters.add(source);
        }
        if (filters.isEmpty()) {
            return null;
        }
        return new Criteria().andOperator(filters);
    }

    private Object idValue(String captureId) {
        String normalized = captureId == null ? "" : captureId.trim();
        return ObjectId.isValid(normalized) ? new ObjectId(normalized) : normalized;
    }

    private long numberAsLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0;
    }

    static class MongoCaptureMetadata {
        @Id
        String id;
        String originalFilename;
        String contentType;
        long sizeBytes;
        Double latitude;
        Double longitude;
        Double accuracy;
        String locationSource;
        String address;
        String clientIp;
        Instant savedAt;
        long audioSizeBytes;
        String videoGridFsId;
        boolean videoReady;
        long videoSizeBytes;

        AdminCaptureMetadata toMetadata() {
            return new AdminCaptureMetadata(
                    id,
                    originalFilename,
                    contentType,
                    sizeBytes,
                    latitude,
                    longitude,
                    accuracy,
                    locationSource,
                    address,
                    clientIp,
                    savedAt,
                    audioSizeBytes > 0,
                    videoReady,
                    videoSizeBytes
            );
        }
    }

    static class MongoPhotoContent {
        @Id
        String id;
        @Field("photo")
        byte[] photo;
        String contentType;
        String originalFilename;
    }

    static class MongoAudioContent {
        @Id String id;
        byte[] audio;
        String audioContentType;
    }

    static class MongoVideoContent {
        @Id String id;
        String videoGridFsId;
    }
}
