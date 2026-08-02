package com.example.location.app.admin;

import java.util.List;
import java.util.Optional;

interface AdminCaptureStore {
    CaptureSlice find(AdminCaptureQuery query);

    AdminCaptureSummary summary();

    Optional<AdminStoredPhoto> findPhoto(String captureId);

    record CaptureSlice(List<AdminCaptureMetadata> content, long totalElements) {
    }
}
