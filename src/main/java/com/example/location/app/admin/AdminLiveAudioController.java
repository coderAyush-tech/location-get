package com.example.location.app.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/live-audio/sessions")
public class AdminLiveAudioController {
    private final LiveAudioRooms rooms;
    public AdminLiveAudioController(LiveAudioRooms rooms) { this.rooms = rooms; }
    @GetMapping
    List<SessionSummary> active() {
        return rooms.activeRooms().stream()
                .map(room -> new SessionSummary(room.id(), LiveAudioRooms.LIVE_MEDIA_TYPE, room.createdAt()))
                .toList();
    }
    public record SessionSummary(String sessionId, String mediaType, java.time.Instant createdAt) { }
}
