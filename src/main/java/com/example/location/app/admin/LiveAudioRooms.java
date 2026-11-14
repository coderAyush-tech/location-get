package com.example.location.app.admin;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LiveAudioRooms {
    private static final int MAX_ACTIVE_ROOMS = 100;
    public static final String LIVE_MEDIA_TYPE = "video/webm;codecs=vp8,opus";
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final GridFsVideoStore videos;

    @org.springframework.beans.factory.annotation.Autowired
    public LiveAudioRooms(GridFsVideoStore videos) {
        this.videos = videos;
    }

    /** Used by focused unit tests; Spring uses the GridFS-injected constructor. */
    public LiveAudioRooms() {
        this.videos = null;
    }

    public synchronized RoomInfo create() {
        if (rooms.size() >= MAX_ACTIVE_ROOMS) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                    "Live audio is temporarily at capacity."
            );
        }
        byte[] secret = new byte[32];
        random.nextBytes(secret);
        String id = UUID.randomUUID().toString();
        String key = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        VideoRecordingWriter writer = videos == null ? new TestWriter() : videos.open(id, key);
        Room room = new Room(id, key, Instant.now(), writer);
        rooms.put(id, room);
        return new RoomInfo(id, key, LIVE_MEDIA_TYPE, room.createdAt);
    }

    public Room room(String id) {
        return rooms.get(id);
    }

    public boolean close(String id, String token) {
        Room room = rooms.get(id);
        if (room == null || !room.matches(token)) return false;
        rooms.remove(id, room);
        room.close();
        return true;
    }

    public String recordingId(String id, String token) {
        Room room = rooms.get(id);
        return room != null && room.matches(token) ? room.recordingId() : null;
    }

    public String attachRecordingToCapture(String id, String token) {
        Room room = rooms.get(id);
        if (room == null || !room.matches(token) || !room.attachToCapture()) return null;
        return room.recordingId();
    }

    public void refreshSavedCapture(String recordingId, String captureId) {
        if (videos != null) videos.refreshCapture(recordingId, captureId);
    }

    public void discardRecording(String recordingId) {
        if (videos != null) videos.delete(recordingId);
    }

    public void publisherDisconnected(WebSocketSession session) {
        for (Map.Entry<String, Room> entry : rooms.entrySet()) {
            Room room = entry.getValue();
            if (room.isPublisher(session) && rooms.remove(entry.getKey(), room)) {
                room.close();
                return;
            }
        }
    }

    public Set<Room> activeRooms() {
        return Set.copyOf(rooms.values());
    }

    public record RoomInfo(String sessionId, String publishToken, String mediaType, Instant createdAt) { }

    public static final class Room {
        private final String id;
        private final String publishToken;
        private final Instant createdAt;
        private final VideoRecordingWriter videoWriter;
        private volatile WebSocketSession publisher;
        private final Set<WebSocketSession> listeners = ConcurrentHashMap.newKeySet();
        // MediaRecorder's first WebM chunk contains the EBML/track initialization
        // data. Keep it so listeners joining later can initialize their decoder.
        private byte[] initializationChunk;
        private boolean closed;
        private boolean attachedToCapture;
        private static final int MAX_LISTENERS = 20;
        Room(String id, String publishToken, Instant createdAt, VideoRecordingWriter videoWriter) {
            this.id = id; this.publishToken = publishToken; this.createdAt = createdAt;
            this.videoWriter = videoWriter;
        }
        public String id() { return id; }
        public Instant createdAt() { return createdAt; }
        public boolean matches(String token) { return publishToken.equals(token); }
        public String recordingId() { return videoWriter.id(); }
        public synchronized boolean attachToCapture() {
            if (closed || attachedToCapture) return false;
            attachedToCapture = true;
            return true;
        }
        public synchronized void attachPublisher(WebSocketSession session) {
            if (!closed) publisher = session;
        }
        public boolean isPublisher(WebSocketSession session) { return publisher == session; }
        public void detach(WebSocketSession session) { if (publisher == session) publisher = null; listeners.remove(session); }
        public synchronized boolean addListener(WebSocketSession session) {
            if (closed) return false;
            if (listeners.size() >= MAX_LISTENERS) return false;
            if (initializationChunk != null) {
                try {
                    session.sendMessage(new BinaryMessage(ByteBuffer.wrap(initializationChunk).asReadOnlyBuffer()));
                } catch (Exception ignored) {
                    return false;
                }
            }
            return listeners.add(session);
        }
        public synchronized boolean attachPublisherIfAvailable(WebSocketSession session) {
            if (closed) return false;
            if (publisher != null && publisher.isOpen()) return false;
            publisher = session;
            return true;
        }
        public synchronized void close() {
            if (closed) return;
            closed = true;
            videoWriter.finish(attachedToCapture);
            try { if (publisher != null && publisher.isOpen()) publisher.close(); } catch (Exception ignored) { }
            for (WebSocketSession listener : listeners) {
                try { if (listener.isOpen()) listener.close(); } catch (Exception ignored) { }
            }
            listeners.clear();
        }
        public synchronized void broadcast(BinaryMessage message) {
            if (closed) return;
            ByteBuffer recordingPayload = message.getPayload().asReadOnlyBuffer();
            byte[] recordingChunk = new byte[recordingPayload.remaining()];
            recordingPayload.get(recordingChunk);
            try {
                videoWriter.append(recordingChunk);
            } catch (java.io.IOException exception) {
                close();
                return;
            }
            if (initializationChunk == null) {
                ByteBuffer payload = message.getPayload().asReadOnlyBuffer();
                initializationChunk = new byte[payload.remaining()];
                payload.get(initializationChunk);
            }
            for (WebSocketSession listener : listeners) {
                try { if (listener.isOpen()) listener.sendMessage(new BinaryMessage(message.getPayload().asReadOnlyBuffer())); }
                catch (Exception ignored) { listeners.remove(listener); }
            }
        }
    }

    private static final class TestWriter implements VideoRecordingWriter {
        private final String id = UUID.randomUUID().toString();
        private long size;
        @Override public String id() { return id; }
        @Override public long bytesWritten() { return size; }
        @Override public void append(byte[] chunk) { size += chunk.length; }
        @Override public void finish(boolean retain) { }
        @Override public void abort() { }
    }
}
