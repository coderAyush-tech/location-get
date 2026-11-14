package com.example.location.app.admin;

import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LiveAudioWebSocketHandler extends AbstractWebSocketHandler {
    private static final int MAX_MEDIA_CHUNK_BYTES = 262_144;
    private final LiveAudioRooms rooms;
    private final AdminTokenService tokens;
    private final ObjectMapper mapper;
    private final Map<String, LiveAudioRooms.Room> authenticated = new ConcurrentHashMap<>();

    public LiveAudioWebSocketHandler(LiveAudioRooms rooms, AdminTokenService tokens, ObjectMapper mapper) {
        this.rooms = rooms; this.tokens = tokens; this.mapper = mapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        session.setTextMessageSizeLimit(4096);
        session.setBinaryMessageSizeLimit(MAX_MEDIA_CHUNK_BYTES);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<?, ?> json = mapper.readValue(message.getPayload(), Map.class);
        String role = String.valueOf(json.get("role"));
        String sessionId = String.valueOf(json.get("sessionId"));
        String credential = String.valueOf(json.get("credential"));
        LiveAudioRooms.Room room = rooms.room(sessionId);
        if (room == null) { session.close(new CloseStatus(4404, "Audio session not found")); return; }
        if ("publisher".equals(role) && room.matches(credential)) {
            if (!room.attachPublisherIfAvailable(session)) { session.close(CloseStatus.POLICY_VIOLATION); return; }
            authenticated.put(session.getId(), room);
        } else if ("admin".equals(role)) {
            AdminTokenService.AdminPrincipal admin = tokens.verify(credential);
            if (!"ADMIN".equals(admin.role())) { session.close(CloseStatus.NOT_ACCEPTABLE); return; }
            if (!room.addListener(session)) { session.close(CloseStatus.POLICY_VIOLATION); return; }
            authenticated.put(session.getId(), room);
        } else {
            session.close(CloseStatus.NOT_ACCEPTABLE);
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        LiveAudioRooms.Room room = authenticated.get(session.getId());
        if (room != null && room.isPublisher(session)
                && message.getPayloadLength() <= MAX_MEDIA_CHUNK_BYTES) room.broadcast(message);
        else closeQuietly(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        LiveAudioRooms.Room room = authenticated.remove(session.getId());
        if (room != null && room.isPublisher(session)) {
            rooms.publisherDisconnected(session);
        } else if (room != null) {
            room.detach(session);
        }
    }

    private void closeQuietly(WebSocketSession session) {
        try { session.close(CloseStatus.NOT_ACCEPTABLE); } catch (Exception ignored) { }
    }
}
