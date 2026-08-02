package com.example.location.app.admin;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

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

    @Autowired
    MongoAdminCaptureStore(MongoTemplate mongoTemplate) {
        this(mongoTemplate, Clock.systemUTC());
    }

    MongoAdminCaptureStore(MongoTemplate mongoTemplate, Clock clock) {
        this.mongoTemplate = mongoTemplate;
        this.clock = clock;
    }

    @Override
    public CaptureSlice find(AdminCaptureQuery request) {
        Criteria filters = filters(request);
        Query dataQuery = filters == null ? new Query() : Query.query(filters);
        dataQuery.fields().exclude("photo");
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
                        .append("storageBytes", new Document("$sum", "$sizeBytes")))))
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
                    savedAt
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
}
