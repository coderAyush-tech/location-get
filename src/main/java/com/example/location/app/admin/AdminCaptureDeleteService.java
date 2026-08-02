package com.example.location.app.admin;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AdminCaptureDeleteService {
    private final AdminCaptureStore store;
    private final AdminDeleteAuditLogger auditLogger;

    public AdminCaptureDeleteService(AdminCaptureStore store, AdminDeleteAuditLogger auditLogger) {
        this.store = store;
        this.auditLogger = auditLogger;
    }

    public void delete(String captureId, String adminUsername, String clientIp) {
        String normalizedId = normalizeId(captureId);
        if (normalizedId == null) {
            auditLogger.record(
                    adminUsername,
                    captureId,
                    clientIp,
                    AdminDeleteAuditLogger.DeleteAuditResult.NOT_FOUND
            );
            throw notFound();
        }

        AdminCaptureStore.DeleteOutcome outcome;
        try {
            outcome = store.deleteExact(normalizedId);
        } catch (RuntimeException exception) {
            throw conflict(adminUsername, normalizedId, clientIp);
        }

        if (outcome == AdminCaptureStore.DeleteOutcome.NOT_FOUND) {
            auditLogger.record(
                    adminUsername,
                    normalizedId,
                    clientIp,
                    AdminDeleteAuditLogger.DeleteAuditResult.NOT_FOUND
            );
            throw notFound();
        }
        if (outcome != AdminCaptureStore.DeleteOutcome.DELETED) {
            throw conflict(adminUsername, normalizedId, clientIp);
        }

        auditLogger.record(
                adminUsername,
                normalizedId,
                clientIp,
                AdminDeleteAuditLogger.DeleteAuditResult.SUCCESS
        );
    }

    private String normalizeId(String captureId) {
        if (captureId == null || captureId.isBlank() || captureId.length() > 200) {
            return null;
        }
        return captureId.trim();
    }

    private AdminApiException notFound() {
        return new AdminApiException(
                HttpStatus.NOT_FOUND,
                "Capture not found",
                "Capture was not found."
        );
    }

    private AdminApiException conflict(String adminUsername, String captureId, String clientIp) {
        auditLogger.record(
                adminUsername,
                captureId,
                clientIp,
                AdminDeleteAuditLogger.DeleteAuditResult.CONFLICT
        );
        return new AdminApiException(
                HttpStatus.CONFLICT,
                "Capture deletion conflict",
                "Capture could not be safely deleted because storage is temporarily inconsistent."
        );
    }
}
