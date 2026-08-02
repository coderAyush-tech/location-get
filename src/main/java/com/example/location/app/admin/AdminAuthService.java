package com.example.location.app.admin;

import com.example.location.app.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class AdminAuthService {
    private final AdminProperties properties;
    private final AdminTokenService tokenService;
    private final AdminLoginRateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AdminAuthService(
            AdminProperties properties,
            AdminTokenService tokenService,
            AdminLoginRateLimiter rateLimiter,
            ClientIpResolver clientIpResolver
    ) {
        this.properties = properties;
        this.tokenService = tokenService;
        this.rateLimiter = rateLimiter;
        this.clientIpResolver = clientIpResolver;
    }

    public AdminLoginResponse login(AdminLoginRequest request, HttpServletRequest servletRequest) {
        if (!properties.isConfigured()) {
            throw new AdminApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Admin authentication unavailable",
                    "Admin authentication is not configured."
            );
        }

        String suppliedUsername = request == null ? null : request.username();
        String suppliedPassword = request == null ? null : request.password();
        String clientIp = clientIpResolver.resolve(servletRequest);
        rateLimiter.assertAllowed(clientIp, suppliedUsername);

        boolean usernameMatches = constantTimeEquals(properties.username(), suppliedUsername);
        boolean passwordMatches;
        try {
            // Always perform the same BCrypt comparison, including for an unknown username.
            passwordMatches = passwordEncoder.matches(
                    suppliedPassword == null ? "" : suppliedPassword,
                    properties.passwordHash()
            );
        } catch (IllegalArgumentException exception) {
            passwordMatches = false;
        }

        if (!usernameMatches || !passwordMatches) {
            rateLimiter.recordFailure(clientIp, suppliedUsername);
            throw new AdminApiException(
                    HttpStatus.UNAUTHORIZED,
                    "Login failed",
                    "Invalid username or password."
            );
        }

        AdminTokenService.IssuedToken token = tokenService.issueAdminToken(properties.username());
        return new AdminLoginResponse(
                token.value(),
                "Bearer",
                properties.tokenTtlSeconds(),
                new AdminLoginResponse.AdminIdentity(properties.username())
        );
    }

    private boolean constantTimeEquals(String expected, String supplied) {
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] suppliedBytes = (supplied == null ? "" : supplied).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, suppliedBytes);
    }
}
