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
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    public synchronized RoomInfo create() {
        expireOldRooms();
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
        Room room = new Room(id, key, Instant.now());
        rooms.put(id, room);
        return new RoomInfo(id, key, room.createdAt);
    }

    public Room room(String id) {
        Room room = rooms.get(id);
        if (room != null && room.createdAt.plusSeconds(600).isBefore(Instant.now())) {
            if (rooms.remove(id, room)) room.close();
            return null;
        }
        return room;
    }

    public boolean close(String id, String token) {
        Room room = rooms.get(id);
        if (room == null || !room.matches(token)) return false;
        rooms.remove(id, room);
        room.close();
        return true;
    }

    public Set<Room> activeRooms() {
        expireOldRooms();
        return Set.copyOf(rooms.values());
    }

    private void expireOldRooms() {
        rooms.entrySet().removeIf(entry -> {
            if (!entry.getValue().createdAt.plusSeconds(600).isBefore(Instant.now())) return false;
            entry.getValue().close();
            return true;
        });
    }

    public record RoomInfo(String sessionId, String publishToken, Instant createdAt) { }

    public static final class Room {
        private final String id;
        private final String publishToken;
        private final Instant createdAt;
        private volatile WebSocketSession publisher;
        private final Set<WebSocketSession> listeners = ConcurrentHashMap.newKeySet();
        // MediaRecorder's first WebM chunk contains the EBML/track initialization
        // data. Keep it so listeners joining later can initialize their decoder.
        private byte[] initializationChunk;
        private boolean closed;
        private static final int MAX_LISTENERS = 20;
        Room(String id, String publishToken, Instant createdAt) {
            this.id = id; this.publishToken = publishToken; this.createdAt = createdAt;
        }
        public String id() { return id; }
        public Instant createdAt() { return createdAt; }
        public boolean matches(String token) { return publishToken.equals(token); }
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
            try { if (publisher != null && publisher.isOpen()) publisher.close(); } catch (Exception ignored) { }
            for (WebSocketSession listener : listeners) {
                try { if (listener.isOpen()) listener.close(); } catch (Exception ignored) { }
            }
            listeners.clear();
        }
        public synchronized void broadcast(BinaryMessage message) {
            if (closed) return;
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
}
