package com.example.location.app.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LiveAudioSessionControllerTests {
    private LiveAudioRooms rooms;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        rooms = new LiveAudioRooms();
        mockMvc = MockMvcBuilders.standaloneSetup(
                new LiveAudioSessionController(rooms),
                new AdminLiveAudioController(rooms)
        ).build();
    }

    @Test
    void liveAvRoomRequiresCameraAndMicrophoneConsent() throws Exception {
        mockMvc.perform(post("/api/v1/live-audio/sessions")
                        .contentType("application/json")
                        .content("{\"cameraConsentConfirmed\":true,\"microphoneConsentConfirmed\":false}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/live-audio/sessions")
                        .contentType("application/json")
                        .content("{\"cameraConsentConfirmed\":false,\"microphoneConsentConfirmed\":true}"))
                .andExpect(status().isBadRequest());

        org.junit.jupiter.api.Assertions.assertTrue(rooms.activeRooms().isEmpty());
    }

    @Test
    void consentedRoomDeclaresTheAvWebmTypeAndPublisherStopRemovesIt() throws Exception {
        String response = mockMvc.perform(post("/api/v1/live-audio/sessions")
                        .contentType("application/json")
                        .content("{\"cameraConsentConfirmed\":true,\"microphoneConsentConfirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mediaType").value(LiveAudioRooms.LIVE_MEDIA_TYPE))
                .andReturn().getResponse().getContentAsString();

        tools.jackson.databind.JsonNode json = new tools.jackson.databind.ObjectMapper().readTree(response);
        String sessionId = json.get("sessionId").asText();
        String publishToken = json.get("publishToken").asText();

        mockMvc.perform(get("/api/v1/admin/live-audio/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sessionId").value(sessionId))
                .andExpect(jsonPath("$[0].mediaType").value(LiveAudioRooms.LIVE_MEDIA_TYPE));

        mockMvc.perform(delete("/api/v1/live-audio/sessions/{sessionId}", sessionId)
                        .header("X-Audio-Publish-Token", publishToken)
                        .header("X-Audio-Last-Chunk-Sequence", "0"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/live-audio/sessions/{sessionId}", sessionId)
                        .header("X-Audio-Publish-Token", publishToken)
                        .header("X-Audio-Last-Chunk-Sequence", "0"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/live-audio/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void validPublisherDeleteWithoutSequenceHeaderFinalizesIdempotently() throws Exception {
        LiveAudioRooms.RoomInfo room = rooms.create();

        mockMvc.perform(delete("/api/v1/live-audio/sessions/{sessionId}", room.sessionId())
                        .header("X-Audio-Publish-Token", room.publishToken()))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/live-audio/sessions/{sessionId}", room.sessionId())
                        .header("X-Audio-Publish-Token", room.publishToken()))
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertNull(rooms.room(room.sessionId()));
        org.junit.jupiter.api.Assertions.assertTrue(rooms.activeRooms().isEmpty());
    }

    @Test
    void deleteStillValidatesPublisherTokenAndSessionId() throws Exception {
        LiveAudioRooms.RoomInfo room = rooms.create();

        mockMvc.perform(delete("/api/v1/live-audio/sessions/{sessionId}", room.sessionId())
                        .header("X-Audio-Publish-Token", "not-the-token"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/live-audio/sessions/{sessionId}", "missing-session")
                        .header("X-Audio-Publish-Token", room.publishToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void closeRejectsBarrierThatDoesNotMatchPersistedChunksAndKeepsRoomOpen() throws Exception {
        LiveAudioRooms.RoomInfo room = rooms.create();
        rooms.room(room.sessionId()).broadcast(new org.springframework.web.socket.BinaryMessage(
                java.nio.ByteBuffer.wrap(new byte[]{1, 2, 3})));

        mockMvc.perform(delete("/api/v1/live-audio/sessions/{sessionId}", room.sessionId())
                        .header("X-Audio-Publish-Token", room.publishToken())
                        .header("X-Audio-Last-Chunk-Sequence", "0"))
                .andExpect(status().isConflict());

        org.junit.jupiter.api.Assertions.assertNotNull(rooms.room(room.sessionId()));
    }
}
