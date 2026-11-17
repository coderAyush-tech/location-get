package com.example.location.app.admin;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.GridFSDownloadStream;
import com.mongodb.client.gridfs.GridFSUploadStream;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Stores streaming WebM output in MongoDB GridFS rather than a size-limited BSON field. */
@Component
public class GridFsVideoStore {
    private static final Logger log = LoggerFactory.getLogger(GridFsVideoStore.class);
    private static final String BUCKET_NAME = "camera_recordings";
    private final GridFSBucket bucket;
    private final MongoTemplate mongoTemplate;
    private final ConcurrentMap<String, MongoWriter> active = new ConcurrentHashMap<>();

    @Autowired
    public GridFsVideoStore(MongoDatabaseFactory databaseFactory, MongoTemplate mongoTemplate) {
        MongoDatabase database = databaseFactory.getMongoDatabase();
        this.bucket = GridFSBuckets.create(database, BUCKET_NAME);
        this.mongoTemplate = mongoTemplate;
    }

    GridFsVideoStore(GridFSBucket bucket, MongoTemplate mongoTemplate) {
        this.bucket = bucket;
        this.mongoTemplate = mongoTemplate;
    }

    public VideoRecordingWriter open(String sessionId, String publishToken) {
        Document metadata = new Document("sessionId", sessionId)
                .append("publishTokenSha256", sha256(publishToken))
                .append("contentType", "video/webm")
                .append("consent", "camera-and-microphone");
        GridFSUploadStream stream = bucket.openUploadStream(
                sessionId + ".webm",
                new GridFSUploadOptions().metadata(metadata)
        );
        MongoWriter writer = new MongoWriter(stream);
        MongoWriter previous = active.putIfAbsent(writer.id(), writer);
        if (previous != null) {
            writer.abort();
            throw new IllegalStateException("Duplicate video recording id.");
        }
        return writer;
    }

    public Optional<RecordingMetadata> findCompleted(String sessionId, String publishToken) {
        Document filter = new Document("filename", sessionId + ".webm")
                .append("metadata.sessionId", sessionId)
                .append("metadata.publishTokenSha256", sha256(publishToken));
        GridFSFile file = bucket.find(filter).first();
        return Optional.ofNullable(file).map(item -> new RecordingMetadata(
                item.getObjectId().toHexString(), item.getLength(), "video/webm"));
    }

    public Optional<StoredVideo> openDownload(String id) {
        if (!ObjectId.isValid(id)) return Optional.empty();
        ObjectId objectId = parseId(id);
        GridFSFile file = bucket.find(new Document("_id", objectId)).first();
        if (file == null) return Optional.empty();
        GridFSDownloadStream stream = bucket.openDownloadStream(objectId);
        return Optional.of(new StoredVideo(stream, file.getLength(), "video/webm"));
    }

    public void refreshCapture(String id, String captureId) {
        if (!ObjectId.isValid(id) || captureId == null || captureId.isBlank()) return;
        GridFSFile file = bucket.find(new Document("_id", new ObjectId(id))).first();
        if (file == null) return; // Still recording; the writer will update it on close.
        Object captureKey = ObjectId.isValid(captureId) ? new ObjectId(captureId) : captureId;
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(captureKey).and("videoGridFsId").is(id)),
                new Update().set("videoReady", true).set("videoSizeBytes", file.getLength()),
                "captured_photos"
        );
    }

    /** Admin deletion removes GridFS files and their chunks. For an active stream it aborts it. */
    public void delete(String id) {
        if (!ObjectId.isValid(id)) {
            log.warn("VIDEO_RECORDING_DELETE_SKIPPED invalidGridFsId={}", id);
            return;
        }
        ObjectId objectId = parseId(id);
        MongoWriter writer = active.remove(id);
        if (writer != null) {
            writer.abort();
            return;
        }
        if (bucket.find(new Document("_id", objectId)).first() != null) {
            bucket.delete(objectId);
        }
    }

    private ObjectId parseId(String id) {
        if (id == null || !ObjectId.isValid(id)) {
            throw new IllegalArgumentException("Invalid GridFS video id.");
        }
        return new ObjectId(id);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    public record RecordingMetadata(String id, long sizeBytes, String contentType) { }

    public record StoredVideo(InputStream stream, long sizeBytes, String contentType) { }

    private final class MongoWriter implements VideoRecordingWriter {
        private final GridFSUploadStream stream;
        private final String id;
        private long bytesWritten;
        private boolean streamClosed;
        private boolean done;

        private MongoWriter(GridFSUploadStream stream) {
            this.stream = stream;
            this.id = stream.getObjectId().toHexString();
        }

        @Override public String id() { return id; }

        @Override public synchronized long bytesWritten() { return bytesWritten; }

        @Override
        public synchronized void append(byte[] chunk) throws java.io.IOException {
            if (done) throw new java.io.IOException("Recording stream is already closed.");
            stream.write(chunk);
            bytesWritten += chunk.length;
        }

        @Override
        public synchronized void finish(boolean retain) {
            if (done) return;
            if (!retain || bytesWritten == 0) {
                stream.abort();
                done = true;
                active.remove(id, this);
                return;
            }
            if (!streamClosed) {
                stream.close();
                streamClosed = true;
            }
            try {
                mongoTemplate.updateMulti(
                        Query.query(Criteria.where("videoGridFsId").is(id)),
                        new Update().set("videoReady", true).set("videoSizeBytes", bytesWritten),
                        "captured_photos"
                );
            } catch (RuntimeException exception) {
                // Keep the writer retryable: an idempotent session DELETE can retry this
                // metadata update without closing the already-completed GridFS stream again.
                log.error("VIDEO_RECORDING_METADATA_UPDATE_FAILED gridFsId={} sizeBytes={} exceptionType={}",
                        id, bytesWritten, exception.getClass().getSimpleName());
                throw exception;
            }
            done = true;
            active.remove(id, this);
            log.info("VIDEO_RECORDING_SAVED gridFsId={} sizeBytes={}", id, bytesWritten);
        }

        @Override
        public synchronized void abort() {
            if (done) return;
            done = true;
            active.remove(id, this);
            try { stream.abort(); }
            catch (RuntimeException exception) { log.warn("VIDEO_RECORDING_ABORT_FAILED gridFsId={}", id); }
        }
    }
}
