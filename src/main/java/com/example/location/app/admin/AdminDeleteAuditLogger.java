package com.example.location.app.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
public class AdminDeleteAuditLogger {
    private static final Logger log = LoggerFactory.getLogger(AdminDeleteAuditLogger.class);

    private final Clock clock;

    @Autowired
    public AdminDeleteAuditLogger() {
        this(Clock.systemUTC());
    }

    AdminDeleteAuditLogger(Clock clock) {
        this.clock = clock;
    }

    public void record(String adminUsername, String captureId, String clientIp, DeleteAuditResult result) {
        log.info(
                "ADMIN_CAPTURE_DELETE admin={} captureId={} timestamp={} clientIp={} result={}",
                safe(adminUsername),
                safe(captureId),
                clock.instant(),
                safe(clientIp),
                result
        );
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String sanitized = value.replace('\r', '_').replace('\n', '_').replace('\t', '_').trim();
        return sanitized.length() <= 200 ? sanitized : sanitized.substring(0, 200);
    }

    public enum DeleteAuditResult {
        SUCCESS,
        NOT_FOUND,
        CONFLICT
    }
}
