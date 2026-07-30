package com.example.location.app.health;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeepAliveServiceTests {
    @Mock
    private MongoTemplate mongoTemplate;

    private KeepAliveService service;

    @BeforeEach
    void setUp() {
        service = new KeepAliveService(mongoTemplate, "test-keep-alive-token");
    }

    @Test
    void validTokenPingsMongoWithoutWritingData() {
        when(mongoTemplate.executeCommand(any(Document.class)))
                .thenReturn(new Document("ok", 1.0));

        KeepAliveResponse response = service.ping("test-keep-alive-token");

        assertEquals("up", response.backend());
        assertEquals("up", response.mongodb());
        verify(mongoTemplate).executeCommand(new Document("ping", 1));
    }

    @Test
    void invalidTokenIsRejectedBeforeMongoCall() {
        KeepAliveException exception = assertThrows(
                KeepAliveException.class,
                () -> service.ping("wrong-token")
        );

        assertEquals(401, exception.getStatus().value());
        verify(mongoTemplate, never()).executeCommand(any(Document.class));
    }

    @Test
    void missingConfigurationFailsClosed() {
        KeepAliveService unconfigured = new KeepAliveService(mongoTemplate, "");

        KeepAliveException exception = assertThrows(
                KeepAliveException.class,
                () -> unconfigured.ping("anything")
        );

        assertEquals(503, exception.getStatus().value());
        verify(mongoTemplate, never()).executeCommand(any(Document.class));
    }

    @Test
    void mongoFailureReturnsServiceUnavailable() {
        when(mongoTemplate.executeCommand(any(Document.class)))
                .thenThrow(new DataAccessResourceFailureException("connection failed"));

        KeepAliveException exception = assertThrows(
                KeepAliveException.class,
                () -> service.ping("test-keep-alive-token")
        );

        assertEquals(503, exception.getStatus().value());
        assertEquals("MongoDB is unavailable.", exception.getMessage());
    }
}
