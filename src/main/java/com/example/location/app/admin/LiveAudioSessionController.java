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
        if (request == null || !request.cameraConsentConfirmed() || !request.microphoneConsentConfirmed()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Explicit camera and microphone consent is required before live audio/video starts.");
        }
        return rooms.create();
    }

    @DeleteMapping("/{sessionId}")
    void close(
            @PathVariable String sessionId,
            @RequestHeader("X-Audio-Publish-Token") String token,
            @RequestHeader("X-Audio-Last-Chunk-Sequence") long lastChunkSequence
    ) {
        LiveAudioRooms.CloseResult result = rooms.close(sessionId, token, lastChunkSequence);
        switch (result) {
            case CLOSED, ALREADY_CLOSED -> { }
            case INVALID_TOKEN -> throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            case INVALID_SEQUENCE -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Last chunk sequence must be non-negative.");
            case SEQUENCE_MISMATCH -> throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "The submitted last chunk sequence does not match persisted video chunks.");
            case SEQUENCE_TIMEOUT -> throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Video chunks are still being persisted; retry DELETE after receiving their acknowledgements.");
        }
    }

    public record ConsentRequest(boolean cameraConsentConfirmed, boolean microphoneConsentConfirmed) { }
}
