package com.example.location.app.admin;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

import java.nio.ByteBuffer;

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
        assertFalse(rooms.close(first.sessionId(), "wrong-token"));
        assertNotNull(rooms.room(first.sessionId()));
        assertTrue(rooms.close(first.sessionId(), first.publishToken()));
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
        assertTrue(rooms.close(info.sessionId(), info.publishToken()));
        assertNull(rooms.room(info.sessionId()));
        assertTrue(rooms.activeRooms().isEmpty());
        verify(publisher).close();
        verify(listener, never()).close();
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
