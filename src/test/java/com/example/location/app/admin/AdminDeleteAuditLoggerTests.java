package com.example.location.app.admin;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminDeleteAuditLoggerTests {

    @Test
    void auditContainsRequiredMetadataWithoutLogInjectionOrSensitiveContent() {
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(AdminDeleteAuditLogger.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            AdminDeleteAuditLogger audit = new AdminDeleteAuditLogger(Clock.fixed(
                    Instant.parse("2026-08-02T10:15:30Z"),
                    ZoneOffset.UTC
            ));

            audit.record(
                    "admin\nforged",
                    "capture\r123",
                    "203.0.113.8",
                    AdminDeleteAuditLogger.DeleteAuditResult.SUCCESS
            );

            String message = appender.list.getFirst().getFormattedMessage();
            assertTrue(message.contains("admin=admin_forged"));
            assertTrue(message.contains("captureId=capture_123"));
            assertTrue(message.contains("timestamp=2026-08-02T10:15:30Z"));
            assertTrue(message.contains("clientIp=203.0.113.8"));
            assertTrue(message.contains("result=SUCCESS"));
            assertFalse(message.contains("Bearer "));
            assertFalse(message.contains("photo="));
        } finally {
            logger.detachAppender(appender);
        }
    }
}
