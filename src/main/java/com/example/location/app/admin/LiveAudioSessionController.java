package com.example.location.app.admin;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(LiveAudioSessionController.class);
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
    ResponseEntity<Void> close(
            @PathVariable("sessionId") String sessionId,
            @RequestHeader(name = "X-Audio-Publish-Token", required = false) String token,
            @RequestHeader(name = "X-Audio-Last-Chunk-Sequence", required = false) String rawLastChunkSequence
    ) {
        String requestPath = "/api/v1/live-audio/sessions/" + sessionId;
        boolean tokenPresent = token != null && !token.isBlank();
        boolean tokenValid = tokenPresent && rooms.isPublishTokenValid(sessionId, token);
        log.info("LIVE_VIDEO_FINALIZATION_REQUEST path={} publishTokenPresent={} publishTokenValid={} lastChunkSequencePresent={}",
                requestPath, tokenPresent, tokenValid, rawLastChunkSequence != null);

        if (!tokenPresent) {
            log.info("LIVE_VIDEO_FINALIZATION_RESULT path={} result=REJECTED_MISSING_TOKEN", requestPath);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "X-Audio-Publish-Token header is required.");
        }
        if (!rooms.sessionExists(sessionId)) {
            log.info("LIVE_VIDEO_FINALIZATION_RESULT path={} result=SESSION_NOT_FOUND", requestPath);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Live video session was not found.");
        }
        if (!tokenValid) {
            log.info("LIVE_VIDEO_FINALIZATION_RESULT path={} result=REJECTED_INVALID_TOKEN", requestPath);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The publish token is invalid for this session.");
        }
        if (rawLastChunkSequence == null || rawLastChunkSequence.isBlank()) {
            log.info("LIVE_VIDEO_FINALIZATION_RESULT path={} result=REJECTED_SEQUENCE_REQUIRED", requestPath);
            throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED,
                    "X-Audio-Last-Chunk-Sequence is required; send the last server-acknowledged chunk sequence (0 when no chunks were sent).");
        }

        final long lastChunkSequence;
        try {
            lastChunkSequence = Long.parseLong(rawLastChunkSequence);
        } catch (NumberFormatException exception) {
            log.info("LIVE_VIDEO_FINALIZATION_RESULT path={} result=REJECTED_INVALID_SEQUENCE", requestPath);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "X-Audio-Last-Chunk-Sequence must be a non-negative integer.");
        }
        if (lastChunkSequence < 0) {
            log.info("LIVE_VIDEO_FINALIZATION_RESULT path={} result=REJECTED_INVALID_SEQUENCE", requestPath);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "X-Audio-Last-Chunk-Sequence must be a non-negative integer.");
        }

        final LiveAudioRooms.CloseResult result;
        try {
            result = rooms.close(sessionId, token, lastChunkSequence);
        } catch (RuntimeException exception) {
            log.error("LIVE_VIDEO_FINALIZATION_RESULT path={} result=FAILED exceptionType={}",
                    requestPath, exception.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Video finalization failed. Retry with the same session, token, and acknowledged sequence.");
        }
        log.info("LIVE_VIDEO_FINALIZATION_RESULT path={} result={}", requestPath, result);
        switch (result) {
            case CLOSED, ALREADY_CLOSED -> {
                return ResponseEntity.ok().build();
            }
            case INVALID_TOKEN -> {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The publish token is invalid for this session.");
            }
            case NOT_FOUND -> {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Live video session was not found.");
            }
            case INVALID_SEQUENCE -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "X-Audio-Last-Chunk-Sequence must be a non-negative integer.");
            case SEQUENCE_REQUIRED -> throw new ResponseStatusException(
                    HttpStatus.PRECONDITION_REQUIRED, "X-Audio-Last-Chunk-Sequence is required.");
            case SEQUENCE_MISMATCH -> throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "The acknowledged chunk sequence does not match persisted video chunks.");
            case SEQUENCE_TIMEOUT -> throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Video chunks are still being persisted; retry DELETE after receiving their acknowledgements.");
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected video finalization result.");
    }

    public record ConsentRequest(boolean cameraConsentConfirmed, boolean microphoneConsentConfirmed) { }
}
