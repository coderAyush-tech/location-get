package com.example.location.app.admin;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminCaptureDeleteServiceTests {

    @Test
    void deletesTheExactCaptureAndItsEmbeddedPhotoOnly() {
        TrackingStore store = new TrackingStore();
        store.photos.put("target", photo(1));
        store.photos.put("keep", photo(2));
        AdminDeleteAuditLogger audit = mock(AdminDeleteAuditLogger.class);
        AdminCaptureDeleteService service = new AdminCaptureDeleteService(store, audit);

        service.delete("target", "admin", "203.0.113.8");

        assertFalse(store.photos.containsKey("target"));
        assertTrue(store.photos.containsKey("keep"));
        assertEquals(1, store.deleteCalls);
        verify(audit).record(
                "admin",
                "target",
                "203.0.113.8",
                AdminDeleteAuditLogger.DeleteAuditResult.SUCCESS
        );
    }

    @Test
    void unknownCaptureReturns404AndDoesNotDeleteAnotherCapture() {
        TrackingStore store = new TrackingStore();
        store.photos.put("keep", photo(2));
        AdminDeleteAuditLogger audit = mock(AdminDeleteAuditLogger.class);
        AdminCaptureDeleteService service = new AdminCaptureDeleteService(store, audit);

        AdminApiException exception = assertThrows(
                AdminApiException.class,
                () -> service.delete("missing", "admin", "203.0.113.8")
        );

        assertEquals(404, exception.getStatus().value());
        assertTrue(store.photos.containsKey("keep"));
        verify(audit).record(
                "admin",
                "missing",
                "203.0.113.8",
                AdminDeleteAuditLogger.DeleteAuditResult.NOT_FOUND
        );
    }

    @Test
    void storageFailureReturns409AndNeverReportsSuccess() {
        AdminCaptureStore store = mock(AdminCaptureStore.class);
        when(store.deleteExact("target")).thenThrow(new AdminCaptureDeleteConflictException("failed"));
        AdminDeleteAuditLogger audit = mock(AdminDeleteAuditLogger.class);
        AdminCaptureDeleteService service = new AdminCaptureDeleteService(store, audit);

        AdminApiException exception = assertThrows(
                AdminApiException.class,
                () -> service.delete("target", "admin", "203.0.113.8")
        );

        assertEquals(409, exception.getStatus().value());
        verify(audit).record(
                "admin",
                "target",
                "203.0.113.8",
                AdminDeleteAuditLogger.DeleteAuditResult.CONFLICT
        );
        verify(audit, never()).record(
                anyString(),
                anyString(),
                anyString(),
                eq(AdminDeleteAuditLogger.DeleteAuditResult.SUCCESS)
        );
    }

    private static AdminStoredPhoto photo(int marker) {
        return new AdminStoredPhoto(new byte[]{(byte) marker}, "image/jpeg", "camera.jpg");
    }

    private static class TrackingStore implements AdminCaptureStore {
        private final Map<String, AdminStoredPhoto> photos = new LinkedHashMap<>();
        private int deleteCalls;

        @Override
        public CaptureSlice find(AdminCaptureQuery query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AdminCaptureSummary summary() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<AdminStoredPhoto> findPhoto(String captureId) {
            return Optional.ofNullable(photos.get(captureId));
        }

        @Override
        public DeleteOutcome deleteExact(String captureId) {
            deleteCalls++;
            return photos.remove(captureId) == null ? DeleteOutcome.NOT_FOUND : DeleteOutcome.DELETED;
        }
    }
}
