package com.example.location.app.admin;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminTokenService {
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final String ISSUER = "photo-genius-admin";

    private final AdminProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public AdminTokenService(AdminProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, Clock.systemUTC());
    }

    AdminTokenService(AdminProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public IssuedToken issueAdminToken(String username) {
        return issue(username, "ADMIN", Duration.ofSeconds(properties.tokenTtlSeconds()));
    }

    IssuedToken issue(String username, String role, Duration lifetime) {
        if (!properties.isConfigured()) {
            throw unavailable();
        }
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(lifetime);
        Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", ISSUER);
        payload.put("sub", username);
        payload.put("role", role);
        payload.put("iat", issuedAt.getEpochSecond());
        payload.put("exp", expiresAt.getEpochSecond());
        payload.put("jti", UUID.randomUUID().toString());

        try {
            String encodedHeader = encodeJson(header);
            String encodedPayload = encodeJson(payload);
            String signingInput = encodedHeader + "." + encodedPayload;
            String signature = URL_ENCODER.encodeToString(sign(signingInput));
            return new IssuedToken(signingInput + "." + signature, expiresAt);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not create admin token", exception);
        }
    }

    public AdminPrincipal verify(String token) {
        if (!properties.isConfigured() || token == null || token.length() > 4096) {
            throw invalidToken();
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) {
            throw invalidToken();
        }
        try {
            String signingInput = parts[0] + "." + parts[1];
            byte[] suppliedSignature = URL_DECODER.decode(parts[2]);
            if (!MessageDigest.isEqual(sign(signingInput), suppliedSignature)) {
                throw invalidToken();
            }

            JsonNode header = objectMapper.readTree(URL_DECODER.decode(parts[0]));
            JsonNode payload = objectMapper.readTree(URL_DECODER.decode(parts[1]));
            if (!"HS256".equals(header.path("alg").asText())
                    || !ISSUER.equals(payload.path("iss").asText())) {
                throw invalidToken();
            }

            String username = payload.path("sub").asText("");
            String role = payload.path("role").asText("");
            long expiresAt = payload.path("exp").asLong(0);
            long issuedAt = payload.path("iat").asLong(0);
            long now = clock.instant().getEpochSecond();
            if (username.isBlank() || role.isBlank() || issuedAt <= 0 || expiresAt <= now || issuedAt > now + 30) {
                throw invalidToken();
            }
            return new AdminPrincipal(username, role, Instant.ofEpochSecond(expiresAt));
        } catch (IllegalArgumentException | JacksonException exception) {
            throw invalidToken();
        }
    }

    private String encodeJson(Object value) throws JacksonException {
        return URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }

    private AdminApiException invalidToken() {
        return new AdminApiException(
                org.springframework.http.HttpStatus.UNAUTHORIZED,
                "Authentication required",
                "A valid Bearer token is required."
        );
    }

    private AdminApiException unavailable() {
        return new AdminApiException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "Admin authentication unavailable",
                "Admin authentication is not configured."
        );
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }

    public record AdminPrincipal(String username, String role, Instant expiresAt) {
    }
}
