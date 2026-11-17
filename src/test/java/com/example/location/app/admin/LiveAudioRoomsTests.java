package com.example.location.app.admin;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.TextMessage;

import java.nio.ByteBuffer;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveAudioRoomsTests {

    @Test
    void publisherTokenIsUniqueAndOnlyItCanCloseItsRoom() {
        LiveAudioRooms rooms = new LiveAudioRooms();
        LiveAudioRooms.RoomInfo first = rooms.create();
        LiveAudioRooms.RoomInfo second = rooms.create();

        assertNotNull(first.sessionId());
        assertNotEquals(first.publishToken(), second.publishToken());
        assertEquals(LiveAudioRooms.CloseResult.INVALID_TOKEN,
                rooms.close(first.sessionId(), "wrong-token", 0L));
        assertNotNull(rooms.room(first.sessionId()));
        assertEquals(LiveAudioRooms.CloseResult.CLOSED,
                rooms.close(first.sessionId(), first.publishToken(), 0L));
        assertNull(rooms.room(first.sessionId()));
    }

    @Test
    void capsConcurrentPublicLiveRooms() {
        LiveAudioRooms rooms = new LiveAudioRooms();
        for (int index = 0; index < 100; index++) rooms.create();

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, rooms::create);

        assertEquals(429, exception.getStatusCode().value());
    }

    @Test
    void lateListenerReceivesWebmInitializationChunkBeforeLiveAudio() throws Exception {
        LiveAudioRooms rooms = new LiveAudioRooms();
        LiveAudioRooms.RoomInfo info = rooms.create();
        LiveAudioRooms.Room room = rooms.room(info.sessionId());
        WebSocketSession publisher = mock(WebSocketSession.class);
        WebSocketSession listener = mock(WebSocketSession.class);
        when(publisher.isOpen()).thenReturn(true);
        when(listener.isOpen()).thenReturn(true);
        byte[] initialization = new byte[]{0x1a, 0x45, (byte) 0xdf, (byte) 0xa3};
        byte[] liveAudio = new byte[]{0x11, 0x22, 0x33};

        assertTrue(room.attachPublisherIfAvailable(publisher));
        room.broadcast(binary(initialization));

        assertTrue(room.addListener(listener));
        room.broadcast(binary(liveAudio));

        org.mockito.ArgumentCaptor<BinaryMessage> messages =
                org.mockito.ArgumentCaptor.forClass(BinaryMessage.class);
        verify(listener, times(2)).sendMessage(messages.capture());
        assertArrayEquals(initialization, bytes(messages.getAllValues().get(0)));
        assertArrayEquals(liveAudio, bytes(messages.getAllValues().get(1)));

        // Stop Listening only detaches the admin socket; it must not end the room.
        room.detach(listener);
        assertNotNull(rooms.room(info.sessionId()));
        assertTrue(room.isPublisher(publisher));

        // Publisher Stop uses its token, closes the room, and removes it from active rooms.
        assertEquals(LiveAudioRooms.CloseResult.CLOSED,
                rooms.close(info.sessionId(), info.publishToken(), 2L));
        assertNull(rooms.room(info.sessionId()));
        assertTrue(rooms.activeRooms().isEmpty());
        verify(publisher).close();
        verify(listener, never()).close();
    }

    @Test
    void closeBarrierRejectsAnIncorrectLastSequenceAndAllowsIdempotentRetry() {
        LiveAudioRooms rooms = new LiveAudioRooms();
        LiveAudioRooms.RoomInfo info = rooms.create();
        LiveAudioRooms.Room room = rooms.room(info.sessionId());
        assertEquals(1, room.broadcast(binary(new byte[]{1, 2, 3})));

        assertEquals(LiveAudioRooms.CloseResult.SEQUENCE_MISMATCH,
                rooms.close(info.sessionId(), info.publishToken(), 0L));
        assertNotNull(rooms.room(info.sessionId()));

        assertEquals(LiveAudioRooms.CloseResult.CLOSED,
                rooms.close(info.sessionId(), info.publishToken(), 1L));
        assertEquals(LiveAudioRooms.CloseResult.ALREADY_CLOSED,
                rooms.close(info.sessionId(), info.publishToken(), 1L));
        assertEquals(LiveAudioRooms.CloseResult.SEQUENCE_MISMATCH,
                rooms.close(info.sessionId(), info.publishToken(), 0L));
        assertNull(rooms.room(info.sessionId()));
        assertTrue(rooms.activeRooms().isEmpty());
    }

    @Test
    void finalizationWaitsUntilTheInFlightChunkAppendCompletes() throws Exception {
        CountDownLatch appendStarted = new CountDownLatch(1);
        CountDownLatch allowAppendToFinish = new CountDownLatch(1);
        AtomicInteger appendedBytes = new AtomicInteger();
        AtomicReference<Boolean> finishSawAppendedChunk = new AtomicReference<>(false);
        VideoRecordingWriter writer = new VideoRecordingWriter() {
            @Override public String id() { return "recording-id"; }
            @Override public long bytesWritten() { return appendedBytes.get(); }
            @Override public void append(byte[] chunk) throws IOException {
                appendStarted.countDown();
                try {
                    allowAppendToFinish.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while writing test chunk.", exception);
                }
                appendedBytes.addAndGet(chunk.length);
            }
            @Override public void finish(boolean retain) {
                finishSawAppendedChunk.set(appendedBytes.get() == 3);
            }
            @Override public void abort() { }
        };
        LiveAudioRooms.Room room = new LiveAudioRooms.Room("session", "token", Instant.now(), writer);
        Thread publisher = new Thread(() -> room.broadcast(binary(new byte[]{1, 2, 3})));
        AtomicReference<LiveAudioRooms.CloseResult> result = new AtomicReference<>();
        Thread closer = new Thread(() -> result.set(room.closeAfterSequence(1, 2_000)));

        publisher.start();
        assertTrue(appendStarted.await(1, java.util.concurrent.TimeUnit.SECONDS));
        closer.start();
        boolean barrierWaitedForAppend;
        try {
            barrierWaitedForAppend = waitForState(closer, Thread.State.BLOCKED);
            assertTrue(barrierWaitedForAppend);
            assertFalse(finishSawAppendedChunk.get());
        } finally {
            allowAppendToFinish.countDown();
        }
        publisher.join(1_000);
        closer.join(1_000);

        assertFalse(publisher.isAlive());
        assertFalse(closer.isAlive());
        assertEquals(LiveAudioRooms.CloseResult.CLOSED, result.get());
        assertTrue(finishSawAppendedChunk.get());
    }

    @Test
    void publisherReceivesSequenceAckOnlyAfterItsChunkIsPersisted() throws Exception {
        LiveAudioRooms rooms = new LiveAudioRooms();
        LiveAudioRooms.RoomInfo info = rooms.create();
        WebSocketSession publisher = mock(WebSocketSession.class);
        when(publisher.getId()).thenReturn("publisher-socket");
        when(publisher.isOpen()).thenReturn(true);
        tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
        LiveAudioWebSocketHandler handler = new LiveAudioWebSocketHandler(rooms, null, mapper);

        handler.handleTextMessage(publisher, new TextMessage(mapper.writeValueAsString(java.util.Map.of(
                "role", "publisher",
                "sessionId", info.sessionId(),
                "credential", info.publishToken()
        ))));
        handler.handleBinaryMessage(publisher, binary(new byte[]{1, 2, 3}));

        org.mockito.ArgumentCaptor<WebSocketMessage<?>> sent =
                org.mockito.ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(publisher).sendMessage(sent.capture());
        assertTrue(sent.getValue() instanceof TextMessage);
        tools.jackson.databind.JsonNode acknowledgement = mapper.readTree(((TextMessage) sent.getValue()).getPayload());
        assertEquals("chunk-ack", acknowledgement.get("type").asText());
        assertEquals(1, acknowledgement.get("sequence").asLong());
        assertEquals(1, rooms.room(info.sessionId()).persistedSequence());
    }

    private boolean waitForState(Thread thread, Thread.State expected) throws InterruptedException {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(1);
        while (System.nanoTime() < deadline) {
            if (thread.getState() == expected) return true;
            Thread.sleep(1);
        }
        return thread.getState() == expected;
    }

    private BinaryMessage binary(byte[] bytes) {
        return new BinaryMessage(ByteBuffer.wrap(bytes));
    }

    private byte[] bytes(BinaryMessage message) {
        ByteBuffer buffer = message.getPayload().asReadOnlyBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }
}
