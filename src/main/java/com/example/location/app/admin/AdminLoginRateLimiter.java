package com.example.location.app.admin;

import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class AdminLoginRateLimiter {
    private final Map<String, Deque<Instant>> failures = new HashMap<>();
    private final AdminProperties properties;
    private final Clock clock;

    @Autowired
    public AdminLoginRateLimiter(AdminProperties properties) {
        this(properties, Clock.systemUTC());
    }

    AdminLoginRateLimiter(AdminProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public synchronized void assertAllowed(String clientIp, String username) {
        Instant now = clock.instant();
        long ipRetry = retryAfterSeconds(ipKey(clientIp), now);
        long usernameRetry = retryAfterSeconds(usernameKey(username), now);
        long retryAfter = Math.max(ipRetry, usernameRetry);
        if (retryAfter > 0) {
            throw new AdminApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Too many login attempts",
                    "Too many login attempts. Try again later.",
                    retryAfter
            );
        }
    }

    public synchronized void recordFailure(String clientIp, String username) {
        Instant now = clock.instant();
        addFailure(ipKey(clientIp), now);
        addFailure(usernameKey(username), now);
    }

    private void addFailure(String key, Instant now) {
        Deque<Instant> attempts = failures.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        prune(attempts, now);
        attempts.addLast(now);
    }

    private long retryAfterSeconds(String key, Instant now) {
        Deque<Instant> attempts = failures.get(key);
        if (attempts == null) {
            return 0;
        }
        prune(attempts, now);
        if (attempts.isEmpty()) {
            failures.remove(key);
            return 0;
        }
        if (attempts.size() < properties.maxLoginFailures()) {
            return 0;
        }
        Instant allowedAt = attempts.peekFirst().plusSeconds(properties.loginWindowSeconds());
        return Math.max(1, allowedAt.getEpochSecond() - now.getEpochSecond());
    }

    private void prune(Deque<Instant> attempts, Instant now) {
        Instant cutoff = now.minusSeconds(properties.loginWindowSeconds());
        while (!attempts.isEmpty() && !attempts.peekFirst().isAfter(cutoff)) {
            attempts.removeFirst();
        }
    }

    private String ipKey(String clientIp) {
        return "ip:" + normalized(clientIp);
    }

    private String usernameKey(String username) {
        return "username:" + normalized(username).toLowerCase(Locale.ROOT);
    }

    private String normalized(String value) {
        return value == null || value.isBlank() ? "<empty>" : value.trim();
    }
}
