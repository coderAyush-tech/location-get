package com.example.location.app.admin;

import com.mongodb.client.result.DeleteResult;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;
import java.io.ByteArrayInputStream;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MongoAdminCaptureStoreTests {
    @Mock
    private MongoTemplate mongoTemplate;

    @Test
    void performsSearchFilterPaginationAndNewestFirstSortInMongo() {
        when(mongoTemplate.find(any(Query.class), eq(MongoAdminCaptureStore.MongoCaptureMetadata.class),
                eq("captured_photos"))).thenReturn(List.of());
        when(mongoTemplate.count(any(Query.class), eq("captured_photos"))).thenReturn(0L);
        MongoAdminCaptureStore store = new MongoAdminCaptureStore(mongoTemplate);

        store.find(new AdminCaptureQuery(
                2,
                20,
                "Delhi",
                AdminCaptureQuery.LocationSourceFilter.GEO_IP
        ));

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(
                queryCaptor.capture(),
                eq(MongoAdminCaptureStore.MongoCaptureMetadata.class),
                eq("captured_photos")
        );
        Query query = queryCaptor.getValue();
        assertEquals(40, query.getSkip());
        assertEquals(20, query.getLimit());
        assertEquals(-1, query.getSortObject().get("savedAt"));
        assertEquals(-1, query.getSortObject().get("_id"));
        Document filter = query.getQueryObject();
        assertTrue(filter.toJson().contains("originalFilename"));
        assertTrue(filter.toJson().contains("clientIp"));
        assertTrue(filter.toJson().contains("address"));
        assertTrue(filter.toJson().contains("locationSource"));
        assertFalse(query.getFieldsObject().isEmpty());
        assertEquals(0, query.getFieldsObject().get("video"));
    }

    @Test
    void streamsGridFsVideoByStoredReference() {
        MongoAdminCaptureStore.MongoVideoContent content = new MongoAdminCaptureStore.MongoVideoContent();
        content.videoGridFsId = "507f1f77bcf86cd799439011";
        when(mongoTemplate.findOne(any(Query.class), eq(MongoAdminCaptureStore.MongoVideoContent.class),
                eq("captured_photos"))).thenReturn(content);
        GridFsVideoStore videos = org.mockito.Mockito.mock(GridFsVideoStore.class);
        when(videos.openDownload(content.videoGridFsId)).thenReturn(Optional.of(new GridFsVideoStore.StoredVideo(
                new ByteArrayInputStream(new byte[]{1, 2, 3}), 3, "video/webm")));
        MongoAdminCaptureStore store = new MongoAdminCaptureStore(mongoTemplate, java.time.Clock.systemUTC(), null, videos);

        AdminStoredVideo video = store.findVideo("capture-123").orElseThrow();

        assertEquals("video/webm", video.contentType());
        assertEquals(3, video.sizeBytes());
        assertTrue(video.stream() instanceof ByteArrayInputStream);
        verify(videos).openDownload(content.videoGridFsId);
    }

    @Test
    void deletesOnlyTheDocumentWithTheExactCaptureId() {
        when(mongoTemplate.remove(any(Query.class), eq("captured_photos")))
                .thenReturn(DeleteResult.acknowledged(1));
        MongoAdminCaptureStore store = new MongoAdminCaptureStore(mongoTemplate);

        AdminCaptureStore.DeleteOutcome outcome = store.deleteExact("capture-123");

        assertEquals(AdminCaptureStore.DeleteOutcome.DELETED, outcome);
        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).remove(queryCaptor.capture(), eq("captured_photos"));
        Document query = queryCaptor.getValue().getQueryObject();
        assertEquals(1, query.size());
        assertEquals("capture-123", query.get("_id"));
    }

    @Test
    void deletingCaptureAlsoDeletesItsGridFsVideo() {
        MongoAdminCaptureStore.MongoVideoContent content = new MongoAdminCaptureStore.MongoVideoContent();
        content.videoGridFsId = "507f1f77bcf86cd799439011";
        when(mongoTemplate.findOne(any(Query.class), eq(MongoAdminCaptureStore.MongoVideoContent.class),
                eq("captured_photos"))).thenReturn(content);
        when(mongoTemplate.remove(any(Query.class), eq("captured_photos")))
                .thenReturn(DeleteResult.acknowledged(1));
        GridFsVideoStore videos = org.mockito.Mockito.mock(GridFsVideoStore.class);
        MongoAdminCaptureStore store = new MongoAdminCaptureStore(
                mongoTemplate, java.time.Clock.systemUTC(), null, videos);

        assertEquals(AdminCaptureStore.DeleteOutcome.DELETED, store.deleteExact("capture-123"));

        verify(videos).delete(content.videoGridFsId);
    }

    @Test
    void zeroDeletedDocumentsIsReportedAsNotFound() {
        when(mongoTemplate.remove(any(Query.class), eq("captured_photos")))
                .thenReturn(DeleteResult.acknowledged(0));
        MongoAdminCaptureStore store = new MongoAdminCaptureStore(mongoTemplate);

        assertEquals(AdminCaptureStore.DeleteOutcome.NOT_FOUND, store.deleteExact("missing"));
    }

    @Test
    void unacknowledgedDeleteNeverReportsSuccess() {
        when(mongoTemplate.remove(any(Query.class), eq("captured_photos")))
                .thenReturn(DeleteResult.unacknowledged());
        MongoAdminCaptureStore store = new MongoAdminCaptureStore(mongoTemplate);

        assertThrows(AdminCaptureDeleteConflictException.class, () -> store.deleteExact("capture-123"));
    }
}
