package com.example.location.app.admin;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/live-audio/sessions")
public class LiveAudioSessionController {
    private final LiveAudioRooms rooms;
    public LiveAudioSessionController(LiveAudioRooms rooms) { this.rooms = rooms; }

    @PostMapping
    LiveAudioRooms.RoomInfo create(@RequestBody ConsentRequest request) {
        if (request == null || !request.consentConfirmed()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Explicit camera and microphone consent is required.");
        }
        return rooms.create();
    }

    @DeleteMapping("/{sessionId}")
    void close(@PathVariable String sessionId, @RequestHeader("X-Audio-Publish-Token") String token) {
        if (!rooms.close(sessionId, token)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    public record ConsentRequest(boolean consentConfirmed) { }
}
