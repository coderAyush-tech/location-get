package com.example.location.app.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class AdminProperties {
    private final String username;
    private final String passwordHash;
    private final String jwtSecret;
    private final long tokenTtlSeconds;
    private final int maxLoginFailures;
    private final long loginWindowSeconds;

    public AdminProperties(
            @Value("${app.admin.username:}") String username,
            @Value("${app.admin.password-hash:}") String passwordHash,
            @Value("${app.admin.jwt-secret:}") String jwtSecret,
            @Value("${app.admin.token-ttl-seconds:900}") long tokenTtlSeconds,
            @Value("${app.admin.login.max-failures:5}") int maxLoginFailures,
            @Value("${app.admin.login.window-seconds:900}") long loginWindowSeconds
    ) {
        this.username = username == null ? "" : username.trim();
        this.passwordHash = passwordHash == null ? "" : passwordHash.trim();
        this.jwtSecret = jwtSecret == null ? "" : jwtSecret;
        this.tokenTtlSeconds = tokenTtlSeconds;
        this.maxLoginFailures = maxLoginFailures;
        this.loginWindowSeconds = loginWindowSeconds;
    }

    public boolean isConfigured() {
        return !username.isBlank()
                && !passwordHash.isBlank()
                && jwtSecret.getBytes(StandardCharsets.UTF_8).length >= 32
                && tokenTtlSeconds >= 60
                && tokenTtlSeconds <= 3600
                && maxLoginFailures > 0
                && loginWindowSeconds > 0;
    }

    public String username() {
        return username;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public String jwtSecret() {
        return jwtSecret;
    }

    public long tokenTtlSeconds() {
        return tokenTtlSeconds;
    }

    public int maxLoginFailures() {
        return maxLoginFailures;
    }

    public long loginWindowSeconds() {
        return loginWindowSeconds;
    }
}
