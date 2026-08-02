package com.example.location.app.admin;

public record AdminLoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        AdminIdentity admin
) {
    public record AdminIdentity(String username) {
    }
}
