package com.example.location.app.admin;

import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSUploadStream;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GridFsVideoStoreTests {
    @Test
    void writesManyChunksWithoutAnApplicationFileSizeCapAndFinalizesMetadata() throws Exception {
        GridFSBucket bucket = mock(GridFSBucket.class);
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        GridFSUploadStream upload = mock(GridFSUploadStream.class);
        when(upload.getObjectId()).thenReturn(new ObjectId("507f1f77bcf86cd799439011"));
        when(bucket.openUploadStream(eq("session-1.webm"), any(GridFSUploadOptions.class))).thenReturn(upload);
        GridFsVideoStore store = new GridFsVideoStore(bucket, mongoTemplate);
        VideoRecordingWriter writer = store.open("session-1", "secret-token");

        byte[] chunk = new byte[256 * 1024];
        for (int index = 0; index < 32; index++) writer.append(chunk);

        assertEquals(8L * 1024 * 1024, writer.bytesWritten());
        writer.finish(true);

        verify(upload, org.mockito.Mockito.times(32)).write(chunk);
        verify(upload).close();
        verify(upload, never()).abort();
        verify(mongoTemplate).updateMulti(any(Query.class), any(Update.class), eq("captured_photos"));
    }

    @Test
    void abortsUnlinkedRecordingInsteadOfLeavingAnOrphanGridFsFile() {
        GridFSBucket bucket = mock(GridFSBucket.class);
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        GridFSUploadStream upload = mock(GridFSUploadStream.class);
        when(upload.getObjectId()).thenReturn(new ObjectId("507f1f77bcf86cd799439011"));
        when(bucket.openUploadStream(eq("session-2.webm"), any(GridFSUploadOptions.class))).thenReturn(upload);
        GridFsVideoStore store = new GridFsVideoStore(bucket, mongoTemplate);
        VideoRecordingWriter writer = store.open("session-2", "secret-token");
        writer.finish(false);

        verify(upload).abort();
        verify(upload, never()).close();
        verify(mongoTemplate, never()).updateMulti(any(Query.class), any(Update.class), eq("captured_photos"));
    }

    @Test
    void retriesFinalizationMetadataAfterGridFsStreamWasAlreadyClosed() throws Exception {
        GridFSBucket bucket = mock(GridFSBucket.class);
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        GridFSUploadStream upload = mock(GridFSUploadStream.class);
        when(upload.getObjectId()).thenReturn(new ObjectId("507f1f77bcf86cd799439011"));
        when(bucket.openUploadStream(eq("session-3.webm"), any(GridFSUploadOptions.class))).thenReturn(upload);
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq("captured_photos")))
                .thenThrow(new IllegalStateException("temporary database failure"))
                .thenReturn(null);
        GridFsVideoStore store = new GridFsVideoStore(bucket, mongoTemplate);
        VideoRecordingWriter writer = store.open("session-3", "secret-token");
        writer.append(new byte[]{1, 2, 3});

        assertThrows(IllegalStateException.class, () -> writer.finish(true));
        writer.finish(true);

        verify(upload).close();
        verify(mongoTemplate, org.mockito.Mockito.times(2))
                .updateMulti(any(Query.class), any(Update.class), eq("captured_photos"));
    }
}
